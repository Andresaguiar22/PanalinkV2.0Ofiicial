-- Live Moderation & Blocked Users Table Migration
create table if not exists public.live_blocked_users (
    id uuid default gen_random_uuid() primary key,
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    user_id text not null,
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(stream_id, user_id)
);

alter table public.live_blocked_users enable row level security;

create policy "Broadcasters can insert blocked users"
    on public.live_blocked_users for insert
    with check (
        auth.uid() in (
            select user_id from public.live_streams where id = stream_id
        )
    );

create policy "Broadcasters can delete blocked users"
    on public.live_blocked_users for delete
    using (
        auth.uid() in (
            select user_id from public.live_streams where id = stream_id
        )
    );

create policy "Anyone can view blocked users in stream"
    on public.live_blocked_users for select
    using (true);

-- Add is_deleted to live_comments if not present
alter table if exists public.live_comments add column if not exists is_deleted boolean default false;
