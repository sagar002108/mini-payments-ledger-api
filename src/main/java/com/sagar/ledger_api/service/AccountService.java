
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.repository.AccountRepository;

import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

@Service
public class AccountService {

    private final AccountRepository accountRepository;

    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public AccountResponse createAccount(
            CreateAccountRequest request,
            String clientId
    ) {

        Account account = new Account(
                UUID.randomUUID(),
                clientId,
                request.currency(),
                new BigDecimal("0.0000"),
                "ACTIVE",
                "CUSTOMER",
                OffsetDateTime.now(ZoneOffset.UTC)
        );

        Account savedAccount = accountRepository.create(account);

        return toResponse(savedAccount);
    }

    public AccountResponse getAccount(UUID id, String clientId) {

        Account account = accountRepository.findById(id);

        if (account == null || !account.ownerId().equals(clientId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Account not found"
            );
        }

        return toResponse(account);
    }

    private AccountResponse toResponse(Account account) {
        return new AccountResponse(
                account.id(),
                account.ownerId(),
                account.currency(),
                account.balance(),
                account.status(),
                account.createdAt()
        );
    }
}
