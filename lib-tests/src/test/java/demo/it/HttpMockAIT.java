package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class HttpMockAIT extends HttpBase {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void serverThreadRoutesToThisTestsMock() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("7.7"));
        meetInSameContext("http", 3);
        ResponseEntity<BigDecimal> response = withBinding().get().uri("/rates/USD/EUR").retrieve().toEntity(BigDecimal.class);
        assertThat(response.getBody()).isEqualByComparingTo("7.7");
        assertThat(response.getHeaders().getFirst("X-Thread")).as("handled on a server thread").isNotEqualTo(Thread.currentThread().getName());
        verify(mock).rate("USD", "EUR");
        Rendezvous.meet("http-done", 3);
    }
}
