-- =============================================================================
-- Migration 10 (Phase 5): access-log views and record export logging.
--
-- Plain-language summary
--   * patient_access_log(): the patient's main doctor (or an admin) sees who
--     opened, changed, shared or exported the record, and when, with names.
--   * admin_activity_log(): admins see everything that happened in the app
--     (approvals, sharing, referrals, views, changes), newest first. Like the
--     audit log itself it holds no clinical values and no patient names.
--   * log_record_export(): exporting a record as PDF is allowed for the main
--     doctor only and is always written to the audit log first.
-- =============================================================================

-- One audit row as the screens show it (shared by both views).
create function public.audit_entry_json(a public.audit_log) returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select jsonb_build_object(
        'id', a.id,
        'occurred_at', a.occurred_at,
        'action', a.action,
        'table_name', a.table_name,
        'record_id', a.record_id,
        'patient_id', a.patient_id,
        'actor_id', a.actor_id,
        'actor_name', d.full_name,
        'actor_grade', d.grade,
        'actor_is_staff', coalesce(d.account_type = 'staff', false),
        'actor_is_me', a.actor_id = auth.uid(),
        'via', case
                   when a.details ? 'via_consult' then 'consult'
                   when a.details ->> 'via' = 'staff' then 'staff'
               end,
        'section', a.details ->> 'section',
        'details', a.details
    )
      from (select 1) x
      left join public.doctors d on d.id = a.actor_id;
$$;

create function public.patient_access_log(p_patient_id uuid, p_limit int default 300) returns jsonb
language plpgsql stable security definer
set search_path = ''
as $$
begin
    if not (public.is_patient_owner(p_patient_id) or public.is_admin()) then
        raise exception 'Only the patient''s main doctor can see this log' using errcode = '42501';
    end if;
    return (
        select coalesce(jsonb_agg(public.audit_entry_json(a) order by a.occurred_at desc, a.id desc), '[]'::jsonb)
          from (select * from public.audit_log
                 where patient_id = p_patient_id
                 order by occurred_at desc, id desc
                 limit least(greatest(coalesce(p_limit, 300), 1), 1000)) a
    );
end;
$$;

create function public.admin_activity_log(p_limit int default 300, p_before timestamptz default null) returns jsonb
language plpgsql stable security definer
set search_path = ''
as $$
begin
    if not public.is_admin() then
        raise exception 'Only administrators can see the activity log' using errcode = '42501';
    end if;
    return (
        select coalesce(jsonb_agg(public.audit_entry_json(a) order by a.occurred_at desc, a.id desc), '[]'::jsonb)
          from (select * from public.audit_log
                 where p_before is null or occurred_at < p_before
                 order by occurred_at desc, id desc
                 limit least(greatest(coalesce(p_limit, 300), 1), 1000)) a
    );
end;
$$;

-- Called by the app just before it creates a PDF of the record.
create function public.log_record_export(p_patient_id uuid) returns void
language plpgsql security definer
set search_path = ''
as $$
begin
    if not public.is_patient_owner(p_patient_id) then
        raise exception 'Only the patient''s main doctor can export the record' using errcode = '42501';
    end if;
    perform public.write_audit('export', 'patients', p_patient_id, p_patient_id, jsonb_build_object('format', 'pdf'));
end;
$$;

revoke execute on function public.audit_entry_json(public.audit_log) from public, anon, authenticated;
revoke execute on function public.patient_access_log(uuid, int) from public, anon;
revoke execute on function public.admin_activity_log(int, timestamptz) from public, anon;
revoke execute on function public.log_record_export(uuid) from public, anon;
grant execute on function public.patient_access_log(uuid, int) to authenticated;
grant execute on function public.admin_activity_log(int, timestamptz) to authenticated;
grant execute on function public.log_record_export(uuid) to authenticated;
