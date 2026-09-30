-- =============================================================================
-- Migration 1 (Phase 1): doctor accounts, licence verification, audit log.
--
-- Plain-language summary
--   * Every sign-up in Supabase Auth automatically gets a row in `doctors`.
--   * New doctors start as 'pending'. Only an administrator can change that,
--     through the function admin_set_verification().
--   * Doctors can edit their own profile, but can never change their own
--     role or verification status (column-level permissions block it).
--   * Changing your licence number or licence document sends a verified or
--     rejected account back to 'pending' for re-checking.
--   * `audit_log` is append-only: nobody (not even admins) can edit or delete
--     rows through the API. Rows are written only by trusted database code.
-- =============================================================================

create type public.verification_status as enum ('pending', 'verified', 'rejected', 'suspended');
create type public.app_role as enum ('doctor', 'admin');
create type public.audit_action as enum
    ('view', 'create', 'update', 'delete', 'share', 'revoke', 'close', 'export', 'verify');

-- -----------------------------------------------------------------------------
-- doctors: one row per user account
-- -----------------------------------------------------------------------------
create table public.doctors (
    id                    uuid primary key references auth.users (id) on delete cascade,
    email                 text not null,
    full_name             text not null default '' check (char_length(full_name) <= 120),
    specialty             text check (char_length(specialty) <= 80),
    hospital              text check (char_length(hospital) <= 120),
    license_number        text check (char_length(license_number) <= 40),
    phone                 text check (char_length(phone) <= 20),
    photo_path            text check (char_length(photo_path) <= 300),
    license_document_path text check (char_length(license_document_path) <= 300),
    preferred_language    text not null default 'en' check (preferred_language in ('en', 'ar')),
    role                  public.app_role not null default 'doctor',
    verification_status   public.verification_status not null default 'pending',
    verification_note     text check (char_length(verification_note) <= 500),
    verified_at           timestamptz,
    verified_by           uuid references public.doctors (id) on delete set null,
    created_at            timestamptz not null default now(),
    updated_at            timestamptz not null default now()
);

comment on table public.doctors is 'Doctor profiles. One row per auth user, created automatically on sign-up.';

-- A licence number can belong to only one account.
create unique index doctors_license_number_key
    on public.doctors (lower(license_number))
    where license_number is not null and license_number <> '';

create index doctors_verification_status_idx on public.doctors (verification_status, created_at);

-- -----------------------------------------------------------------------------
-- audit_log: who did what to which record, and when
-- -----------------------------------------------------------------------------
create table public.audit_log (
    id          bigint generated always as identity primary key,
    occurred_at timestamptz not null default now(),
    actor_id    uuid,          -- no foreign key: audit rows must outlive deleted accounts
    action      public.audit_action not null,
    table_name  text not null,
    record_id   uuid,
    patient_id  uuid,          -- no foreign key, for the same reason
    -- Only metadata goes here (e.g. names of changed columns, shared section
    -- names). Never copy clinical values into the audit log.
    details     jsonb not null default '{}'::jsonb
);

comment on table public.audit_log is 'Append-only audit trail. Written only by security-definer functions and triggers.';

create index audit_log_patient_idx on public.audit_log (patient_id, occurred_at desc);
create index audit_log_actor_idx on public.audit_log (actor_id, occurred_at desc);

-- -----------------------------------------------------------------------------
-- Helper functions
--
-- "security definer" functions run with the rights of their owner, so they can
-- look things up that the caller cannot see directly. Each one pins
-- search_path to '' and uses fully qualified names, which blocks a known
-- attack where a caller creates look-alike tables in another schema.
-- -----------------------------------------------------------------------------

create function public.set_updated_at() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    new.updated_at := now();
    return new;
end;
$$;

-- Is this doctor verified? (Defaults to the current user.)
create function public.is_verified_doctor(p_doctor_id uuid default auth.uid()) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.doctors
        where id = p_doctor_id and verification_status = 'verified'
    );
$$;

