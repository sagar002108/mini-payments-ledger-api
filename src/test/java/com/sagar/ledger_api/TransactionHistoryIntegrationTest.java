
package com.sagar.ledger_api;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.dto.CreateTransferRequest;
import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.TransactionHistoryResponse;
import com.sagar.ledger_api.dto.WithdrawalRequest;
import com.sagar.ledger_api.service.AccountService;
import com.sagar.ledger_api.service.DepositService;
import com.sagar.ledger_api.service.TransferService;
import com.sagar.ledger_api.service.TransactionHistoryService;
import com.sagar.ledger_api.service.WithdrawalService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TransactionHistoryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Autowired
    private DepositService depositService;

    @Autowired
    private WithdrawalService withdrawalService;

    @Autowired
    private TransferService transferService;

    @Autowired
    private TransactionHistoryService transactionHistoryService;

    @Test
    void shouldShowDepositInTransactionHistory() {

        String clientId = "history-deposit-client";

        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"), clientId
        );

        depositService.createDeposit(
                new DepositRequest(
                        account.id(),
                        new BigDecimal("500.0000"),
                        "THB"
                ),
                clientId,
                "history-deposit-key"
        );

        List<TransactionHistoryResponse> history =
                transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, 0, 20
                );

        assertEquals(1, history.size());
        assertEquals("DEPOSIT", history.get(0).transactionType());
        assertEquals("CREDIT", history.get(0).entryType());
        assertEquals(0, new BigDecimal("500.0000")
                .compareTo(history.get(0).amount()));
    }

    @Test
    void shouldShowWithdrawalInTransactionHistory() {

        String clientId = "history-withdrawal-client";

        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"), clientId
        );

        depositService.createDeposit(
                new DepositRequest(
                        account.id(),
                        new BigDecimal("500.0000"),
                        "THB"
                ),
                clientId,
                "history-fund-key"
        );

        withdrawalService.createWithdrawal(
                new WithdrawalRequest(
                        account.id(),
                        new BigDecimal("200.0000"),
                        "THB"
                ),
                clientId,
                "history-withdrawal-key"
        );

        List<TransactionHistoryResponse> history =
                transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, 0, 20
                );

        assertEquals(2, history.size());
        assertEquals("WITHDRAWAL", history.get(0).transactionType());
        assertEquals("DEBIT", history.get(0).entryType());
        assertEquals("DEPOSIT", history.get(1).transactionType());
    }

    @Test
    void shouldShowTransferForSenderAndReceiver() {

        String senderClient = "history-sender-client";
        String receiverClient = "history-receiver-client";

        AccountResponse sender = accountService.createAccount(
                new CreateAccountRequest("THB"), senderClient
        );

        AccountResponse receiver = accountService.createAccount(
                new CreateAccountRequest("THB"), receiverClient
        );

        depositService.createDeposit(
                new DepositRequest(
                        sender.id(),
                        new BigDecimal("500.0000"),
                        "THB"
                ),
                senderClient,
                "history-transfer-fund-key"
        );

        transferService.createTransfer(
                new CreateTransferRequest(
                        sender.id(),
                        receiver.id(),
                        new BigDecimal("100.0000"),
                        "THB"
                ),
                senderClient,
                "history-transfer-key"
        );

        List<TransactionHistoryResponse> senderHistory =
                transactionHistoryService.getTransactionHistory(
                        sender.id(), senderClient, 0, 20
                );

        List<TransactionHistoryResponse> receiverHistory =
                transactionHistoryService.getTransactionHistory(
                        receiver.id(), receiverClient, 0, 20
                );

        assertEquals(2, senderHistory.size());
        assertEquals("TRANSFER", senderHistory.get(0).transactionType());
        assertEquals("DEBIT", senderHistory.get(0).entryType());

        assertEquals(1, receiverHistory.size());
        assertEquals("TRANSFER", receiverHistory.get(0).transactionType());
        assertEquals("CREDIT", receiverHistory.get(0).entryType());
    }

    @Test
    void shouldApplyPaginationToTransactionHistory() {

        String clientId = "history-pagination-client";

        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"), clientId
        );

        for (int i = 1; i <= 5; i++) {
            depositService.createDeposit(
                    new DepositRequest(
                            account.id(),
                            new BigDecimal("100.0000"),
                            "THB"
                    ),
                    clientId,
                    "history-page-key-" + i
            );
        }

        List<TransactionHistoryResponse> firstPage =
                transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, 0, 2
                );

        List<TransactionHistoryResponse> secondPage =
                transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, 1, 2
                );

        assertEquals(2, firstPage.size());
        assertEquals(2, secondPage.size());

        assertNotEquals(
                firstPage.get(0).transactionId(),
                secondPage.get(0).transactionId()
        );
    }

    @Test
    void shouldRejectAccessToAnotherClientsTransactionHistory() {

        String ownerClient = "history-owner-client";
        String differentClient = "history-different-client";

        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"), ownerClient
        );

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> transactionHistoryService.getTransactionHistory(
                        account.id(), differentClient, 0, 20
                )
        );

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void shouldRejectInvalidPaginationValues() {

        String clientId = "history-invalid-page-client";

        AccountResponse account = accountService.createAccount(
                new CreateAccountRequest("THB"), clientId
        );

        assertThrows(
                ResponseStatusException.class,
                () -> transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, -1, 20
                )
        );

        assertThrows(
                ResponseStatusException.class,
                () -> transactionHistoryService.getTransactionHistory(
                        account.id(), clientId, 0, 101
                )
        );
    }
}
