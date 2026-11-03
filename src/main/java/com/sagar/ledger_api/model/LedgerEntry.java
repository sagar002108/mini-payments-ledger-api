package com.sagar.ledger_api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record LedgerEntry(

        UUID id,
        UUID transferId,
        UUID accountId,
        String entryType,
        BigDecimal amount,
        String currency,
        OffsetDateTime createdAt

) {
}