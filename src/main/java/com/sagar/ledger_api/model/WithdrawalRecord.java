package com.sagar.ledger_api.model;

import com.sagar.ledger_api.dto.WithdrawalResponse;

public record WithdrawalRecord(
        WithdrawalResponse withdrawal,
        String requestHash
) {}