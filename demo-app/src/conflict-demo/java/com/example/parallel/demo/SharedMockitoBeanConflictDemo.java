package com.example.parallel.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.fx.ExchangeRateClient;
import com.example.parallel.support.Rendezvous;
import com.hittech.paralleltest.data.TestData;
import com.example.parallel.transfer.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * NEGATIVE CONTROL (expected to FAIL). Two concurrent tests stub the SAME @MockitoBean
 * (one singleton in the shared context) WITHOUT @ResourceLock.
 * Forced interleaving: A stubs 2 -> B stubs 3 -> both transfer. A gets B's rate.
 * Fix: @ResourceLock on all classes using the mock (AbstractMockitoBeanIT) or a thread-safe fake.
 */
@SpringBootTest
@ActiveProfiles("test")
class SharedMockitoBeanConflictDemo {

    @MockitoBean ExchangeRateClient exchangeRates;
    @Autowired TransferService transferService;
    @Autowired AccountRepository accounts;

    private BigDecimal transferTen() {
        Account a = accounts.save(new Account(TestData.unique("d"), "USD", new BigDecimal("100")));
        Account b = accounts.save(new Account(TestData.unique("d"), "EUR", BigDecimal.ZERO));
        return transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN).credited();
    }

    @Test
    void testA_stubsRate2() {
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        Rendezvous.await("mock-stubbed-A", 2);
        Rendezvous.await("mock-stubbed-B", 2);
        assertThat(transferTen()).isEqualByComparingTo("20");
        verify(exchangeRates).rate("USD", "EUR");
    }

    @Test
    void testB_stubsRate3() {
        Rendezvous.await("mock-stubbed-A", 2);
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("3"));
        Rendezvous.await("mock-stubbed-B", 2);
        assertThat(transferTen()).isEqualByComparingTo("30");
        verify(exchangeRates).rate("USD", "EUR");
    }
}
