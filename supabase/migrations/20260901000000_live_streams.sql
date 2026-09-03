create table if not exists public.live_streams (
    id uuid primary key default gen_random_uuid(),
    host_id uuid not null references auth.users(id) on delete cascade,
    room_name text not null unique,
    title text not null,
    description text default null,
    thumbnail_url text default null,
    status text not null check (status in ('CREATED', 'LIVE', 'ENDED')),
    viewer_count integer default 0,
    started_at timestamptz default null,
    ended_at timestamptz default null,
    created_at timestamptz default now(),
    updated_at timestamptz default now()
);

alter table public.live_streams enable row level security;

create policy "Authenticated users can select live streams"
    on public.live_streams
    for select
    to authenticated
    using (status = 'LIVE' or host_id = auth.uid());

create policy "Authenticated users can insert own live streams"
    on public.live_streams
    for insert
    to authenticated
    with check (host_id = auth.uid());

create policy "Users can update own live streams"
    on public.live_streams
    for update
    to authenticated
    using (host_id = auth.uid())
    with check (host_id = auth.uid());

-- Enable Realtime for live_streams
alter publication supabase_realtime add table public.live_streams;
