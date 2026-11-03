package com.sagar.ledger_api.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.UUID;

public record CreateTransferRequest(

        @NotNull(message = "Source account ID is required")
        UUID sourceAccountId,

        @NotNull(message = "Destination account ID is required")
        UUID destinationAccountId,

        @NotNull(message = "Amount is required")
        @DecimalMin(
                value = "0.0001",
                message = "Amount must be greater than zero"
        )
        BigDecimal amount,

        @NotNull(message = "Currency is required")
        @Pattern(
                regexp = "^[A-Z]{3}$",
                message = "Currency must be a 3-letter uppercase code"
        )
        String currency

) {
}