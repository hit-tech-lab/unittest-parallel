package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;

class MockitoBeanTransferIT extends AbstractMockitoBeanIT {

    @RepeatedTest(5)
    void usesStubbedRate() {
        when(exchangeRates.rate("USD", "EUR")).thenReturn(new BigDecimal("0.5"));
        var a = newAccount("USD", "100");
        var b = newAccount("EUR", "0");

        var result = transferService.transfer(a.getId(), b.getId(), BigDecimal.TEN);

        assertThat(result.credited()).isEqualByComparingTo("5");
        verify(exchangeRates).rate("USD", "EUR"); // exactly once -> would break if another test used the mock now
    }
}
