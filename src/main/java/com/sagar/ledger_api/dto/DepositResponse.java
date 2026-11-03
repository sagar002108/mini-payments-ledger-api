package com.sagar.ledger_api.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record DepositResponse(

        UUID id,
        UUID accountId,
        BigDecimal amount,
        String currency,
        String status,
        OffsetDateTime createdAt

) {
}
