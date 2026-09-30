-- =============================================================================
-- Migration 2 (schema for Phase 2): patients and the record sections.
--
-- Plain-language summary
--   * Each patient belongs to exactly one doctor (owner_id).
--   * Each record section lives in its own table, tagged with the section name
--     that the "Consult" checkboxes use (see type record_section).
--   * Only the owner can add, change or delete anything.
--   * Reading goes through can_read_section(patient, section). In this
--     migration it only allows the owner. Migration 3 (consultations) extends it
--     so consultants can read exactly the sections that were shared with them.
--   * Patients are never hard-deleted through the app; deleted_at hides them
--     (medical records must be retained).
--   * Every change to every table here is written to audit_log.
-- =============================================================================

create type public.record_section as enum (
    'identifiers',          -- name, national ID, file number, phone, address, emergency contact
    'presenting_complaint', -- presenting complaint + history of present illness
    'past_medical',
    'past_surgical',
    'medications',
    'allergies',
    'family_history',
    'social_history',
    'examination',          -- examination findings + vital signs
    'investigations',       -- labs, imaging, pathology (tables added in Phase 3)
    'surgical_care'         -- diagnosis, planned operation, checklist, op notes, follow-up
);

create type public.patient_sex as enum ('male', 'female', 'unknown');
create type public.allergy_severity as enum ('mild', 'moderate', 'severe', 'life_threatening');
create type public.smoking_status as enum ('never', 'former', 'current', 'unknown');
create type public.surgical_case_status as enum ('planned', 'scheduled', 'done', 'cancelled');

-- -----------------------------------------------------------------------------
-- patients: personal data ("identifiers" section) + search helpers
-- Only full_name is required, so quick-add takes seconds; the rest can be
-- completed later.
-- -----------------------------------------------------------------------------
create table public.patients (
    id                      uuid primary key default gen_random_uuid(),
    owner_id                uuid not null default auth.uid() references public.doctors (id),
    full_name               text not null check (char_length(trim(full_name)) between 1 and 120),
    date_of_birth           date,
    age_years               smallint check (age_years between 0 and 130), -- when DOB unknown
    sex                     public.patient_sex not null default 'unknown',
    national_id             text check (char_length(national_id) <= 30),
    file_number             text check (char_length(file_number) <= 40),
    phone                   text check (char_length(phone) <= 20),
    address                 text check (char_length(address) <= 300),
    emergency_contact_name  text check (char_length(emergency_contact_name) <= 120),
    emergency_contact_phone text check (char_length(emergency_contact_phone) <= 20),
    primary_diagnosis       text check (char_length(primary_diagnosis) <= 200),
    tags                    text[] not null default '{}' check (cardinality(tags) <= 20),
    created_at              timestamptz not null default now(),
    updated_at              timestamptz not null default now(),
    deleted_at              timestamptz
);

create index patients_owner_idx on public.patients (owner_id, updated_at desc) where deleted_at is null;
create index patients_tags_idx on public.patients using gin (tags);
create unique index patients_owner_file_number_key
    on public.patients (owner_id, file_number) where file_number is not null and file_number <> '';

-- -----------------------------------------------------------------------------
-- Section tables. Every one has: id, patient_id, created_by, created_at, updated_at.
-- -----------------------------------------------------------------------------

-- presenting_complaint
create table public.presenting_complaints (
    id          uuid primary key default gen_random_uuid(),
    patient_id  uuid not null references public.patients (id) on delete cascade,
    complaint   text not null check (char_length(complaint) between 1 and 500),
    hpi         text check (char_length(hpi) <= 10000), -- history of present illness
    onset_date  date,
    recorded_at timestamptz not null default now(),
    created_by  uuid not null default auth.uid() references public.doctors (id),
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now()
);