-- Is the current user a verified administrator?
create function public.is_admin() returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.doctors
        where id = auth.uid() and role = 'admin' and verification_status = 'verified'
    );
$$;

-- Internal: append a row to the audit log. Not callable from the app.
create function public.write_audit(
    p_action     public.audit_action,
    p_table_name text,
    p_record_id  uuid,
    p_patient_id uuid,
    p_details    jsonb default '{}'::jsonb
) returns void
language sql security definer
set search_path = ''
as $$
    insert into public.audit_log (actor_id, action, table_name, record_id, patient_id, details)
    values (auth.uid(), p_action, p_table_name, p_record_id, p_patient_id, coalesce(p_details, '{}'::jsonb));
$$;

-- Generic trigger: logs every insert/update/delete on the table it is attached
-- to. For updates it records only the NAMES of the changed columns.
create function public.audit_row_change() returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
    v_row     jsonb := case when tg_op = 'DELETE' then to_jsonb(old) else to_jsonb(new) end;
    v_changed text[];
    v_patient uuid;
begin
    if tg_op = 'UPDATE' then
        select coalesce(array_agg(n.key order by n.key), '{}')
          into v_changed
          from jsonb_each(to_jsonb(new)) n
          join jsonb_each(to_jsonb(old)) o using (key)
         where n.value is distinct from o.value
           and n.key not in ('updated_at');
        if cardinality(v_changed) = 0 then
            return new; -- nothing really changed
        end if;
    end if;

    v_patient := case
        when tg_table_name = 'patients' then (v_row ->> 'id')::uuid
        else (v_row ->> 'patient_id')::uuid
    end;

    perform public.write_audit(
        (case tg_op when 'INSERT' then 'create' when 'UPDATE' then 'update' else 'delete' end)::public.audit_action,
        tg_table_name,
        (v_row ->> 'id')::uuid,
        v_patient,
        case when tg_op = 'UPDATE' then jsonb_build_object('changed', to_jsonb(v_changed)) else '{}'::jsonb end
    );
    return case when tg_op = 'DELETE' then old else new end;
end;
$$;

-- -----------------------------------------------------------------------------
-- Create a doctor row automatically when someone signs up.
-- The app passes full_name and preferred_language as sign-up metadata.
-- -----------------------------------------------------------------------------
create function public.handle_new_user() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    insert into public.doctors (id, email, full_name, preferred_language)
    values (
        new.id,
        coalesce(new.email, ''),
        coalesce(left(trim(new.raw_user_meta_data ->> 'full_name'), 120), ''),
        case when new.raw_user_meta_data ->> 'preferred_language' = 'ar' then 'ar' else 'en' end
    );
    return new;
end;
$$;

create trigger on_auth_user_created
    after insert on auth.users
    for each row execute function public.handle_new_user();

-- Keep doctors.email in sync if the user changes their login email.
create function public.handle_user_email_change() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    update public.doctors set email = coalesce(new.email, '') where id = new.id;
    return new;
end;
$$;

create trigger on_auth_user_email_changed
    after update of email on auth.users
    for each row when (old.email is distinct from new.email)
    execute function public.handle_user_email_change();

-- Editing licence details sends the account back for re-verification.
create function public.doctors_require_reverification() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if (new.license_number is distinct from old.license_number
        or new.license_document_path is distinct from old.license_document_path)
       and old.verification_status in ('verified', 'rejected')
       and new.verification_status = old.verification_status then
        new.verification_status := 'pending';
        new.verified_at := null;
        new.verified_by := null;
    end if;
    return new;
end;
$$;

create trigger doctors_set_updated_at
    before update on public.doctors
    for each row execute function public.set_updated_at();

create trigger doctors_reverify
    before update on public.doctors
    for each row execute function public.doctors_require_reverification();

create trigger doctors_audit
    after update on public.doctors
    for each row execute function public.audit_row_change();

-- -----------------------------------------------------------------------------
-- Row-Level Security and permissions for doctors and audit_log
-- -----------------------------------------------------------------------------
alter table public.doctors enable row level security;
alter table public.audit_log enable row level security;

