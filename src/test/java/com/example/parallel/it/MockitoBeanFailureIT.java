package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;

/** Same context as MockitoBeanTransferIT, but stubs the SAME method differently. */
class MockitoBeanFailureIT extends AbstractMockitoBeanIT {

    @RepeatedTest(5)
    void fxOutageRollsBackTransfer() {
        when(exchangeRates.rate("USD", "EUR")).thenThrow(new IllegalStateException("FX down"));
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");

        assertThatThrownBy(() -> transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN))
                .hasMessage("FX down");
        assertThat(accounts.findById(a.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100");
    }
}
