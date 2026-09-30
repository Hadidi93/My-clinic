-- =============================================================================
-- Security tests for the Row-Level Security rules.
-- Run with:  supabase/tests/run_local.sh   (or automatically in GitHub Actions)
--
-- Each check prints "ok - ..." or stops the run with "FAIL: ...".
-- All people and patients below are fake demo data.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1
set client_min_messages = notice;

-- ---------------------------------------------------------------------------
-- Tiny test helpers
-- ---------------------------------------------------------------------------
create schema tests;
grant usage on schema tests to authenticated, anon;

create function tests.ok(p_condition boolean, p_message text) returns void
language plpgsql as $$
begin
    if p_condition is not true then
        raise exception 'FAIL: %', p_message;
    end if;
    raise notice 'ok - %', p_message;
end;
$$;

-- Runs p_sql and passes only if it raises an error with the given SQLSTATE.
-- 42501 = permission denied / row-level security violation.
create function tests.fails(p_sql text, p_sqlstate text, p_message text) returns void
language plpgsql as $$
begin
    begin
        execute p_sql;
    exception when others then
        if sqlstate = p_sqlstate then
            raise notice 'ok - %', p_message;
            return;
        end if;
        raise exception 'FAIL: % (expected SQLSTATE %, got %: %)', p_message, p_sqlstate, sqlstate, sqlerrm;
    end;
    raise exception 'FAIL: % (statement succeeded but should have failed)', p_message;
end;
$$;

-- Number of rows a statement affects (for UPDATEs that RLS silently filters).
create function tests.affected(p_sql text) returns bigint
language plpgsql as $$
declare n bigint;
begin
    execute p_sql;
    get diagnostics n = row_count;
    return n;
end;
$$;

-- Demo account ids
\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''
\set pending    '''a0000000-0000-0000-0000-000000000005'''
\set patient    '''b0000000-0000-0000-0000-000000000001'''

-- ---------------------------------------------------------------------------
-- Setup (as the database superuser, like Supabase Auth would)
-- ---------------------------------------------------------------------------
insert into auth.users (id, email, raw_user_meta_data) values
    (:admin,      'admin@demo.test',      '{"full_name": "Demo Admin"}'),
    (:owner,      'owner@demo.test',      '{"full_name": "Dr Demo Owner", "preferred_language": "ar"}'),
    (:consultant, 'consultant@demo.test', '{"full_name": "Dr Demo Consultant"}'),
    (:stranger,   'stranger@demo.test',   '{"full_name": "Dr Demo Stranger"}'),
    (:pending,    'pending@demo.test',    '{"full_name": "Dr Demo Pending"}');

select tests.ok((select count(*) from public.doctors) = 5, 'sign-up creates a doctor row for every user');
select tests.ok((select verification_status = 'pending' and full_name = 'Dr Demo Owner' and preferred_language = 'ar'
                   from public.doctors where id = :owner),
                'new doctor starts as pending with name and language from sign-up');

-- Bootstrap the first administrator directly in the database (documented in docs/SETUP.md).
update public.doctors set role = 'admin', verification_status = 'verified' where id = :admin;
update public.doctors set license_number = 'LIC-OWNER', specialty = 'General Surgery' where id = :owner;
update public.doctors set license_number = 'LIC-CONS',  specialty = 'Cardiology'      where id = :consultant;
update public.doctors set license_number = 'LIC-STR',   specialty = 'Orthopaedics'    where id = :stranger;

-- ---------------------------------------------------------------------------
-- Anonymous visitors see nothing
-- ---------------------------------------------------------------------------
set role anon;
select tests.fails('select * from public.doctors', '42501', 'anonymous users cannot read doctors');
select tests.fails('select * from public.patients', '42501', 'anonymous users cannot read patients');
reset role;

-- ---------------------------------------------------------------------------
-- Doctor profiles and verification
-- ---------------------------------------------------------------------------
set role authenticated;
set request.jwt.claim.sub = :pending;

select tests.ok((select count(*) from public.doctors) = 1, 'a doctor sees only their own profile');
select tests.ok(tests.affected($$update public.doctors set full_name = 'Dr Demo Pending Two' where id = auth.uid()$$) = 1,
                'a doctor can edit their own profile');
select tests.ok(tests.affected($$update public.doctors set full_name = 'Hacked' where id = 'a0000000-0000-0000-0000-000000000002'$$) = 0,
                'a doctor cannot edit someone else''s profile');
