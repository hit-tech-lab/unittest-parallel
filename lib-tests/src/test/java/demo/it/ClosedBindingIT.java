package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.ParallelTestException;
import java.math.BigDecimal;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterAll;
import com.hit.ptest.SuppressGuard;
import org.junit.jupiter.api.Test;

/** Tác vụ rò rỉ qua binding đã đóng: sau khi test kết thúc không được định tuyến vào mock cũ (6.6) mà phải lỗi rõ ràng. */
@SuppressGuard(rules = "PT06", reason = "one test method coordinates with its leaked task through static latch/outcome; no other test touches them")
class ClosedBindingIT extends MainIT {

    static final CountDownLatch RELEASE = new CountDownLatch(1);
    static final AtomicReference<Object> OUTCOME = new AtomicReference<>();
    static volatile Thread leaked;

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void startsATaskThatOutlivesTheTest() {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("6.6"));
        assertThat(primaryFx.rate("USD", "EUR")).as("control: inside the test the stub is routed").isEqualByComparingTo("6.6");
        leaked = new Thread(ParallelTest.wrap((Runnable) () -> {
            try {
                RELEASE.await(60, TimeUnit.SECONDS);
                OUTCOME.set(primaryFx.rate("USD", "EUR"));
            } catch (Throwable t) {
                OUTCOME.set(t);
            }
        }), "leaked-task");
        leaked.start();
    }

    @AfterAll
    static void leakedTaskRunsAfterTheTestBindingWasClosed() throws Exception {
        RELEASE.countDown();
        leaked.join(30_000);
        assertThat(OUTCOME.get()).as("must not return the dead test's mock value").isInstanceOf(ParallelTestException.UnboundThread.class);
        assertThat(((Throwable) OUTCOME.get()).getMessage()).contains("already closed");
    }
}
