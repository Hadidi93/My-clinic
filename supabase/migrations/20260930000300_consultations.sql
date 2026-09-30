-- =============================================================================
-- Migration 3 (schema for Phase 4): consultations and section-level sharing.
--
-- Plain-language summary
--   * A consult is created ONLY through create_consult(), which checks:
--     both doctors verified, caller owns the patient, patient consent confirmed
--     with a date, at least one section chosen, anonymized consults exclude
--     identifiers, and duration 1-90 days.
--   * While a consult is live (not revoked, not closed, not expired) the
--     consultant can READ exactly the chosen sections, nothing else, and can
--     never write to the record.
--   * The requester can revoke at any time; access stops on the very next
--     request because every query re-checks these rules.
--   * Consultants see who the patient is only through get_consult_patient(),
--     which hides name / IDs / phone when anonymized or not shared.
--   * Messages cannot be edited or deleted (medico-legal record).
-- =============================================================================

create type public.consult_status as enum ('pending', 'answered', 'closed');
create type public.consult_urgency as enum ('routine', 'urgent', 'emergency');

create table public.consults (
    id                uuid primary key default gen_random_uuid(),
    patient_id        uuid not null references public.patients (id) on delete cascade,
    requester_id      uuid not null references public.doctors (id),
    consultant_id     uuid not null references public.doctors (id),
    question          text not null check (char_length(trim(question)) between 1 and 4000),
    urgency           public.consult_urgency not null default 'routine',
    anonymized        boolean not null,
    consent_confirmed boolean not null check (consent_confirmed),
    consent_date      date not null,
    status            public.consult_status not null default 'pending',
    expires_at        timestamptz not null,
    revoked_at        timestamptz,
    closed_at         timestamptz,
    created_at        timestamptz not null default now(),
    updated_at        timestamptz not null default now(),
    check (requester_id <> consultant_id),
    check (expires_at > created_at)
);

create index consults_consultant_idx on public.consults (consultant_id, status, created_at desc);
create index consults_requester_idx on public.consults (requester_id, status, created_at desc);
create index consults_patient_idx on public.consults (patient_id);

-- Which sections each consult shares (one row per ticked checkbox).
create table public.consult_sections (
    consult_id uuid not null references public.consults (id) on delete cascade,
    section    public.record_section not null,
    primary key (consult_id, section)
);

create table public.consult_messages (
    id               uuid primary key default gen_random_uuid(),
    consult_id       uuid not null references public.consults (id) on delete cascade,
    sender_id        uuid not null default auth.uid() references public.doctors (id),
    body             text not null check (char_length(trim(body)) between 1 and 8000),
    -- Storage paths of attached files (bucket and policies added in Phase 4).
    attachment_paths text[] not null default '{}' check (cardinality(attachment_paths) <= 10),
    created_at       timestamptz not null default now()
);
create index consult_messages_consult_idx on public.consult_messages (consult_id, created_at);

-- -----------------------------------------------------------------------------
-- Helpers
-- -----------------------------------------------------------------------------

-- Is the consult live right now? (Access-granting state.)
create function public.is_consult_active(p_consult_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1
          from public.consults c
          join public.patients p on p.id = c.patient_id
         where c.id = p_consult_id
           and c.revoked_at is null
           and c.status <> 'closed'
           and c.expires_at > now()
           and p.deleted_at is null
           and p.owner_id = c.requester_id
    );
$$;

-- Is the current user part of this consult? A consultant whose account is no
-- longer verified is treated as not part of it (they receive nothing).
create function public.is_consult_participant(p_consult_id uuid) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select exists (
        select 1 from public.consults c
         where c.id = p_consult_id
           and (c.requester_id = auth.uid()
                or (c.consultant_id = auth.uid() and public.is_verified_doctor()))
    );
$$;

-- THE central sharing rule. Replaces the owner-only version from migration 2.
-- Keep in sync with AccessPolicy.canReadSection in core/domain.
create or replace function public.can_read_section(p_patient_id uuid, p_section public.record_section) returns boolean
language sql stable security definer
set search_path = ''
as $$
    select public.is_patient_owner(p_patient_id)
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

