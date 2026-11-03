
package com.sagar.ledger_api.controller;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.service.AccountService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(
            @Valid @RequestBody CreateAccountRequest request,
            Authentication authentication) {

        String clientId = authentication.getName();

        return accountService.createAccount(request, clientId);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(
            @PathVariable UUID id,
            Authentication authentication) {

        String clientId = authentication.getName();

        return accountService.getAccount(id, clientId);
    }
}
