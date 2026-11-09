package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import demo.core.ExchangeRateClient;
import com.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Hai test SPY và một test REAL cùng bean {@code primaryFx}, cùng MỘT context, chạy ĐỒNG THỜI (cả ba hẹn nhau ở rendezvous rồi mới
 * gọi bean): mỗi test spy chỉ thấy stub và lời gọi của mình, test REAL luôn thấy bản thật.
 */
class ConcurrentSpyRealIT extends MainIT {

    @PerTestSpy @Qualifier("primaryFx") ExchangeRateClient spy;

    @Test
    void firstSpyStubsItsOwnPair() {
        doReturn(new BigDecimal("7.01")).when(spy).rate("USD", "JPY");
        meetInSameContext("spy-spy-real", 3);

        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("7.01");
        assertThat(primaryFx.rate("USD", "EUR")).as("not stubbed: the real bean").isEqualByComparingTo("1.10");
        verify(spy, times(1)).rate("USD", "JPY");
    }

    @Test
    void secondSpyStubsAnotherValueForTheSamePair() {
        doReturn(new BigDecimal("8.02")).when(spy).rate("USD", "JPY");
        meetInSameContext("spy-spy-real", 3);

        assertThat(primaryFx.rate("USD", "JPY")).isEqualByComparingTo("8.02");
        verify(spy, times(1)).rate("USD", "JPY");
    }

    @Test
    void aRealTestOnTheSameBeanSeesNeitherStubNorInteractions() {
        meetInSameContext("spy-spy-real", 3);

        assertThat(primaryFx.rate("USD", "JPY")).as("the real bean, whatever the spies stub").isEqualByComparingTo("1.10");
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10");
    }
}
