-- =============================================================================
-- Migration 6 (Phase 3): investigations, results, files, and lab/radiology staff.
--
-- Plain-language summary
--   * Two kinds of account now: 'doctor' (default) and 'staff' (lab, radiology,
--     pathology). Staff belong to one department ("facility") and must be
--     approved by an admin, like doctors. Staff can never own patients, be
--     consulted, be referred to, or appear in the doctor directory.
--   * Facilities (e.g. "Demo Hospital – Main Lab") can be added by anyone
--     signed in; only admins can edit or remove them.
--   * A doctor (owner or co-manager) requests an investigation and may send it
--     to a facility's inbox. Approved staff of that facility then see a minimal
--     view: the request, the patient's name/age/sex/file number, allergies, and
--     earlier results of the same kind. Nothing else. They lose access once the
--     doctor marks the result reviewed (or cancels the request).
--   * Results hold free text, typed values (for trend charts) and files
--     (camera photo / gallery / PDF). A result uploaded by staff can't be
--     edited by doctors, only marked "entered in error".
--   * Files live in the private bucket 'clinical-files' at
--     <section>/<patient id>/<file>, readable only by those who may read that
--     section of that patient.
-- =============================================================================

alter type public.audit_action add value if not exists 'submit_result';

-- -----------------------------------------------------------------------------
-- Facilities and staff accounts
-- -----------------------------------------------------------------------------
create type public.facility_kind as enum ('lab', 'radiology', 'pathology', 'other');

create table public.facilities (
    id         uuid primary key default gen_random_uuid(),
    name       text not null check (char_length(trim(name)) between 2 and 120),
    kind       public.facility_kind not null,
    hospital   text check (char_length(hospital) <= 120),
    created_by uuid default auth.uid() references public.doctors (id) on delete set null,
    created_at timestamptz not null default now()
);
-- One facility per (kind, name, hospital), ignoring case and spaces, to limit duplicates.
create unique index facilities_unique_name
    on public.facilities (kind, lower(trim(name)), lower(trim(coalesce(hospital, ''))));

alter table public.doctors
    add column account_type text not null default 'doctor' check (account_type in ('doctor', 'staff')),
    add column facility_id uuid references public.facilities (id) on delete set null;

grant update (account_type, facility_id) on public.doctors to authenticated;

-- Changing account type or department also sends the account back for review.
create or replace function public.doctors_require_reverification() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    -- A doctor who still owns patients can't become a staff account (transfer them first).
    if new.account_type = 'staff' and old.account_type = 'doctor'
       and exists (select 1 from public.patients where owner_id = new.id) then
        raise exception 'Transfer your patients before switching to a staff account' using errcode = '42501';
    end if;
    if (new.license_number is distinct from old.license_number
        or new.license_document_path is distinct from old.license_document_path
        or new.account_type is distinct from old.account_type
        or new.facility_id is distinct from old.facility_id)
       and old.verification_status in ('verified', 'rejected')
       and new.verification_status = old.verification_status then
        new.verification_status := 'pending';
        new.verified_at := null;
        new.verified_by := null;
    end if;
    return new;
end;
$$;

-- "Verified doctor" now means a verified account of type doctor. Every rule
-- built on it (consults, referrals, directory, sharing) therefore excludes staff.
create or replace function public.is_verified_doctor(p_doctor_id uuid default auth.uid()) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.doctors
        where id = p_doctor_id and verification_status = 'verified' and account_type = 'doctor'
    );
$$;

-- Is the current user an account of type doctor (verified or not)?
create function public.is_doctor_account() returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (select 1 from public.doctors where id = auth.uid() and account_type = 'doctor');
$$;

-- Is the current user approved lab/radiology staff with a department?
create function public.is_verified_staff() returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.doctors
        where id = auth.uid() and account_type = 'staff'
          and verification_status = 'verified' and facility_id is not null
    );
$$;

create function public.my_facility_id() returns uuid
language sql stable security definer
set search_path = ''
as $$
    select facility_id from public.doctors where id = auth.uid() and account_type = 'staff';
$$;

