
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.WithdrawalRequest;
import com.sagar.ledger_api.dto.WithdrawalResponse;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.model.LedgerEntry;
import com.sagar.ledger_api.model.WithdrawalRecord;
import com.sagar.ledger_api.repository.AccountRepository;
import com.sagar.ledger_api.repository.LedgerRepository;
import com.sagar.ledger_api.repository.WithdrawalRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class WithdrawalService {

    private final AccountRepository accountRepository;
    private final WithdrawalRepository withdrawalRepository;
    private final LedgerRepository ledgerRepository;

    public WithdrawalService(
            AccountRepository accountRepository,
            WithdrawalRepository withdrawalRepository,
            LedgerRepository ledgerRepository) {

        this.accountRepository = accountRepository;
        this.withdrawalRepository = withdrawalRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public WithdrawalResponse createWithdrawal(
            WithdrawalRequest request,
            String clientId,
            String idempotencyKey) {

        if (clientId == null || clientId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID and idempotency key are required"
            );
        }

        if (request.amount() == null
                || request.amount().signum() <= 0
                || request.amount().scale() > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Withdrawal amount must be positive with at most 4 decimal places"
            );
        }

        String requestHash = createRequestHash(request);

        withdrawalRepository.lockIdempotencyKey(clientId, idempotencyKey);
        
        WithdrawalRecord existing =
                withdrawalRepository.findByIdempotencyKey(
                        clientId, idempotencyKey
                );

        if (existing != null) {
            return handleExistingWithdrawal(existing, requestHash);
        }

        Account customerAccount =
                accountRepository.findByIdForUpdate(request.accountId());

        if (customerAccount == null
                || !customerAccount.ownerId().equals(clientId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            );
        }

        if (!"ACTIVE".equals(customerAccount.status())
                || !"CUSTOMER".equals(customerAccount.accountType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A withdrawal requires an active customer account"
            );
        }

        if (!customerAccount.currency().equals(request.currency())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Withdrawal currency must match account currency"
            );
        }

        if (customerAccount.balance().compareTo(request.amount()) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient account balance"
            );
        }

        // Check again after acquiring the account lock.
        existing = withdrawalRepository.findByIdempotencyKey(
                clientId, idempotencyKey
        );

        if (existing != null) {
            return handleExistingWithdrawal(existing, requestHash);
        }

        Account systemAccount =
                accountRepository.findOrCreateSystemAccount(
                        request.currency()
                );

        UUID withdrawalId = UUID.randomUUID();

        WithdrawalResponse savedWithdrawal =
                withdrawalRepository.create(
                        withdrawalId,
                        clientId,
                        idempotencyKey,
                        requestHash,
                        customerAccount.id(),
                        request.amount(),
                        request.currency()
                );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Debit the customer account.
        ledgerRepository.createWithdrawalEntry(
                new LedgerEntry(
                        UUID.randomUUID(),
                        null,
                        customerAccount.id(),
                        "DEBIT",
                        request.amount(),
                        request.currency(),
                        now
                ),
                withdrawalId
        );

        // Credit the system account.
        ledgerRepository.createWithdrawalEntry(
                new LedgerEntry(
                        UUID.randomUUID(),
                        null,
                        systemAccount.id(),
                        "CREDIT",
                        request.amount(),
                        request.currency(),
                        now
                ),
                withdrawalId
        );

        accountRepository.updateBalance(
                customerAccount.id(),
                customerAccount.balance().subtract(request.amount())
        );

        accountRepository.updateBalance(
                systemAccount.id(),
                systemAccount.balance().add(request.amount())
        );

        return savedWithdrawal;
    }

    private WithdrawalResponse handleExistingWithdrawal(
            WithdrawalRecord existing,
            String requestHash) {

        if (!existing.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request"
            );
        }

        return existing.withdrawal();
    }

    private String createRequestHash(WithdrawalRequest request) {

        String value = request.accountId() + "|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|" + request.currency();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate request hash", exception
            );
        }
    }
}
