
package com.sagar.ledger_api.controller;

import com.sagar.ledger_api.dto.TransactionHistoryResponse;
import com.sagar.ledger_api.service.TransactionHistoryService;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/accounts")
public class TransactionHistoryController {

    private final TransactionHistoryService transactionHistoryService;

    public TransactionHistoryController(
            TransactionHistoryService transactionHistoryService) {
        this.transactionHistoryService = transactionHistoryService;
    }

    @GetMapping("/{accountId}/transactions")
    public List<TransactionHistoryResponse> getTransactionHistory(
            @PathVariable UUID accountId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {

        return transactionHistoryService.getTransactionHistory(
                accountId,
                authentication.getName(),
                page,
                size
        );
    }
}
