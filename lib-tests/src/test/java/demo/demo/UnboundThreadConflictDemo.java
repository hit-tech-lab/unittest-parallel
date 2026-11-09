package demo.demo;

import demo.it.MainIT;
import io.hit.ptest.PerTestMock;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;

/** Negative control D: thread không binding gọi bean đang MOCK mà không wrap => UNBOUND_THREAD, không lệch âm thầm. */
class UnboundThreadConflictDemo extends MainIT {
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void commonPoolIsNotBound() throws Exception {
        CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get();
    }
}
