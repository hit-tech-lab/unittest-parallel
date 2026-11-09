package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import demo.core.RateController;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.web.context.WebApplicationContext;

/**
 * {@code webEnvironment = MOCK} (không có server, gọi controller trên chính thread test) chạy ĐỒNG THỜI với test RANDOM_PORT
 * ({@link WebRandomPortEnvIT}, context "main"): hai kiểu môi trường web, hai context, cùng một lần chạy song song.
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@ParallelSpringTest(context = "web-mock")
class WebMockEnvIT {

    @Autowired ApplicationContext ctx;
    @Autowired RateController controller;
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void mockEnvironmentHasNoServerAndStillRoutesToThisTestsMock() {
        assertThat(ctx).isInstanceOf(WebApplicationContext.class);
        assertThat(ctx.getEnvironment().getProperty("local.server.port")).as("MOCK: no embedded server").isNull();
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("5.5"));

        Rendezvous.meet("web-env", 2); // đối tác là test RANDOM_PORT đang chạy cùng lúc

        assertThat(controller.rate("USD", "EUR", "primary").getBody()).isEqualByComparingTo("5.5");
    }
}
