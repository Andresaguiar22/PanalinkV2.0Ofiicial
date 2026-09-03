create table if not exists public.live_comments (
    id uuid primary key default gen_random_uuid(),
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    user_id uuid not null references auth.users(id) on delete cascade,
    text text not null,
    created_at timestamptz default now()
);

alter table public.live_comments enable row level security;

create policy "Authenticated users can select live comments"
    on public.live_comments
    for select
    to authenticated
    using (true);

create policy "Authenticated users can insert own live comments"
    on public.live_comments
    for insert
    to authenticated
    with check (user_id = auth.uid());

-- Enable Realtime for live_comments
alter publication supabase_realtime add table public.live_comments;
