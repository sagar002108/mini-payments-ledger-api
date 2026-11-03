
CREATE OR REPLACE FUNCTION check_ledger_transaction_balance()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    transaction_id UUID;
    transaction_type TEXT;
    transaction_status VARCHAR(20);
    transaction_amount NUMERIC(19, 4);
    transaction_currency CHAR(3);

    entry_count BIGINT;
    debit_count BIGINT;
    credit_count BIGINT;
    debit_total NUMERIC(19, 4);
    credit_total NUMERIC(19, 4);
    currency_count BIGINT;
    currency_mismatch_count BIGINT;
BEGIN
    IF TG_TABLE_NAME = 'ledger_entries' THEN
        IF NEW.transfer_id IS NOT NULL THEN
            transaction_id := NEW.transfer_id;
            transaction_type := 'TRANSFER';

            SELECT status, amount, currency
            INTO transaction_status, transaction_amount, transaction_currency
            FROM transfers WHERE id = transaction_id;

        ELSIF NEW.deposit_id IS NOT NULL THEN
            transaction_id := NEW.deposit_id;
            transaction_type := 'DEPOSIT';

            SELECT status, amount, currency
            INTO transaction_status, transaction_amount, transaction_currency
            FROM deposits WHERE id = transaction_id;

        ELSE
            transaction_id := NEW.withdrawal_id;
            transaction_type := 'WITHDRAWAL';

            SELECT status, amount, currency
            INTO transaction_status, transaction_amount, transaction_currency
            FROM withdrawals WHERE id = transaction_id;
        END IF;
    ELSE
        transaction_id := NEW.id;
        transaction_status := NEW.status;
        transaction_amount := NEW.amount;
        transaction_currency := NEW.currency;

        IF TG_TABLE_NAME = 'transfers' THEN
            transaction_type := 'TRANSFER';
        ELSIF TG_TABLE_NAME = 'deposits' THEN
            transaction_type := 'DEPOSIT';
        ELSE
            transaction_type := 'WITHDRAWAL';
        END IF;
    END IF;

    IF transaction_status IS DISTINCT FROM 'COMPLETED' THEN
        RETURN NULL;
    END IF;

    SELECT
        COUNT(*),
        COUNT(*) FILTER (WHERE entry_type = 'DEBIT'),
        COUNT(*) FILTER (WHERE entry_type = 'CREDIT'),
        COALESCE(SUM(amount) FILTER (WHERE entry_type = 'DEBIT'), 0),
        COALESCE(SUM(amount) FILTER (WHERE entry_type = 'CREDIT'), 0),
        COUNT(DISTINCT currency),
        COUNT(*) FILTER (WHERE currency <> transaction_currency)
    INTO
        entry_count, debit_count, credit_count,
        debit_total, credit_total, currency_count,
        currency_mismatch_count
    FROM ledger_entries
    WHERE
        (transaction_type = 'TRANSFER' AND transfer_id = transaction_id)
        OR (transaction_type = 'DEPOSIT' AND deposit_id = transaction_id)
        OR (transaction_type = 'WITHDRAWAL' AND withdrawal_id = transaction_id);

    IF entry_count <> 2
       OR debit_count <> 1
       OR credit_count <> 1
       OR debit_total <> credit_total
       OR debit_total <> transaction_amount
       OR currency_count <> 1
       OR currency_mismatch_count <> 0 THEN
        RAISE EXCEPTION
            'Ledger integrity violation for % transaction %',
            transaction_type, transaction_id
            USING ERRCODE = '23514';
    END IF;

    RETURN NULL;
END;
$$;

CREATE CONSTRAINT TRIGGER trg_check_ledger_balance
AFTER INSERT ON ledger_entries
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION check_ledger_transaction_balance();

CREATE CONSTRAINT TRIGGER trg_check_transfer_balance
AFTER INSERT ON transfers
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION check_ledger_transaction_balance();

CREATE CONSTRAINT TRIGGER trg_check_deposit_balance
AFTER INSERT ON deposits
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION check_ledger_transaction_balance();

CREATE CONSTRAINT TRIGGER trg_check_withdrawal_balance
AFTER INSERT ON withdrawals
DEFERRABLE INITIALLY DEFERRED
FOR EACH ROW
EXECUTE FUNCTION check_ledger_transaction_balance();
