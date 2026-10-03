-- =============================================================================
-- Migration 7 (Phase 4): notifications, push devices, consult files, and the
-- lists behind the Consults and Referrals screens.
--
-- Plain-language summary
--   * The database writes a notification row when something needs the user's
--     attention: a consult request or a new consult message, a referral or the
--     answer to one, or (for lab/radiology staff) a new request in their
--     department's inbox. A row holds only WHAT happened and an id; never a
--     patient name or any clinical text. Phones get a push that says only
--     "New consult request" etc.; the details are loaded inside the app.
--   * Each phone registers its push address ("device token"). Nobody can read
--     the tokens through the API; only the push function (server side) can.
--   * Photos/PDFs in a consult conversation live in the private bucket
--     'consult-files' at <consult id>/<file>, visible only to the two doctors
--     of that consult. New files only while the consult is open.
--   * my_consults() and my_referrals() return each user's lists with just
--     enough patient detail: an anonymized consult never reveals the name.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- Notifications
-- -----------------------------------------------------------------------------
create table public.notifications (
    id           uuid primary key default gen_random_uuid(),
    recipient_id uuid not null references public.doctors (id) on delete cascade,
    kind         text not null check (kind in
                     ('consult_request', 'consult_message', 'referral_request', 'referral_response', 'lab_request')),
    -- The consult, referral or investigation request it is about. Not patient data.
    ref_id       uuid,
    created_at   timestamptz not null default now(),
    read_at      timestamptz
);
create index notifications_recipient_idx on public.notifications (recipient_id, created_at desc);

alter table public.notifications enable row level security;
revoke all on public.notifications from anon, authenticated;
grant select on public.notifications to authenticated;
grant update (read_at) on public.notifications to authenticated;
create policy notifications_select_own on public.notifications
    for select to authenticated using (recipient_id = auth.uid());
create policy notifications_update_own on public.notifications
    for update to authenticated using (recipient_id = auth.uid()) with check (recipient_id = auth.uid());

-- Internal: only the triggers below call it.
create function public.notify(p_recipient uuid, p_kind text, p_ref uuid) returns void
language sql security definer
set search_path = ''
as $$
    insert into public.notifications (recipient_id, kind, ref_id)
    select p_recipient, p_kind, p_ref
     where p_recipient is not null and p_recipient is distinct from auth.uid();
$$;

create function public.mark_all_notifications_read() returns void
language sql security definer
set search_path = ''
as $$
    update public.notifications set read_at = now() where recipient_id = auth.uid() and read_at is null;
$$;

-- A consult request goes to the consultant.
create function public.consults_notify() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    perform public.notify(new.consultant_id, 'consult_request', new.id);
    return new;
end;
$$;
create trigger consults_notify after insert on public.consults
    for each row execute function public.consults_notify();

-- A message goes to the other doctor of the consult.
create function public.consult_messages_notify() returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
    c public.consults;
begin
    select * into c from public.consults where id = new.consult_id;
    perform public.notify(
        case when new.sender_id = c.requester_id then c.consultant_id else c.requester_id end,
        'consult_message', c.id);
    return new;
end;
$$;
create trigger consult_messages_notify after insert on public.consult_messages
    for each row execute function public.consult_messages_notify();

-- A referral goes to the colleague; their answer goes back to the sender.
create function public.referrals_notify() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    if tg_op = 'INSERT' then
        perform public.notify(new.to_doctor_id, 'referral_request', new.id);
    elsif old.status = 'pending' and new.status in ('accepted', 'declined') then
        perform public.notify(new.from_doctor_id, 'referral_response', new.id);
    end if;
    return new;
end;
$$;
create trigger referrals_notify after insert or update of status on public.referrals
    for each row execute function public.referrals_notify();

