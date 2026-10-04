-- =============================================================================
-- Migration 11: a consult shares the patient's NAME (with age and sex) unless
-- the requesting doctor switches on "Hide the patient's name".
--
-- Plain-language summary
--   * Name, age and sex: shared with the colleague in every consult that is
--     not anonymized. They identify the patient and matter for the plan.
--   * National ID, file number, phone, address and emergency contact: still
--     shared only when "Personal data" is ticked (and the consult is not
--     anonymized).
--   * Anonymized consults (made before or after this change) still show only
--     age and sex. Every look at the patient's details is audit-logged.
-- Run after migration 10.
-- Keep in sync with PatientIdentityMasker in core/domain.
-- =============================================================================

create or replace function public.get_consult_patient(p_consult_id uuid) returns jsonb
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
    if not v_consult.anonymized then
        v_result := v_result || jsonb_build_object('full_name', v_patient.full_name);
    end if;
    if v_show then
        v_result := v_result || jsonb_build_object(
            'national_id', v_patient.national_id,
            'file_number', v_patient.file_number,
            'phone', v_patient.phone,
            'address', v_patient.address,
            'emergency_contact_name', v_patient.emergency_contact_name,
            'emergency_contact_phone', v_patient.emergency_contact_phone
        );
    end if;

    perform public.write_audit('view', 'patients', v_patient.id, v_patient.id,
        jsonb_build_object('via_consult', p_consult_id, 'name_shown', not v_consult.anonymized,
                           'identifiers_shown', v_show));
    return v_result;
end;
$$;

-- The consult list shows the name on the same rule.
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
                       when not c.anonymized and public.is_consult_active(c.id) then p.full_name
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
