
-- Create withdrawals table
CREATE TABLE withdrawals (
    id UUID PRIMARY KEY,
    client_id VARCHAR(100) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    account_id UUID NOT NULL REFERENCES accounts(id),
    amount NUMERIC(19, 4) NOT NULL,
    currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_withdrawal_amount
        CHECK (amount > 0),

    CONSTRAINT chk_withdrawal_status
        CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),

    CONSTRAINT uq_withdrawal_idempotency
        UNIQUE (client_id, idempotency_key)
);

-- Allow ledger entries to belong to a withdrawal
ALTER TABLE ledger_entries
ADD COLUMN withdrawal_id UUID REFERENCES withdrawals(id);

-- Every ledger entry must reference exactly one transaction
ALTER TABLE ledger_entries
DROP CONSTRAINT chk_ledger_transaction_reference;

ALTER TABLE ledger_entries
ADD CONSTRAINT chk_ledger_transaction_reference
CHECK (
    (transfer_id IS NOT NULL AND deposit_id IS NULL AND withdrawal_id IS NULL)
    OR
    (transfer_id IS NULL AND deposit_id IS NOT NULL AND withdrawal_id IS NULL)
    OR
    (transfer_id IS NULL AND deposit_id IS NULL AND withdrawal_id IS NOT NULL)
);
