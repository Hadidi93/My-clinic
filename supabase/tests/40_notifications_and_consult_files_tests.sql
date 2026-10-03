-- =============================================================================
-- Security tests for notifications, push devices, consult files and the
-- consult/referral lists. Runs after the earlier test files and reuses their
-- demo accounts. All people and patients are fake demo data.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''
\set labstaff   '''a0000000-0000-0000-0000-000000000006'''
\set newstaff   '''a0000000-0000-0000-0000-000000000008'''
\set patient2   '''b0000000-0000-0000-0000-000000000002'''
\set lab        '''f0000000-0000-0000-0000-000000000001'''

set role authenticated;

-- ---------------------------------------------------------------------------
-- Consult requests and messages notify the other doctor
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select public.create_consult(:patient2, :consultant, 'Demo: anaesthetic opinion?',
                             array['allergies', 'investigations']::public.record_section[], true, 7, true, current_date)
       as anon_consult \gset
set test.anon_consult = :'anon_consult';
select tests.ok((select count(*) from public.notifications where ref_id = current_setting('test.anon_consult')::uuid) = 0,
                'the sender gets no notification about their own consult');

set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.notifications
                  where kind = 'consult_request' and ref_id = current_setting('test.anon_consult')::uuid) = 1,
                'the consultant is notified of a consult request');
select tests.ok((select count(*) from information_schema.columns
                  where table_schema = 'public' and table_name = 'notifications'
                    and column_name not in ('id', 'recipient_id', 'kind', 'ref_id', 'created_at', 'read_at')) = 0,
                'notifications hold no patient data (only what happened and an id)');
insert into public.consult_messages (consult_id, body) values (current_setting('test.anon_consult')::uuid, 'Demo: fit for surgery.');

set request.jwt.claim.sub = :owner;
select tests.ok((select count(*) from public.notifications
                  where kind = 'consult_message' and ref_id = current_setting('test.anon_consult')::uuid) = 1,
                'the requester is notified of the reply');
insert into public.consult_messages (consult_id, body) values (current_setting('test.anon_consult')::uuid, 'Demo: thank you.');
select tests.ok((select count(*) from public.notifications where recipient_id = :consultant) = 0,
                'nobody can read another doctor''s notifications');
select tests.ok(tests.affected($$update public.notifications set read_at = now() where recipient_id = 'a0000000-0000-0000-0000-000000000003'$$) = 0,
                'nobody can mark another doctor''s notifications as read');

set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from public.notifications
                  where kind = 'consult_message' and ref_id = current_setting('test.anon_consult')::uuid) = 1,
                'the consultant is notified of the requester''s message');
select tests.fails($$update public.notifications set kind = 'lab_request'$$, '42501', 'a notification''s content cannot be changed');
select tests.fails($$insert into public.notifications (recipient_id, kind) values ('a0000000-0000-0000-0000-000000000002', 'consult_request')$$,
                   '42501', 'notifications cannot be created through the API (no fake alerts)');
select tests.ok((select (public.my_consults() -> 0 ->> 'unread')::int) >= 1, 'consult list shows unread messages');
select public.mark_all_notifications_read();
select tests.ok((select count(*) from public.notifications where read_at is null) = 0, 'marking all as read works');

-- ---------------------------------------------------------------------------
-- Consult list: the consultant never sees the name of an anonymized patient
-- ---------------------------------------------------------------------------
select tests.ok((select x ->> 'patient_name' is null and x ->> 'role' = 'consultant' and x -> 'other_doctor' ->> 'full_name' = 'Dr Demo Owner'
                   from jsonb_array_elements(public.my_consults()) x
                  where x ->> 'id' = current_setting('test.anon_consult')),
                'an anonymized consult hides the patient''s name from the consultant');
set request.jwt.claim.sub = :owner;
select tests.ok((select x ->> 'patient_name' = 'Demo Patient Beta' and x ->> 'role' = 'requester'
                   from jsonb_array_elements(public.my_consults()) x
                  where x ->> 'id' = current_setting('test.anon_consult')),
                'the requester sees the patient''s name');
set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from jsonb_array_elements(public.my_consults()) x
                  where x ->> 'id' = current_setting('test.anon_consult')) = 0,
                'other doctors don''t see the consult in their list');

-- ---------------------------------------------------------------------------
-- Consult files
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select :'anon_consult' || '/demo-xray.jpg' as cfile \gset
set test.cfile = :'cfile';
insert into storage.objects (bucket_id, name) values ('consult-files', current_setting('test.cfile'));
insert into public.consult_messages (consult_id, body, attachment_paths)
values (current_setting('test.anon_consult')::uuid, 'Demo: X-ray attached', array[current_setting('test.cfile')]);
select tests.ok(true, 'a participant can attach a file to the consult');
select tests.fails($$insert into public.consult_messages (consult_id, body, attachment_paths)
                     values (current_setting('test.anon_consult')::uuid, 'x',
                             array['00000000-0000-0000-0000-000000000000/other.jpg'])$$,
                   '42501', 'a message cannot point to another consult''s files');

