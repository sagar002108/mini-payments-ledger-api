
package com.sagar.ledger_api.repository;

import com.sagar.ledger_api.dto.WithdrawalResponse;
import com.sagar.ledger_api.model.WithdrawalRecord;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class WithdrawalRepository {

    private final JdbcTemplate jdbcTemplate;

    public WithdrawalRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public WithdrawalResponse create(
            UUID id,
            String clientId,
            String idempotencyKey,
            String requestHash,
            UUID accountId,
            BigDecimal amount,
            String currency) {

        String sql = """
                INSERT INTO withdrawals
                    (id, client_id, idempotency_key, request_hash,
                     account_id, amount, currency, status)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'COMPLETED')
                RETURNING id, account_id, amount, currency,
                          status, created_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> new WithdrawalResponse(
                        rs.getObject("id", UUID.class),
                        rs.getObject("account_id", UUID.class),
                        rs.getBigDecimal("amount"),
                        rs.getString("currency").trim(),
                        rs.getString("status"),
                        rs.getObject(
                                "created_at",
                                java.time.OffsetDateTime.class
                        )
                ),
                id,
                clientId,
                idempotencyKey,
                requestHash,
                accountId,
                amount,
                currency
        );
    }

    public WithdrawalRecord findByIdempotencyKey(
            String clientId,
            String idempotencyKey) {

        String sql = """
                SELECT id, account_id, amount, currency,
                       status, created_at, request_hash
                FROM withdrawals
                WHERE client_id = ? AND idempotency_key = ?
                """;

        return jdbcTemplate.query(
                sql,
                (rs, rowNum) -> new WithdrawalRecord(
                        new WithdrawalResponse(
                                rs.getObject("id", UUID.class),
                                rs.getObject("account_id", UUID.class),
                                rs.getBigDecimal("amount"),
                                rs.getString("currency").trim(),
                                rs.getString("status"),
                                rs.getObject(
                                        "created_at",
                                        java.time.OffsetDateTime.class
                                )
                        ),
                        rs.getString("request_hash")
                ),
                clientId,
                idempotencyKey
        ).stream().findFirst().orElse(null);
    }
}
