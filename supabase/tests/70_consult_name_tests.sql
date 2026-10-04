-- =============================================================================
-- Tests for migration 11: a consult shares the name with age and sex unless
-- anonymized; ID, phone and address still need "Personal data". Demo data only.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set colleague  '''a0000000-0000-0000-0000-000000000004'''
\set patient2   '''b0000000-0000-0000-0000-000000000002'''

set role authenticated;

set request.jwt.claim.sub = :owner;
select public.create_consult(:patient2, :colleague, 'Demo question: named consult',
                             array['allergies']::public.record_section[], false, 7, true, current_date)
       as named \gset
set test.named = :'named';
select public.create_consult(:patient2, :colleague, 'Demo question: anonymous consult',
                             array['allergies']::public.record_section[], true, 7, true, current_date)
       as anon \gset
set test.anon = :'anon';

set request.jwt.claim.sub = :colleague;
select tests.ok((select public.get_consult_patient(current_setting('test.named')::uuid) ->> 'full_name') = 'Demo Patient Beta',
                'a consult that is not anonymized shares the name');
select tests.ok((select p ->> 'sex' is not null and p ? 'age_years'
                   from public.get_consult_patient(current_setting('test.named')::uuid) p),
                'age and sex are shared with the name');
select tests.ok((select not (p ? 'national_id') and not (p ? 'phone') and not (p ? 'address')
                   from public.get_consult_patient(current_setting('test.named')::uuid) p),
                'ID, phone and address still need "Personal data"');
select tests.ok((select not (public.get_consult_patient(current_setting('test.anon')::uuid) ? 'full_name')),
                'an anonymized consult still hides the name');
select tests.ok((select x ->> 'patient_name' = 'Demo Patient Beta'
                   from jsonb_array_elements(public.my_consults()) x where x ->> 'id' = current_setting('test.named')),
                'the consult list shows the name');
select tests.ok((select x ->> 'patient_name' is null
                   from jsonb_array_elements(public.my_consults()) x where x ->> 'id' = current_setting('test.anon')),
                'the consult list hides the name of an anonymized consult');

set request.jwt.claim.sub = :owner;
select public.revoke_consult(current_setting('test.named')::uuid);
set request.jwt.claim.sub = :colleague;
select tests.ok((select x ->> 'patient_name' is null
                   from jsonb_array_elements(public.my_consults()) x where x ->> 'id' = current_setting('test.named')),
                'once access is withdrawn the name is hidden again');
reset role;
\echo 'ALL CONSULT NAME TESTS PASSED'
