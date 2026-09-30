-- =============================================================================
-- Migration 5 (Phase 2): referrals (co-management / transfer), soft delete of
-- record entries, and server functions for offline sync.
--
-- Plain-language summary
--   * REFERRAL = a lasting permission, unlike a consult (temporary, read-only).
--     - co-management: the colleague reads and edits the whole record like a
--       team member. They cannot delete the patient, transfer or share it.
--       Either doctor can end it; there is no expiry.
--     - transfer: on acceptance the colleague becomes the owner. The original
--       doctor keeps READ-ONLY access to entries recorded up to the transfer
--       time, and nothing added afterwards.
--     - Both need patient consent (checkbox + date), and the colleague must
--       ACCEPT before getting any access. Both doctors must be verified.
--   * Closing or revoking a consult never affects a referral: they are separate.
--   * Record entries are never hard-deleted any more: "delete" sets deleted_at
--     (entered in error). This keeps the medical record and lets offline
--     phones learn about deletions.
--   * sync_pull() / my_patient_ids() let the app keep an encrypted offline copy
--     of the patients it may edit (owned + co-managed).
-- =============================================================================

alter type public.audit_action add value if not exists 'refer';
alter type public.audit_action add value if not exists 'transfer';

create type public.referral_kind as enum ('comanagement', 'transfer');
create type public.referral_status as enum ('pending', 'accepted', 'declined', 'cancelled', 'ended');

