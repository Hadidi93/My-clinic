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
    (5, '20261001000500_referrals_and_sync.sql',     to_regprocedure('public.sync_pull(text,timestamp with time zone,uuid,integer)') is not null)
) as m (n, file, installed)
order by m.n;
