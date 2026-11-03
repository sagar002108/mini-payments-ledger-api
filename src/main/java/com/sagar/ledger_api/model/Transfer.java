package com.sagar.ledger_api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Transfer(

        UUID id,
        String clientId,
        String idempotencyKey,
        String requestHash,
        UUID sourceAccountId,
        UUID destinationAccountId,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime createdAt

) {
}