package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.FinalPricer;
import demo.support.Rendezvous;
import io.hit.ptest.PerTestMock;
import io.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** E: bean là lớp final, tiêm theo lớp cụ thể, ba chế độ chạy đồng thời. */
class FinalClassIT extends MainIT {

    @Test
    void mockFinal(@PerTestMock FinalPricer mock) {
        when(mock.price(BigDecimal.TEN)).thenReturn(BigDecimal.ONE);
        Rendezvous.meet("final", 3);
        assertThat(finalPricer.price(BigDecimal.TEN)).isEqualByComparingTo("1");
    }

    @Test
    void spyFinal(@PerTestSpy FinalPricer spy) {
        Rendezvous.meet("final", 3);
        assertThat(finalPricer.price(BigDecimal.TEN)).isEqualByComparingTo("12.0");
        verify(spy).price(BigDecimal.TEN);
    }

    @Test
    void realFinal() {
        Rendezvous.meet("final", 3);
        assertThat(finalPricer.price(BigDecimal.TEN)).isEqualByComparingTo("12.0");
    }
}
