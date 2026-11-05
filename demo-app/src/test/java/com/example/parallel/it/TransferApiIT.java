package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.example.parallel.account.Account;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.transfer.TransferService.TransferResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

/** Real HTTP through the embedded server; mocks follow the request to the server thread. */
class TransferApiIT extends AbstractIntegrationTest {

    private TransferResult transfer(Account from, Account to, String amount) {
        return http().post().uri("/transfers").contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("fromAccountId", from.getId(), "toAccountId", to.getId(), "amount", new BigDecimal(amount)))
                .retrieve().body(TransferResult.class);
    }

    @Test
    void sameCurrency() {
        Account a = newAccount("USD", "100");
        Account b = newAccount("USD", "0");

        transfer(a, b, "30");

        assertThat(balanceOf(a)).isEqualByComparingTo("70");
        assertThat(balanceOf(b)).isEqualByComparingTo("30");
        verifyNoInteractions(exchangeRates);
        // AuditLog is a shared singleton: filter by OUR accounts (AuditLog#clear() is a forbidden call)
        assertThat(auditLog.events()).filteredOn(e -> e.fromAccountId().equals(a.getId()))
                .extracting(AuditLog.Event::toAccountId).containsExactly(b.getId());
    }

    /**
     * Every repetition stubs the SAME call (USD->EUR) with a DIFFERENT rate, concurrently, and the
     * call is made on a Tomcat thread: impossible with @MockitoBean, routine with @PerTestMock.
     */
    @RepeatedTest(15)
    void crossCurrencyThroughHttp() {
        BigDecimal rate = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 1000), 2);
        when(exchangeRates.rate("USD", "EUR")).thenReturn(rate);
        Account a = newAccount("USD", "100");
        Account b = newAccount("EUR", "0");

        TransferResult result = transfer(a, b, "10");

        assertThat(result.credited()).isEqualByComparingTo(rate.multiply(BigDecimal.TEN));
        verify(exchangeRates).rate("USD", "EUR"); // exactly once: no foreign invocations
    }

    @Test
    void fxOutageIsRolledBack() {
        when(exchangeRates.rate("USD", "EUR")).thenThrow(new IllegalStateException("FX down"));
        Account a = newAccount("USD", "100");
        Account b = newAccount("EUR", "0");

        assertThatThrownBy(() -> transfer(a, b, "10")).isInstanceOf(Exception.class);
        assertThat(balanceOf(a)).isEqualByComparingTo("100");
    }

    @Test
    void insufficientFundsIsRolledBack() {
        Account a = newAccount("USD", "5");
        Account b = newAccount("USD", "0");

        assertThatThrownBy(() -> transfer(a, b, "10"))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        assertThat(balanceOf(a)).isEqualByComparingTo("5");
    }
}
