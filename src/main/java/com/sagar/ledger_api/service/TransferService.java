
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.CreateTransferRequest;
import com.sagar.ledger_api.dto.TransferResponse;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.model.LedgerEntry;
import com.sagar.ledger_api.model.Transfer;
import com.sagar.ledger_api.repository.AccountRepository;
import com.sagar.ledger_api.repository.LedgerRepository;
import com.sagar.ledger_api.repository.TransferRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class TransferService {

    private final AccountRepository accountRepository;
    private final TransferRepository transferRepository;
    private final LedgerRepository ledgerRepository;

    public TransferService(
            AccountRepository accountRepository,
            TransferRepository transferRepository,
            LedgerRepository ledgerRepository) {

        this.accountRepository = accountRepository;
        this.transferRepository = transferRepository;
        this.ledgerRepository = ledgerRepository;
    }

    @Transactional
    public TransferResponse createTransfer(
            CreateTransferRequest request,
            String clientId,
            String idempotencyKey) {

        if (clientId == null || clientId.isBlank()
                || idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Client ID and idempotency key are required"
            );
        }

        if (request.sourceAccountId()
                .equals(request.destinationAccountId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Source and destination accounts must be different"
            );
        }

        BigDecimal amount = request.amount();

        if (amount.signum() <= 0 || amount.scale() > 4) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Amount must be positive with at most 4 decimal places"
            );
        }

        String requestHash = createRequestHash(request);

        // Serialize requests using the same client ID and idempotency key.
        transferRepository.lockIdempotencyKey(clientId, idempotencyKey);

        Transfer existing = transferRepository.findByIdempotencyKey(
                clientId, idempotencyKey
        );

        if (existing != null) {
            return handleExistingTransfer(existing, requestHash);
        }

        UUID firstId = request.sourceAccountId()
                .compareTo(request.destinationAccountId()) < 0
                ? request.sourceAccountId()
                : request.destinationAccountId();

        UUID secondId = firstId.equals(request.sourceAccountId())
                ? request.destinationAccountId()
                : request.sourceAccountId();

        Account firstAccount = accountRepository.findByIdForUpdate(firstId);
        Account secondAccount = accountRepository.findByIdForUpdate(secondId);

        Account source = firstId.equals(request.sourceAccountId())
                ? firstAccount : secondAccount;

        Account destination = firstId.equals(request.destinationAccountId())
                ? firstAccount : secondAccount;

        if (source == null || destination == null) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "One or both accounts were not found"
            );
        }

        if (!source.ownerId().equals(clientId)) {
            throw new ResponseStatusException(
                   HttpStatus.NOT_FOUND,
                   "Source account not found"
             );
        }

        // Check again after acquiring account locks.
        existing = transferRepository.findByIdempotencyKey(
                clientId, idempotencyKey
        );

        if (existing != null) {
            return handleExistingTransfer(existing, requestHash);
        }

        if (!"ACTIVE".equals(source.status())
                || !"ACTIVE".equals(destination.status())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Both accounts must be active"
            );
        }

        if (!source.currency().equals(destination.currency())
                || !source.currency().equals(request.currency())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Account currencies must match the transfer currency"
            );
        }

        if (source.balance().compareTo(amount) < 0) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Insufficient account balance"
            );
        }

        UUID transferId = UUID.randomUUID();

        Transfer transfer = new Transfer(
                transferId,
                clientId,
                idempotencyKey,
                requestHash,
                source.id(),
                destination.id(),
                amount,
                request.currency(),
                "COMPLETED",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        Transfer savedTransfer = transferRepository.create(transfer);

        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

        ledgerRepository.create(new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                source.id(),
                "DEBIT",
                amount,
                request.currency(),
                now
        ));

        ledgerRepository.create(new LedgerEntry(
                UUID.randomUUID(),
                transferId,
                destination.id(),
                "CREDIT",
                amount,
                request.currency(),
                now
        ));

        accountRepository.updateBalance(
                source.id(),
                source.balance().subtract(amount)
        );

        accountRepository.updateBalance(
                destination.id(),
                destination.balance().add(amount)
        );

        return toResponse(savedTransfer);
    }

    private TransferResponse handleExistingTransfer(
            Transfer existing,
            String requestHash) {

        if (!existing.requestHash().equals(requestHash)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with different details"
            );
        }

        return toResponse(existing);
    }

    private String createRequestHash(CreateTransferRequest request) {

        String value = request.sourceAccountId() + "|"
                + request.destinationAccountId() + "|"
                + request.amount().stripTrailingZeros().toPlainString() + "|"
                + request.currency();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");

            byte[] hash = digest.digest(
                    value.getBytes(StandardCharsets.UTF_8)
            );

            return HexFormat.of().formatHex(hash);

        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "Unable to calculate request hash", exception
            );
        }
    }

    private TransferResponse toResponse(Transfer transfer) {

        return new TransferResponse(
                transfer.id(),
                transfer.sourceAccountId(),
                transfer.destinationAccountId(),
                transfer.amount(),
                transfer.currency(),
                transfer.status(),
                transfer.createdAt()
        );
    }
}
