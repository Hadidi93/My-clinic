-- =============================================================================
-- Tests for migration 12: discharge and research-export logging. Demo data only.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set colleague  '''a0000000-0000-0000-0000-000000000004'''
\set pending    '''a0000000-0000-0000-0000-000000000005'''
\set patient2   '''b0000000-0000-0000-0000-000000000002'''

set role authenticated;

set request.jwt.claim.sub = :owner;
select tests.ok(tests.affected($$update public.patients set discharged_at = now() where id = 'b0000000-0000-0000-0000-000000000002'$$) = 1,
                'the main doctor can mark a patient as discharged');
select tests.ok((select discharged_at is not null and deleted_at is null from public.patients where id = :patient2),
                'a discharged patient is not deleted');
select tests.ok(tests.affected($$update public.patients set discharged_at = null where id = 'b0000000-0000-0000-0000-000000000002'$$) = 1,
                'and can re-open the patient');

select tests.ok(public.log_research_export(array[:patient2]::uuid[]) = 1, 'a research export is logged');
select tests.ok((select count(*) from jsonb_array_elements(public.patient_access_log(:patient2)) x
                  where x ->> 'action' = 'export') >= 1,
                'the research export shows in the patient''s access log');
select tests.fails($$select public.log_research_export(array[]::uuid[])$$, '22023', 'an empty export is refused');

set request.jwt.claim.sub = :colleague;
select tests.ok(tests.affected($$update public.patients set discharged_at = now() where id = 'b0000000-0000-0000-0000-000000000002'$$) = 0,
                'another doctor cannot discharge someone else''s patient');
select tests.fails($$select public.log_research_export(array['b0000000-0000-0000-0000-000000000002']::uuid[])$$, '42501',
                   'nobody can export another doctor''s patients');

set request.jwt.claim.sub = :pending;
select tests.fails($$select public.log_research_export(array['b0000000-0000-0000-0000-000000000002']::uuid[])$$, '42501',
                   'an unverified account cannot export');
reset role;
\echo 'ALL DISCHARGE AND RESEARCH EXPORT TESTS PASSED'
