-- Shows which My Clinic migrations are already in this database.
-- Safe to run any time: it only reads, it changes nothing.
select
    m.file,
    case when m.installed then '✅ already installed — skip it'
         else '❌ not installed — run this file next' end as status
from (values
    (1, '20260930000100_doctors_and_audit.sql',      to_regclass('public.doctors') is not null
                                                     and to_regprocedure('public.search_doctors(text,integer)') is not null),
    (2, '20260930000200_patient_records.sql',        to_regclass('public.postop_followups') is not null),
    (3, '20260930000300_consultations.sql',          to_regprocedure('public.get_consult_patient(uuid)') is not null),
    (4, '20260930000400_storage_profile_files.sql',  exists (select 1 from pg_policies where schemaname = 'storage' and policyname = 'licenses_admin_read')),
    (5, '20261001000500_referrals_and_sync.sql',     to_regprocedure('public.sync_pull(text,timestamp with time zone,uuid,integer)') is not null),
    (6, '20261003000600_investigations_and_files.sql', to_regprocedure('public.staff_submit_result(uuid,text,date,text,jsonb,jsonb)') is not null
                                                     and exists (select 1 from pg_policies where schemaname = 'storage' and policyname = 'clinical_files_insert')),
    (7, '20261004000700_notifications_and_consult_files.sql', to_regprocedure('public.my_referrals()') is not null
                                                     and exists (select 1 from pg_policies where schemaname = 'storage' and policyname = 'consult_files_insert')),
    (8, '20261005000800_doctor_grade_and_search.sql',  exists (select 1 from information_schema.columns
                                                              where table_schema = 'public' and table_name = 'doctors' and column_name = 'grade')),
    (9, '20261005000900_grade_change_requests.sql',    to_regprocedure('public.admin_review_grade(uuid,boolean)') is not null)
) as m (n, file, installed)
order by m.n;
