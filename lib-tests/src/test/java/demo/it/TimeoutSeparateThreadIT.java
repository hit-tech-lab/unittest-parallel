package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import com.hit.ptest.ParallelTest;
import com.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * {@code @Timeout(threadMode = SEPARATE_THREAD)} chạy method test trên thread khác thread đã gắn binding: thư viện gắn lại binding ở
 * đó, nên mock của test vẫn có hiệu lực (không rơi vào chính sách thread không binding) và hai test như vậy không thấy mock của nhau.
 */
class TimeoutSeparateThreadIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void firstMockIsSeenOnTheTimeoutThread() {
        when(mock.rate("USD", "JPY")).thenReturn(new BigDecimal("5.5"));
        String thread = Thread.currentThread().getName();
        meetInSameContext("timeout-thread", 2);

        assertThat(ParallelTest.hasBinding()).as("binding re-attached on thread " + thread).isTrue();
        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("5.5");
        verify(mock).rate("USD", "JPY");
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS, threadMode = Timeout.ThreadMode.SEPARATE_THREAD)
    void secondMockIsIndependentOfTheFirst() {
        when(mock.rate("USD", "JPY")).thenReturn(new BigDecimal("6.6"));
        meetInSameContext("timeout-thread", 2);

        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("6.6");
    }
}