select tests.fails($$update public.doctors set verification_status = 'verified' where id = auth.uid()$$, '42501',
                   'a doctor cannot verify themselves');
select tests.fails($$update public.doctors set role = 'admin' where id = auth.uid()$$, '42501',
                   'a doctor cannot make themselves admin');
select tests.fails($$select public.admin_set_verification('a0000000-0000-0000-0000-000000000002', 'verified')$$, '42501',
                   'a non-admin cannot approve doctors');
select tests.ok((select count(*) from public.search_doctors('')) = 0, 'an unverified doctor cannot search the directory');

set request.jwt.claim.sub = :admin;
select tests.ok((select count(*) from public.doctors) = 5, 'admin sees all doctors (approval screen)');
select public.admin_set_verification(:owner, 'verified');
select public.admin_set_verification(:consultant, 'verified');
select public.admin_set_verification(:stranger, 'verified');
select tests.ok((select count(*) from public.doctors where verification_status = 'verified') = 4, 'admin approved three doctors');
select tests.fails($$select public.admin_set_verification(auth.uid(), 'suspended')$$, '42501',
                   'admin cannot change their own status');
select tests.ok((select count(*) from public.audit_log where action = 'verify') = 3, 'every approval is audit-logged');

set request.jwt.claim.sub = :owner;
select tests.ok((select array_agg(full_name order by full_name) from public.search_doctors(''))
                = array['Demo Admin', 'Dr Demo Consultant', 'Dr Demo Stranger'],
                'directory lists verified doctors only, excluding yourself and pending accounts');
select tests.ok((select count(*) from public.search_doctors('cardio')) = 1, 'directory search matches specialty');
select tests.ok((select count(*) from public.search_doctors('%')) = 0, 'wildcards typed by the user are treated literally');

select tests.fails($$insert into public.audit_log (action, table_name) values ('view', 'x')$$, '42501',
                   'nobody can write to the audit log directly');
select tests.fails($$delete from public.audit_log$$, '42501', 'nobody can delete audit history');

-- Changing licence details sends a verified account back for review.
set request.jwt.claim.sub = :stranger;
update public.doctors set license_number = 'LIC-STR-2' where id = auth.uid();
select tests.ok((select verification_status = 'pending' from public.doctors where id = auth.uid()),
                'editing your licence number requires re-verification');
set request.jwt.claim.sub = :admin;
select public.admin_set_verification(:stranger, 'verified');

-- ---------------------------------------------------------------------------
-- Patient records: owner only
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
insert into public.patients (id, full_name) values (:patient, 'Demo Patient Alpha'); -- quick-add: name only
update public.patients set date_of_birth = '1980-10-01', sex = 'female', national_id = '00000000000001',
                           phone = '+201000000001', tags = array['awaiting surgery'] where id = :patient;
insert into public.allergies (patient_id, allergen, severity) values (:patient, 'Penicillin (demo)', 'severe');
insert into public.examinations (patient_id, pulse_bpm, systolic_mmhg, diastolic_mmhg) values (:patient, 88, 120, 80);
insert into public.medications (patient_id, name) values (:patient, 'Demo drug 5 mg');
select tests.ok((select count(*) from public.patients) = 1, 'owner sees their patient');
select tests.ok((select count(*) from public.allergies) = 1, 'owner sees their patient''s allergies');
select tests.fails($$delete from public.patients$$, '42501', 'patients cannot be hard-deleted through the API');
select tests.fails($$update public.patients set owner_id = 'a0000000-0000-0000-0000-000000000004'$$, '42501',
                   'a patient cannot be moved to another doctor');
select tests.ok((select count(*) from public.audit_log where patient_id = :patient and action = 'create') >= 4,
                'creating the patient and its sections is audit-logged');
select tests.ok((select not (details::text ilike '%penicillin%') from public.audit_log
                  where table_name = 'allergies' limit 1),
                'the audit log stores no clinical values');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.patients) = 0, 'another doctor cannot see the patient');
select tests.ok((select count(*) from public.allergies) = 0, 'another doctor cannot see any section');
select tests.ok((select count(*) from public.audit_log where patient_id = :patient) = 0,
                'another doctor cannot read the patient''s audit history');
