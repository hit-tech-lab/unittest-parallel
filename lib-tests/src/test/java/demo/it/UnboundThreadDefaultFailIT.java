package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import io.hit.ptest.ParallelTest;
import io.hit.ptest.ParallelTestException;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** D: thread không binding gọi bean đang bị mock (chính sách mặc định GUARDED) lỗi rõ ràng; wrap để truyền binding. */
class UnboundThreadDefaultFailIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    void rawThreadFailsLoudlyAndWrappedThreadIsRouted() throws Exception {
        when(mock.rate("USD", "EUR")).thenReturn(new BigDecimal("6.6"));

        // 1. new Thread không binding => lỗi cấu hình rõ ràng (không âm thầm gọi bean thật)
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread raw = new Thread(() -> {
            try {
                primaryFx.rate("USD", "EUR");
            } catch (Throwable t) {
                error.set(t);
            }
        }, "raw-thread");
        raw.start();
        raw.join();
        assertThat(error.get()).isInstanceOf(ParallelTestException.UnboundThread.class).hasMessageContaining("UNBOUND_THREAD");

        // 2. ForkJoin common pool (CompletableFuture mặc định) cũng không binding
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get())
                .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);

        // 3. wrap(Runnable/Callable/Executor) truyền binding
        assertThat(CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR"), ParallelTest.wrap(Runnable::run)).get())
                .isEqualByComparingTo("6.6");
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            assertThat(CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR"), ParallelTest.wrap(pool)).get())
                    .isEqualByComparingTo("6.6");
        } finally {
            pool.shutdownNow();
        }
    }

    /**
     * Mặc định GUARDED vì mọi bean ứng dụng đều được bọc: bean mà không test nào mock/spy (untouchedService) vẫn là bean THẬT cho
     * thread không binding; bean mà test này vừa mock thì bị từ chối.
     */
    @Test
    void defaultPolicyOnlyRejectsBeansSomeTestMocked() throws Exception {
        assertThat(CompletableFuture.supplyAsync(() -> untouchedService.hello()).get()).isEqualTo("hello");
        assertThatThrownBy(() -> CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get())
                .isInstanceOf(ExecutionException.class).hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);
    }

    /** toString của wrapper (log, message lỗi) không bao giờ ném trên thread không binding, cả nhánh mock lẫn JDK proxy. */
    @Test
    void toStringOnUnboundThreadNeverThrows() throws Exception {
        assertThat(CompletableFuture.supplyAsync(() -> String.valueOf(primaryFx)).get()).isEqualTo("Interceptor[primaryFx]");
        assertThat(CompletableFuture.supplyAsync(() -> String.valueOf(accounts)).get()).isEqualTo("Interceptor[accountRepository]");
    }
}
