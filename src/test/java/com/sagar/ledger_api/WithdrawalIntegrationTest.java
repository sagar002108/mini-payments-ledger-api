
package com.sagar.ledger_api;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.WithdrawalRequest;
import com.sagar.ledger_api.dto.WithdrawalResponse;
import com.sagar.ledger_api.service.AccountService;
import com.sagar.ledger_api.service.DepositService;
import com.sagar.ledger_api.service.WithdrawalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class WithdrawalIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private AccountResponse createFundedAccount(String clientId, String amount) {
        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"),
                clientId
        );

        depositService.createDeposit(
                new DepositRequest(
                        account.id(),
                        new BigDecimal(amount),
                        "THB"
                ),
                clientId,
                "fund-" + UUID.randomUUID()
        );

        return account;
    }

    @Test
    void shouldCreateBalancedWithdrawal() {
        String clientId = "withdrawal-client";

        AccountResponse account = createFundedAccount(clientId, "500.0000");

        WithdrawalResponse withdrawal = withdrawalService.createWithdrawal(
                new WithdrawalRequest(
                        account.id(),
                        new BigDecimal("200.0000"),
                        "THB"
                ),
                clientId,
                "withdrawal-" + UUID.randomUUID()
        );

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                account.id()
        );

        assertEquals(0, new BigDecimal("300.0000").compareTo(balance));

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE withdrawal_id = ?",
                Integer.class,
                withdrawal.id()
        );

        assertEquals(2, entryCount);

        BigDecimal debit = jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount) FROM ledger_entries
                WHERE withdrawal_id = ? AND entry_type = 'DEBIT'
                """,
                BigDecimal.class,
                withdrawal.id()
        );

        BigDecimal credit = jdbcTemplate.queryForObject(
                """
                SELECT SUM(amount) FROM ledger_entries
                WHERE withdrawal_id = ? AND entry_type = 'CREDIT'
                """,
                BigDecimal.class,
                withdrawal.id()
        );

        assertEquals(0, debit.compareTo(credit));
    }

    @Test
    void shouldNotDuplicateWithdrawalWithSameIdempotencyKey() {
        String clientId = "withdrawal-replay-client";

        AccountResponse account = createFundedAccount(clientId, "500.0000");

        WithdrawalRequest request = new WithdrawalRequest(
                account.id(),
                new BigDecimal("200.0000"),
                "THB"
        );

        String key = "same-withdrawal-key-" + UUID.randomUUID();

        WithdrawalResponse first = withdrawalService.createWithdrawal(
                request, clientId, key
        );

        WithdrawalResponse second = withdrawalService.createWithdrawal(
                request, clientId, key
        );

        assertEquals(first.id(), second.id());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                account.id()
        );

        assertEquals(0, new BigDecimal("300.0000").compareTo(balance));

        Integer withdrawalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdrawals WHERE client_id = ? AND idempotency_key = ?",
                Integer.class,
                clientId,
                key
        );

        assertEquals(1, withdrawalCount);
    }

    @Test
    void shouldRejectDifferentRequestWithSameIdempotencyKey() {
        String clientId = "withdrawal-conflict-client";

        AccountResponse account = createFundedAccount(clientId, "1000.0000");

        String key = "conflict-withdrawal-key-" + UUID.randomUUID();

        WithdrawalRequest firstRequest = new WithdrawalRequest(
                account.id(),
                new BigDecimal("200.0000"),
                "THB"
        );

        WithdrawalRequest secondRequest = new WithdrawalRequest(
                account.id(),
                new BigDecimal("300.0000"),
                "THB"
        );

        withdrawalService.createWithdrawal(firstRequest, clientId, key);

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> withdrawalService.createWithdrawal(
                        secondRequest, clientId, key
                )
        );

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void shouldRejectWithdrawalWithInsufficientBalance() {
        String clientId = "withdrawal-insufficient-client";

        AccountResponse account = createFundedAccount(clientId, "100.0000");

        WithdrawalRequest request = new WithdrawalRequest(
                account.id(),
                new BigDecimal("200.0000"),
                "THB"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> withdrawalService.createWithdrawal(
                        request,
                        clientId,
                        "insufficient-" + UUID.randomUUID()
                )
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                account.id()
        );

        assertEquals(0, new BigDecimal("100.0000").compareTo(balance));

        Integer withdrawalCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM withdrawals WHERE account_id = ?",
                Integer.class,
                account.id()
        );

        assertEquals(0, withdrawalCount);
    }

    @Test
    void shouldRejectWithdrawalWhenAccountBelongsToAnotherClient() {
        AccountResponse account = createFundedAccount(
                "withdrawal-owner-client",
                "500.0000"
        );

        WithdrawalRequest request = new WithdrawalRequest(
                account.id(),
                new BigDecimal("100.0000"),
                "THB"
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> withdrawalService.createWithdrawal(
                        request,
                        "different-withdrawal-client",
                        "unauthorized-" + UUID.randomUUID()
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());

        BigDecimal balance = jdbcTemplate.queryForObject(
                "SELECT balance FROM accounts WHERE id = ?",
                BigDecimal.class,
                account.id()
        );

        assertEquals(0, new BigDecimal("500.0000").compareTo(balance));
    }
}
