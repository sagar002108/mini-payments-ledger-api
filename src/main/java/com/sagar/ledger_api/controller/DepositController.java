
package com.sagar.ledger_api.controller;

import com.sagar.ledger_api.dto.DepositRequest;
import com.sagar.ledger_api.dto.DepositResponse;
import com.sagar.ledger_api.service.DepositService;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/deposits")
public class DepositController {

    private final DepositService depositService;

    public DepositController(DepositService depositService) {
        this.depositService = depositService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DepositResponse createDeposit(
            @Valid @RequestBody DepositRequest request,
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String clientId = authentication.getName();

        return depositService.createDeposit(
                request,
                clientId,
                idempotencyKey
        );
    }
}
