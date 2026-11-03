
package com.sagar.ledger_api;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.dto.CreateTransferRequest;
import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.TransferResponse;
import com.sagar.ledger_api.service.AccountService;
import com.sagar.ledger_api.service.DepositService;
import com.sagar.ledger_api.service.TransferService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class TransferIntegrationTest extends AbstractIntegrationTest {

    private static final String CLIENT_ID = "transfer-test-client";

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldTransferMoneyAndCreateBalancedLedgerEntries() {

        AccountResponse sender = createAccount();
        AccountResponse receiver = createAccount();

        depositMoney(sender.id(), "1000.00", "deposit-" + UUID.randomUUID());

        TransferResponse transfer = transferMoney(
                sender.id(), receiver.id(), "300.00",
                "transfer-" + UUID.randomUUID()
        );

        AccountResponse updatedSender =
                accountService.getAccount(sender.id(), CLIENT_ID);
        AccountResponse updatedReceiver =
                accountService.getAccount(receiver.id(), CLIENT_ID);

        assertEquals(0, updatedSender.balance().compareTo(new BigDecimal("700.00")));
        assertEquals(0, updatedReceiver.balance().compareTo(new BigDecimal("300.00")));

        Integer entryCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?",
                Integer.class, transfer.id()
        );

        assertEquals(2, entryCount);

        BigDecimal debitTotal = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0)
                FROM ledger_entries
                WHERE transfer_id = ? AND entry_type = 'DEBIT'
                """,
                BigDecimal.class, transfer.id()
        );

        BigDecimal creditTotal = jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(SUM(amount), 0)
                FROM ledger_entries
                WHERE transfer_id = ? AND entry_type = 'CREDIT'
                """,
                BigDecimal.class, transfer.id()
        );

        assertEquals(0, debitTotal.compareTo(creditTotal));
    }

    @Test
    void shouldNotDuplicateTransferWithSameIdempotencyKey() {

        AccountResponse sender = createAccount();
        AccountResponse receiver = createAccount();

        depositMoney(sender.id(), "1000.00", "deposit-" + UUID.randomUUID());

        String key = "transfer-" + UUID.randomUUID();

        TransferResponse first = transferMoney(
                sender.id(), receiver.id(), "200.00", key
        );

        TransferResponse second = transferMoney(
                sender.id(), receiver.id(), "200.00", key
        );

        assertEquals(first.id(), second.id());

        AccountResponse updatedSender =
                accountService.getAccount(sender.id(), CLIENT_ID);
        AccountResponse updatedReceiver =
                accountService.getAccount(receiver.id(), CLIENT_ID);

        assertEquals(0, updatedSender.balance().compareTo(new BigDecimal("800.00")));
        assertEquals(0, updatedReceiver.balance().compareTo(new BigDecimal("200.00")));
    }

    @Test
    void shouldHandleConcurrentRequestsWithSameIdempotencyKey() throws Exception {

        AccountResponse sender = createAccount();
        AccountResponse receiver = createAccount();

        depositMoney(sender.id(), "1000.00", "deposit-" + UUID.randomUUID());

        String key = "concurrent-same-key-" + UUID.randomUUID();

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);

        try {
                Future<TransferResponse> firstFuture = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start");
                }
                return transferMoney(sender.id(), receiver.id(), "200.00", key);
                });

                Future<TransferResponse> secondFuture = executor.submit(() -> {
                ready.countDown();
                if (!start.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("Timed out waiting to start");
                }
                return transferMoney(sender.id(), receiver.id(), "200.00", key);
                });

                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();

                TransferResponse first = firstFuture.get();
                TransferResponse second = secondFuture.get();

                // Both requests must return the same transfer.
                assertEquals(first.id(), second.id());

                // Only one transfer record should exist for this key.
                Integer transferCount = jdbcTemplate.queryForObject(
                        """
                        SELECT COUNT(*)
                        FROM transfers
                        WHERE client_id = ? AND idempotency_key = ?
                        """,
                        Integer.class,
                        CLIENT_ID,
                        key
                );

                assertEquals(1, transferCount);

                // Only two ledger entries should be created.
                Integer entryCount = jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM ledger_entries WHERE transfer_id = ?",
                        Integer.class,
                        first.id()
                );

                assertEquals(2, entryCount);

                // Money must only be deducted once.
                AccountResponse updatedSender =
                        accountService.getAccount(sender.id(), CLIENT_ID);
                AccountResponse updatedReceiver =
                        accountService.getAccount(receiver.id(), CLIENT_ID);

                assertEquals(0, updatedSender.balance().compareTo(new BigDecimal("800.00")));
                assertEquals(0, updatedReceiver.balance().compareTo(new BigDecimal("200.00")));

        } finally {
                executor.shutdownNow();
        }
    }

    @Test
    void shouldRejectTransferWhenBalanceIsInsufficient() {

        AccountResponse sender = createAccount();
        AccountResponse receiver = createAccount();

        depositMoney(sender.id(), "500.00", "deposit-" + UUID.randomUUID());

        Integer transfersBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers", Integer.class
        );

        assertThrows(
                RuntimeException.class,
                () -> transferMoney(
                        sender.id(), receiver.id(), "800.00",
                        "transfer-" + UUID.randomUUID()
                )
        );

        AccountResponse updatedSender =
                accountService.getAccount(sender.id(), CLIENT_ID);
        AccountResponse updatedReceiver =
                accountService.getAccount(receiver.id(), CLIENT_ID);

        assertEquals(0, updatedSender.balance().compareTo(new BigDecimal("500.00")));
        assertEquals(0, updatedReceiver.balance().compareTo(BigDecimal.ZERO));

        Integer transfersAfter = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM transfers", Integer.class
        );

        assertEquals(transfersBefore, transfersAfter);
    }

    @Test
    void shouldHandleConcurrentTransfersWithoutLosingMoney() throws Exception {

        AccountResponse sender = createAccount();

        depositMoney(
                sender.id(), "1000.00",
                "deposit-" + UUID.randomUUID()
        );

        List<AccountResponse> receivers = new ArrayList<>();

        for (int i = 0; i < 10; i++) {
            receivers.add(createAccount());
        }

        ExecutorService executor = Executors.newFixedThreadPool(10);

        try {
            List<Future<TransferResponse>> futures = new ArrayList<>();

            for (int i = 0; i < 10; i++) {
                AccountResponse receiver = receivers.get(i);
                String key = "concurrent-transfer-" + UUID.randomUUID();

                futures.add(executor.submit(() ->
                        transferMoney(
                                sender.id(), receiver.id(), "100.00", key
                        )
                ));
            }

            for (Future<TransferResponse> future : futures) {
                assertNotNull(future.get());
            }

        } finally {
            executor.shutdown();
        }

        AccountResponse updatedSender =
                accountService.getAccount(sender.id(), CLIENT_ID);

        assertEquals(0, updatedSender.balance().compareTo(BigDecimal.ZERO));

        for (AccountResponse receiver : receivers) {
            AccountResponse updatedReceiver =
                    accountService.getAccount(receiver.id(), CLIENT_ID);

            assertEquals(
                    0,
                    updatedReceiver.balance().compareTo(new BigDecimal("100.00"))
            );
        }
    }

    @Test
    void shouldKeepAccountBalancesConsistentWithLedgerEntries() {

        AccountResponse sender = createAccount();
        AccountResponse receiver = createAccount();

        depositMoney(
                sender.id(), "1000.00",
                "deposit-" + UUID.randomUUID()
        );

        transferMoney(
                sender.id(), receiver.id(), "300.00",
                "transfer-" + UUID.randomUUID()
        );

        BigDecimal senderLedgerBalance = getLedgerBalance(sender.id());
        BigDecimal receiverLedgerBalance = getLedgerBalance(receiver.id());

        assertEquals(
                0,
                accountService.getAccount(sender.id(), CLIENT_ID)
                        .balance().compareTo(senderLedgerBalance)
        );

        assertEquals(
                0,
                accountService.getAccount(receiver.id(), CLIENT_ID)
                        .balance().compareTo(receiverLedgerBalance)
        );
    }

    private BigDecimal getLedgerBalance(UUID accountId) {
        return jdbcTemplate.queryForObject(
                """
                SELECT COALESCE(
                    SUM(CASE
                        WHEN entry_type = 'CREDIT' THEN amount
                        WHEN entry_type = 'DEBIT' THEN -amount
                        ELSE 0
                    END), 0
                )
                FROM ledger_entries
                WHERE account_id = ?
                """,
                BigDecimal.class,
                accountId
        );
    }

    private AccountResponse createAccount() {
        return accountService.createAccount(
                new CreateAccountRequest("THB"),
                CLIENT_ID
        );
    }

    private void depositMoney(UUID accountId, String amount, String key) {
        depositService.createDeposit(
                new DepositRequest(
                        accountId, new BigDecimal(amount), "THB"
                ),
                CLIENT_ID,
                key
        );
    }

    private TransferResponse transferMoney(
            UUID senderId,
            UUID receiverId,
            String amount,
            String key
    ) {
        return transferService.createTransfer(
                new CreateTransferRequest(
                        senderId, receiverId, new BigDecimal(amount), "THB"
                ),
                CLIENT_ID,
                key
        );
    }

    
@Test
void shouldRejectTransferWhenSourceAccountBelongsToAnotherClient() {

    AccountResponse sender = accountService.createAccount(
            new CreateAccountRequest("THB"),
            "account-owner-client"
    );

    AccountResponse receiver = createAccount();

    // Deposit using the actual account owner.
    depositService.createDeposit(
            new DepositRequest(
                    sender.id(),
                    new BigDecimal("500.00"),
                    "THB"
            ),
            "account-owner-client",
            "owner-deposit-" + UUID.randomUUID()
    );

    assertThrows(
            org.springframework.web.server.ResponseStatusException.class,
            () -> transferService.createTransfer(
                    new CreateTransferRequest(
                            sender.id(),
                            receiver.id(),
                            new BigDecimal("100.00"),
                            "THB"
                    ),
                    CLIENT_ID,
                    "unauthorized-transfer-" + UUID.randomUUID()
            )
    );

    BigDecimal senderBalance = jdbcTemplate.queryForObject(
            "SELECT balance FROM accounts WHERE id = ?",
            BigDecimal.class,
            sender.id()
    );

    assertEquals(
            0,
            senderBalance.compareTo(new BigDecimal("500.00"))
    );
}

    
}
