-- =============================================================================
-- Security tests for investigations, results, files and lab/radiology staff.
-- Runs after the earlier test files and reuses their demo doctors.
-- All people and patients are fake demo data.
-- =============================================================================
\set ON_ERROR_STOP 1
\set QUIET 1

\set admin      '''a0000000-0000-0000-0000-000000000001'''
\set owner      '''a0000000-0000-0000-0000-000000000002'''
\set consultant '''a0000000-0000-0000-0000-000000000003'''
\set stranger   '''a0000000-0000-0000-0000-000000000004'''
\set labstaff   '''a0000000-0000-0000-0000-000000000006'''
\set radstaff   '''a0000000-0000-0000-0000-000000000007'''
\set newstaff   '''a0000000-0000-0000-0000-000000000008'''
\set patient2   '''b0000000-0000-0000-0000-000000000002'''
\set lab        '''f0000000-0000-0000-0000-000000000001'''
\set radiology  '''f0000000-0000-0000-0000-000000000002'''

-- Setup as Supabase Auth would: three staff sign-ups.
insert into auth.users (id, email, raw_user_meta_data) values
    (:labstaff, 'lab@demo.test',       '{"full_name": "Demo Lab Tech"}'),
    (:radstaff, 'radiology@demo.test', '{"full_name": "Demo Radiographer"}'),
    (:newstaff, 'newlab@demo.test',    '{"full_name": "Demo New Lab Tech"}');

set role authenticated;

-- ---------------------------------------------------------------------------
-- Facilities (departments)
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :labstaff;
insert into public.facilities (id, name, kind, hospital) values (:lab, 'Main Lab', 'lab', 'Demo Hospital');
select tests.ok(true, 'anyone signed in can add a department');
select tests.fails($$insert into public.facilities (name, kind, hospital) values (' main lab ', 'lab', 'demo hospital')$$,
                   '23505', 'the same department cannot be added twice');
select tests.fails($$insert into public.facilities (name, kind, created_by)
                     values ('Fake', 'lab', 'a0000000-0000-0000-0000-000000000002')$$,
                   '42501', 'a department cannot be added in someone else''s name');
select tests.ok(tests.affected($$update public.facilities set name = 'Renamed'$$) = 0,
                'only admins can rename a department');

set request.jwt.claim.sub = :owner;
insert into public.facilities (id, name, kind, hospital) values (:radiology, 'X-ray', 'radiology', 'Demo Hospital');
select tests.ok((select count(*) from public.facilities) = 2, 'doctors can see all departments');

-- ---------------------------------------------------------------------------
-- Staff accounts
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :labstaff;
update public.doctors set account_type = 'staff', facility_id = :lab, specialty = 'Lab technician'
 where id = auth.uid();
set request.jwt.claim.sub = :radstaff;
update public.doctors set account_type = 'staff', facility_id = :radiology where id = auth.uid();
set request.jwt.claim.sub = :newstaff;
update public.doctors set account_type = 'staff', facility_id = :lab where id = auth.uid();

set request.jwt.claim.sub = :consultant;  -- owns Demo Patient Alpha after the transfer test
select tests.fails($$update public.doctors set account_type = 'staff' where id = auth.uid()$$, '42501',
                   'a doctor who owns patients cannot switch to a staff account');

set request.jwt.claim.sub = :admin;
select public.admin_set_verification(:labstaff, 'verified');
select public.admin_set_verification(:radstaff, 'verified');

set request.jwt.claim.sub = :labstaff;
select tests.fails($$insert into public.patients (full_name) values ('Demo Staff Patient')$$, '42501',
                   'staff cannot create patients');
select tests.ok((select count(*) from public.search_doctors('')) = 0, 'staff cannot browse the doctor directory');
select tests.fails($$select public.create_consult('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000003',
                     'q', array['allergies']::public.record_section[], true, 7, true, current_date)$$,
                   '42501', 'staff cannot start consults');
update public.doctors set account_type = 'doctor' where id = auth.uid();
select tests.ok((select verification_status = 'pending' from public.doctors where id = auth.uid()),
                'changing account type requires re-verification');
update public.doctors set account_type = 'staff' where id = auth.uid();
set request.jwt.claim.sub = :admin;
select public.admin_set_verification(:labstaff, 'verified');

set request.jwt.claim.sub = :owner;
select tests.ok((select count(*) from public.search_doctors('demo lab')) = 0,
                'staff never appear in the doctor directory');
select tests.fails($$select public.create_referral('b0000000-0000-0000-0000-000000000002',
                     'a0000000-0000-0000-0000-000000000006', 'comanagement', null, true, current_date)$$,
                   '42501', 'patients cannot be referred to staff');