-- A request sent to a department goes to every approved staff member there.
create function public.investigation_requests_notify() returns trigger
language plpgsql security definer
set search_path = ''
as $$
begin
    if new.facility_id is not null and new.deleted_at is null and new.status = 'requested'
       and (tg_op = 'INSERT' or new.facility_id is distinct from old.facility_id) then
        insert into public.notifications (recipient_id, kind, ref_id)
        select d.id, 'lab_request', new.id
          from public.doctors d
         where d.account_type = 'staff' and d.facility_id = new.facility_id
           and d.verification_status = 'verified';
    end if;
    return new;
end;
$$;
create trigger investigation_requests_notify after insert or update of facility_id on public.investigation_requests
    for each row execute function public.investigation_requests_notify();

-- -----------------------------------------------------------------------------
-- Push devices
-- -----------------------------------------------------------------------------
create table public.device_tokens (
    token      text primary key check (char_length(token) between 10 and 4096),
    user_id    uuid not null references public.doctors (id) on delete cascade,
    platform   text not null default 'android' check (platform in ('android')),
    updated_at timestamptz not null default now()
);
create index device_tokens_user_idx on public.device_tokens (user_id);
-- No access through the API at all: only the functions below and the push function (service role).
alter table public.device_tokens enable row level security;
revoke all on public.device_tokens from anon, authenticated;

-- Registers this phone for the signed-in user. A phone used by another person
-- before moves to the new user, so the previous person gets no more pushes on it.
create function public.register_device(p_token text) returns void
language plpgsql security definer
set search_path = ''
as $$
begin
    if auth.uid() is null then
        raise exception 'Not signed in' using errcode = '42501';
    end if;
    insert into public.device_tokens (token, user_id) values (p_token, auth.uid())
    on conflict (token) do update set user_id = excluded.user_id, updated_at = now();
end;
$$;

-- On sign-out.
create function public.unregister_device(p_token text) returns void
language sql security definer
set search_path = ''
as $$
    delete from public.device_tokens where token = p_token and user_id = auth.uid();
$$;

-- -----------------------------------------------------------------------------
-- Consult files: <consult id>/<file>
-- -----------------------------------------------------------------------------
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('consult-files', 'consult-files', false, 15 * 1024 * 1024,
        array['image/jpeg', 'image/png', 'application/pdf'])
on conflict (id) do nothing;

create policy consult_files_read on storage.objects for select to authenticated
    using (bucket_id = 'consult-files'
           and public.is_consult_participant(public.try_uuid((storage.foldername(name))[1])));

create policy consult_files_insert on storage.objects for insert to authenticated
    with check (bucket_id = 'consult-files'
                and public.is_consult_participant(public.try_uuid((storage.foldername(name))[1]))
                and public.is_consult_active(public.try_uuid((storage.foldername(name))[1])));
-- No update or delete: the conversation is part of the medical record.

-- A message may only point to files of its own consult.
create function public.consult_messages_check_files() returns trigger
language plpgsql
set search_path = ''
as $$
begin
    if exists (select 1 from unnest(new.attachment_paths) p
                where left(p, 37) <> new.consult_id::text || '/' or p like '%..%') then
        raise exception 'Attached files must belong to this consult' using errcode = '42501';
    end if;
    return new;
end;
$$;
create trigger consult_messages_check_files before insert on public.consult_messages
    for each row execute function public.consult_messages_check_files();

-- -----------------------------------------------------------------------------
-- Lists for the app
-- -----------------------------------------------------------------------------

