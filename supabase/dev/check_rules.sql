-- Checks that every table has exactly the access rules (RLS policies) it should
-- have after migrations 1–7. Safe to run any time: it only reads.
-- Every row should say ✅. Any ❌ means something is missing or extra.
with expected (tablename, rules) as (
    values
        -- the patient record: read, add, edit (no "delete" rule: entries are only marked "entered in error")
        ('patients', 3), ('presenting_complaints', 3), ('medical_conditions', 3),
        ('surgical_history', 3), ('medications', 3), ('allergies', 3),
        ('family_history', 3), ('social_history', 3), ('examinations', 3),
        ('surgical_cases', 3), ('postop_followups', 3),
        -- investigations and files (Phase 3): read, add, edit
        ('investigation_requests', 3), ('investigation_results', 3), ('attachments', 3),
        -- departments: everyone reads, anyone adds, only admins edit or remove
        ('facilities', 4),
        -- doctor profiles: read, edit own profile
        ('doctors', 2),
        -- audit log: two read rules (admins / yourself, and owners of the patient); nobody can write
        ('audit_log', 2),
        -- consults: read only; changes go through the safe functions
        ('consults', 1), ('consult_sections', 1),
        -- consult messages: read, post
        ('consult_messages', 2),
        -- referrals and ownership history: read only; changes go through the safe functions
        ('referrals', 1), ('patient_ownership_history', 1),
        -- notifications: read your own, mark them read. Phone push addresses: no API access at all (0 rules)
        ('notifications', 2), ('device_tokens', 0)
),
actual as (
    select tablename, count(*) as rules
    from pg_policies
    where schemaname = 'public'
    group by tablename
),
rls as (
    select c.relname as tablename, c.relrowsecurity as rls_on
    from pg_class c join pg_namespace n on n.oid = c.relnamespace
    where n.nspname = 'public' and c.relkind = 'r'
)
select
    coalesce(e.tablename, a.tablename) as table_name,
    e.rules as expected_rules,
    coalesce(a.rules, 0) as actual_rules,
    coalesce(r.rls_on, false) as rls_on,
    case
        when e.tablename is null then '❌ unexpected table/rules — tell Claude'
        when coalesce(r.rls_on, false) = false then '❌ RLS is OFF — tell Claude'
        when coalesce(a.rules, 0) <> e.rules then '❌ wrong number of rules — tell Claude'
        else '✅ OK'
    end as result
from expected e
full join actual a on a.tablename = e.tablename
left join rls r on r.tablename = coalesce(e.tablename, a.tablename)
order by result desc, table_name;
