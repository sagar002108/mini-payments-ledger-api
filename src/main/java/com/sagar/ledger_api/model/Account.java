package com.sagar.ledger_api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record Account(
        UUID id,
        String ownerId,
        String currency,
        BigDecimal balance,
        String status,
        String accountType,
        OffsetDateTime createdAt
) {
}