select tests.fails($$insert into public.allergies (patient_id, allergen) values ('b0000000-0000-0000-0000-000000000001', 'x')$$,
                   '42501', 'another doctor cannot add to the record');
select tests.ok(tests.affected($$update public.patients set full_name = 'x'$$) = 0, 'another doctor cannot edit the patient');
select tests.fails($$select public.log_record_view('b0000000-0000-0000-0000-000000000001', 'allergies')$$, '42501',
                   'cannot log a view of a record you cannot read');

-- ---------------------------------------------------------------------------
-- Consultations: the checks in create_consult
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :pending;
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'an unverified doctor cannot share');

set request.jwt.claim.sub = :stranger;
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'you cannot share a patient you do not own');

set request.jwt.claim.sub = :owner;
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000005',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'you cannot share with an unverified doctor');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, false, current_date)$$,
                   '22023', 'patient consent is required');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date + 1)$$,
                   '22023', 'consent date cannot be in the future');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['identifiers']::public.record_section[], true, 7, true, current_date)$$,
                   '22023', 'an anonymized consult cannot include identifiers');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array[]::public.record_section[], true, 7, true, current_date)$$,
                   '22023', 'at least one section must be chosen');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 91, true, current_date)$$,
                   '22023', 'access duration is limited to 90 days');
select tests.fails($$insert into public.consults (patient_id, requester_id, consultant_id, question, anonymized,
                     consent_confirmed, consent_date, expires_at) values ('b0000000-0000-0000-0000-000000000001',
                     auth.uid(), 'a0000000-0000-0000-0000-000000000003', 'q', true, true, current_date, now() + interval '1 day')$$,
                   '42501', 'consults cannot be inserted directly, bypassing the checks');

-- A valid, anonymized consult sharing allergies + examination for 7 days.
select public.create_consult(:patient, :consultant, 'Demo question: fit for surgery?',
                             array['allergies', 'examination']::public.record_section[], true, 7, true, current_date)
       as consult1 \gset
set test.consult1 = :'consult1';

-- ---------------------------------------------------------------------------
-- Consultations: what the consultant can and cannot do
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.allergies) = 1, 'consultant can read a shared section (allergies)');
select tests.ok((select count(*) from public.examinations) = 1, 'consultant can read a shared section (examination)');
select tests.ok((select count(*) from public.medications) = 0, 'consultant cannot read a section that was not shared');
select tests.ok((select count(*) from public.patients) = 0, 'consultant cannot read the patients table directly');
select tests.ok((select public.get_consult_patient(current_setting('test.consult1')::uuid) ->> 'sex') = 'female',
                'consultant sees sex');
select tests.ok((select (public.get_consult_patient(current_setting('test.consult1')::uuid) ->> 'age_years') is not null),
                'consultant sees age');
select tests.ok((select not (public.get_consult_patient(current_setting('test.consult1')::uuid) ? 'full_name')),
                'anonymized consult hides the patient''s name');
select tests.fails($$insert into public.allergies (patient_id, allergen) values ('b0000000-0000-0000-0000-000000000001', 'x')$$,
                   '42501', 'consultant cannot add to the record (read-only)');
select tests.ok(tests.affected($$update public.allergies set allergen = 'x'$$) = 0, 'consultant cannot edit the record');
select tests.fails($$update public.consults set expires_at = now() + interval '1 year'$$, '42501',
                   'consultant cannot extend their own access');
select tests.fails($$select public.revoke_consult(current_setting('test.consult1')::uuid)$$, '42501',
                   'only the requester can revoke');

insert into public.consult_messages (consult_id, body) values (current_setting('test.consult1')::uuid, 'Demo reply: fit.');
select tests.ok((select status = 'answered' from public.consults where id = current_setting('test.consult1')::uuid),
                'a reply from the consultant marks the consult answered');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.consults) = 0, 'other doctors cannot see the consult');
select tests.ok((select count(*) from public.consult_messages) = 0, 'other doctors cannot see the messages');
select tests.fails($$insert into public.consult_messages (consult_id, body) values (current_setting('test.consult1')::uuid, 'x')$$,
                   '42501', 'other doctors cannot post in the thread');
select tests.fails($$select public.get_consult_patient(current_setting('test.consult1')::uuid)$$, '42501',
                   'other doctors cannot use the consult to see the patient');

