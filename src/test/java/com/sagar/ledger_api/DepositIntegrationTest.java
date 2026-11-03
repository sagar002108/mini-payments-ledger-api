
package com.sagar.ledger_api;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.DepositResponse;
import com.sagar.ledger_api.service.AccountService;
import com.sagar.ledger_api.service.DepositService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class DepositIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldCreateBalancedDeposit() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"),
                "integration-client"
        );

        DepositRequest request = new DepositRequest(
                account.id(), new BigDecimal("1000.0000"), "THB"
        );

        DepositResponse deposit = depositService.createDeposit(
                request, "integration-client", "deposit-" + UUID.randomUUID()
        );

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class, account.id()
        );

        assertEquals(0, new BigDecimal("1000.0000").compareTo(balance));

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE deposit_id = ?",
                Integer.class, deposit.id()
        );

        assertEquals(2, entryCount);

        BigDecimal debit = jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount) FROM ledger_entries
                WHERE deposit_id = ? AND entry_type = 'DEBIT'
                """, BigDecimal.class, deposit.id()
        );

        BigDecimal credit = jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount) FROM ledger_entries
                WHERE deposit_id = ? AND entry_type = 'CREDIT'
                """, BigDecimal.class, deposit.id()
        );

        assertEquals(0, debit.compareTo(credit));
    }

    @Test
    void shouldNotDuplicateDepositWithSameIdempotencyKey() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"),
                "integration-client"
    );

        DepositRequest request = new DepositRequest(
                account.id(), new BigDecimal("500.0000"), "THB"
        );

        String clientId = "integration-client";
        String key = "same-key-" + UUID.randomUUID();

        DepositResponse first = depositService.createDeposit(request, clientId, key);
        DepositResponse second = depositService.createDeposit(request, clientId, key);

        assertEquals(first.id(), second.id());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class, account.id()
        );

        assertEquals(0, new BigDecimal("500.0000").compareTo(balance));
    }

    @Test
    void shouldRejectDifferentRequestWithSameIdempotencyKey() {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"),
                "integration-client"
    );

        String clientId = "integration-client";
        String key = "conflict-key-" + UUID.randomUUID();

        DepositRequest firstRequest = new DepositRequest(
                account.id(), new BigDecimal("500.0000"), "THB"
        );

        DepositRequest secondRequest = new DepositRequest(
                account.id(), new BigDecimal("700.0000"), "THB"
        );

        depositService.createDeposit(firstRequest, clientId, key);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> depositService.createDeposit(secondRequest, clientId, key)
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    
@Test
void shouldRejectDepositWhenAccountBelongsToAnotherClient() {

    AccountResponse account = accountService.createAccount(
            new CreateAccountRequest("THB"),
            "account-owner-client"
    );

    DepositRequest request = new DepositRequest(
            account.id(),
            new BigDecimal("500.0000"),
            "THB"
    );

    ResponseStatusException exception = assertThrows(
            ResponseStatusException.class,
            () -> depositService.createDeposit(
                    request,
                    "different-client",
                    "unauthorized-deposit-" + UUID.randomUUID()
            )
    );

    assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

    BigDecimal balance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            account.id()
    );

    assertEquals(0, BigDecimal.ZERO.compareTo(balance));
}

}