-- ---------------------------------------------------------------------------
-- Doctor side: requests and results
-- ---------------------------------------------------------------------------
insert into public.patients (id, full_name, sex, age_years, file_number, national_id, phone)
values (:patient2, 'Demo Patient Beta', 'male', 54, 'F-200', '00000000000002', '+201000000002');
insert into public.allergies (patient_id, allergen, severity) values (:patient2, 'Iodine contrast (demo)', 'severe');
insert into public.medications (patient_id, name) values (:patient2, 'Demo secret drug');

-- An earlier lab result entered by the doctor, and an imaging result.
insert into public.investigation_results (patient_id, kind, title, result_date, lab_values)
values (:patient2, 'lab', 'CBC (old)', current_date - 30, '[{"test":"Hb","value":10.1,"unit":"g/dL","low":13,"high":17}]');
insert into public.investigation_results (patient_id, kind, title, report_text)
values (:patient2, 'imaging', 'Chest X-ray (old)', 'Demo: clear lungs');

insert into public.investigation_requests (patient_id, kind, tests, urgency, clinical_notes, facility_id)
values (:patient2, 'lab', array['CBC', 'Creatinine'], 'urgent', 'Demo: pre-op', :lab)
returning id as labreq \gset
set test.labreq = :'labreq';
insert into public.investigation_requests (patient_id, kind, tests)
values (:patient2, 'imaging', array['Ultrasound abdomen'])
returning id as ownreq \gset
set test.ownreq = :'ownreq';

select tests.fails($$insert into public.investigation_results (patient_id, kind, title, source)
                     values ('b0000000-0000-0000-0000-000000000002', 'lab', 'Fake lab result', 'staff')$$,
                   '42501', 'a doctor cannot create a result that looks lab-uploaded');
select tests.fails($$insert into public.investigation_results (patient_id, request_id, kind, title)
                     values ('b0000000-0000-0000-0000-000000000001', current_setting('test.labreq')::uuid, 'lab', 'x')$$,
                   '42501', 'a result cannot be filed under a patient you cannot edit');
select tests.fails($$insert into public.investigation_results (patient_id, kind, title, lab_values)
                     values ('b0000000-0000-0000-0000-000000000002', 'lab', 'x', '{"Hb": 1}')$$,
                   '23514', 'typed values must be a list');
select tests.fails($$insert into public.attachments (patient_id, section, storage_path, mime_type)
                     values ('b0000000-0000-0000-0000-000000000002', 'investigations',
                             'investigations/b0000000-0000-0000-0000-000000000001/x.pdf', 'application/pdf')$$,
                   '23514', 'an attachment must be stored under its own patient');
select tests.fails($$insert into public.attachments (patient_id, section, storage_path, mime_type)
                     values ('b0000000-0000-0000-0000-000000000002', 'allergies',
                             'allergies/b0000000-0000-0000-0000-000000000002/x.pdf', 'application/pdf')$$,
                   '23514', 'files are only for investigations and surgical care');
select tests.fails($$insert into public.attachments (patient_id, section, storage_path, mime_type)
                     values ('b0000000-0000-0000-0000-000000000002', 'investigations',
                             'investigations/b0000000-0000-0000-0000-000000000002/x.exe', 'application/x-msdownload')$$,
                   '23514', 'only photos and PDFs can be attached');

-- A wound photo on a post-op follow-up.
insert into public.surgical_cases (id, patient_id, diagnosis)
values ('c0000000-0000-0000-0000-000000000002', :patient2, 'Demo inguinal hernia');
insert into public.postop_followups (id, patient_id, surgical_case_id, notes)
values ('d0000000-0000-0000-0000-000000000002', :patient2, 'c0000000-0000-0000-0000-000000000002', 'Demo: wound clean');
insert into storage.objects (bucket_id, name)
values ('clinical-files', 'surgical_care/b0000000-0000-0000-0000-000000000002/wound1.jpg');
insert into public.attachments (patient_id, section, followup_id, storage_path, mime_type, caption)
values (:patient2, 'surgical_care', 'd0000000-0000-0000-0000-000000000002',
        'surgical_care/b0000000-0000-0000-0000-000000000002/wound1.jpg', 'image/jpeg', 'Day 7');
select tests.ok((select count(*) from public.attachments) = 1, 'the doctor can attach a wound photo to a follow-up');
select tests.ok((select count(*) from storage.objects where bucket_id = 'clinical-files') = 1,
                'the doctor can open their patient''s files');
select tests.fails($$insert into storage.objects (bucket_id, name)
                     values ('clinical-files', 'investigations/b0000000-0000-0000-0000-000000000001/x.pdf')$$,
                   '42501', 'a doctor cannot upload files to a patient they cannot edit');
