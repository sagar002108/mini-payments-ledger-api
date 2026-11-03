package com.sagar.ledger_api.repository;

import com.sagar.ledger_api.model.Account;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import java.math.BigDecimal;
import java.util.UUID;

@Repository
public class AccountRepository {

    private final JdbcTemplate jdbcTemplate;

    public AccountRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    private final RowMapper<Account> accountRowMapper = (rs, rowNum) ->
            new Account(
                    rs.getObject("id", UUID.class),
                    rs.getString("owner_id"),
                    rs.getString("currency").trim(),
                    rs.getBigDecimal("balance"),
                    rs.getString("status"),
                    rs.getString("account_type"),
                    rs.getObject("created_at",
                            java.time.OffsetDateTime.class)
            );

    public Account create(Account account) {

        String sql = """
                INSERT INTO accounts
                   (id, owner_id, currency, balance, status, account_type)
                VALUES (?, ?, ?, ?, ?, ?)
                RETURNING id, owner_id, currency, balance, status,
                     account_type, created_at
                """;

        return jdbcTemplate.queryForObject(
                sql,
                accountRowMapper,
                account.id(),
                account.ownerId(),
                account.currency(),
                account.balance(),
                account.status(),
                account.accountType()
        );
    }
    public Account findById(UUID id) {

    String sql = """
            SELECT id, owner_id, currency, balance, status, account_type, created_at
            FROM accounts
            WHERE id = ?
            """;

    return jdbcTemplate.query(
            sql,
            accountRowMapper,
            id
    ).stream().findFirst().orElse(null);
}
public Account findByIdForUpdate(UUID id) {

    String sql = """
            SELECT id, owner_id, currency, balance, status, account_type, created_at
            FROM accounts
            WHERE id = ?
            FOR UPDATE
            """;

    return jdbcTemplate.query(
            sql,
            accountRowMapper,
            id
    ).stream().findFirst().orElse(null);
}
public void updateBalance(UUID id, BigDecimal newBalance) {

    String sql = """
            UPDATE accounts
            SET balance = ?
            WHERE id = ?
            """;

    jdbcTemplate.update(sql, newBalance, id);
}
public Account findOrCreateSystemAccount(String currency) {

    String insertSql = """
            INSERT INTO accounts
                (id, owner_id, currency, balance, status, account_type)
            VALUES (?, 'SYSTEM', ?, 0, 'ACTIVE', 'SYSTEM')
            ON CONFLICT (currency)
            WHERE account_type = 'SYSTEM'
            DO NOTHING
            """;

    jdbcTemplate.update(
            insertSql,
            UUID.randomUUID(),
            currency
    );

    String selectSql = """
            SELECT id, owner_id, currency, balance, status,
                   account_type, created_at
            FROM accounts
            WHERE currency = ? AND account_type = 'SYSTEM'
            FOR UPDATE
            """;

    return jdbcTemplate.queryForObject(
            selectSql,
            accountRowMapper,
            currency
    );
}
}