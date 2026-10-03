-- =============================================================================
-- Migration 9: a grade change is a REQUEST that an admin approves, and it no
-- longer blocks the account.
--
-- Plain-language summary
--   * Doctors choose a new grade -> it is saved as "requested". Colleagues keep
--     seeing the current (approved) grade, and the account keeps working.
--   * An admin approves (the new grade then shows everywhere) or rejects (the
--     old grade stays). Every decision is audit-logged.
--   * For a new account the requested grade is approved together with the
--     account itself.
-- Run after migration 8.
-- =============================================================================

alter table public.doctors
    add column requested_grade text check (requested_grade in ('resident', 'specialist', 'consultant'));

-- Doctors may no longer set the approved grade themselves, only request one.
revoke update (grade) on public.doctors from authenticated;
grant update (requested_grade) on public.doctors to authenticated;

-- A grade change no longer sends the account back for approval.
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
    -- Requesting the grade you already have is not a request.
    if new.requested_grade is not distinct from new.grade then
        new.requested_grade := null;
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
    -- Approving a new account also approves the grade it asked for.
    if new.verification_status = 'verified' and old.verification_status <> 'verified'
       and new.requested_grade is not null then
        new.grade := new.requested_grade;
        new.requested_grade := null;
    end if;
    return new;
end;
$$;

-- Admin decision on a grade change.
create function public.admin_review_grade(p_doctor_id uuid, p_approve boolean) returns void
language plpgsql security definer
set search_path = ''
as $$
declare
    v_requested text;
begin
    if not public.is_admin() then
        raise exception 'Only administrators can approve grades' using errcode = '42501';
    end if;
    select requested_grade into v_requested from public.doctors where id = p_doctor_id for update;
    if v_requested is null then
        raise exception 'No grade change is waiting for this doctor' using errcode = '22023';
    end if;
    update public.doctors
       set grade = case when p_approve then v_requested else grade end,
           requested_grade = null
     where id = p_doctor_id;
    perform public.write_audit('verify', 'doctors', p_doctor_id, null,
        jsonb_build_object('grade', v_requested, 'approved', p_approve is true));
end;
$$;

-- What the admin has to review: new/changed accounts and grade changes.
create function public.admin_review_queue() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(to_jsonb(d) - 'verified_by' order by d.created_at), '[]'::jsonb)
      from public.doctors d
     where public.is_admin()
       and (d.verification_status = 'pending' or d.requested_grade is not null);
$$;

revoke execute on function public.admin_review_grade(uuid, boolean) from public, anon;
revoke execute on function public.admin_review_queue() from public, anon;
grant execute on function public.admin_review_grade(uuid, boolean) to authenticated;
grant execute on function public.admin_review_queue() to authenticated;

-- Grades set directly under migration 8 by a doctor who wasn't approved yet stay as they are.
