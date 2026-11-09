package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** Đối tác RANDOM_PORT của {@link WebMockEnvIT}: hẹn nhau rồi gọi qua HTTP thật, nhận đúng stub của riêng mình (không phải 5.5 của bên kia). */
class WebRandomPortEnvIT extends HttpBase {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void randomPortRunsAlongsideMockEnvironment() {
        assertThat(port).isPositive();
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("6.6"));

        Rendezvous.meet("web-env", 2);

        assertThat(withBinding().get().uri("/rates/USD/EUR").retrieve().body(BigDecimal.class)).isEqualByComparingTo("6.6");
    }
}