set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from storage.objects where bucket_id = 'consult-files') = 1,
                'the consultant can open the consult''s files');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from storage.objects where bucket_id = 'consult-files') = 0,
                'other doctors cannot open consult files');
select tests.fails($$insert into storage.objects (bucket_id, name) values ('consult-files', current_setting('test.anon_consult') || '/x.jpg')$$,
                   '42501', 'other doctors cannot add files to the consult');
select tests.fails($$insert into storage.objects (bucket_id, name) values ('consult-files', 'not-a-uuid/x.jpg')$$,
                   '42501', 'a malformed consult file path is refused');

set request.jwt.claim.sub = :consultant;
select public.close_consult(current_setting('test.anon_consult')::uuid);
select tests.fails($$insert into storage.objects (bucket_id, name) values ('consult-files', current_setting('test.anon_consult') || '/late.jpg')$$,
                   '42501', 'no new files once the consult is closed');
select tests.ok((select count(*) from storage.objects where bucket_id = 'consult-files') = 1,
                'the conversation''s files stay readable to the two doctors after closing');

-- ---------------------------------------------------------------------------
-- Referrals notify both ways; the list shows the receiving doctor who the patient is
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select public.create_referral(:patient2, :stranger, 'comanagement', 'Demo: please co-manage', true, current_date) as ref \gset
set test.ref = :'ref';

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.notifications
                  where kind = 'referral_request' and ref_id = current_setting('test.ref')::uuid) = 1,
                'the colleague is notified of a referral');
select tests.ok((select x ->> 'patient_name' = 'Demo Patient Beta' and x ->> 'direction' = 'received' and x ->> 'status' = 'pending'
                   from jsonb_array_elements(public.my_referrals()) x where x ->> 'id' = current_setting('test.ref')),
                'the receiving doctor sees who the patient is, to decide');
select public.respond_referral(current_setting('test.ref')::uuid, false);

set request.jwt.claim.sub = :owner;
select tests.ok((select count(*) from public.notifications
                  where kind = 'referral_response' and ref_id = current_setting('test.ref')::uuid) = 1,
                'the sender is notified of the answer');
set request.jwt.claim.sub = :consultant;
select tests.ok((select count(*) from jsonb_array_elements(public.my_referrals()) x where x ->> 'id' = current_setting('test.ref')) = 0,
                'other doctors don''t see the referral');

-- ---------------------------------------------------------------------------
-- New lab requests notify the department's approved staff only
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
insert into public.investigation_requests (patient_id, kind, tests, facility_id)
values (:patient2, 'lab', array['CRP'], :lab) returning id as labreq2 \gset
set test.labreq2 = :'labreq2';
insert into public.investigation_requests (patient_id, kind, tests) values (:patient2, 'lab', array['Urea']) returning id as ownreq2 \gset
set test.ownreq2 = :'ownreq2';

set request.jwt.claim.sub = :labstaff;
select tests.ok((select count(*) from public.notifications
                  where kind = 'lab_request' and ref_id = current_setting('test.labreq2')::uuid) = 1,
                'department staff are notified of a new request');
select tests.ok((select count(*) from public.notifications where ref_id = current_setting('test.ownreq2')::uuid) = 0,
                'requests not sent to a department notify nobody');
set request.jwt.claim.sub = :newstaff;
select tests.ok((select count(*) from public.notifications where kind = 'lab_request') = 0,
                'unapproved staff get no notifications');

-- ---------------------------------------------------------------------------
-- Push devices
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select public.register_device('demo-device-token-0001');
select tests.fails($$select * from public.device_tokens$$, '42501', 'device tokens cannot be read through the API');
set request.jwt.claim.sub = :consultant;
select public.register_device('demo-device-token-0001');
reset role;
select tests.ok((select user_id = :consultant from public.device_tokens where token = 'demo-device-token-0001'),
                'a phone used by another doctor moves to them (the previous doctor gets no more pushes there)');
set role authenticated;
set request.jwt.claim.sub = :owner;
select public.unregister_device('demo-device-token-0001');
reset role;
select tests.ok((select count(*) from public.device_tokens) = 1, 'a doctor cannot unregister someone else''s phone');
set role authenticated;
set request.jwt.claim.sub = :consultant;
select public.unregister_device('demo-device-token-0001');
reset role;
select tests.ok((select count(*) from public.device_tokens) = 0, 'signing out unregisters the phone');

set role anon;
select tests.fails($$select public.register_device('demo-device-token-0002')$$, '42501', 'anonymous users cannot register devices');
reset role;
\echo 'ALL NOTIFICATION AND CONSULT FILE TESTS PASSED'
