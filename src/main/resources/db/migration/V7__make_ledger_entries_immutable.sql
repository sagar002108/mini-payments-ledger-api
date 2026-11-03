CREATE OR REPLACE FUNCTION prevent_ledger_entry_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger entries are immutable and cannot be modified or deleted'
        USING ERRCODE = '55000';

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_prevent_ledger_update_delete
BEFORE UPDATE OR DELETE ON ledger_entries
FOR EACH ROW
EXECUTE FUNCTION prevent_ledger_entry_mutation();


CREATE OR REPLACE FUNCTION prevent_ledger_truncate()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION
        'Ledger entries cannot be truncated'
        USING ERRCODE = '55000';

    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_prevent_ledger_truncate
BEFORE TRUNCATE ON ledger_entries
FOR EACH STATEMENT
EXECUTE FUNCTION prevent_ledger_truncate();