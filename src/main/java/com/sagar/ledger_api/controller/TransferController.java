
package com.sagar.ledger_api.controller;

import com.sagar.ledger_api.dto.CreateTransferRequest;
import com.sagar.ledger_api.dto.TransferResponse;
import com.sagar.ledger_api.service.TransferService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/transfers")
public class TransferController {

    private final TransferService transferService;

    public TransferController(TransferService transferService) {
        this.transferService = transferService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransferResponse createTransfer(
            @Valid @RequestBody CreateTransferRequest request,
            Authentication authentication,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {

        String clientId = authentication.getName();

        return transferService.createTransfer(
                request,
                clientId,
                idempotencyKey
        );
    }
}
