-- 1. Accounts
CREATE TABLE accounts (
    id UUID PRIMARY KEY,
    owner_id VARCHAR(100) NOT NULL,
    currency CHAR(3) NOT NULL,
    balance NUMERIC(19, 4) NOT NULL DEFAULT 0,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_account_balance
        CHECK (balance >= 0),

    CONSTRAINT chk_account_status
        CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED'))
);

-- 2. Transfers
CREATE TABLE transfers (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    source_account_id UUID NOT NULL REFERENCES accounts(id),
    destination_account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_transfer_amount
        CHECK (amount > 0),

    CONSTRAINT chk_different_accounts
        CHECK (source_account_id <> destination_account_id),

    CONSTRAINT chk_transfer_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT uq_transfer_idempotency
        UNIQUE (client_id, idempotency_key)
);

-- 3. Ledger entries
CREATE TABLE ledger_entries (
    id UUID PRIMARY KEY,
    transfer_id UUID NOT NULL REFERENCES transfers(id),
    account_id UUID NOT NULL REFERENCES accounts(id),
    entry_type VARCHAR(10) NOT NULL,
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_entry_type
        CHECK (entry_type IN ('DEBIT', 'CREDIT')),

    CONSTRAINT chk_entry_amount
        CHECK (amount > 0)
);

-- 4. Indexes
CREATE INDEX idx_transfers_source
    ON transfers(source_account_id);

CREATE INDEX idx_transfers_destination
    ON transfers(destination_account_id);

CREATE INDEX idx_ledger_account
    ON ledger_entries(account_id);

CREATE INDEX idx_ledger_transfer
    ON ledger_entries(transfer_id);