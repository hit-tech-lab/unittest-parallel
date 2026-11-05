package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.parallel.account.Account;
import com.example.parallel.audit.AuditLog;
import com.example.parallel.support.TestData;
import com.example.parallel.transfer.TransferService.TransferResult;
import java.math.BigDecimal;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpClientErrorException;

/** Real HTTP through the embedded server (random port) -> full stack, real transactions. */
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
        // The AuditLog singleton is shared by every concurrent test: filter by OUR accounts,
        // never assert on events().size().
        assertThat(auditLog.events()).filteredOn(e -> e.fromAccountId().equals(a.getId()))
                .extracting(AuditLog.Event::toAccountId).containsExactly(b.getId());
    }

    /**
     * Every repetition registers a DIFFERENT rate in the shared fake, but under its own unique
     * currency pair, so concurrent repetitions cannot see each other's "stubbing".
     */
    @RepeatedTest(15)
    void crossCurrencyWithIsolatedFakeRates() {
        String from = TestData.uniqueCurrency();
        String to = TestData.uniqueCurrency();
        BigDecimal rate = BigDecimal.valueOf(ThreadLocalRandom.current().nextInt(1, 1000), 2);
        fx.register(from, to, rate);
        Account a = newAccount(from, "100");
        Account b = newAccount(to, "0");

        TransferResult result = transfer(a, b, "10");

        assertThat(result.credited()).isEqualByComparingTo(rate.multiply(BigDecimal.TEN));
        assertThat(balanceOf(b)).isEqualByComparingTo(rate.multiply(BigDecimal.TEN));
        assertThat(fx.calls(from, to)).isEqualTo(1);
    }

    @Test
    void insufficientFundsIsRolledBack() {
        Account a = newAccount("USD", "5");
        Account b = newAccount("USD", "0");

        assertThatThrownBy(() -> transfer(a, b, "10"))
                .isInstanceOfSatisfying(HttpClientErrorException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        assertThat(balanceOf(a)).isEqualByComparingTo("5");
        assertThat(balanceOf(b)).isEqualByComparingTo("0");
    }
}
