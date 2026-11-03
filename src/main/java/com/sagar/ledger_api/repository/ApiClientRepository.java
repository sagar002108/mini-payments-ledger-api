
package com.sagar.ledger_api.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class ApiClientRepository {

    private final JdbcTemplate jdbcTemplate;

    public ApiClientRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<String> findActiveClientIdByApiKeyHash(String apiKeyHash) {

        String sql = """
                SELECT client_id
                FROM api_clients
                WHERE api_key_hash = ?
                AND status = 'ACTIVE'
                """;

        return jdbcTemplate.query(
                sql,
                rs -> {
                    if (rs.next()) {
                        return Optional.of(rs.getString("client_id"));
                    }
                    return Optional.empty();
                },
                apiKeyHash
        );
    }
}
