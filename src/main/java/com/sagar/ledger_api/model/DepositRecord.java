package com.sagar.ledger_api.model;

import com.sagar.ledger_api.dto.DepositResponse;

public record DepositRecord(
        DepositResponse deposit,
        String requestHash
) {
}