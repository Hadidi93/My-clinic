-- =============================================================================
-- Phone notifications: make the database call the send-push function for
-- every new notification. (Does the same as a "Database Webhook", without
-- needing to find that page in the dashboard.)
--
-- BEFORE RUNNING: replace the address on the line marked  <<< 1 >>>  with
-- your own Project URL (Supabase -> Project Settings -> Data API / API ->
-- Project URL, looks like https://abcd1234.supabase.co).
--
-- Safe to run again (e.g. to fix a typo in the address).
-- At the end it shows a long code: copy it into the Edge Function secret
-- PUSH_WEBHOOK_SECRET (docs/SETUP.md section 9 D).
-- =============================================================================

-- Lets the database make web requests (Supabase's built-in pg_net).
create extension if not exists pg_net;

do $$
declare
    v_project_url text := 'https://YOUR-PROJECT.supabase.co';   -- <<< 1 >>> your Project URL
begin
    if v_project_url like '%YOUR-PROJECT%' then
        raise exception 'Please put your own Project URL on the line marked <<< 1 >>> first';
    end if;
    -- The function's address, kept in Supabase Vault (encrypted).
    delete from vault.secrets where name = 'push_url';
    perform vault.create_secret(rtrim(v_project_url, '/') || '/functions/v1/send-push', 'push_url');
    -- A random password that proves a call really comes from this database (made once).
    if not exists (select 1 from vault.secrets where name = 'push_secret') then
        perform vault.create_secret(replace(gen_random_uuid()::text || gen_random_uuid()::text, '-', ''), 'push_secret');
    end if;
end;
$$;

-- For each new notification: ask send-push to deliver it. Only the
-- notification id is sent; the function looks up the rest itself.
create or replace function public.notifications_push() returns trigger
language plpgsql security definer
set search_path = ''
as $$
declare
    v_url    text;
    v_secret text;
begin
    select decrypted_secret into v_url from vault.decrypted_secrets where name = 'push_url';
    select decrypted_secret into v_secret from vault.decrypted_secrets where name = 'push_secret';
    if v_url is not null and v_secret is not null then
        perform net.http_post(
            url := v_url,
            body := jsonb_build_object('record', jsonb_build_object('id', new.id)),
            headers := jsonb_build_object('Content-Type', 'application/json', 'x-push-secret', v_secret),
            timeout_milliseconds := 5000
        );
    end if;
    return new;
end;
$$;
revoke execute on function public.notifications_push() from public, anon, authenticated;

drop trigger if exists notifications_push on public.notifications;
create trigger notifications_push after insert on public.notifications
    for each row execute function public.notifications_push();

-- Copy this code into the Edge Function secret PUSH_WEBHOOK_SECRET:
select decrypted_secret as "copy_this_into_PUSH_WEBHOOK_SECRET"
  from vault.decrypted_secrets where name = 'push_secret';
