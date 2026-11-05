package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.parallel.support.Rendezvous;
import com.example.parallel.transfer.TransferService;
import com.hittech.paralleltest.mock.PerTestMocks;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

class TransferServiceIT extends AbstractIntegrationTest {

    @Autowired TransferService transferService;
    @Autowired MockMvc mvc;

    private BigDecimal transferTenUsdToEur() {
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");
        return transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN).credited();
    }

    /**
     * The exact interleaving that breaks SharedMockitoBeanConflictDemo (A stubs 2, B stubs 3 on the same
     * bean, then both call it) passes here. The rendezvous proves both tests run at the same time.
     */
    @Test
    void sameInterleavingAsConflictDemo_A() {
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("2"));
        Rendezvous.await("svc-stubbed-A", 2);
        Rendezvous.await("svc-stubbed-B", 2);
        assertThat(transferTenUsdToEur()).isEqualByComparingTo("20");
        verify(exchangeRates).rate("USD", "EUR");
    }

    @Test
    void sameInterleavingAsConflictDemo_B() {
        Rendezvous.await("svc-stubbed-A", 2);
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("3"));
        Rendezvous.await("svc-stubbed-B", 2);
        assertThat(transferTenUsdToEur()).isEqualByComparingTo("30");
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

    @Test
    void asyncWorkCarriesTheMocksWhenWrapped() throws Exception {
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("4"));
        assertThat(CompletableFuture.supplyAsync(PerTestMocks.wrapSupplier(this::transferTenUsdToEur)).get())
                .isEqualByComparingTo("40");
    }

    @Test
    void unwrappedAsyncWorkFailsLoudlyInsteadOfUsingAnotherTestsMock() {
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(this::transferTenUsdToEur).get())
                .isInstanceOf(ExecutionException.class)
                .rootCause().hasMessageContaining("belongs to no test");
    }
}
