-- =============================================================================
-- Tests for the access-log views and export logging. Reuses earlier demo data.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''
\set patient2   '''b0000000-0000-0000-0000-000000000002'''

set role authenticated;

-- The consultant from the investigations tests opens the shared section.
set request.jwt.claim.sub = :stranger;
select public.log_record_view(:patient2, 'investigations');

set request.jwt.claim.sub = :owner;
select public.log_record_export(:patient2);
select tests.ok(jsonb_array_length(public.patient_access_log(:patient2)) > 0, 'the main doctor sees the patient''s access log');
select tests.ok((select x ->> 'action' = 'export' and (x ->> 'actor_is_me')::boolean
                   from jsonb_array_elements(public.patient_access_log(:patient2)) x limit 1),
                'exporting is logged first, newest at the top');
select tests.ok((select count(*) from jsonb_array_elements(public.patient_access_log(:patient2)) x
                  where x ->> 'via' = 'staff' and (x ->> 'actor_is_staff')::boolean) >= 1,
                'lab staff opening the request shows up as "via the department"');
select tests.ok((select count(*) from jsonb_array_elements(public.patient_access_log(:patient2)) x
                  where x ->> 'action' = 'view' and x ->> 'actor_name' = 'Dr Demo Stranger') >= 1,
                'a consultant''s views are listed with their name');
select tests.ok((select bool_and(not (x::text ilike '%Iodine%') and not (x::text ilike '%Beta%'))
                   from jsonb_array_elements(public.patient_access_log(:patient2)) x),
                'the log holds no clinical values or patient names');

set request.jwt.claim.sub = :stranger;
select tests.fails($$select public.patient_access_log('b0000000-0000-0000-0000-000000000002')$$, '42501',
                   'other doctors cannot see a patient''s access log');
select tests.fails($$select public.log_record_export('b0000000-0000-0000-0000-000000000002')$$, '42501',
                   'only the main doctor can export a record');
select tests.fails($$select public.admin_activity_log()$$, '42501', 'only admins see the app-wide activity log');

set request.jwt.claim.sub = :consultant; -- owns Demo Patient Alpha, co-managed by nobody now
select tests.fails($$select public.log_record_export('b0000000-0000-0000-0000-000000000002')$$, '42501',
                   'a doctor with read access via consult cannot export');

set request.jwt.claim.sub = :admin;
select tests.ok(jsonb_array_length(public.admin_activity_log(50)) = 50, 'admins see the activity log, newest first, in pages');
select tests.ok((select count(*) from jsonb_array_elements(public.admin_activity_log(1000)) x where x ->> 'action' = 'verify') >= 3,
                'approvals appear in the activity log');
select tests.ok(jsonb_array_length(public.admin_activity_log(10, '2000-01-01')) = 0, 'paging by time works');
select tests.ok(jsonb_array_length(public.patient_access_log(:patient2)) > 0, 'admins can open a patient''s access log');

set role anon;
select tests.fails($$select public.admin_activity_log()$$, '42501', 'anonymous users get nothing');
reset role;
\echo 'ALL ACCESS LOG TESTS PASSED'
