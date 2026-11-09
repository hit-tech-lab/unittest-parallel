package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * {@code @RepeatedTest} + {@code @PerTestMock}: mỗi lần lặp là một test riêng với mock MỚI. Cả 4 lần lặp hẹn nhau ở rào chắn (chứng minh chạy
 * đồng thời), rồi mỗi lần vẫn thấy đúng giá trị stub của mình và mock của 4 lần là 4 đối tượng khác nhau.
 */
class RepeatedMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @RepeatedTest(4)
    void eachRepetitionHasItsOwnMock(RepetitionInfo info) {
        BigDecimal mine = BigDecimal.valueOf(info.getCurrentRepetition());
        when(mock.rate("USD", "EUR")).thenReturn(mine);
        Rendezvous.share("repeated:mocks", Integer.toString(System.identityHashCode(mock)));

        Rendezvous.meet("repeated", 4);

        assertThat(Rendezvous.shared("repeated:mocks")).as("one distinct mock per repetition").hasSize(4);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo(mine);
        verify(mock, times(1)).rate("USD", "EUR");
    }
}
