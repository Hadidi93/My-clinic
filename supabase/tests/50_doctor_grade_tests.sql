-- =============================================================================
-- Tests for doctor grade and directory search. Reuses the earlier demo doctors.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''

set role authenticated;

set request.jwt.claim.sub = :consultant;
select tests.fails($$update public.doctors set grade = 'consultant' where id = auth.uid()$$, '42501',
                   'a doctor cannot set their approved grade directly');
select tests.fails($$update public.doctors set requested_grade = 'professor' where id = auth.uid()$$, '23514',
                   'grade is one of resident, specialist, consultant');
update public.doctors set requested_grade = 'consultant' where id = auth.uid();
select tests.ok((select verification_status = 'verified' and grade is null and requested_grade = 'consultant'
                   from public.doctors where id = auth.uid()),
                'requesting a grade keeps the account working with the old grade');
select tests.fails($$select public.admin_review_grade(auth.uid(), true)$$, '42501', 'only admins approve grades');

set request.jwt.claim.sub = :owner;
select tests.ok((select grade is null from public.search_doctors('cardio')),
                'colleagues see the old grade until it is approved');

set request.jwt.claim.sub = :admin;
select tests.ok((select count(*) from jsonb_array_elements(public.admin_review_queue()) x
                  where x ->> 'id' = 'a0000000-0000-0000-0000-000000000003' and x ->> 'requested_grade' = 'consultant') = 1,
                'the admin sees the grade change waiting');
select public.admin_review_grade(:consultant, true);
select tests.ok((select grade = 'consultant' and requested_grade is null from public.doctors where id = :consultant),
                'approving applies the new grade');
select tests.ok((select count(*) from public.audit_log where action = 'verify' and record_id = :consultant
                   and details ->> 'grade' = 'consultant') = 1, 'grade decisions are audit-logged');

set request.jwt.claim.sub = :consultant;
update public.doctors set requested_grade = 'specialist' where id = auth.uid();
set request.jwt.claim.sub = :admin;
select public.admin_review_grade(:consultant, false);
select tests.ok((select grade = 'consultant' and requested_grade is null from public.doctors where id = :consultant),
                'rejecting keeps the old grade');
select tests.fails($$select public.admin_review_grade('a0000000-0000-0000-0000-000000000003', true)$$, '22023',
                   'nothing to approve once decided');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from jsonb_array_elements(public.admin_review_queue())) = 0,
                'non-admins get an empty review list');
update public.doctors set license_number = 'LIC-STR-3', requested_grade = 'resident' where id = auth.uid();
set request.jwt.claim.sub = :admin;
select public.admin_set_verification(:stranger, 'verified');
select tests.ok((select grade = 'resident' and requested_grade is null from public.doctors where id = :stranger),
                'approving an account also approves the grade it asked for');

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
