-- Live Guests Table Migration
create table if not exists public.live_guests (
    id uuid default gen_random_uuid() primary key,
    stream_id uuid not null references public.live_streams(id) on delete cascade,
    guest_user_id text not null,
    status text not null check (status in ('INVITED', 'ACCEPTED', 'REJECTED', 'CONNECTED', 'DISCONNECTED', 'REMOVED')),
    created_at timestamp with time zone default timezone('utc'::text, now()) not null,
    joined_at timestamp with time zone,
    updated_at timestamp with time zone default timezone('utc'::text, now()) not null,
    unique(stream_id, guest_user_id)
);

create index if not exists idx_live_guests_stream_id on public.live_guests(stream_id);
create index if not exists idx_live_guests_guest_user_id on public.live_guests(guest_user_id);

alter table public.live_guests enable row level security;

create policy "Broadcaster can manage guests"
    on public.live_guests for all
    using (
        auth.uid()::text in (
            select user_id::text from public.live_streams where id = stream_id
        )
    )
    with check (
        auth.uid()::text in (
            select user_id::text from public.live_streams where id = stream_id
        )
    );

create policy "Guest can view own invitation"
    on public.live_guests for select
    using (guest_user_id = auth.uid()::text);

create policy "Guest can update own invitation status"
    on public.live_guests for update
    using (guest_user_id = auth.uid()::text)
    with check (guest_user_id = auth.uid()::text);