-- My consults, as requester or consultant, newest activity first.
-- The patient's name is only included when the consultant may see identifiers.
create function public.my_consults() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(x order by (x ->> 'last_activity_at') desc), '[]'::jsonb)
      from (
        select jsonb_build_object(
                   'id', c.id,
                   'patient_id', c.patient_id,
                   'role', case when c.requester_id = auth.uid() then 'requester' else 'consultant' end,
                   'question', c.question,
                   'urgency', c.urgency,
                   'status', c.status,
                   'anonymized', c.anonymized,
                   'sections', (select coalesce(jsonb_agg(s.section order by s.section), '[]'::jsonb)
                                  from public.consult_sections s where s.consult_id = c.id),
                   'created_at', c.created_at,
                   'expires_at', c.expires_at,
                   'revoked_at', c.revoked_at,
                   'closed_at', c.closed_at,
                   'active', public.is_consult_active(c.id),
                   'other_doctor', jsonb_build_object('id', o.id, 'full_name', o.full_name,
                                                      'specialty', o.specialty, 'hospital', o.hospital),
                   'patient_name', case
                       when c.requester_id = auth.uid() then p.full_name
                       when not c.anonymized and public.is_consult_active(c.id)
                            and exists (select 1 from public.consult_sections s
                                         where s.consult_id = c.id and s.section = 'identifiers') then p.full_name
                       end,
                   'patient_sex', p.sex,
                   'patient_age', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years),
                   'last_activity_at', greatest(c.created_at,
                       coalesce((select max(m.created_at) from public.consult_messages m where m.consult_id = c.id), c.created_at)),
                   'unread', (select count(*) from public.notifications n
                               where n.recipient_id = auth.uid() and n.ref_id = c.id and n.read_at is null
                                 and n.kind in ('consult_request', 'consult_message'))
               ) as x
          from public.consults c
          join public.patients p on p.id = c.patient_id
          join public.doctors o on o.id = case when c.requester_id = auth.uid() then c.consultant_id else c.requester_id end
         where public.is_consult_participant(c.id)
      ) t;
$$;

-- My referrals, sent and received. The receiving doctor sees who the patient
-- is (the patient consented to the referral) to decide whether to accept.
create function public.my_referrals() returns jsonb
language sql stable security definer
set search_path = ''
as $$
    select coalesce(jsonb_agg(x order by (x ->> 'created_at') desc), '[]'::jsonb)
      from (
        select jsonb_build_object(
                   'id', r.id,
                   'patient_id', r.patient_id,
                   'direction', case when r.from_doctor_id = auth.uid() then 'sent' else 'received' end,
                   'kind', r.kind,
                   'status', r.status,
                   'note', r.note,
                   'created_at', r.created_at,
                   'responded_at', r.responded_at,
                   'ended_at', r.ended_at,
                   'other_doctor', jsonb_build_object('id', o.id, 'full_name', o.full_name,
                                                      'specialty', o.specialty, 'hospital', o.hospital),
                   'patient_name', p.full_name,
                   'patient_sex', p.sex,
                   'patient_age', coalesce(extract(year from age(current_date, p.date_of_birth))::int, p.age_years),
                   'primary_diagnosis', p.primary_diagnosis
               ) as x
          from public.referrals r
          join public.patients p on p.id = r.patient_id
          join public.doctors o on o.id = case when r.from_doctor_id = auth.uid() then r.to_doctor_id else r.from_doctor_id end
         where r.from_doctor_id = auth.uid()
            or (r.to_doctor_id = auth.uid() and public.is_verified_doctor())
      ) t;
$$;

-- -----------------------------------------------------------------------------
-- Permissions
-- -----------------------------------------------------------------------------
revoke execute on function public.notify(uuid, text, uuid) from public, anon, authenticated;
revoke execute on function public.consults_notify() from public, anon, authenticated;
revoke execute on function public.consult_messages_notify() from public, anon, authenticated;
revoke execute on function public.referrals_notify() from public, anon, authenticated;
revoke execute on function public.investigation_requests_notify() from public, anon, authenticated;
revoke execute on function public.consult_messages_check_files() from public, anon, authenticated;
revoke execute on function public.mark_all_notifications_read() from public, anon;
revoke execute on function public.register_device(text) from public, anon;
revoke execute on function public.unregister_device(text) from public, anon;
revoke execute on function public.my_consults() from public, anon;
revoke execute on function public.my_referrals() from public, anon;
grant execute on function public.mark_all_notifications_read() to authenticated;
grant execute on function public.register_device(text) to authenticated;
grant execute on function public.unregister_device(text) to authenticated;
grant execute on function public.my_consults() to authenticated;
grant execute on function public.my_referrals() to authenticated;
