package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.parallel.support.Rendezvous;
import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

/** Same context and same router bean as RoutedMockTransferIT, different stubbing, no lock. */
class RoutedMockFailureIT extends AbstractRoutedMockIT {

    @Test
    void sameScenarioAsTheConflictDemo_stubs3() {
        CrossClassConcurrency.assumeClassesShareJvm();
        Rendezvous.await("routed-stubbed-A", 2);
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("3"));
        Rendezvous.await("routed-stubbed-B", 2);
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");
        assertThat(transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN).credited()).isEqualByComparingTo("30");
        verify(exchangeRates).rate("USD", "EUR");
    }

    @RepeatedTest(10)
    void fxOutageRollsBackTransfer() {
        when(exchangeRates.rate("USD", "EUR")).thenThrow(new IllegalStateException("FX down"));
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");

        assertThatThrownBy(() -> transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN)).hasMessage("FX down");
        assertThat(accounts.findById(a.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100");
    }
}
