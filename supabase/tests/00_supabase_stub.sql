-- =============================================================================
-- Minimal stand-in for the parts of Supabase our migrations depend on, so the
-- security rules can be tested on a plain PostgreSQL server (locally or in CI)
-- without Docker or a Supabase account.
--
-- NOT used in production: real Supabase provides these itself.
-- =============================================================================

do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'anon') then
        create role anon nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'authenticated') then
        create role authenticated nologin;
    end if;
    if not exists (select 1 from pg_roles where rolname = 'service_role') then
        create role service_role nologin bypassrls;
    end if;
end;
$$;

create schema auth;
grant usage on schema auth to anon, authenticated;

create table auth.users (
    id                 uuid primary key default gen_random_uuid(),
    email              text,
    raw_user_meta_data jsonb default '{}'::jsonb,
    email_confirmed_at timestamptz
);

-- Same behaviour as Supabase: the user id comes from the request's JWT claims.
create function auth.uid() returns uuid
language sql stable
as $$
    select coalesce(
        nullif(current_setting('request.jwt.claim.sub', true), ''),
        (nullif(current_setting('request.jwt.claims', true), '')::jsonb ->> 'sub')
    )::uuid;
$$;

create schema storage;
grant usage on schema storage to anon, authenticated;

create table storage.buckets (
    id                 text primary key,
    name               text not null,
    public             boolean default false,
    file_size_limit    bigint,
    allowed_mime_types text[]
);

create table storage.objects (
    id        uuid primary key default gen_random_uuid(),
    bucket_id text references storage.buckets (id),
    name      text not null,
    owner     uuid
);
alter table storage.objects enable row level security;
grant select, insert, update, delete on storage.objects to authenticated;

create function storage.foldername(name text) returns text[]
language sql immutable
as $$
    select (string_to_array(name, '/'))[1:array_length(string_to_array(name, '/'), 1) - 1];
$$;

grant usage on schema public to anon, authenticated;
