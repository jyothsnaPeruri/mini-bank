create table users (
    id            uuid primary key,
    email         varchar(255) not null unique,
    password_hash varchar(100) not null,
    full_name     varchar(100) not null,
    role          varchar(20)  not null,
    created_at    timestamptz  not null
);

create table accounts (
    id             uuid primary key,
    account_number varchar(10)    not null unique,
    owner_id       uuid           not null references users (id),
    type           varchar(20)    not null,
    nickname       varchar(50)    not null,
    -- Last line of defence: the service checks funds first, the database refuses a negative balance regardless
    balance        numeric(19, 2) not null check (balance >= 0),
    currency       varchar(3)     not null,
    status         varchar(20)    not null,
    created_at     timestamptz    not null
);

create index idx_accounts_owner on accounts (owner_id);

create table transfers (
    id              uuid primary key,
    initiated_by    uuid           not null references users (id),
    from_account_id uuid           not null references accounts (id),
    to_account_id   uuid           not null references accounts (id),
    amount          numeric(19, 2) not null check (amount > 0),
    description     varchar(140),
    idempotency_key varchar(64)    not null,
    created_at      timestamptz    not null,
    -- A retried request with the same key can never create a second transfer
    constraint uq_transfers_idempotency unique (initiated_by, idempotency_key),
    constraint chk_transfers_distinct_accounts check (from_account_id <> to_account_id)
);

-- Every balance change is recorded as a ledger entry; amount is negative for money going out
create table ledger_entries (
    id            uuid primary key,
    account_id    uuid           not null references accounts (id),
    transfer_id   uuid references transfers (id),
    type          varchar(20)    not null,
    amount        numeric(19, 2) not null,
    balance_after numeric(19, 2) not null,
    description   varchar(140),
    counterparty  varchar(100),
    created_at    timestamptz    not null
);

create index idx_ledger_account_created on ledger_entries (account_id, created_at desc);
