package com.example.parallel.unit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountNotFoundException;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.account.InsufficientFundsException;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.fx.ExchangeRateClient;
import com.example.parallel.transfer.TransferService;
import java.math.BigDecimal;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Pure unit test: no Spring context, no DB. Methods of this class run concurrently.
 * Safe because JUnit creates a NEW test instance per method (PER_METHOD lifecycle),
 * so every method gets its own @Mock objects -- nothing is shared.
 */
@ExtendWith(MockitoExtension.class)
class TransferServiceTest {

    @Mock AccountRepository accounts;
    @Mock ExchangeRateClient exchangeRates;
    @Mock AuditLog auditLog;
    @InjectMocks TransferService service;

    private static Account account(long id, String currency, String balance) {
        Account a = new Account("owner-" + id, currency, new BigDecimal(balance));
        ReflectionTestUtils.setField(a, "id", id);
        return a;
    }

    private void given(Account... as) {
        for (Account a : as) {
            when(accounts.findForUpdate(a.getId())).thenReturn(Optional.of(a));
        }
    }

    @Test
    void sameCurrencyTransferDoesNotCallFx() {
        Account from = account(1, "USD", "100");
        Account to = account(2, "USD", "0");
        given(from, to);

        var result = service.transfer(1L, 2L, new BigDecimal("40"));

        assertThat(result.credited()).isEqualByComparingTo("40");
        assertThat(from.getBalance()).isEqualByComparingTo("60");
        assertThat(to.getBalance()).isEqualByComparingTo("40");
        verifyNoInteractions(exchangeRates);
        verify(auditLog).record(any());
    }

    @Test
    void crossCurrencyTransferUsesRate() {
        given(account(1, "USD", "100"), account(2, "EUR", "0"));
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("0.9"));

        var result = service.transfer(1L, 2L, new BigDecimal("10"));

        assertThat(result.credited()).isEqualByComparingTo("9");
    }

    @Test
    void insufficientFundsLeavesBalancesUntouched() {
        Account from = account(1, "USD", "5");
        Account to = account(2, "USD", "0");
        given(from, to);

        assertThatThrownBy(() -> service.transfer(1L, 2L, new BigDecimal("10")))
                .isInstanceOf(InsufficientFundsException.class);
        assertThat(from.getBalance()).isEqualByComparingTo("5");
        assertThat(to.getBalance()).isEqualByComparingTo("0");
        verify(auditLog, never()).record(any());
    }

    @Test
    void locksAccountsInIdOrderToAvoidDeadlocks() {
        given(account(1, "USD", "0"), account(2, "USD", "100"));

        service.transfer(2L, 1L, BigDecimal.ONE);

        InOrder order = inOrder(accounts);
        order.verify(accounts).findForUpdate(1L);
        order.verify(accounts).findForUpdate(2L);
    }

    @Test
    void unknownAccount() {
        when(accounts.findForUpdate(1L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.transfer(1L, 2L, BigDecimal.ONE))
                .isInstanceOf(AccountNotFoundException.class);
    }

    @Test
    void rejectsInvalidInput() {
        assertThatThrownBy(() -> service.transfer(1L, 2L, BigDecimal.ZERO)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.transfer(1L, 1L, BigDecimal.ONE)).isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(accounts, exchangeRates, auditLog);
    }

    /**
     * 50 repetitions running concurrently with each other and with the tests above, each stubbing
     * the SAME method with a DIFFERENT value. If mocks leaked between tests, some repetition would
     * read another repetition's rate and fail.
     */
    @RepeatedTest(50)
    void concurrentRepetitionsSeeOnlyTheirOwnStubbing() throws InterruptedException {
        int cents = ThreadLocalRandom.current().nextInt(1, 10_000);
        BigDecimal rate = BigDecimal.valueOf(cents, 2);
        given(account(1, "USD", "100"), account(2, "EUR", "0"));
        when(exchangeRates.rate("USD", "EUR")).thenReturn(rate);

        Thread.sleep(ThreadLocalRandom.current().nextInt(5)); // widen the race window

        var result = service.transfer(1L, 2L, BigDecimal.ONE);
        assertThat(result.credited()).isEqualByComparingTo(rate);
        verify(exchangeRates).rate("USD", "EUR"); // exactly once: no foreign invocations either
    }
}