select tests.fails($$insert into storage.objects (bucket_id, name) values ('clinical-files', 'investigations/not-a-uuid/x.pdf')$$,
                   '42501', 'a malformed file path is refused');
select tests.ok(tests.affected($$update storage.objects set name = name where bucket_id = 'clinical-files'$$) = 0
                and tests.affected($$delete from storage.objects where bucket_id = 'clinical-files'$$) = 0,
                'stored files cannot be changed or deleted');

select tests.ok((select count(*) from jsonb_array_elements(
                    public.sync_pull('investigation_requests', null))) = 2
                and (select count(*) from jsonb_array_elements(public.sync_pull('investigation_results', null))) = 2
                and (select count(*) from jsonb_array_elements(public.sync_pull('attachments', null))) = 1,
                'investigations and files are synced for offline use');

set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.investigation_requests) = 0
                and (select count(*) from public.investigation_results) = 0
                and (select count(*) from public.attachments) = 0
                and (select count(*) from storage.objects where bucket_id = 'clinical-files') = 0,
                'another doctor sees no investigations or files');
select tests.fails($$insert into public.investigation_requests (patient_id, kind, tests)
                     values ('b0000000-0000-0000-0000-000000000002', 'lab', array['CBC'])$$,
                   '42501', 'another doctor cannot request investigations');

-- ---------------------------------------------------------------------------
-- Staff side: the department inbox
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :newstaff;
select tests.ok(jsonb_array_length(public.staff_worklist()) = 0, 'unapproved staff see an empty inbox');
select tests.fails($$select public.staff_request_detail(current_setting('test.labreq')::uuid)$$, '42501',
                   'unapproved staff cannot open a request');

set request.jwt.claim.sub = :radstaff;
select tests.ok(jsonb_array_length(public.staff_worklist()) = 0, 'staff see only their own department''s requests');
select tests.fails($$select public.staff_request_detail(current_setting('test.labreq')::uuid)$$, '42501',
                   'staff cannot open another department''s request');

set request.jwt.claim.sub = :labstaff;
select tests.ok((select count(*) from public.patients) = 0
                and (select count(*) from public.allergies) = 0
                and (select count(*) from public.medications) = 0
                and (select count(*) from public.investigation_requests) = 0
                and (select count(*) from public.investigation_results) = 0,
                'staff cannot read any record table directly');
select tests.fails($$select public.staff_request_open(current_setting('test.labreq')::uuid)$$, '42501',
                   'the internal request lookup is not callable');
select tests.ok(jsonb_array_length(public.staff_worklist()) = 1, 'the lab inbox shows the request sent to it');
select tests.ok((select public.staff_worklist() -> 0 ->> 'patient_name') = 'Demo Patient Beta'
                and (select public.staff_worklist() -> 0 ->> 'patient_id') = 'b0000000-0000-0000-0000-000000000002',
                'the inbox shows who the request is for');
select public.staff_request_detail(current_setting('test.labreq')::uuid) as detail \gset
set test.detail = :'detail';
select tests.ok((current_setting('test.detail')::jsonb -> 'allergies' -> 0 ->> 'allergen') = 'Iodine contrast (demo)',
                'staff see the patient''s allergies');
select tests.ok(jsonb_array_length(current_setting('test.detail')::jsonb -> 'previous_results') = 1
                and (current_setting('test.detail')::jsonb -> 'previous_results' -> 0 ->> 'title') = 'CBC (old)',
                'staff see earlier results of the same kind only');
select tests.ok(not (current_setting('test.detail')::jsonb -> 'patient' ?| array['national_id', 'phone', 'address'])
                and current_setting('test.detail') not ilike '%secret drug%',
                'staff do not see identifiers or other sections');
reset role;
select tests.ok((select count(*) from public.audit_log
                  where actor_id = :labstaff and action = 'view' and table_name = 'investigation_requests') = 1,
                'staff opening a request is audit-logged');
set role authenticated;
set request.jwt.claim.sub = :labstaff;

select public.staff_mark_sample_taken(current_setting('test.labreq')::uuid);
select tests.fails($$select public.staff_mark_sample_taken(current_setting('test.ownreq')::uuid)$$, '42501',
                   'staff cannot touch requests not sent to their department');
select tests.fails($$select public.staff_submit_result(current_setting('test.labreq')::uuid, 'CBC', null, '  ', '[]', '[]')$$,
                   '22023', 'an empty result is refused');
select tests.fails($$select public.staff_submit_result(current_setting('test.labreq')::uuid, 'CBC', current_date + 1, 'x', '[]', '[]')$$,
                   '22023', 'a result cannot be dated in the future');

