-- Add account type
ALTER TABLE accounts
ADD COLUMN account_type VARCHAR(20) NOT NULL DEFAULT 'CUSTOMER';

-- Validate account types
ALTER TABLE accounts
ADD CONSTRAINT chk_account_type
CHECK (account_type IN ('CUSTOMER', 'SYSTEM'));

-- Allow negative balances only for system accounts
ALTER TABLE accounts
DROP CONSTRAINT chk_account_balance;

ALTER TABLE accounts
ADD CONSTRAINT chk_account_balance
CHECK (
    account_type = 'SYSTEM'
    OR balance >= 0
);