package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ModeMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void mockStubIsPrivateToThisTest() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("9.9"));
        meetInSameContext("mix", 6);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("9.9");
        verify(mock).rate("USD", "EUR");
    }
}
