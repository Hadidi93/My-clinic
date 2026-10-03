-- =============================================================================
-- Migration 8: doctor grade (resident / specialist / consultant) and a better
-- doctor search.
--
-- Plain-language summary
--   * Doctors can state their grade. It is shown to colleagues in the
--     directory, consults and referrals. Like the licence number, changing it
--     sends the account back to the admin for approval.
--   * The directory search now matches every word typed against the name,
--     specialty, hospital or grade: "consultant cardiology" or
--     "surgery ain shams" both work. Results show grade, specialty and hospital.
-- =============================================================================

alter table public.doctors
    add column grade text check (grade in ('resident', 'specialist', 'consultant'));
grant update (grade) on public.doctors to authenticated;

-- Changing the grade also needs the admin's approval again.
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
        or new.facility_id is distinct from old.facility_id
        or new.grade is distinct from old.grade)
       and old.verification_status in ('verified', 'rejected')
       and new.verification_status = old.verification_status then
        new.verification_status := 'pending';
        new.verified_at := null;
        new.verified_by := null;
    end if;
    return new;
end;
$$;

-- The result now includes the grade, so the function is replaced.
drop function public.search_doctors(text, int);
create function public.search_doctors(p_query text default '', p_limit int default 30)
returns table (id uuid, full_name text, specialty text, hospital text, photo_path text, grade text)
language sql stable security definer
set search_path = ''
as $$
    with words as (
        -- Each word typed, with % and _ taken literally.
        select '%' || replace(replace(replace(w, '\', '\\'), '%', '\%'), '_', '\_') || '%' as pattern
          from unnest(regexp_split_to_array(trim(coalesce(p_query, '')), '\s+')) as w
         where w <> ''
    )
    select d.id, d.full_name, d.specialty, d.hospital, d.photo_path, d.grade
      from public.doctors d
     where public.is_verified_doctor()
       and d.verification_status = 'verified'
       and d.account_type = 'doctor'
       and d.id <> auth.uid()
       and not exists (
           select 1 from words
            where not (d.full_name ilike words.pattern
                       or coalesce(d.specialty, '') ilike words.pattern
                       or coalesce(d.hospital, '') ilike words.pattern
                       or coalesce(d.grade, '') ilike words.pattern)
       )
     order by d.full_name
     limit least(greatest(coalesce(p_limit, 30), 1), 100);
$$;
revoke execute on function public.search_doctors(text, int) from public, anon;
grant execute on function public.search_doctors(text, int) to authenticated;

-- Consult and referral lists: include the colleague's grade.
create or replace function public.my_consults() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(x order by (x ->> 'last_activity_at') desc), '[]'::jsonb)
      from (
        select jsonb_build_object(
                   'id', c.id,
                   'patient_id', c.patient_id,
                   'role', case when c.requester_id = auth.uid() then 'requester' else 'consultant' end,
                   'question', c.question,
                   'urgency', c.urgency,
                   'status', c.status,
                   'anonymized', c.anonymized,
                   'sections', (select coalesce(jsonb_agg(s.section order by s.section), '[]'::jsonb)
                                  from public.consult_sections s where s.consult_id = c.id),
                   'created_at', c.created_at,
                   'expires_at', c.expires_at,
                   'revoked_at', c.revoked_at,
                   'closed_at', c.closed_at,
                   'active', public.is_consult_active(c.id),
                   'other_doctor', jsonb_build_object('id', o.id, 'full_name', o.full_name, 'specialty', o.specialty,
                                                      'hospital', o.hospital, 'grade', o.grade),
                   'patient_name', case
                       when c.requester_id = auth.uid() then p.full_name
                       when not c.anonymized and public.is_consult_active(c.id)
                            and exists (select 1 from public.consult_sections s
                                         where s.consult_id = c.id and s.section = 'identifiers') then p.full_name
                       end,
                   'patient_sex', p.sex,
                   'patient_age', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years),
                   'last_activity_at', greatest(c.created_at,
                       coalesce((select max(m.created_at) from public.consult_messages m where m.consult_id = c.id), c.created_at)),
                   'unread', (select count(*) from public.notifications n
                               where n.recipient_id = auth.uid() and n.ref_id = c.id and n.read_at is null
                                 and n.kind in ('consult_request', 'consult_message'))
               ) as x
          from public.consults c
          join public.patients p on p.id = c.patient_id
          join public.doctors o on o.id = case when c.requester_id = auth.uid() then c.consultant_id else c.requester_id end
         where public.is_consult_participant(c.id)
      ) t;
$$;

create or replace function public.my_referrals() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(x order by (x ->> 'created_at') desc), '[]'::jsonb)
      from (
        select jsonb_build_object(
                   'id', r.id,
                   'patient_id', r.patient_id,
                   'direction', case when r.from_doctor_id = auth.uid() then 'sent' else 'received' end,
                   'kind', r.kind,
                   'status', r.status,
                   'note', r.note,
                   'created_at', r.created_at,
                   'responded_at', r.responded_at,
                   'ended_at', r.ended_at,
                   'other_doctor', jsonb_build_object('id', o.id, 'full_name', o.full_name, 'specialty', o.specialty,
                                                      'hospital', o.hospital, 'grade', o.grade),
                   'patient_name', p.full_name,
                   'patient_sex', p.sex,
                   'patient_age', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years),
                   'primary_diagnosis', p.primary_diagnosis
               ) as x
          from public.referrals r
          join public.patients p on p.id = r.patient_id
          join public.doctors o on o.id = case when r.from_doctor_id = auth.uid() then r.to_doctor_id else r.from_doctor_id end
         where r.from_doctor_id = auth.uid()
            or (r.to_doctor_id = auth.uid() and public.is_verified_doctor())
      ) t;
$$;
