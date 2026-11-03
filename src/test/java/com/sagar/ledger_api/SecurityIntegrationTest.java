
package com.sagar.ledger_api;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
class SecurityIntegrationTest extends AbstractIntegrationTest {

    private static final String TEST_API_KEY = "security-test-key-123";
    private static final String CLIENT_B_API_KEY = "security-client-b-key-123";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void registerTestClient() throws Exception {

        registerClient("security-test-client", TEST_API_KEY);
    }

    private void registerClient(String clientId, String apiKey)
            throws Exception {

        String hash = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256")
                        .digest(apiKey.getBytes(StandardCharsets.UTF_8))
        );

        jdbcTemplate.update(
                "DELETE FROM api_clients WHERE client_id = ?",
                clientId
        );

        jdbcTemplate.update("""
                INSERT INTO api_clients
                    (id, client_id, api_key_hash, status)
                VALUES (?, ?, ?, 'ACTIVE')
                """,
                UUID.randomUUID(),
                clientId,
                hash
        );
    }

    @Test
    void shouldRejectRequestWithoutApiKey() throws Exception {

        mockMvc.perform(post("/api/accounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currency": "THB"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldRejectRequestWithInvalidApiKey() throws Exception {

        mockMvc.perform(post("/api/accounts")
                        .header("X-API-KEY", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currency": "THB"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldAllowRequestWithValidApiKey() throws Exception {

        mockMvc.perform(post("/api/accounts")
                        .header("X-API-KEY", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currency": "THB"
                                }
                                """))
                .andExpect(status().isCreated());
    }

    @Test
    void shouldAllowHealthCheckWithoutApiKey() throws Exception {

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectRequestWithRevokedApiKey() throws Exception {

        jdbcTemplate.update(
                """
                UPDATE api_clients
                SET status = 'REVOKED'
                WHERE client_id = ?
                """,
                "security-test-client"
        );

        mockMvc.perform(post("/api/accounts")
                        .header("X-API-KEY", TEST_API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "currency": "THB"
                                }
                                """))
                .andExpect(status().isUnauthorized());
    }

    @Test
void shouldPreventClientFromAccessingAnotherClientsAccount()
        throws Exception {

    // Client A creates an account.
    MvcResult createResult = mockMvc.perform(
                    post("/api/accounts")
                            .header("X-API-KEY", TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "currency": "THB"
                                    }
                                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String responseBody =
            createResult.getResponse().getContentAsString();

    java.util.regex.Matcher matcher =
            java.util.regex.Pattern
                    .compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(responseBody);

    if (!matcher.find()) {
        throw new AssertionError(
                "Account ID not found in response: " + responseBody
        );
    }

    String accountId = matcher.group(1);

    // Register Client B with a different API key.
    registerClient("security-client-b", CLIENT_B_API_KEY);

    // Client B attempts to access Client A's account.
    mockMvc.perform(
                    get("/api/accounts/{id}", accountId)
                            .header("X-API-KEY", CLIENT_B_API_KEY)
            )
            .andExpect(status().isNotFound());
}

    @Test
void shouldPreventClientFromTransferringFromAnotherClientsAccount()
        throws Exception {

    // Client A creates an account.
    MvcResult accountResult = mockMvc.perform(
                    post("/api/accounts")
                            .header("X-API-KEY", TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "currency": "THB"
                                    }
                                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String responseBody =
            accountResult.getResponse().getContentAsString();

    java.util.regex.Matcher matcher =
            java.util.regex.Pattern
                    .compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(responseBody);

    if (!matcher.find()) {
        throw new AssertionError("Account ID not found");
    }

    String sourceAccountId = matcher.group(1);

    // Client B attempts to transfer from Client A's account.
    registerClient("security-client-b", CLIENT_B_API_KEY);

    mockMvc.perform(
                    post("/api/transfers")
                            .header("X-API-KEY", CLIENT_B_API_KEY)
                            .header("Idempotency-Key", "unauthorized-transfer-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "sourceAccountId": "%s",
                                        "destinationAccountId": "%s",
                                        "amount": 100.00,
                                        "currency": "THB"
                                    }
                                    """.formatted(
                                    sourceAccountId,
                                    UUID.randomUUID()
                            ))
            )
            .andExpect(status().isNotFound());
}

    
@Test
void shouldPreventClientFromWithdrawingFromAnotherClientsAccount()
        throws Exception {

    // Client A creates an account.
    MvcResult accountResult = mockMvc.perform(
                    post("/api/accounts")
                            .header("X-API-KEY", TEST_API_KEY)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "currency": "THB"
                                    }
                                    """))
            .andExpect(status().isCreated())
            .andReturn();

    String responseBody =
            accountResult.getResponse().getContentAsString();

    java.util.regex.Matcher matcher =
            java.util.regex.Pattern
                    .compile("\"id\"\\s*:\\s*\"([^\"]+)\"")
                    .matcher(responseBody);

    if (!matcher.find()) {
        throw new AssertionError("Account ID not found");
    }

    String accountId = matcher.group(1);

    // Register Client B.
    registerClient("security-client-b", CLIENT_B_API_KEY);

    // Client B attempts to withdraw from Client A's account.
    mockMvc.perform(
                    post("/api/withdrawals")
                            .header("X-API-KEY", CLIENT_B_API_KEY)
                            .header("Idempotency-Key", "unauthorized-withdrawal-001")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                        "accountId": "%s",
                                        "amount": 100.00,
                                        "currency": "THB"
                                    }
                                    """.formatted(accountId))
            )
            .andExpect(status().isNotFound());
}

}
