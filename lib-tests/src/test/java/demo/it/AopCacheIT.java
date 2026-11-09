package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.ExchangeRateClient;
import demo.core.RateCache;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** E: proxy @Cacheable BỌC NGOÀI spy (như @MockitoSpyBean): lần hai do cache trả lời nên spy thấy 1 lời gọi; primaryFx mock chỉ bị gọi 1 lần. */
class AopCacheIT extends MainIT {

    @PerTestSpy RateCache cacheSpy;
    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient fxMock;

    @Test
    void cacheProxyIsAboveTheSpy() {
        String to = "T" + UUID.randomUUID();   // khóa riêng: cache là state singleton dùng chung
        when(fxMock.rate("USD", to)).thenReturn(new BigDecimal("5.5"));
        assertThat(rateCache.cachedRate("USD", to)).isEqualByComparingTo("5.5");
        assertThat(rateCache.cachedRate("USD", to)).isEqualByComparingTo("5.5");
        verify(cacheSpy, times(1)).cachedRate("USD", to);
        verify(fxMock, times(1)).rate("USD", to);
        assertThat(rateCache.computations("USD", to)).isEqualTo(1);
    }
}
