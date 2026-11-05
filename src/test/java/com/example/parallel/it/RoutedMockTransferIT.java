package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.parallel.support.Rendezvous;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class RoutedMockTransferIT extends AbstractRoutedMockIT {

    @Autowired MockMvc mvc;

    /**
     * The exact interleaving that breaks SharedMockitoBeanConflictDemo -- A stubs 2, B (another
     * class, same context, same bean) stubs 3, both transfer -- now passes for both, without locks.
     * The rendezvous also PROVES the two classes run at the same time.
     */
    @Test
    void sameScenarioAsTheConflictDemo_stubs2() {
        CrossClassConcurrency.assumeClassesShareJvm();
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        Rendezvous.await("routed-stubbed-A", 2);
        Rendezvous.await("routed-stubbed-B", 2);
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");
        assertThat(transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN).credited()).isEqualByComparingTo("20");
        verify(exchangeRates).rate("USD", "EUR");
    }

    @RepeatedTest(10)
    void throughMockMvc() throws Exception {
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("0.5"));
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");

        mvc.perform(post("/transfers").contentType(MediaType.APPLICATION_JSON).content(
                        """
                        {"fromAccountId":%d,"toAccountId":%d,"amount":10}""".formatted(a.getId(), b.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credited").value(5));
        verify(exchangeRates).rate("USD", "EUR");
    }

    /** The limitation is detected loudly instead of silently using another test's mock. */
    @Test
    void callFromAnotherThreadFailsLoudly() {
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");
        assertThatThrownBy(() -> CompletableFuture
                .supplyAsync(() -> transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN)).get())
                .isInstanceOf(ExecutionException.class)
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .rootCause().hasMessageContaining("No @PerTestMock ExchangeRateClient bound to thread");
    }
}
