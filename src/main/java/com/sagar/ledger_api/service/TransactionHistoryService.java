
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.TransactionHistoryResponse;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.repository.AccountRepository;
import com.sagar.ledger_api.repository.LedgerRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class TransactionHistoryService {

    private final AccountRepository accountRepository;
    private final LedgerRepository ledgerRepository;

    public TransactionHistoryService(
            AccountRepository accountRepository,
            LedgerRepository ledgerRepository) {
        this.accountRepository = accountRepository;
        this.ledgerRepository = ledgerRepository;
    }

    public List<TransactionHistoryResponse> getTransactionHistory(
            UUID accountId,
            String clientId,
            int page,
            int size) {

        if (page < 0 || size < 1 || size > 100) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page must be non-negative and size must be between 1 and 100"
            );
        }

        Account account = accountRepository.findById(accountId);

        if (account == null || !account.ownerId().equals(clientId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            );
        }

        int offset;

        try {
            offset = Math.multiplyExact(page, size);
        } catch (ArithmeticException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Page value is too large"
            );
        }

        return ledgerRepository.findTransactionHistory(
                accountId,
                size,
                offset
        );
    }
}
