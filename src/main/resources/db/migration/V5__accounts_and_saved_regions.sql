create table user_account (
    id uuid primary key,
    apple_subject varchar(255) not null unique,
    refresh_token_ciphertext text not null,
    apple_validated_at timestamptz not null,
    default_region_id varchar(128) references weather_region(id)
);
create table user_session (
    token_hash varchar(64) primary key,
    user_id uuid not null references user_account(id) on delete cascade,
    expires_at timestamptz not null
);
create index user_session_user on user_session(user_id);
create index user_session_expiry on user_session(expires_at);
create table login_challenge (
    id uuid primary key,
    nonce_hash varchar(64) not null,
    expires_at timestamptz not null
);
create index login_challenge_expiry on login_challenge(expires_at);
create table saved_region (
    id uuid primary key,
    user_id uuid not null references user_account(id) on delete cascade,
    region_id varchar(128) not null references weather_region(id),
    position integer not null check (position between 0 and 9),
    unique(user_id, region_id)
);
create index saved_region_user_order on saved_region(user_id, position);
