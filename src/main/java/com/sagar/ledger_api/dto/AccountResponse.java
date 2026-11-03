package com.sagar.ledger_api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record AccountResponse(
        UUID id,
        String ownerId,
        String currency,
        BigDecimal balance,
        String status,
        OffsetDateTime createdAt
) {
}