-- -----------------------------------------------------------------------------
-- RPC: create a consult (the only way to share patient data)
-- -----------------------------------------------------------------------------
create function public.create_consult(
    p_patient_id        uuid,
    p_consultant_id     uuid,
    p_question          text,
    p_sections          public.record_section[],
    p_anonymize         boolean,
    p_duration_days     int,
    p_consent_confirmed boolean,
    p_consent_date      date,
    p_urgency           public.consult_urgency default 'routine'
) returns uuid
language plpgsql security definer
set search_path = ''
as $$
declare
    v_uid uuid := auth.uid();
    v_id  uuid;
    v_sections public.record_section[];
begin
    if v_uid is null then
        raise exception 'Not signed in' using errcode = '42501';
    end if;
    if not public.is_verified_doctor(v_uid) then
        raise exception 'Your account must be verified before you can share patient data' using errcode = '42501';
    end if;
    if not exists (select 1 from public.patients
                    where id = p_patient_id and owner_id = v_uid and deleted_at is null) then
        raise exception 'Patient not found' using errcode = '42501';
    end if;
    if p_consultant_id is null or p_consultant_id = v_uid then
        raise exception 'Choose another doctor to consult' using errcode = '22023';
    end if;
    if not public.is_verified_doctor(p_consultant_id) then
        raise exception 'The selected doctor is not verified' using errcode = '42501';
    end if;
    if p_consent_confirmed is not true or p_consent_date is null then
        raise exception 'Patient consent and consent date are required' using errcode = '22023';
    end if;
    if p_consent_date > current_date then
        raise exception 'Consent date cannot be in the future' using errcode = '22023';
    end if;
    if coalesce(char_length(trim(p_question)), 0) = 0 then
        raise exception 'Question is required' using errcode = '22023';
    end if;

    select coalesce(array_agg(distinct s), '{}') into v_sections
      from unnest(p_sections) as s where s is not null;
    if cardinality(v_sections) = 0 then
        raise exception 'Choose at least one section to share' using errcode = '22023';
    end if;
    if p_anonymize is null then
        raise exception 'Choose whether to anonymize the patient' using errcode = '22023';
    end if;
    if p_anonymize and 'identifiers' = any (v_sections) then
        raise exception 'An anonymized consult cannot include personal identifiers' using errcode = '22023';
    end if;
    if p_duration_days is null or p_duration_days not between 1 and 90 then
        raise exception 'Access duration must be between 1 and 90 days' using errcode = '22023';
    end if;

    insert into public.consults (patient_id, requester_id, consultant_id, question, urgency,
                                 anonymized, consent_confirmed, consent_date, expires_at)
    values (p_patient_id, v_uid, p_consultant_id, trim(p_question), coalesce(p_urgency, 'routine'),
            p_anonymize, true, p_consent_date, now() + make_interval(days => p_duration_days))
    returning id into v_id;

    insert into public.consult_sections (consult_id, section)
    select v_id, s from unnest(v_sections) as s;

    perform public.write_audit('share', 'consults', v_id, p_patient_id,
        jsonb_build_object('consultant_id', p_consultant_id,
                           'sections', to_jsonb(v_sections),
                           'anonymized', p_anonymize,
                           'duration_days', p_duration_days,
                           'consent_date', p_consent_date));
    return v_id;
end;
$$;

-- RPC: requester revokes access immediately.
create function public.revoke_consult(p_consult_id uuid) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    v_patient uuid;
begin
    update public.consults
       set revoked_at = now()
     where id = p_consult_id and requester_id = auth.uid() and revoked_at is null
    returning patient_id into v_patient;
    if not found then
        raise exception 'Consult not found or already revoked' using errcode = '42501';
    end if;
    perform public.write_audit('revoke', 'consults', p_consult_id, v_patient, '{}'::jsonb);
end;
$$;

-- RPC: either participant closes the consult (ends data access; thread stays readable).
create function public.close_consult(p_consult_id uuid) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    v_patient uuid;
begin
    if not public.is_consult_participant(p_consult_id) then
        raise exception 'Consult not found' using errcode = '42501';
    end if;
    update public.consults
       set status = 'closed', closed_at = now()
     where id = p_consult_id and status <> 'closed'
    returning patient_id into v_patient;
    if found then
        perform public.write_audit('close', 'consults', p_consult_id, v_patient, '{}'::jsonb);
    end if;
end;
$$;

-- RPC: what the consultant may know about who the patient is. Age and sex are
-- always returned; identifiers only if shared and not anonymized.
-- Keep in sync with PatientIdentityMasker in core/domain.
create function public.get_consult_patient(p_consult_id uuid) returns jsonb
language plpgsql security definer
set search_path = ''
as $$
declare
    v_consult public.consults;
    v_patient public.patients;
    v_show    boolean;
    v_result  jsonb;
