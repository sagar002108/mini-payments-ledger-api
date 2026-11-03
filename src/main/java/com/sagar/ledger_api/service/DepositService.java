
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.DepositResponse;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.model.DepositRecord;
import com.sagar.ledger_api.model.LedgerEntry;
import com.sagar.ledger_api.repository.AccountRepository;
import com.sagar.ledger_api.repository.DepositRepository;
import com.sagar.ledger_api.repository.LedgerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class DepositService {

    private final AccountRepository accountRepository;
    private final DepositRepository depositRepository;
    private final LedgerRepository ledgerRepository;

    public DepositService(
            AccountRepository accountRepository,
            DepositRepository depositRepository,
            LedgerRepository ledgerRepository) {

        this.accountRepository = accountRepository;
        this.depositRepository = depositRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public DepositResponse createDeposit(
            DepositRequest request,
            String clientId,
            String idempotencyKey) {

        if (clientId == null || clientId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID and idempotency key are required"
            );
        }

        if (request.amount().signum() <= 0
                || request.amount().scale() > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deposit amount must be positive with at most 4 decimal places"
            );
        }

        String requestHash = createRequestHash(request);

        depositRepository.lockIdempotencyKey(clientId, idempotencyKey);
        
        DepositRecord existing =
                depositRepository.findByIdempotencyKey(
                        clientId, idempotencyKey
                );

        if (existing != null) {
            return handleExistingDeposit(existing, requestHash);
        }

        Account customerAccount =
                accountRepository.findByIdForUpdate(request.accountId());

        if (customerAccount == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            );
        }

        if (!customerAccount.ownerId().equals(clientId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            );
        }

        if (!"ACTIVE".equals(customerAccount.status())
                || !"CUSTOMER".equals(customerAccount.accountType())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A deposit requires an active customer account"
            );
        }

        if (!customerAccount.currency().equals(request.currency())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Deposit currency must match account currency"
            );
        }

        // Check again after acquiring the customer account lock.
        existing = depositRepository.findByIdempotencyKey(
                clientId, idempotencyKey
        );

        if (existing != null) {
            return handleExistingDeposit(existing, requestHash);
        }

        Account systemAccount =
                accountRepository.findOrCreateSystemAccount(
                        request.currency()
                );

        UUID depositId = UUID.randomUUID();

        DepositResponse savedDeposit = depositRepository.create(
                depositId,
                clientId,
                idempotencyKey,
                requestHash,
                customerAccount.id(),
                request.amount(),
                request.currency()
        );

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        // Debit the system account.
        ledgerRepository.createDepositEntry(
                new LedgerEntry(
                        UUID.randomUUID(),
                        null,
                        systemAccount.id(),
                        "DEBIT",
                        request.amount(),
                        request.currency(),
                        now
                ),
                depositId
        );

        // Credit the customer account.
        ledgerRepository.createDepositEntry(
                new LedgerEntry(
                        UUID.randomUUID(),
                        null,
                        customerAccount.id(),
                        "CREDIT",
                        request.amount(),
                        request.currency(),
                        now
                ),
                depositId
        );

        accountRepository.updateBalance(
                customerAccount.id(),
                customerAccount.balance().add(request.amount())
        );

        accountRepository.updateBalance(
                systemAccount.id(),
                systemAccount.balance().subtract(request.amount())
        );

        return savedDeposit;
    }

    private DepositResponse handleExistingDeposit(
            DepositRecord existing,
            String requestHash) {

        if (!existing.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request"
            );
        }

        return existing.deposit();
    }

    private String createRequestHash(DepositRequest request) {

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
