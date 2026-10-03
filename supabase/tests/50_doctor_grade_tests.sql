-- =============================================================================
-- Tests for doctor grade and directory search. Reuses the earlier demo doctors.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''

set role authenticated;

set request.jwt.claim.sub = :consultant;
select tests.fails($$update public.doctors set grade = 'professor' where id = auth.uid()$$, '23514',
                   'grade is one of resident, specialist, consultant');
update public.doctors set grade = 'consultant' where id = auth.uid();
select tests.ok((select verification_status = 'pending' from public.doctors where id = auth.uid()),
                'changing your grade needs the admin''s approval again');
set request.jwt.claim.sub = :admin;
select public.admin_set_verification(:consultant, 'verified');

set request.jwt.claim.sub = :owner;
select tests.ok((select grade = 'consultant' and specialty = 'Cardiology' and hospital is null
                   from public.search_doctors('cardio')),
                'search results show grade and specialty');
select tests.ok((select count(*) from public.search_doctors('consultant cardiology')) = 1,
                'every word can match a different field (grade + specialty)');
select tests.ok((select count(*) from public.search_doctors('consultant orthopaedics')) = 0,
                'all typed words must match');
select tests.ok((select count(*) from public.search_doctors('  CARDIOLOGY  ')) = 1,
                'search ignores case and extra spaces');
select tests.ok((select count(*) from public.search_doctors('%')) = 0, 'wildcards are still taken literally');
select tests.ok((select (x -> 'other_doctor' ->> 'grade') = 'consultant'
                   from jsonb_array_elements(public.my_consults()) x
                  where x -> 'other_doctor' ->> 'id' = 'a0000000-0000-0000-0000-000000000003' limit 1),
                'consult lists show the colleague''s grade');

reset role;
\echo 'ALL GRADE AND SEARCH TESTS PASSED'
