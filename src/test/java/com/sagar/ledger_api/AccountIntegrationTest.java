
package com.sagar.ledger_api;

import com.sagar.ledger_api.dto.AccountResponse;
import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.service.AccountService;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class AccountIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AccountService accountService;

    @Test
    void shouldCreateAndRetrieveAccountFromDatabase() {

        String clientId = "integration-client-" + UUID.randomUUID();

        CreateAccountRequest request =
                new CreateAccountRequest("THB");

        AccountResponse created =
                accountService.createAccount(request, clientId);

        AccountResponse retrieved =
                accountService.getAccount(created.id(), clientId);

        assertNotNull(created.id());
        assertEquals(clientId, retrieved.ownerId());
        assertEquals("THB", retrieved.currency());
        assertEquals("0.0000", retrieved.balance().toPlainString());
        assertEquals("ACTIVE", retrieved.status());
    }
}
