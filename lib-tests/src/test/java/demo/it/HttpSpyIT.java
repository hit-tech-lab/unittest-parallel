package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import io.hit.ptest.PerTestSpy;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

/** 7: thread Tomcat nhận binding qua header và tới đúng spy của test (stub một phần, còn lại gọi thật). */
class HttpSpyIT extends HttpBase {

    @PerTestSpy @Qualifier("primaryFx") ExchangeRateClient spy;

    private void callOverHttp(String to, String stub) {
        doReturn(new BigDecimal(stub)).when(spy).rate("USD", to);
        meetInSameContext("http-spy", 2);
        ResponseEntity<BigDecimal> stubbed = withBinding().get().uri("/rates/USD/" + to).retrieve().toEntity(BigDecimal.class);
        ResponseEntity<BigDecimal> real = withBinding().get().uri("/rates/USD/OTHER").retrieve().toEntity(BigDecimal.class);
        assertThat(stubbed.getBody()).isEqualByComparingTo(stub);
        assertThat(real.getBody()).as("not stubbed: spy calls the real method").isEqualByComparingTo("1.10");
        assertThat(stubbed.getHeaders().getFirst("X-Thread")).isNotEqualTo(Thread.currentThread().getName());
        verify(spy, times(1)).rate("USD", to);
        verify(spy, times(1)).rate("USD", "OTHER");
        Rendezvous.meet("http-spy-done", 2);
    }

    @Test
    void serverThreadRoutesToSpyOne() {
        callOverHttp("EUR", "7.7");
    }

    @Test
    void serverThreadRoutesToSpyTwo() {
        callOverHttp("GBP", "8.8");
    }
}