insert into storage.objects (bucket_id, name)
values ('clinical-files', 'investigations/b0000000-0000-0000-0000-000000000002/cbc.pdf');
select tests.ok(true, 'staff can upload a file for an open request');
select tests.fails($$insert into storage.objects (bucket_id, name)
                     values ('clinical-files', 'surgical_care/b0000000-0000-0000-0000-000000000002/x.jpg')$$,
                   '42501', 'staff cannot upload to other sections');
select tests.fails($$insert into storage.objects (bucket_id, name)
                     values ('clinical-files', 'investigations/b0000000-0000-0000-0000-000000000001/x.pdf')$$,
                   '42501', 'staff cannot upload for patients without an open request');
select tests.ok((select count(*) from storage.objects where bucket_id = 'clinical-files') = 1,
                'staff can see investigation files of the request''s patient, but not wound photos');
select tests.fails($$select public.staff_submit_result(current_setting('test.labreq')::uuid, 'CBC', null, null, '[]',
                     '[{"storage_path": "investigations/b0000000-0000-0000-0000-000000000001/x.pdf", "mime_type": "application/pdf"}]')$$,
                   '42501', 'staff cannot attach files of another patient');

select public.staff_submit_result(current_setting('test.labreq')::uuid, null, null, 'Demo: mild anaemia',
       '[{"test":"Hb","value":11.2,"unit":"g/dL","low":13,"high":17},{"test":"Creatinine","value":0.9,"unit":"mg/dL"}]',
       '[{"storage_path": "investigations/b0000000-0000-0000-0000-000000000002/cbc.pdf", "mime_type": "application/pdf", "file_name": "cbc.pdf"}]')
       as labres \gset
set test.labres = :'labres';
select tests.ok(jsonb_array_length(public.staff_request_detail(current_setting('test.labreq')::uuid)
                                   -> 'this_request_results') = 1,
                'staff see the result they submitted until it is reviewed');

-- ---------------------------------------------------------------------------
-- Back on the doctor side
-- ---------------------------------------------------------------------------
set request.jwt.claim.sub = :owner;
select tests.ok((select status = 'result_uploaded' and sample_taken_at is not null and resulted_at is not null
                   from public.investigation_requests where id = current_setting('test.labreq')::uuid),
                'submitting a result moves the request to "result uploaded"');
select tests.ok((select title = 'CBC, Creatinine' and source = 'staff' and created_by = :labstaff
                   from public.investigation_results where id = current_setting('test.labres')::uuid),
                'the doctor sees the lab result, marked as uploaded by the lab');
select tests.ok((select count(*) from public.attachments where result_id = current_setting('test.labres')::uuid) = 1,
                'the lab''s file is attached to the result');
select tests.fails($$update public.investigation_results set report_text = 'changed'
                     where id = current_setting('test.labres')::uuid$$, '42501',
                   'a doctor cannot edit a lab-uploaded result');
select tests.ok(tests.affected($$update public.investigation_results set report_text = 'Demo: corrected'
                                 where title = 'CBC (old)'$$) = 1,
                'a doctor can edit their own typed result');

update public.investigation_requests set status = 'reviewed' where id = current_setting('test.labreq')::uuid;
select tests.ok((select reviewed_by = :owner and reviewed_at is not null
                   from public.investigation_requests where id = current_setting('test.labreq')::uuid),
                'reviewing records who reviewed and when');

set request.jwt.claim.sub = :labstaff;
select tests.ok(jsonb_array_length(public.staff_worklist()) = 0, 'a reviewed request leaves the lab inbox');
select tests.fails($$select public.staff_request_detail(current_setting('test.labreq')::uuid)$$, '42501',
                   'staff lose access once the result is reviewed');
select tests.ok((select count(*) from storage.objects where bucket_id = 'clinical-files') = 0,
                'staff can no longer open the patient''s files');

set request.jwt.claim.sub = :owner;
update public.investigation_results set deleted_at = now() where id = current_setting('test.labres')::uuid;
select tests.ok(true, 'a doctor can mark a lab-uploaded result as entered in error');

-- Consultants given the investigations section can read results and their files.
select public.create_consult(:patient2, :stranger, 'Demo: opinion on labs?',
                             array['investigations']::public.record_section[], false, 7, true, current_date);
set request.jwt.claim.sub = :stranger;
select tests.ok((select count(*) from public.investigation_results) = 3
                and (select count(*) from public.attachments) = 1
                and (select count(*) from storage.objects where bucket_id = 'clinical-files') = 1,
                'a consultant shown investigations can read results and their files');
select tests.ok((select count(*) from public.medications where patient_id = :patient2) = 0,
                'but not other sections');
select tests.ok(cardinality(public.my_patient_ids()) = 0
                and jsonb_array_length(public.sync_pull('investigation_results', null)) = 0,
                'consultants cannot download investigations for offline use');

reset role;
\echo 'ALL INVESTIGATION TESTS PASSED'