set request.jwt.claim.sub = :owner;
select tests.ok((select count(*) from public.audit_log where patient_id = :patient and action = 'share') = 1,
                'sharing is audit-logged');
select tests.ok((select count(*) from public.audit_log where patient_id = :patient and action = 'view'
                   and actor_id = :consultant) >= 1,
                'the owner can see that the consultant viewed the patient');

-- Revoke: access stops immediately.
select public.revoke_consult(current_setting('test.consult1')::uuid);
set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.allergies) = 0, 'after revoking, the consultant sees nothing');
select tests.fails($$select public.get_consult_patient(current_setting('test.consult1')::uuid)$$, '42501',
                   'after revoking, patient details are blocked');
select tests.fails($$insert into public.consult_messages (consult_id, body) values (current_setting('test.consult1')::uuid, 'x')$$,
                   '42501', 'after revoking, no new messages can be posted');
select tests.ok((select count(*) from public.consult_messages) = 1, 'after revoking, the existing thread stays readable');

-- ---------------------------------------------------------------------------
-- Expiry, closing, identifiers, suspension
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select public.create_consult(:patient, :consultant, 'Demo question 2',
                             array['identifiers', 'allergies']::public.record_section[], false, 7, true, current_date)
       as consult2 \gset
set test.consult2 = :'consult2';

set request.jwt.claim.sub = :consultant;
select tests.ok((select public.get_consult_patient(current_setting('test.consult2')::uuid) ->> 'full_name') = 'Demo Patient Alpha',
                'identifiers are shown when shared and not anonymized');

-- Suspended consultant receives nothing, even with a live consult.
reset role;
update public.doctors set verification_status = 'suspended' where id = :consultant;
set role authenticated;
select tests.ok((select count(*) from public.allergies) = 0, 'a suspended consultant loses access');
select tests.ok((select count(*) from public.consults) = 0, 'a suspended consultant cannot see consults');
reset role;
update public.doctors set verification_status = 'verified' where id = :consultant;

-- Expired consult gives no access.
update public.consults set created_at = now() - interval '8 days', expires_at = now() - interval '1 day'
 where id = :'consult2';
set role authenticated;
select tests.ok((select count(*) from public.allergies) = 0, 'an expired consult gives no access');

-- Closed consult gives no access.
reset role;
update public.consults set created_at = now(), expires_at = now() + interval '7 days' where id = :'consult2';
set role authenticated;
select tests.ok((select count(*) from public.allergies) = 1, '(consult re-opened for the next test)');
select public.close_consult(current_setting('test.consult2')::uuid);
select tests.ok((select count(*) from public.allergies) = 0, 'a closed consult gives no access');

-- Soft-deleted patient is hidden from consultants.
set request.jwt.claim.sub = :owner;
select public.create_consult(:patient, :consultant, 'Demo question 3',
                             array['allergies']::public.record_section[], true, 7, true, current_date) \gset
update public.patients set deleted_at = now() where id = :patient;
set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.allergies) = 0, 'a deleted patient is hidden from consultants');
set request.jwt.claim.sub = :owner;
update public.patients set deleted_at = null where id = :patient;

-- ---------------------------------------------------------------------------
-- Storage: profile photos and licence documents
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
insert into storage.objects (bucket_id, name) values ('avatars', 'a0000000-0000-0000-0000-000000000002/avatar.jpg');
insert into storage.objects (bucket_id, name) values ('licenses', 'a0000000-0000-0000-0000-000000000002/license.jpg');
select tests.ok(true, 'a doctor can upload their own photo and licence');
select tests.fails($$insert into storage.objects (bucket_id, name) values ('avatars', 'a0000000-0000-0000-0000-000000000004/avatar.jpg')$$,
                   '42501', 'a doctor cannot upload into someone else''s folder');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from storage.objects where bucket_id = 'avatars') = 1, 'verified doctors can see profile photos');
select tests.ok((select count(*) from storage.objects where bucket_id = 'licenses') = 0, 'other doctors cannot see licence documents');

set request.jwt.claim.sub = :pending;
select tests.ok((select count(*) from storage.objects where bucket_id = 'avatars') = 0, 'unverified doctors cannot browse photos');

set request.jwt.claim.sub = :admin;
select tests.ok((select count(*) from storage.objects where bucket_id = 'licenses') = 1, 'admins can review licence documents');

reset role;
\echo 'ALL SECURITY TESTS PASSED'
