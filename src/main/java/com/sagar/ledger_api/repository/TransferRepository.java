package com.sagar.ledger_api.repository;

import java.sql.PreparedStatement;
import org.springframework.jdbc.core.ConnectionCallback;
import com.sagar.ledger_api.model.Transfer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public class TransferRepository {

    private final JdbcTemplate jdbcTemplate;

    public TransferRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Transfer> transferRowMapper = (rs, rowNum) ->
            new Transfer(
                    rs.getObject("id", UUID.class),
                    rs.getString("client_id"),
                    rs.getString("idempotency_key"),
                    rs.getString("request_hash"),
                    rs.getObject("source_account_id", UUID.class),
                    rs.getObject("destination_account_id", UUID.class),
                    rs.getBigDecimal("amount"),
                    rs.getString("currency").trim(),
                    rs.getString("status"),
                    rs.getObject("created_at",
                            java.time.OffsetDateTime.class)
            );

    public Transfer findByIdempotencyKey(
            String clientId,
            String idempotencyKey) {

        String sql = """
                SELECT id, client_id, idempotency_key, request_hash,
                       source_account_id, destination_account_id,
                       amount, currency, status, created_at
                FROM transfers
                WHERE client_id = ? AND idempotency_key = ?
                """;

        return jdbcTemplate.query(
                sql,
                transferRowMapper,
                clientId,
                idempotencyKey
        ).stream().findFirst().orElse(null);
    }

    public Transfer create(Transfer transfer) {

    String sql = """
            INSERT INTO transfers
                (id, client_id, idempotency_key, request_hash,
                 source_account_id, destination_account_id,
                 amount, currency, status)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            RETURNING id, client_id, idempotency_key, request_hash,
                      source_account_id, destination_account_id,
                      amount, currency, status, created_at
            """;

    return jdbcTemplate.queryForObject(
            sql,
            transferRowMapper,
            transfer.id(),
            transfer.clientId(),
            transfer.idempotencyKey(),
            transfer.requestHash(),
            transfer.sourceAccountId(),
            transfer.destinationAccountId(),
            transfer.amount(),
            transfer.currency(),
            transfer.status()
    );
  }

     public void lockIdempotencyKey(
        String clientId,
        String idempotencyKey) {

    String lockValue = clientId.length() + ":" + clientId
            + ":" + idempotencyKey;

    jdbcTemplate.execute((ConnectionCallback<Void>) connection -> {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))")) {

            statement.setString(1, lockValue);
            statement.execute();
        }

        return null;
    });
}
}