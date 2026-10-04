-- =============================================================================
-- Migration 12: "Discharged" patients and de-identified research export.
--
-- Plain-language summary
--   * A patient can be marked "discharged / follow-up finished". They leave
--     the main list but stay complete and searchable (unlike deleting).
--     Whoever may edit the patient may discharge or re-open them.
--   * Research export: the app builds a de-identified spreadsheet ON THE
--     PHONE from the doctor's own patients. Before that, this function writes
--     one audit entry per exported patient, so the export is always on record.
--     Only the patient's main doctor can export, as with the PDF export.
-- Run after migration 11.
-- =============================================================================

alter table public.patients add column discharged_at timestamptz;
grant update (discharged_at) on public.patients to authenticated;

create function public.log_research_export(p_patient_ids uuid[]) returns int
language plpgsql security definer
set search_path = ''
as $$
declare
    v_id    uuid;
    v_count int := coalesce(cardinality(p_patient_ids), 0);
begin
    if not public.is_verified_doctor() then
        raise exception 'Only verified doctors can export' using errcode = '42501';
    end if;
    if v_count = 0 or v_count > 5000 then
        raise exception 'Choose between 1 and 5000 patients' using errcode = '22023';
    end if;
    foreach v_id in array p_patient_ids loop
        if not public.is_patient_owner(v_id) then
            raise exception 'Only the patient''s main doctor can export the record' using errcode = '42501';
        end if;
    end loop;
    foreach v_id in array p_patient_ids loop
        perform public.write_audit('export', 'patients', v_id, v_id,
            jsonb_build_object('format', 'research_csv', 'deidentified', true, 'patients_in_export', v_count));
    end loop;
    return v_count;
end;
$$;

revoke execute on function public.log_research_export(uuid[]) from public, anon;
grant execute on function public.log_research_export(uuid[]) to authenticated;
