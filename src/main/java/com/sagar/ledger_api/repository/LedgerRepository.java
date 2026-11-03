
package com.sagar.ledger_api.repository;

import com.sagar.ledger_api.model.LedgerEntry;
import com.sagar.ledger_api.dto.TransactionHistoryResponse;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class LedgerRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void create(LedgerEntry entry) {

        String sql = """
                INSERT INTO ledger_entries
                    (id, transfer_id, account_id, entry_type,
                     amount, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                entry.id(),
                entry.transferId(),
                entry.accountId(),
                entry.entryType(),
                entry.amount(),
                entry.currency()
        );
    }

    public void createDepositEntry(LedgerEntry entry, UUID depositId) {

        String sql = """
                INSERT INTO ledger_entries
                    (id, deposit_id, account_id, entry_type, amount, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                entry.id(),
                depositId,
                entry.accountId(),
                entry.entryType(),
                entry.amount(),
                entry.currency()
        );
    }

    public void createWithdrawalEntry(
            LedgerEntry entry,
            UUID withdrawalId
    ) {

        String sql = """
                INSERT INTO ledger_entries
                    (id, withdrawal_id, account_id, entry_type, amount, currency)
                VALUES (?, ?, ?, ?, ?, ?)
                """;

        jdbcTemplate.update(
                sql,
                entry.id(),
                withdrawalId,
                entry.accountId(),
                entry.entryType(),
                entry.amount(),
                entry.currency()
        );
    }

    
public List<TransactionHistoryResponse> findTransactionHistory(
        UUID accountId,
        int limit,
        int offset
) {

    String sql = """
            SELECT
                transaction_id,
                transaction_type,
                entry_type,
                amount,
                currency,
                created_at
            FROM (
                SELECT
                    t.id AS transaction_id,
                    'TRANSFER' AS transaction_type,
                    le.entry_type,
                    le.amount,
                    le.currency,
                    le.created_at
                FROM ledger_entries le
                JOIN transfers t ON le.transfer_id = t.id
                WHERE le.account_id = ?

                UNION ALL

                SELECT
                    d.id AS transaction_id,
                    'DEPOSIT' AS transaction_type,
                    le.entry_type,
                    le.amount,
                    le.currency,
                    le.created_at
                FROM ledger_entries le
                JOIN deposits d ON le.deposit_id = d.id
                WHERE le.account_id = ?

                UNION ALL

                SELECT
                    w.id AS transaction_id,
                    'WITHDRAWAL' AS transaction_type,
                    le.entry_type,
                    le.amount,
                    le.currency,
                    le.created_at
                FROM ledger_entries le
                JOIN withdrawals w ON le.withdrawal_id = w.id
                WHERE le.account_id = ?
            ) transactions
            ORDER BY created_at DESC, transaction_id DESC
            LIMIT ? OFFSET ?
            """;

    return jdbcTemplate.query(
            sql,
            (rs, rowNum) -> new TransactionHistoryResponse(
                    rs.getObject("transaction_id", UUID.class),
                    rs.getString("transaction_type"),
                    rs.getString("entry_type"),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency").trim(),
                    rs.getObject("created_at", java.time.OffsetDateTime.class)
            ),
            accountId,
            accountId,
            accountId,
            limit,
            offset
    );
}

}
