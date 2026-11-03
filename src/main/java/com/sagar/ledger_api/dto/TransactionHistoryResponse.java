
package com.sagar.ledger_api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionHistoryResponse(
        UUID transactionId,
        String transactionType,
        String entryType,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt
) {}