-- past_medical. condition_code is set when picked from the app's list of
-- common chronic diseases (e.g. 'diabetes_t2'); free text leaves it null.
create table public.medical_conditions (
    id             uuid primary key default gen_random_uuid(),
    patient_id     uuid not null references public.patients (id) on delete cascade,
    condition_code text check (char_length(condition_code) <= 50),
    name           text not null check (char_length(name) between 1 and 200),
    is_chronic     boolean not null default true,
    diagnosed_on   date,
    notes          text check (char_length(notes) <= 2000),
    created_by     uuid not null default auth.uid() references public.doctors (id),
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- past_surgical
create table public.surgical_history (
    id            uuid primary key default gen_random_uuid(),
    patient_id    uuid not null references public.patients (id) on delete cascade,
    procedure     text not null check (char_length(procedure) between 1 and 200),
    performed_on  date,
    hospital      text check (char_length(hospital) <= 120),
    complications text check (char_length(complications) <= 2000),
    created_by    uuid not null default auth.uid() references public.doctors (id),
    created_at    timestamptz not null default now(),
    updated_at    timestamptz not null default now()
);

-- medications (drug history)
create table public.medications (
    id         uuid primary key default gen_random_uuid(),
    patient_id uuid not null references public.patients (id) on delete cascade,
    name       text not null check (char_length(name) between 1 and 200),
    dose       text check (char_length(dose) <= 100),
    route      text check (char_length(route) <= 50),
    frequency  text check (char_length(frequency) <= 100),
    started_on date,
    stopped_on date,
    is_current boolean not null default true,
    notes      text check (char_length(notes) <= 1000),
    created_by uuid not null default auth.uid() references public.doctors (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- allergies (shown as a red banner on every patient screen)
create table public.allergies (
    id         uuid primary key default gen_random_uuid(),
    patient_id uuid not null references public.patients (id) on delete cascade,
    allergen   text not null check (char_length(allergen) between 1 and 200),
    reaction   text check (char_length(reaction) <= 500),
    severity   public.allergy_severity not null default 'moderate',
    created_by uuid not null default auth.uid() references public.doctors (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- family_history
create table public.family_history (
    id         uuid primary key default gen_random_uuid(),
    patient_id uuid not null references public.patients (id) on delete cascade,
    relation   text not null check (char_length(relation) between 1 and 50),
    condition  text not null check (char_length(condition) between 1 and 200),
    notes      text check (char_length(notes) <= 1000),
    created_by uuid not null default auth.uid() references public.doctors (id),
    created_at timestamptz not null default now(),
    updated_at timestamptz not null default now()
);

-- social_history (one row per patient)
create table public.social_history (
    id               uuid primary key default gen_random_uuid(),
    patient_id       uuid not null unique references public.patients (id) on delete cascade,
    smoking          public.smoking_status not null default 'unknown',
    pack_years       numeric(5, 1) check (pack_years between 0 and 300),
    alcohol          text check (char_length(alcohol) <= 200),
    occupation       text check (char_length(occupation) <= 120),
    marital_status   text check (char_length(marital_status) <= 50),
    notes            text check (char_length(notes) <= 2000),
    created_by       uuid not null default auth.uid() references public.doctors (id),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now()
);

-- examination + vital signs (one row per examination; the trend chart plots these)
create table public.examinations (
    id              uuid primary key default gen_random_uuid(),
    patient_id      uuid not null references public.patients (id) on delete cascade,
    examined_at     timestamptz not null default now(),
    findings        text check (char_length(findings) <= 10000),
    pulse_bpm       smallint check (pulse_bpm between 20 and 300),
    systolic_mmhg   smallint check (systolic_mmhg between 40 and 300),
    diastolic_mmhg  smallint check (diastolic_mmhg between 20 and 200),
    resp_rate       smallint check (resp_rate between 4 and 80),
    temperature_c   numeric(4, 1) check (temperature_c between 30 and 45),
    spo2_percent    smallint check (spo2_percent between 50 and 100),
    weight_kg       numeric(5, 1) check (weight_kg between 0.3 and 400),
    height_cm       numeric(4, 1) check (height_cm between 20 and 250),
    pain_score      smallint check (pain_score between 0 and 10),
    created_by      uuid not null default auth.uid() references public.doctors (id),
    created_at      timestamptz not null default now(),
    updated_at      timestamptz not null default now()
);
create index examinations_patient_time_idx on public.examinations (patient_id, examined_at);

-- surgical_care: one surgical episode (diagnosis -> operation)
create table public.surgical_cases (
    id                uuid primary key default gen_random_uuid(),
    patient_id        uuid not null references public.patients (id) on delete cascade,
    diagnosis         text not null check (char_length(diagnosis) between 1 and 300),
    planned_operation text check (char_length(planned_operation) <= 300),
    planned_date      date,
    status            public.surgical_case_status not null default 'planned',
    -- e.g. {"consent_signed": true, "npo": true, "blood_available": false}
    preop_checklist   jsonb not null default '{}'::jsonb,
    operation_date    date,
    operative_notes   text check (char_length(operative_notes) <= 20000),
    complications     text check (char_length(complications) <= 2000),
    created_by        uuid not null default auth.uid() references public.doctors (id),
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    unique (id, patient_id) -- lets follow-ups prove they belong to the same patient
);
create index surgical_cases_planned_idx on public.surgical_cases (planned_date) where status in ('planned', 'scheduled');

-- surgical_care: post-op follow-up visits (wound photos are attached in Phase 3)
create table public.postop_followups (
    id               uuid primary key default gen_random_uuid(),
    patient_id       uuid not null,
    surgical_case_id uuid not null,
    visit_date       date not null default current_date,
    wound_status     text check (char_length(wound_status) <= 200),
    notes            text check (char_length(notes) <= 5000),
    created_by       uuid not null default auth.uid() references public.doctors (id),
    created_at       timestamptz not null default now(),
    updated_at       timestamptz not null default now(),
    foreign key (surgical_case_id, patient_id)
        references public.surgical_cases (id, patient_id) on delete cascade
);

-- Every section table carries patient_id, so index it for fast lookups.
create index presenting_complaints_patient_idx on public.presenting_complaints (patient_id);
create index medical_conditions_patient_idx on public.medical_conditions (patient_id);
create index surgical_history_patient_idx on public.surgical_history (patient_id);
create index medications_patient_idx on public.medications (patient_id);
create index allergies_patient_idx on public.allergies (patient_id);
create index family_history_patient_idx on public.family_history (patient_id);
create index surgical_cases_patient_idx on public.surgical_cases (patient_id);
create index postop_followups_patient_idx on public.postop_followups (patient_id);

-- -----------------------------------------------------------------------------
-- Access helpers
-- -----------------------------------------------------------------------------

-- Does the current user own this patient?
create function public.is_patient_owner(p_patient_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.patients
        where id = p_patient_id and owner_id = auth.uid()
    );
$$;

-- Can the current user read this section of this patient?
-- Phase 2 version: owner only. Replaced in migration 3 to add consultants.
create function public.can_read_section(p_patient_id uuid, p_section public.record_section) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.is_patient_owner(p_patient_id);
$$;

-- The app calls this when a record section is opened, so reads are audited
-- too (the database cannot detect reads on its own).
create function public.log_record_view(p_patient_id uuid, p_section public.record_section) returns void
language plpgsql security definer
set search_path = ''
as $$
begin
    if not public.can_read_section(p_patient_id, p_section) then
        raise exception 'No access to this record' using errcode = '42501';
    end if;
    perform public.write_audit('view', 'patients', p_patient_id, p_patient_id,
                               jsonb_build_object('section', p_section));
end;
$$;

revoke execute on function public.is_patient_owner(uuid) from public, anon;
revoke execute on function public.can_read_section(uuid, public.record_section) from public, anon;
revoke execute on function public.log_record_view(uuid, public.record_section) from public, anon;
grant execute on function public.is_patient_owner(uuid) to authenticated;
grant execute on function public.can_read_section(uuid, public.record_section) to authenticated;
grant execute on function public.log_record_view(uuid, public.record_section) to authenticated;

-- -----------------------------------------------------------------------------
-- Row-Level Security: patients
-- -----------------------------------------------------------------------------
alter table public.patients enable row level security;
revoke all on public.patients from anon, authenticated;
grant select, insert on public.patients to authenticated;
-- owner_id, id and created_at can never be changed; there is no DELETE grant.
grant update (full_name, date_of_birth, age_years, sex, national_id, file_number, phone, address,
              emergency_contact_name, emergency_contact_phone, primary_diagnosis, tags, deleted_at)
    on public.patients to authenticated;

-- Consultants never read this table directly; they use get_consult_patient()
-- (migration 3), which hides identifiers when required.
create policy patients_select_owner on public.patients
    for select to authenticated using (owner_id = auth.uid());
create policy patients_insert_owner on public.patients
    for insert to authenticated with check (owner_id = auth.uid());
create policy patients_update_owner on public.patients
    for update to authenticated using (owner_id = auth.uid()) with check (owner_id = auth.uid());

create trigger patients_set_updated_at before update on public.patients
    for each row execute function public.set_updated_at();
create trigger patients_audit after insert or update or delete on public.patients
    for each row execute function public.audit_row_change();

-- Owners can see all audit activity on their own patients (who viewed what).
create policy audit_select_patient_owner on public.audit_log
    for select to authenticated
    using (patient_id is not null and public.is_patient_owner(patient_id));

-- -----------------------------------------------------------------------------
-- Row-Level Security: section tables (same four rules for each table)
--   read   : can_read_section(patient, <this table's section>)
--   insert : owner of the patient, and created_by must be yourself
--   update : owner of the patient (row can't be moved to someone else's patient)
--   delete : owner of the patient
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
        execute format('alter table public.%I enable row level security', t.table_name);
        execute format('revoke all on public.%I from anon, authenticated', t.table_name);
        execute format('grant select, insert, update, delete on public.%I to authenticated', t.table_name);

        execute format(
            'create policy %I on public.%I for select to authenticated
                 using (public.can_read_section(patient_id, %L::public.record_section))',
            t.table_name || '_select', t.table_name, t.section);
        execute format(
            'create policy %I on public.%I for insert to authenticated
                 with check (public.is_patient_owner(patient_id) and created_by = auth.uid())',
            t.table_name || '_insert', t.table_name);
        execute format(
            'create policy %I on public.%I for update to authenticated
                 using (public.is_patient_owner(patient_id))
                 with check (public.is_patient_owner(patient_id))',
            t.table_name || '_update', t.table_name);
        execute format(
            'create policy %I on public.%I for delete to authenticated
                 using (public.is_patient_owner(patient_id))',
            t.table_name || '_delete', t.table_name);

        execute format(
            'create trigger %I before update on public.%I
                 for each row execute function public.set_updated_at()',
            t.table_name || '_set_updated_at', t.table_name);
        execute format(
            'create trigger %I after insert or update or delete on public.%I
                 for each row execute function public.audit_row_change()',
            t.table_name || '_audit', t.table_name);
    end loop;
end;
$$;
