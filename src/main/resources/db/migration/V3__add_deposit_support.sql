-- Create deposits table
CREATE TABLE deposits (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_deposit_amount
        CHECK (amount > 0),

    CONSTRAINT chk_deposit_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT uq_deposit_idempotency
        UNIQUE (client_id, idempotency_key)
);

-- Allow ledger entries to belong to either a transfer or deposit
ALTER TABLE ledger_entries
ADD COLUMN deposit_id UUID REFERENCES deposits(id);

ALTER TABLE ledger_entries
ALTER COLUMN transfer_id DROP NOT NULL;

-- Every ledger entry must reference exactly one transaction
ALTER TABLE ledger_entries
ADD CONSTRAINT chk_ledger_transaction_reference
CHECK (
    (transfer_id IS NOT NULL AND deposit_id IS NULL)
    OR
    (transfer_id IS NULL AND deposit_id IS NOT NULL)
);

-- One system account per currency
CREATE UNIQUE INDEX uq_system_account_currency
ON accounts(currency)
WHERE account_type = 'SYSTEM';