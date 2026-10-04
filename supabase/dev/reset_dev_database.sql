-- =============================================================================
-- DEVELOPMENT ONLY — DELETES ALL MY CLINIC TABLES AND THEIR DATA.
--
-- Use this only on the development project with fake demo data, when a
-- migration was half-applied and you want to start again cleanly. Afterwards,
-- run the migration files again in filename order.
--
-- It does NOT delete user accounts (Authentication → Users) or uploaded files.
-- Existing accounts will have no doctor profile afterwards; delete them in
-- Authentication → Users and sign up again.
--
-- NEVER run this on a project that holds real patient data.
-- =============================================================================

-- Sign-up triggers on Supabase's own users table
drop trigger if exists on_auth_user_created on auth.users;
drop trigger if exists on_auth_user_email_changed on auth.users;

-- Storage policies (the buckets and files are kept)
drop policy if exists avatars_owner_all on storage.objects;
drop policy if exists avatars_verified_read on storage.objects;
drop policy if exists licenses_owner_all on storage.objects;
drop policy if exists licenses_admin_read on storage.objects;
drop policy if exists clinical_files_read on storage.objects;
drop policy if exists clinical_files_insert on storage.objects;
drop policy if exists consult_files_read on storage.objects;
drop policy if exists consult_files_insert on storage.objects;

-- Tables (CASCADE also removes their policies, triggers and indexes)
drop table if exists
    public.notifications, public.device_tokens,
    public.attachments, public.investigation_results, public.investigation_requests,
    public.patient_ownership_history, public.referrals,
    public.consult_messages, public.consult_sections, public.consults,
    public.postop_followups, public.surgical_cases, public.examinations,
    public.social_history, public.family_history, public.allergies,
    public.medications, public.surgical_history, public.medical_conditions,
    public.presenting_complaints, public.patients,
    public.audit_log, public.doctors, public.facilities
    cascade;

-- Functions
drop function if exists
    public.admin_set_verification, public.audit_row_change, public.can_edit_patient,
    public.can_read_section, public.close_consult, public.consult_message_after_insert,
    public.create_consult, public.create_referral, public.doctors_require_reverification,
    public.end_referral, public.get_consult_patient, public.handle_new_user,
    public.handle_user_email_change, public.is_admin, public.is_comanager,
    public.is_consult_active, public.is_consult_participant, public.is_patient_owner,
    public.is_verified_doctor, public.log_record_view, public.my_patient_ids,
    public.patients_guard_delete, public.patients_record_initial_owner,
    public.respond_referral, public.revoke_consult, public.search_doctors,
    public.set_updated_at, public.sync_pull, public.was_owner_at, public.write_audit,
    public.is_doctor_account, public.is_verified_staff, public.my_facility_id, public.try_uuid,
    public.staff_can_access_patient, public.staff_request_open, public.staff_worklist,
    public.staff_request_detail, public.staff_mark_sample_taken, public.staff_submit_result,
    public.investigation_requests_status_times, public.investigation_results_guard,
    public.investigation_results_advance_request,
    public.notify, public.mark_all_notifications_read, public.consults_notify, public.consult_messages_notify,
    public.referrals_notify, public.investigation_requests_notify, public.register_device, public.unregister_device,
    public.consult_messages_check_files, public.my_consults, public.my_referrals,
    public.admin_review_grade, public.admin_review_queue,
    public.audit_entry_json, public.patient_access_log, public.admin_activity_log, public.log_record_export, public.log_research_export
    cascade;

-- Types
drop type if exists
    public.verification_status, public.app_role, public.audit_action,
    public.record_section, public.patient_sex, public.allergy_severity,
    public.smoking_status, public.surgical_case_status,
    public.consult_status, public.consult_urgency,
    public.referral_kind, public.referral_status,
    public.facility_kind, public.investigation_kind,
    public.investigation_urgency, public.investigation_status
    cascade;
