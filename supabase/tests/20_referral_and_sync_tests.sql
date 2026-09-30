-- =============================================================================
-- Security tests for referrals (co-management / transfer), soft delete and
-- offline sync. Runs after 10_security_tests.sql and reuses its demo accounts.
-- All people and patients are fake demo data.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''
\set pending    '''a0000000-0000-0000-0000-000000000005'''
\set patient    '''b0000000-0000-0000-0000-000000000001'''

set role authenticated;

-- ---------------------------------------------------------------------------
-- Creating referrals: the checks
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :pending;
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000004', 'comanagement', null, true, current_date)$$,
                   '42501', 'an unverified doctor cannot refer');

set request.jwt.claim.sub = :stranger;
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000003', 'comanagement', null, true, current_date)$$,
                   '42501', 'only the primary doctor can refer a patient');

set request.jwt.claim.sub = :owner;
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000005', 'comanagement', null, true, current_date)$$,
                   '42501', 'cannot refer to an unverified doctor');
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000004', 'comanagement', null, false, current_date)$$,
                   '22023', 'referral requires patient consent');
select tests.fails($$insert into public.referrals (patient_id, from_doctor_id, to_doctor_id, kind, consent_confirmed, consent_date, status)
                     values ('b0000000-0000-0000-0000-000000000001', auth.uid(), 'a0000000-0000-0000-0000-000000000004',
                             'comanagement', true, current_date, 'accepted')$$,
                   '42501', 'referrals cannot be inserted directly (e.g. pre-accepted)');

select public.create_referral(:patient, :stranger, 'comanagement', 'Demo: please co-manage', true, current_date)
       as coman \gset
set test.coman = :'coman';
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000004', 'comanagement', null, true, current_date)$$,
                   '22023', 'cannot send a second open referral to the same doctor');

-- ---------------------------------------------------------------------------
-- Co-management
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.patients) = 0, 'no access before the colleague accepts');
select tests.ok((select count(*) from public.referrals) = 1, 'the colleague can see the pending referral');

set request.jwt.claim.sub = :consultant;
select tests.fails($$select public.respond_referral(current_setting('test.coman')::uuid, true)$$, '42501',
                   'only the invited doctor can accept');

set request.jwt.claim.sub = :stranger;
select public.respond_referral(current_setting('test.coman')::uuid, true);
select tests.ok((select count(*) from public.patients) = 1, 'co-manager can read the patient');
select tests.ok((select count(*) from public.medications) = 1, 'co-manager can read every section');
insert into public.examinations (patient_id, pulse_bpm) values (:patient, 92);
select tests.ok(true, 'co-manager can add entries');
select tests.ok(tests.affected($$update public.patients set phone = '+201000000009'$$) = 1,
                'co-manager can edit patient details');
select tests.fails($$update public.patients set deleted_at = now()$$, '42501', 'co-manager cannot delete the patient');
select tests.fails($$delete from public.examinations$$, '42501', 'nobody can hard-delete record entries');
select tests.ok(tests.affected($$update public.examinations set deleted_at = now() where pulse_bpm = 92$$) = 1,
                'co-manager can mark an entry as entered in error');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'co-manager cannot share the patient by consult');
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000003', 'transfer', null, true, current_date)$$,
                   '42501', 'co-manager cannot transfer the patient');

-- Offline sync for the co-manager
select tests.ok((select count(*) from public.my_patient_ids() as id where id = :patient) = 1,
                'co-managed patient is included in the offline list');
select tests.ok(jsonb_array_length(public.sync_pull('allergies', null)) = 1, 'co-manager can sync the record offline');
select tests.fails($$select public.sync_pull('doctors', null)$$, '22023', 'sync is limited to record tables');

set request.jwt.claim.sub = :consultant;
select tests.ok(jsonb_array_length(public.sync_pull('allergies', null)) = 0,
                'consult readers cannot download records for offline use');

