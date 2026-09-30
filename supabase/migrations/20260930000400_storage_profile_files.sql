-- =============================================================================
-- Migration 4 (Phase 1): private storage buckets for profile photos and
-- licence documents.
--
-- Files are stored under a folder named after the owner's user id:
--     avatars/<user-id>/avatar.jpg
--     licenses/<user-id>/license.jpg
-- Buckets are PRIVATE: files are only reachable through short-lived signed URLs
-- created by users who pass these policies.
-- =============================================================================

insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values
    ('avatars',  'avatars',  false, 2 * 1024 * 1024, array['image/jpeg', 'image/png', 'image/webp']),
    ('licenses', 'licenses', false, 5 * 1024 * 1024, array['image/jpeg', 'image/png', 'application/pdf'])
on conflict (id) do nothing;

-- Avatars: you manage your own; verified doctors can view others (directory).
create policy avatars_owner_all on storage.objects
    for all to authenticated
    using (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'avatars' and (storage.foldername(name))[1] = auth.uid()::text);

create policy avatars_verified_read on storage.objects
    for select to authenticated
    using (bucket_id = 'avatars' and public.is_verified_doctor());

-- Licence documents: only the owner and administrators.
create policy licenses_owner_all on storage.objects
    for all to authenticated
    using (bucket_id = 'licenses' and (storage.foldername(name))[1] = auth.uid()::text)
    with check (bucket_id = 'licenses' and (storage.foldername(name))[1] = auth.uid()::text);

create policy licenses_admin_read on storage.objects
    for select to authenticated
    using (bucket_id = 'licenses' and public.is_admin());
