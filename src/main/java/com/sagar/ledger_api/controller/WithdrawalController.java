
package com.sagar.ledger_api.controller;

import com.sagar.ledger_api.dto.WithdrawalRequest;
import com.sagar.ledger_api.dto.WithdrawalResponse;
import com.sagar.ledger_api.service.WithdrawalService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/withdrawals")
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    public WithdrawalController(WithdrawalService withdrawalService) {
        this.withdrawalService = withdrawalService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public WithdrawalResponse createWithdrawal(
            @Valid @RequestBody WithdrawalRequest request,
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String clientId = authentication.getName();

        return withdrawalService.createWithdrawal(
                request,
                clientId,
                idempotencyKey
        );
    }
}