set request.jwt.claim.sub = :owner;
select tests.ok(jsonb_array_length(public.sync_pull('patients', null)) = 1, 'owner can sync their patient');
select tests.ok(jsonb_array_length(public.sync_pull('allergies', now() + interval '1 day')) = 0,
                'sync returns only rows changed after the given time');

-- Either doctor can end co-management.
set request.jwt.claim.sub = :stranger;
select public.end_referral(current_setting('test.coman')::uuid);
select tests.ok((select count(*) from public.patients) = 0, 'ending co-management removes access');
select tests.ok((select count(*) from public.my_patient_ids()) = 0, 'and removes the patient from the offline list');

-- Decline / cancel
set request.jwt.claim.sub = :owner;
select public.create_referral(:patient, :stranger, 'comanagement', null, true, current_date) as r2 \gset
set test.r2 = :'r2';
set request.jwt.claim.sub = :stranger;
select public.respond_referral(current_setting('test.r2')::uuid, false);
select tests.ok((select status = 'declined' from public.referrals where id = current_setting('test.r2')::uuid),
                'the colleague can decline');
select tests.ok((select count(*) from public.patients) = 0, 'declining gives no access');

set request.jwt.claim.sub = :owner;
select public.create_referral(:patient, :stranger, 'comanagement', null, true, current_date) as r3 \gset
set test.r3 = :'r3';
select public.end_referral(current_setting('test.r3')::uuid);
select tests.ok((select status = 'cancelled' from public.referrals where id = current_setting('test.r3')::uuid),
                'the sender can cancel a pending referral');

-- ---------------------------------------------------------------------------
-- Transfer
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select public.create_referral(:patient, :stranger, 'comanagement', null, true, current_date) as r4 \gset
set test.r4 = :'r4';
select public.create_referral(:patient, :consultant, 'transfer', 'Demo: please take over', true, current_date)
       as transfer \gset
set test.transfer = :'transfer';
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000001',
                     'a0000000-0000-0000-0000-000000000001', 'transfer', null, true, current_date)$$,
                   '22023', 'only one transfer can wait for acceptance');

set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.medications) = 0, 'no transfer access before accepting');
select public.respond_referral(current_setting('test.transfer')::uuid, true);
select tests.ok((select owner_id = :consultant from public.patients where id = :patient),
                'accepting a transfer makes the colleague the owner');
select tests.ok((select count(*) from public.medications) = 1, 'the new owner can read the whole record');
insert into public.medications (patient_id, name) values (:patient, 'Demo drug added after transfer');

set request.jwt.claim.sub = :owner;
select tests.ok((select status = 'cancelled' from public.referrals where id = current_setting('test.r4')::uuid),
                'the previous owner''s pending referrals are cancelled on transfer');
select tests.ok((select count(*) from public.patients) = 1, 'the previous owner keeps read-only history');
select tests.ok((select count(*) from public.medications) = 1,
                'the previous owner does not see entries added after the transfer');
select tests.ok(tests.affected($$update public.patients set phone = 'x'$$) = 0, 'the previous owner can no longer edit');
select tests.fails($$insert into public.allergies (patient_id, allergen) values ('b0000000-0000-0000-0000-000000000001', 'x')$$,
                   '42501', 'the previous owner can no longer add entries');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000004',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'the previous owner can no longer share the patient');
select tests.ok((select count(*) from public.my_patient_ids()) = 0, 'history is not kept offline by the previous owner');
select tests.ok((select count(*) from public.audit_log where action = 'transfer') = 0,
                'the previous owner no longer sees the patient''s audit trail');

set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.audit_log where action = 'transfer' and patient_id = :patient) = 1,
                'the transfer is audit-logged for the new owner');
select tests.fails($$select public.end_referral(current_setting('test.transfer')::uuid)$$, '42501',
                   'a completed transfer cannot be undone (transfer back instead)');

reset role;
\echo 'ALL REFERRAL AND SYNC TESTS PASSED'
