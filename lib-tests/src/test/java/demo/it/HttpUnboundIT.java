package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.client.HttpServerErrorException;

/**
 * Request KHÔNG mang header binding: phải lỗi UNBOUND_THREAD rõ ràng (không chỉ 500 bất kỳ) cho bean mà test này mock; request có
 * header được định tuyến tới mock của test. Bean mà không test nào mock vẫn là bean thật (chính sách mặc định GUARDED).
 */
class HttpUnboundIT extends HttpBase {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void requestWithoutBindingFailsWithUnboundThreadWhileHeaderRoutesIt() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("2.5"));
        meetInSameContext("http", 3);
        assertThatThrownBy(() -> withoutBinding().get().uri("/rates/USD/EUR").retrieve().toBodilessEntity())
                .isInstanceOfSatisfying(HttpServerErrorException.class, e -> assertThat(e.getResponseBodyAsString())
                        .as("server-side cause, not just a 500").contains("UNBOUND_THREAD").contains("primaryFx"));
        BigDecimal viaHeader = withBinding().get().uri("/rates/USD/EUR?via=primary").retrieve().toEntity(BigDecimal.class).getBody();
        assertThat(viaHeader).as("header routes the server thread to this test's mock").isEqualByComparingTo("2.5");
        Rendezvous.meet("http-done", 3);
    }
}