-- Start from nothing, then grant only what is needed.
revoke all on public.doctors from anon, authenticated;
revoke all on public.audit_log from anon, authenticated;

grant select on public.doctors to authenticated;
-- Doctors may update only these columns. role and verification_* are not in
-- the list, so the API rejects any attempt to change them.
grant update (full_name, specialty, hospital, license_number, phone, photo_path,
              license_document_path, preferred_language)
    on public.doctors to authenticated;

create policy doctors_select_own_or_admin on public.doctors
    for select to authenticated
    using (id = auth.uid() or public.is_admin());

create policy doctors_update_own on public.doctors
    for update to authenticated
    using (id = auth.uid())
    with check (id = auth.uid());

grant select on public.audit_log to authenticated;

-- Admins see the whole log; every doctor sees their own actions.
-- (Migration 2 adds: owners see all activity on their own patients.)
create policy audit_select_admin_or_self on public.audit_log
    for select to authenticated
    using (public.is_admin() or actor_id = auth.uid());

-- -----------------------------------------------------------------------------
-- Admin: approve / reject / suspend a doctor
-- -----------------------------------------------------------------------------
create function public.admin_set_verification(
    p_doctor_id uuid,
    p_status    public.verification_status,
    p_note      text default null
) returns void
language plpgsql security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'Only administrators can change verification status' using errcode = '42501';
    end if;
    if p_doctor_id = auth.uid() then
        raise exception 'Administrators cannot change their own verification status' using errcode = '42501';
    end if;

    update public.doctors
       set verification_status = p_status,
           verification_note   = left(p_note, 500),
           verified_at         = case when p_status = 'verified' then now() end,
           verified_by         = auth.uid()
     where id = p_doctor_id;

    if not found then
        raise exception 'Doctor not found' using errcode = 'P0002';
    end if;

    perform public.write_audit('verify', 'doctors', p_doctor_id, null,
                               jsonb_build_object('status', p_status));
end;
$$;

-- -----------------------------------------------------------------------------
-- Doctor directory (used by "Consult" in Phase 4)
-- Returns only public, non-sensitive fields of verified doctors, and only to
-- callers who are verified themselves. Licence number and phone are never
-- returned.
-- -----------------------------------------------------------------------------
create function public.search_doctors(p_query text default '', p_limit int default 30)
returns table (id uuid, full_name text, specialty text, hospital text, photo_path text)
language sql stable security definer
set search_path = ''
as $$
    with q as (
        -- Escape LIKE wildcards so '%' or '_' typed by the user match literally.
        select '%' || replace(replace(replace(coalesce(trim(p_query), ''), '\', '\\'), '%', '\%'), '_', '\_') || '%' as pattern
    )
    select d.id, d.full_name, d.specialty, d.hospital, d.photo_path
      from public.doctors d, q
     where public.is_verified_doctor()
       and d.verification_status = 'verified'
       and d.id <> auth.uid()
       and (d.full_name ilike q.pattern or d.specialty ilike q.pattern or d.hospital ilike q.pattern)
     order by d.full_name
     limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;

-- Function permissions: nothing for anonymous visitors, internal helpers for nobody.
revoke execute on function public.write_audit(public.audit_action, text, uuid, uuid, jsonb) from public, anon, authenticated;
revoke execute on function public.audit_row_change() from public, anon, authenticated;
revoke execute on function public.handle_new_user() from public, anon, authenticated;
revoke execute on function public.handle_user_email_change() from public, anon, authenticated;
revoke execute on function public.admin_set_verification(uuid, public.verification_status, text) from public, anon;
revoke execute on function public.search_doctors(text, int) from public, anon;
revoke execute on function public.is_admin() from public, anon;
revoke execute on function public.is_verified_doctor(uuid) from public, anon;
grant execute on function public.admin_set_verification(uuid, public.verification_status, text) to authenticated;
grant execute on function public.search_doctors(text, int) to authenticated;
grant execute on function public.is_admin() to authenticated;
grant execute on function public.is_verified_doctor(uuid) to authenticated;