create table public.referrals (
    id                uuid primary key default gen_random_uuid(),
    patient_id        uuid not null references public.patients (id) on delete cascade,
    from_doctor_id    uuid not null references public.doctors (id),
    to_doctor_id      uuid not null references public.doctors (id),
    kind              public.referral_kind not null,
    note              text check (char_length(note) <= 2000),
    consent_confirmed boolean not null check (consent_confirmed),
    consent_date      date not null,
    status            public.referral_status not null default 'pending',
    responded_at      timestamptz,
    ended_at          timestamptz,
    ended_by          uuid references public.doctors (id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    check (from_doctor_id <> to_doctor_id)
);

-- At most one open (pending or accepted) referral per patient per colleague,
-- and at most one pending transfer per patient.
create unique index referrals_one_open_per_doctor
    on public.referrals (patient_id, to_doctor_id) where status in ('pending', 'accepted');
create unique index referrals_one_pending_transfer
    on public.referrals (patient_id) where kind = 'transfer' and status = 'pending';
create index referrals_to_idx on public.referrals (to_doctor_id, status);

-- Who owned each patient, and until when (drives read-only history after transfer).
create table public.patient_ownership_history (
    id          bigint generated always as identity primary key,
    patient_id  uuid not null references public.patients (id) on delete cascade,
    doctor_id   uuid not null references public.doctors (id),
    owned_from  timestamptz not null,
    owned_until timestamptz,
    referral_id uuid references public.referrals (id)
);
create index ownership_history_idx on public.patient_ownership_history (patient_id, doctor_id);

insert into public.patient_ownership_history (patient_id, doctor_id, owned_from)
select id, owner_id, created_at from public.patients;

create function public.patients_record_initial_owner() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    insert into public.patient_ownership_history (patient_id, doctor_id, owned_from)
    values (new.id, new.owner_id, new.created_at);
    return new;
end;
$$;
create trigger patients_initial_owner after insert on public.patients
    for each row execute function public.patients_record_initial_owner();

-- -----------------------------------------------------------------------------
-- Access helpers
-- -----------------------------------------------------------------------------

-- Is the current user an accepted, verified co-manager of this (not deleted) patient?
create function public.is_comanager(p_patient_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.is_verified_doctor()
       and exists (
           select 1
             from public.referrals r
             join public.patients p on p.id = r.patient_id
            where r.patient_id = p_patient_id
              and r.to_doctor_id = auth.uid()
              and r.kind = 'comanagement'
              and r.status = 'accepted'
              and p.deleted_at is null
       );
$$;

-- Owner or co-manager: may add and change record entries.
create function public.can_edit_patient(p_patient_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.is_patient_owner(p_patient_id) or public.is_comanager(p_patient_id);
$$;

-- Did the current user own this patient at time p_at (before transferring it away)?
create function public.was_owner_at(p_patient_id uuid, p_at timestamptz) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.patient_ownership_history h
         where h.patient_id = p_patient_id
           and h.doctor_id = auth.uid()
           and h.owned_until is not null
           and p_at <= h.owned_until
    );
$$;

-- Central read rule, now including co-managers. Consult part is unchanged.
-- Keep in sync with AccessPolicy.canReadSection in core/domain.
create or replace function public.can_read_section(p_patient_id uuid, p_section public.record_section) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.can_edit_patient(p_patient_id)
        or (
            public.is_verified_doctor()
            and exists (
                select 1
                  from public.consults c
                  join public.consult_sections s on s.consult_id = c.id
                  join public.patients p on p.id = c.patient_id
                 where c.patient_id = p_patient_id
                   and c.consultant_id = auth.uid()
                   and s.section = p_section
                   and not (p_section = 'identifiers' and c.anonymized)
                   and c.revoked_at is null
                   and c.status <> 'closed'
                   and c.expires_at > now()
                   and p.deleted_at is null
                   and p.owner_id = c.requester_id
            )
        );
$$;

revoke execute on function public.is_comanager(uuid) from public, anon;
revoke execute on function public.can_edit_patient(uuid) from public, anon;
revoke execute on function public.was_owner_at(uuid, timestamptz) from public, anon;
revoke execute on function public.patients_record_initial_owner() from public, anon, authenticated;
grant execute on function public.is_comanager(uuid) to authenticated;
grant execute on function public.can_edit_patient(uuid) to authenticated;
grant execute on function public.was_owner_at(uuid, timestamptz) to authenticated;

-- -----------------------------------------------------------------------------
-- patients: co-managers may read and edit; former owners may read.
-- Only the owner may delete (soft delete) or restore the patient.
-- -----------------------------------------------------------------------------
drop policy patients_select_owner on public.patients;
drop policy patients_update_owner on public.patients;

create policy patients_select on public.patients
    for select to authenticated
    using (owner_id = auth.uid() or public.is_comanager(id) or public.was_owner_at(id, created_at));

create policy patients_update on public.patients
    for update to authenticated
    using (public.can_edit_patient(id))
    with check (public.can_edit_patient(id));

create function public.patients_guard_delete() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if new.deleted_at is distinct from old.deleted_at and old.owner_id is distinct from auth.uid() then
        raise exception 'Only the patient''s primary doctor can delete or restore the patient'
            using errcode = '42501';
    end if;
    return new;
end;
$$;
create trigger patients_guard_delete before update on public.patients
    for each row execute function public.patients_guard_delete();

create index patients_sync_idx on public.patients (updated_at, id);

-- -----------------------------------------------------------------------------
-- Section tables: soft delete, co-manager editing, former-owner history.
-- -----------------------------------------------------------------------------
do $$
declare
    t record;
begin
    for t in
        select * from (values
            ('presenting_complaints', 'presenting_complaint'),
            ('medical_conditions',    'past_medical'),
            ('surgical_history',      'past_surgical'),
            ('medications',           'medications'),
            ('allergies',             'allergies'),
            ('family_history',        'family_history'),
            ('social_history',        'social_history'),
            ('examinations',          'examination'),
            ('surgical_cases',        'surgical_care'),
            ('postop_followups',      'surgical_care')
        ) as v (table_name, section)
    loop
        execute format('alter table public.%I add column deleted_at timestamptz', t.table_name);
        execute format('create index %I on public.%I (updated_at, id)', t.table_name || '_sync_idx', t.table_name);

        -- No more hard deletes: "delete" = set deleted_at.
        execute format('revoke delete on public.%I from authenticated', t.table_name);
        execute format('drop policy %I on public.%I', t.table_name || '_delete', t.table_name);

        execute format('drop policy %I on public.%I', t.table_name || '_select', t.table_name);
        execute format('drop policy %I on public.%I', t.table_name || '_insert', t.table_name);
        execute format('drop policy %I on public.%I', t.table_name || '_update', t.table_name);

        execute format(
            'create policy %I on public.%I for select to authenticated
                 using (public.can_read_section(patient_id, %L::public.record_section)
                        or public.was_owner_at(patient_id, created_at))',
            t.table_name || '_select', t.table_name, t.section);
        execute format(
            'create policy %I on public.%I for insert to authenticated
                 with check (public.can_edit_patient(patient_id) and created_by = auth.uid())',
            t.table_name || '_insert', t.table_name);
        execute format(
            'create policy %I on public.%I for update to authenticated
                 using (public.can_edit_patient(patient_id))
                 with check (public.can_edit_patient(patient_id))',
            t.table_name || '_update', t.table_name);
    end loop;
end;
$$;

-- social_history is one row per patient; soft-deleted rows would block a new
-- one, so the unique rule now applies only to live rows.
alter table public.social_history drop constraint social_history_patient_id_key;
create unique index social_history_one_live_row on public.social_history (patient_id) where deleted_at is null;

-- -----------------------------------------------------------------------------
-- Referral RPCs
-- -----------------------------------------------------------------------------
create function public.create_referral(
    p_patient_id        uuid,
    p_to_doctor_id      uuid,
    p_kind              public.referral_kind,
    p_note              text,
    p_consent_confirmed boolean,
    p_consent_date      date
) returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_id  uuid;
begin
    if v_uid is null or not public.is_verified_doctor(v_uid) then
        raise exception 'Your account must be verified before you can refer patients' using errcode = '42501';
    end if;
    if not exists (select 1 from public.patients
                    where id = p_patient_id and owner_id = v_uid and deleted_at is null) then
        raise exception 'Only the patient''s primary doctor can refer this patient' using errcode = '42501';
    end if;
    if p_to_doctor_id is null or p_to_doctor_id = v_uid then
        raise exception 'Choose another doctor' using errcode = '22023';
    end if;
    if not public.is_verified_doctor(p_to_doctor_id) then
        raise exception 'The selected doctor is not verified' using errcode = '42501';
    end if;
    if p_kind is null then
        raise exception 'Choose co-management or transfer' using errcode = '22023';
    end if;
    if p_consent_confirmed is not true or p_consent_date is null then
        raise exception 'Patient consent and consent date are required' using errcode = '22023';
    end if;
    if p_consent_date > current_date then
        raise exception 'Consent date cannot be in the future' using errcode = '22023';
    end if;
    if exists (select 1 from public.referrals
                where patient_id = p_patient_id and to_doctor_id = p_to_doctor_id
                  and status in ('pending', 'accepted')) then
        raise exception 'This doctor already has an open referral for this patient' using errcode = '22023';
    end if;
    if p_kind = 'transfer' and exists (select 1 from public.referrals
                where patient_id = p_patient_id and kind = 'transfer' and status = 'pending') then
        raise exception 'A transfer is already waiting for acceptance' using errcode = '22023';
    end if;

    insert into public.referrals (patient_id, from_doctor_id, to_doctor_id, kind, note, consent_confirmed, consent_date)
    values (p_patient_id, v_uid, p_to_doctor_id, p_kind, nullif(trim(p_note), ''), true, p_consent_date)
    returning id into v_id;

    perform public.write_audit('refer', 'referrals', v_id, p_patient_id,
        jsonb_build_object('event', 'created', 'kind', p_kind, 'to_doctor_id', p_to_doctor_id,
                           'consent_date', p_consent_date));
    return v_id;
end;
$$;

-- The receiving doctor accepts or declines. Accepting a transfer moves ownership.
create function public.respond_referral(p_referral_id uuid, p_accept boolean) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_ref public.referrals;
    v_now timestamptz := now();
begin
    select * into v_ref from public.referrals where id = p_referral_id for update;
    if not found or v_ref.to_doctor_id <> v_uid then
        raise exception 'Referral not found' using errcode = '42501';
    end if;
    if v_ref.status <> 'pending' then
        raise exception 'This referral is no longer pending' using errcode = '22023';
    end if;

    if p_accept is not true then
        update public.referrals set status = 'declined', responded_at = v_now where id = p_referral_id;
        perform public.write_audit('refer', 'referrals', p_referral_id, v_ref.patient_id,
                                   jsonb_build_object('event', 'declined'));
        return;
    end if;

    if not public.is_verified_doctor(v_uid) then
        raise exception 'Your account must be verified to accept referrals' using errcode = '42501';
    end if;
    -- The sender must still own the (not deleted) patient.
    if not exists (select 1 from public.patients
                    where id = v_ref.patient_id and owner_id = v_ref.from_doctor_id and deleted_at is null) then
        update public.referrals set status = 'cancelled', responded_at = v_now where id = p_referral_id;
        raise exception 'This patient can no longer be referred by the sender' using errcode = '22023';
    end if;

    update public.referrals set status = 'accepted', responded_at = v_now where id = p_referral_id;

    if v_ref.kind = 'transfer' then
        -- 1. Ownership moves.
        update public.patients set owner_id = v_uid where id = v_ref.patient_id;
        update public.patient_ownership_history
           set owned_until = v_now, referral_id = p_referral_id
         where patient_id = v_ref.patient_id and doctor_id = v_ref.from_doctor_id and owned_until is null;
        insert into public.patient_ownership_history (patient_id, doctor_id, owned_from, referral_id)
        values (v_ref.patient_id, v_uid, v_now, p_referral_id);
        -- 2. The previous owner's other referrals end; the new owner decides afresh.
        update public.referrals
           set status = case when status = 'pending' then 'cancelled'::public.referral_status
                             else 'ended'::public.referral_status end,
               ended_at = v_now, ended_by = v_uid
         where patient_id = v_ref.patient_id and id <> p_referral_id and status in ('pending', 'accepted');
        -- 3. Consults opened by the previous owner stop giving access
        --    automatically (can_read_section requires requester = owner).
        perform public.write_audit('transfer', 'patients', v_ref.patient_id, v_ref.patient_id,
            jsonb_build_object('from_doctor_id', v_ref.from_doctor_id, 'to_doctor_id', v_uid,
                               'referral_id', p_referral_id));
    else
        perform public.write_audit('refer', 'referrals', p_referral_id, v_ref.patient_id,
                                   jsonb_build_object('event', 'accepted'));
    end if;
end;
$$;

-- Cancel a pending referral (sender) or end an accepted co-management
-- (current owner or the co-manager). Completed transfers cannot be undone;
-- the new owner can transfer back instead.
create function public.end_referral(p_referral_id uuid) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_ref public.referrals;
    v_owner uuid;
begin
    select * into v_ref from public.referrals where id = p_referral_id for update;
    if not found then
        raise exception 'Referral not found' using errcode = '42501';
    end if;
    select owner_id into v_owner from public.patients where id = v_ref.patient_id;

    if v_ref.status = 'pending' and v_ref.from_doctor_id = v_uid then
        update public.referrals set status = 'cancelled', ended_at = now(), ended_by = v_uid where id = p_referral_id;
    elsif v_ref.status = 'accepted' and v_ref.kind = 'comanagement'
          and (v_uid = v_owner or v_uid = v_ref.to_doctor_id) then
        update public.referrals set status = 'ended', ended_at = now(), ended_by = v_uid where id = p_referral_id;
    else
        raise exception 'This referral cannot be ended by you' using errcode = '42501';
    end if;

    perform public.write_audit('refer', 'referrals', p_referral_id, v_ref.patient_id,
                               jsonb_build_object('event', 'ended'));
end;
$$;

alter table public.referrals enable row level security;
revoke all on public.referrals from anon, authenticated;
grant select on public.referrals to authenticated; -- changes only through the RPCs above

create policy referrals_select on public.referrals
    for select to authenticated
    using (from_doctor_id = auth.uid()
           or (to_doctor_id = auth.uid() and public.is_verified_doctor())
           or public.is_patient_owner(patient_id));

create trigger referrals_set_updated_at before update on public.referrals
    for each row execute function public.set_updated_at();

alter table public.patient_ownership_history enable row level security;
revoke all on public.patient_ownership_history from anon, authenticated;
grant select on public.patient_ownership_history to authenticated;
create policy ownership_history_select on public.patient_ownership_history
    for select to authenticated
    using (doctor_id = auth.uid() or public.is_patient_owner(patient_id));

revoke execute on function public.create_referral(uuid, uuid, public.referral_kind, text, boolean, date) from public, anon;
revoke execute on function public.respond_referral(uuid, boolean) from public, anon;
revoke execute on function public.end_referral(uuid) from public, anon;
grant execute on function public.create_referral(uuid, uuid, public.referral_kind, text, boolean, date) to authenticated;
grant execute on function public.respond_referral(uuid, boolean) to authenticated;
grant execute on function public.end_referral(uuid) to authenticated;

-- -----------------------------------------------------------------------------
-- Offline sync helpers
-- -----------------------------------------------------------------------------

-- Patients this user may keep offline: owned (including deleted, for the
-- trash) and co-managed. The app deletes any local patient not in this list.
create function public.my_patient_ids() returns setof uuid
language sql stable security definer
set search_path = ''
as $$
    select id from public.patients where owner_id = auth.uid()
    union
    select r.patient_id from public.referrals r
      join public.patients p on p.id = r.patient_id
     where r.to_doctor_id = auth.uid() and r.kind = 'comanagement' and r.status = 'accepted'
       and p.deleted_at is null and public.is_verified_doctor();
$$;

-- Returns up to p_limit rows of p_table changed after (p_since, p_after_id),
-- limited to patients the caller may edit. Runs with the CALLER's rights
-- (security invoker), so Row-Level Security still applies on top.
create function public.sync_pull(
    p_table    text,
    p_since    timestamptz,
    p_after_id uuid default '00000000-0000-0000-0000-000000000000',
    p_limit    int default 500
) returns jsonb
language plpgsql stable security invoker
set search_path = ''
as $$
declare
    v_result jsonb;
    v_patient_col text;
begin
    if p_table not in ('patients', 'presenting_complaints', 'medical_conditions', 'surgical_history',
                       'medications', 'allergies', 'family_history', 'social_history',
                       'examinations', 'surgical_cases', 'postop_followups') then
        raise exception 'Unknown table %', p_table using errcode = '22023';
    end if;
    v_patient_col := case when p_table = 'patients' then 'id' else 'patient_id' end;

    execute format(
        'select coalesce(jsonb_agg(to_jsonb(t) order by t.updated_at, t.id), ''[]''::jsonb)
           from (select * from public.%I
                  where (updated_at, id) > ($1, $2)
                    and public.can_edit_patient(%I)
                  order by updated_at, id
                  limit $3) t',
        p_table, v_patient_col)
    into v_result
    using coalesce(p_since, '-infinity'::timestamptz),
          coalesce(p_after_id, '00000000-0000-0000-0000-000000000000'::uuid),
          least(greatest(coalesce(p_limit, 500), 1), 1000);
    return v_result;
end;
$$;

revoke execute on function public.my_patient_ids() from public, anon;
revoke execute on function public.sync_pull(text, timestamptz, uuid, int) from public, anon;
grant execute on function public.my_patient_ids() to authenticated;
grant execute on function public.sync_pull(text, timestamptz, uuid, int) to authenticated;