-- Staff never own patients.
drop policy patients_insert_owner on public.patients;
create policy patients_insert_owner on public.patients
    for insert to authenticated
    with check (owner_id = auth.uid() and public.is_doctor_account());

-- Doctor directory: doctors only.
create or replace function public.search_doctors(p_query text default '', p_limit int default 30)
returns table (id uuid, full_name text, specialty text, hospital text, photo_path text)
language sql stable security definer
set search_path = ''
as $$
    with q as (
        select '%' || replace(replace(replace(coalesce(trim(p_query), ''), '\', '\\'), '%', '\%'), '_', '\_') || '%' as pattern
    )
    select d.id, d.full_name, d.specialty, d.hospital, d.photo_path
      from public.doctors d, q
     where public.is_verified_doctor()
       and d.verification_status = 'verified'
       and d.account_type = 'doctor'
       and d.id <> auth.uid()
       and (d.full_name ilike q.pattern or d.specialty ilike q.pattern or d.hospital ilike q.pattern)
     order by d.full_name
     limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;

alter table public.facilities enable row level security;
revoke all on public.facilities from anon, authenticated;
grant select, insert on public.facilities to authenticated;
grant update (name, kind, hospital), delete on public.facilities to authenticated;
create policy facilities_select on public.facilities for select to authenticated using (true);
create policy facilities_insert on public.facilities for insert to authenticated with check (created_by = auth.uid());
create policy facilities_admin_update on public.facilities for update to authenticated
    using (public.is_admin()) with check (public.is_admin());
create policy facilities_admin_delete on public.facilities for delete to authenticated using (public.is_admin());

revoke execute on function public.is_doctor_account() from public, anon;
revoke execute on function public.is_verified_staff() from public, anon;
revoke execute on function public.my_facility_id() from public, anon;
grant execute on function public.is_doctor_account() to authenticated;
grant execute on function public.is_verified_staff() to authenticated;
grant execute on function public.my_facility_id() to authenticated;

-- -----------------------------------------------------------------------------
-- Investigation requests, results, attachments
-- -----------------------------------------------------------------------------
create type public.investigation_kind as enum ('lab', 'imaging', 'pathology', 'other');
create type public.investigation_urgency as enum ('routine', 'urgent', 'stat');
create type public.investigation_status as enum
    ('requested', 'sample_taken', 'result_uploaded', 'reviewed', 'cancelled');

create table public.investigation_requests (
    id              uuid primary key default gen_random_uuid(),
    patient_id      uuid not null references public.patients (id) on delete cascade,
    kind            public.investigation_kind not null,
    tests           text[] not null check (cardinality(tests) between 1 and 50),
    urgency         public.investigation_urgency not null default 'routine',
    clinical_notes  text check (char_length(clinical_notes) <= 4000),
    facility_id     uuid references public.facilities (id) on delete set null, -- null = handled by the doctor
    status          public.investigation_status not null default 'requested',
    requested_at    timestamptz not null default now(),
    sample_taken_at timestamptz,
    resulted_at     timestamptz,
    reviewed_at     timestamptz,
    reviewed_by     uuid references public.doctors (id) on delete set null,
    created_by      uuid not null default auth.uid() references public.doctors (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now(),
    deleted_at      timestamptz,
    unique (id, patient_id)
);
create index investigation_requests_patient_idx on public.investigation_requests (patient_id);
create index investigation_requests_inbox_idx on public.investigation_requests (facility_id, status)
    where deleted_at is null;
create index investigation_requests_sync_idx on public.investigation_requests (updated_at, id);

create table public.investigation_results (
    id          uuid primary key default gen_random_uuid(),
    patient_id  uuid not null,
    request_id  uuid,
    kind        public.investigation_kind not null,
    title       text not null check (char_length(trim(title)) between 1 and 200),
    result_date date not null default current_date,
    report_text text check (char_length(report_text) <= 20000),
    -- Typed values: [{"test":"Hb","value":11.2,"unit":"g/dL","low":12,"high":16}, ...]
    lab_values  jsonb not null default '[]'::jsonb
                check (jsonb_typeof(lab_values) = 'array' and jsonb_array_length(lab_values) <= 100),
    source      text not null default 'doctor' check (source in ('doctor', 'staff')),
    created_by  uuid not null default auth.uid() references public.doctors (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),
    deleted_at  timestamptz,
    unique (id, patient_id),
    foreign key (patient_id) references public.patients (id) on delete cascade,
    foreign key (request_id, patient_id) references public.investigation_requests (id, patient_id) on delete cascade
);
create index investigation_results_patient_idx on public.investigation_results (patient_id, kind, result_date desc);
create index investigation_results_sync_idx on public.investigation_results (updated_at, id);

alter table public.postop_followups add constraint postop_followups_id_patient_key unique (id, patient_id);

create table public.attachments (
    id           uuid primary key default gen_random_uuid(),
    patient_id   uuid not null references public.patients (id) on delete cascade,
    section      public.record_section not null check (section in ('investigations', 'surgical_care')),
    result_id    uuid,
    followup_id  uuid,
    storage_path text not null unique check (char_length(storage_path) <= 300),
    mime_type    text not null check (mime_type in ('image/jpeg', 'image/png', 'application/pdf')),
    file_name    text check (char_length(file_name) <= 200),
    caption      text check (char_length(caption) <= 500),
    taken_at     timestamptz not null default now(),
    created_by   uuid not null default auth.uid() references public.doctors (id),
    created_at   timestamptz not null default now(),
    updated_at   timestamptz not null default now(),
    deleted_at   timestamptz,
    foreign key (result_id, patient_id) references public.investigation_results (id, patient_id) on delete cascade,
    foreign key (followup_id, patient_id) references public.postop_followups (id, patient_id) on delete cascade,
    -- The file must be stored under this attachment's own section and patient.
    check (storage_path like section::text || '/' || patient_id::text || '/%')
);
create index attachments_patient_idx on public.attachments (patient_id);
create index attachments_sync_idx on public.attachments (updated_at, id);

-- Status timestamps are filled in automatically. An edit made offline on an
-- older copy never moves the status backwards (e.g. back to "requested" after
-- the lab uploaded a result) and never clears a recorded time.
create function public.investigation_requests_status_times() returns trigger
language plpgsql
set search_path = ''
as $$
declare
    v_order constant text[] := array['requested', 'sample_taken', 'result_uploaded', 'reviewed'];
begin
    if new.status <> 'cancelled' and old.status <> 'cancelled'
       and array_position(v_order, new.status::text) < array_position(v_order, old.status::text) then
        new.status := old.status;
    end if;
    new.sample_taken_at := coalesce(new.sample_taken_at, old.sample_taken_at);
    new.resulted_at := coalesce(new.resulted_at, old.resulted_at);
    if new.status = old.status then
        new.reviewed_at := old.reviewed_at;
        new.reviewed_by := old.reviewed_by;
    end if;
    if new.status is distinct from old.status then
        case new.status
            when 'sample_taken' then new.sample_taken_at := coalesce(new.sample_taken_at, now());
            when 'result_uploaded' then new.resulted_at := coalesce(new.resulted_at, now());
            when 'reviewed' then new.reviewed_at := now(); new.reviewed_by := auth.uid();
            else null;
        end case;
    end if;
    return new;
end;
$$;
create trigger investigation_requests_status_times before update on public.investigation_requests
    for each row execute function public.investigation_requests_status_times();

-- Results uploaded by staff can't be changed by doctors, and only staff
-- (through staff_submit_result) can create them.
create function public.investigation_results_guard() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' and new.source = 'staff' and not public.is_verified_staff() then
        raise exception 'Only lab/radiology staff can submit staff results' using errcode = '42501';
    end if;
    if tg_op = 'UPDATE' and old.source = 'staff'
       and (to_jsonb(new) - 'deleted_at' - 'updated_at') is distinct from (to_jsonb(old) - 'deleted_at' - 'updated_at') then
        raise exception 'A result uploaded by the lab cannot be edited; mark it as entered in error instead'
            using errcode = '42501';
    end if;
    return new;
end;
$$;
create trigger investigation_results_guard before insert or update on public.investigation_results
    for each row execute function public.investigation_results_guard();

-- A new result moves its request to "result uploaded".
create function public.investigation_results_advance_request() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    if new.request_id is not null then
        update public.investigation_requests
           set status = 'result_uploaded'
         where id = new.request_id and status in ('requested', 'sample_taken');
    end if;
    return new;
end;
$$;
create trigger investigation_results_advance_request after insert on public.investigation_results
    for each row execute function public.investigation_results_advance_request();

-- Doctor-side access: same rules as the other record tables.
do $$
declare
    t text;
begin
    foreach t in array array['investigation_requests', 'investigation_results'] loop
        execute format('alter table public.%I enable row level security', t);
        execute format('revoke all on public.%I from anon, authenticated', t);
        execute format('grant select, insert, update on public.%I to authenticated', t);
        execute format(
            'create policy %I on public.%I for select to authenticated
                 using (public.can_read_section(patient_id, ''investigations'')
                        or public.was_owner_at(patient_id, created_at))', t || '_select', t);
        execute format(
            'create policy %I on public.%I for insert to authenticated
                 with check (public.can_edit_patient(patient_id) and created_by = auth.uid())', t || '_insert', t);
        execute format(
            'create policy %I on public.%I for update to authenticated
                 using (public.can_edit_patient(patient_id)) with check (public.can_edit_patient(patient_id))',
            t || '_update', t);
        execute format('create trigger %I before update on public.%I for each row execute function public.set_updated_at()',
                       t || '_set_updated_at', t);
        execute format('create trigger %I after insert or update or delete on public.%I for each row execute function public.audit_row_change()',
                       t || '_audit', t);
    end loop;
end;
$$;

alter table public.attachments enable row level security;
revoke all on public.attachments from anon, authenticated;
grant select, insert, update on public.attachments to authenticated;
create policy attachments_select on public.attachments for select to authenticated
    using (public.can_read_section(patient_id, section) or public.was_owner_at(patient_id, created_at));
create policy attachments_insert on public.attachments for insert to authenticated
    with check (public.can_edit_patient(patient_id) and created_by = auth.uid());
create policy attachments_update on public.attachments for update to authenticated
    using (public.can_edit_patient(patient_id)) with check (public.can_edit_patient(patient_id));
create trigger attachments_set_updated_at before update on public.attachments
    for each row execute function public.set_updated_at();
create trigger attachments_audit after insert or update or delete on public.attachments
    for each row execute function public.audit_row_change();

-- Offline sync now includes the new tables.
create or replace function public.sync_pull(
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
                       'examinations', 'surgical_cases', 'postop_followups',
                       'investigation_requests', 'investigation_results', 'attachments') then
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

-- -----------------------------------------------------------------------------
-- Staff side: access only through these functions (no direct table access)
-- -----------------------------------------------------------------------------

-- Turns text into a uuid, or null if it isn't one (safe inside policies).
create function public.try_uuid(p text) returns uuid
language plpgsql immutable
set search_path = ''
as $$
begin
    return p::uuid;
exception when others then
    return null;
end;
$$;

-- Does the current staff member have an open request for this patient in their department?
create function public.staff_can_access_patient(p_patient_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.is_verified_staff() and exists (
        select 1
          from public.investigation_requests r
          join public.patients p on p.id = r.patient_id
         where r.patient_id = p_patient_id
           and r.facility_id = public.my_facility_id()
           and r.status in ('requested', 'sample_taken', 'result_uploaded')
           and r.deleted_at is null
           and p.deleted_at is null
    );
$$;

create function public.staff_request_open(p_request_id uuid) returns public.investigation_requests
language sql stable security definer
set search_path = ''
as $$
    select r.*
      from public.investigation_requests r
      join public.patients p on p.id = r.patient_id
     where r.id = p_request_id
       and public.is_verified_staff()
       and r.facility_id = public.my_facility_id()
       and r.status in ('requested', 'sample_taken', 'result_uploaded')
       and r.deleted_at is null
       and p.deleted_at is null;
$$;

-- The department inbox.
create function public.staff_worklist() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(item order by urgency_rank, requested_at), '[]'::jsonb)
      from (
        select jsonb_build_object(
                   'id', r.id, 'patient_id', r.patient_id, 'kind', r.kind, 'tests', to_jsonb(r.tests),
                   'urgency', r.urgency, 'status', r.status, 'requested_at', r.requested_at,
                   'patient_name', p.full_name, 'patient_sex', p.sex, 'file_number', p.file_number,
                   'age_years', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years),
                   'requested_by', d.full_name
               ) as item,
               case r.urgency when 'stat' then 0 when 'urgent' then 1 else 2 end as urgency_rank,
               r.requested_at
          from public.investigation_requests r
          join public.patients p on p.id = r.patient_id
          join public.doctors d on d.id = r.created_by
         where public.is_verified_staff()
           and r.facility_id = public.my_facility_id()
           and r.status in ('requested', 'sample_taken', 'result_uploaded')
           and r.deleted_at is null
           and p.deleted_at is null
      ) x;
$$;

-- One request in detail: what the lab needs, and nothing more.
create function public.staff_request_detail(p_request_id uuid) returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
    r public.investigation_requests;
    p public.patients;
    v jsonb;
begin
    r := public.staff_request_open(p_request_id);
    if r.id is null then
        raise exception 'Request not found' using errcode = '42501';
    end if;
    select * into p from public.patients where id = r.patient_id;

    v := jsonb_build_object(
        'id', r.id, 'patient_id', r.patient_id, 'kind', r.kind, 'tests', to_jsonb(r.tests),
        'urgency', r.urgency, 'status', r.status,
        'clinical_notes', r.clinical_notes, 'requested_at', r.requested_at,
        'requested_by', (select full_name from public.doctors where id = r.created_by),
        'patient', jsonb_build_object(
            'full_name', p.full_name, 'sex', p.sex, 'file_number', p.file_number,
            'age_years', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years)),
        'allergies', coalesce((
            select jsonb_agg(jsonb_build_object('allergen', a.allergen, 'severity', a.severity, 'reaction', a.reaction))
              from public.allergies a where a.patient_id = p.id and a.deleted_at is null), '[]'::jsonb),
        -- Earlier results of the same kind (e.g. earlier labs for a lab request).
        'previous_results', coalesce((
            select jsonb_agg(x order by (x ->> 'result_date') desc)
              from (select jsonb_build_object('title', res.title, 'result_date', res.result_date,
                                              'report_text', res.report_text, 'lab_values', res.lab_values) as x
                      from public.investigation_results res
                     where res.patient_id = p.id and res.kind = r.kind and res.deleted_at is null
                       and res.request_id is distinct from r.id
                     order by res.result_date desc
                     limit 10) y), '[]'::jsonb),
        'this_request_results', coalesce((
            select jsonb_agg(jsonb_build_object('title', res.title, 'result_date', res.result_date,
                                                'report_text', res.report_text, 'lab_values', res.lab_values))
              from public.investigation_results res
             where res.request_id = r.id and res.deleted_at is null), '[]'::jsonb)
    );
    perform public.write_audit('view', 'investigation_requests', r.id, r.patient_id,
                               jsonb_build_object('via', 'staff'));
    return v;
end;
$$;

-- Staff mark the sample as taken / study as done.
create function public.staff_mark_sample_taken(p_request_id uuid) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    r public.investigation_requests;
begin
    r := public.staff_request_open(p_request_id);
    if r.id is null then
        raise exception 'Request not found' using errcode = '42501';
    end if;
    update public.investigation_requests set status = 'sample_taken'
     where id = r.id and status = 'requested';
end;
$$;

-- Staff submit a result: text and/or typed values and/or already-uploaded files.
-- p_files: [{"storage_path": "investigations/<patient>/<file>", "mime_type": "...", "file_name": "..."}]
create function public.staff_submit_result(
    p_request_id  uuid,
    p_title       text,
    p_result_date date,
    p_report_text text,
    p_lab_values  jsonb,
    p_files       jsonb
) returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
    r public.investigation_requests;
    v_id uuid;
    f jsonb;
    v_prefix text;
begin
    r := public.staff_request_open(p_request_id);
    if r.id is null then
        raise exception 'Request not found' using errcode = '42501';
    end if;
    if coalesce(trim(p_report_text), '') = '' and coalesce(jsonb_array_length(p_lab_values), 0) = 0
       and coalesce(jsonb_array_length(p_files), 0) = 0 then
        raise exception 'Enter a result, values or attach a file' using errcode = '22023';
    end if;
    if p_result_date is not null and p_result_date > current_date then
        raise exception 'Result date cannot be in the future' using errcode = '22023';
    end if;

    insert into public.investigation_results (patient_id, request_id, kind, title, result_date,
                                              report_text, lab_values, source)
    values (r.patient_id, r.id, r.kind, coalesce(nullif(trim(p_title), ''), array_to_string(r.tests, ', ')),
            coalesce(p_result_date, current_date), nullif(trim(p_report_text), ''),
            coalesce(p_lab_values, '[]'::jsonb), 'staff')
    returning id into v_id;

    v_prefix := 'investigations/' || r.patient_id::text || '/';
    for f in select * from jsonb_array_elements(coalesce(p_files, '[]'::jsonb)) loop
        if left(f ->> 'storage_path', length(v_prefix)) <> v_prefix then
            raise exception 'File is not stored for this patient' using errcode = '42501';
        end if;
        insert into public.attachments (patient_id, section, result_id, storage_path, mime_type, file_name)
        values (r.patient_id, 'investigations', v_id, f ->> 'storage_path', f ->> 'mime_type', f ->> 'file_name');
    end loop;

    perform public.write_audit('submit_result', 'investigation_results', v_id, r.patient_id,
        jsonb_build_object('request_id', r.id, 'files', coalesce(jsonb_array_length(p_files), 0)));
    return v_id;
end;
$$;

revoke execute on function public.try_uuid(text) from public, anon;
revoke execute on function public.staff_can_access_patient(uuid) from public, anon;
revoke execute on function public.staff_request_open(uuid) from public, anon, authenticated;
revoke execute on function public.staff_worklist() from public, anon;
revoke execute on function public.staff_request_detail(uuid) from public, anon;
revoke execute on function public.staff_mark_sample_taken(uuid) from public, anon;
revoke execute on function public.staff_submit_result(uuid, text, date, text, jsonb, jsonb) from public, anon;
revoke execute on function public.investigation_requests_status_times() from public, anon, authenticated;
revoke execute on function public.investigation_results_guard() from public, anon, authenticated;
revoke execute on function public.investigation_results_advance_request() from public, anon, authenticated;
grant execute on function public.try_uuid(text) to authenticated;
grant execute on function public.staff_can_access_patient(uuid) to authenticated;
grant execute on function public.staff_worklist() to authenticated;
grant execute on function public.staff_request_detail(uuid) to authenticated;
grant execute on function public.staff_mark_sample_taken(uuid) to authenticated;
grant execute on function public.staff_submit_result(uuid, text, date, text, jsonb, jsonb) to authenticated;

-- -----------------------------------------------------------------------------
-- Storage: clinical files at <section>/<patient id>/<file>
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('clinical-files', 'clinical-files', false, 15 * 1024 * 1024,
        array['image/jpeg', 'image/png', 'application/pdf'])
on conflict (id) do nothing;

create policy clinical_files_read on storage.objects for select to authenticated
    using (bucket_id = 'clinical-files'
           and (storage.foldername(name))[1] in ('investigations', 'surgical_care')
           and (public.can_read_section(public.try_uuid((storage.foldername(name))[2]),
                                        ((storage.foldername(name))[1])::public.record_section)
                or ((storage.foldername(name))[1] = 'investigations'
                    and public.staff_can_access_patient(public.try_uuid((storage.foldername(name))[2])))
                -- Files of entries you may see (e.g. history kept after a transfer).
                or exists (select 1 from public.attachments a where a.storage_path = objects.name)));

create policy clinical_files_insert on storage.objects for insert to authenticated
    with check (bucket_id = 'clinical-files'
                and (storage.foldername(name))[1] in ('investigations', 'surgical_care')
                and (public.can_edit_patient(public.try_uuid((storage.foldername(name))[2]))
                     or ((storage.foldername(name))[1] = 'investigations'
                         and public.staff_can_access_patient(public.try_uuid((storage.foldername(name))[2])))));
-- No update or delete policy: stored files are never changed or removed through the app.
