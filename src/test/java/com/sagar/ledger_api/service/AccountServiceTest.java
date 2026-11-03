
package com.sagar.ledger_api.service;

import com.sagar.ledger_api.dto.CreateAccountRequest;
import com.sagar.ledger_api.model.Account;
import com.sagar.ledger_api.repository.AccountRepository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @InjectMocks
    private AccountService accountService;

    @Test
    void shouldReturnAccountWhenClientOwnsAccount() {

        UUID id = UUID.randomUUID();

        Account account = new Account(
                id,
                "client-001",
                "THB",
                new BigDecimal("100.0000"),
                "ACTIVE",
                "CUSTOMER",
                OffsetDateTime.now()
        );

        when(accountRepository.findById(id)).thenReturn(account);

        var result = accountService.getAccount(id, "client-001");

        assertEquals(id, result.id());
        assertEquals("client-001", result.ownerId());
        assertEquals("THB", result.currency());

        verify(accountRepository).findById(id);
    }

    @Test
    void shouldRejectAccessWhenAccountBelongsToAnotherClient() {

        UUID id = UUID.randomUUID();

        Account account = new Account(
                id,
                "client-001",
                "THB",
                new BigDecimal("100.0000"),
                "ACTIVE",
                "CUSTOMER",
                OffsetDateTime.now()
        );

        when(accountRepository.findById(id)).thenReturn(account);

        assertThrows(
                ResponseStatusException.class,
                () -> accountService.getAccount(id, "client-002")
        );
    }

    @Test
    void shouldThrowExceptionWhenAccountDoesNotExist() {

        UUID id = UUID.randomUUID();

        when(accountRepository.findById(id)).thenReturn(null);

        assertThrows(
                ResponseStatusException.class,
                () -> accountService.getAccount(id, "client-001")
        );
    }

    @Test
    void shouldAssignAuthenticatedClientAsAccountOwner() {

        CreateAccountRequest request =
                new CreateAccountRequest("INR");

        when(accountRepository.create(any(Account.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var result = accountService.createAccount(
                request,
                "authenticated-client"
        );

        assertNotNull(result.id());
        assertEquals("authenticated-client", result.ownerId());
        assertNotEquals("fake-client", result.ownerId());
        assertEquals("INR", result.currency());
        assertEquals(0, result.balance().compareTo(BigDecimal.ZERO));
        assertEquals("ACTIVE", result.status());

        verify(accountRepository).create(any(Account.class));
    }
}
