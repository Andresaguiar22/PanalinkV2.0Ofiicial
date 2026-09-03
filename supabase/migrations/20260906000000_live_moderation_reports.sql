-- Live Moderation & Reports Migration
-- Create moderation tables (mute)
create table if not exists public.live_muted_users (
    id uuid default gen_random_uuid() primary key,
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    user_id text not null,
    muted_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(stream_id, user_id)
);

create table if not exists public.live_reports (
    id uuid default gen_random_uuid() primary key,
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    reporter_user_id text not null,
    reported_user_id text,
    comment_id uuid,
    reason text not null,
    status text default 'PENDING',
    created_at timestamp with time zone default timezone('utc'::text, now()) not null
);

-- RLS
alter table public.live_muted_users enable row level security;
alter table public.live_reports enable row level security;

-- Moderator policy
create policy "Broadcaster can manage mutes" on public.live_muted_users for all
using (auth.uid()::text in (select user_id::text from public.live_streams where id = stream_id));

-- Report policies
create policy "User can create own reports" on public.live_reports for insert
with check (reporter_user_id = auth.uid()::text);

create policy "User can view own reports" on public.live_reports for select
using (reporter_user_id = auth.uid()::text);