begin
    select * into v_consult from public.consults where id = p_consult_id;
    if not found
       or v_consult.consultant_id <> auth.uid()
       or not public.is_verified_doctor()
       or not public.is_consult_active(p_consult_id) then
        raise exception 'No access to this consult' using errcode = '42501';
    end if;

    select * into v_patient from public.patients where id = v_consult.patient_id;
    v_show := public.can_read_section(v_patient.id, 'identifiers');

    v_result := jsonb_build_object(
        'age_years', coalesce(extract(year from age(current_date, v_patient.date_of_birth))::int,
                              v_patient.age_years),
        'sex', v_patient.sex,
        'anonymized', v_consult.anonymized
    );
    if v_show then
        v_result := v_result || jsonb_build_object(
            'full_name', v_patient.full_name,
            'national_id', v_patient.national_id,
            'file_number', v_patient.file_number,
            'phone', v_patient.phone,
            'address', v_patient.address,
            'emergency_contact_name', v_patient.emergency_contact_name,
            'emergency_contact_phone', v_patient.emergency_contact_phone
        );
    end if;

    perform public.write_audit('view', 'patients', v_patient.id, v_patient.id,
        jsonb_build_object('via_consult', p_consult_id, 'identifiers_shown', v_show));
    return v_result;
end;
$$;

-- When the consultant replies, a pending consult becomes 'answered'.
create function public.consult_message_after_insert() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    update public.consults
       set status = 'answered'
     where id = new.consult_id and consultant_id = new.sender_id and status = 'pending';
    return new;
end;
$$;

-- -----------------------------------------------------------------------------
-- Row-Level Security
-- -----------------------------------------------------------------------------
alter table public.consults enable row level security;
alter table public.consult_sections enable row level security;
alter table public.consult_messages enable row level security;

revoke all on public.consults from anon, authenticated;
revoke all on public.consult_sections from anon, authenticated;
revoke all on public.consult_messages from anon, authenticated;

-- consults and consult_sections: read only through the API; all changes go
-- through the RPC functions above.
grant select on public.consults to authenticated;
grant select on public.consult_sections to authenticated;
grant select, insert on public.consult_messages to authenticated; -- no update/delete

create policy consults_select_participant on public.consults
    for select to authenticated using (public.is_consult_participant(id));

create policy consult_sections_select_participant on public.consult_sections
    for select to authenticated using (public.is_consult_participant(consult_id));

create policy consult_messages_select_participant on public.consult_messages
    for select to authenticated using (public.is_consult_participant(consult_id));

-- New messages only while the consult is live, and only as yourself.
create policy consult_messages_insert_participant on public.consult_messages
    for insert to authenticated
    with check (sender_id = auth.uid()
                and public.is_consult_participant(consult_id)
                and public.is_consult_active(consult_id));

create trigger consults_set_updated_at before update on public.consults
    for each row execute function public.set_updated_at();
create trigger consult_messages_answered after insert on public.consult_messages
    for each row execute function public.consult_message_after_insert();
create trigger consult_messages_audit after insert on public.consult_messages
    for each row execute function public.audit_row_change();

-- Function permissions
revoke execute on function public.is_consult_active(uuid) from public, anon;
revoke execute on function public.is_consult_participant(uuid) from public, anon;
revoke execute on function public.create_consult(uuid, uuid, text, public.record_section[], boolean, int, boolean, date, public.consult_urgency) from public, anon;
revoke execute on function public.revoke_consult(uuid) from public, anon;
revoke execute on function public.close_consult(uuid) from public, anon;
revoke execute on function public.get_consult_patient(uuid) from public, anon;
revoke execute on function public.consult_message_after_insert() from public, anon, authenticated;

grant execute on function public.is_consult_active(uuid) to authenticated;
grant execute on function public.is_consult_participant(uuid) to authenticated;
grant execute on function public.create_consult(uuid, uuid, text, public.record_section[], boolean, int, boolean, date, public.consult_urgency) to authenticated;
grant execute on function public.revoke_consult(uuid) to authenticated;
grant execute on function public.close_consult(uuid) to authenticated;
grant execute on function public.get_consult_patient(uuid) to authenticated;
