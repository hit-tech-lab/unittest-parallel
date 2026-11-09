package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import demo.core.ExchangeRateClient;
import io.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/** Spy khai cấp class ở lớp cơ sở + handle ở lớp con: hai test song song, mỗi test một spy riêng, cùng context "main". */
class ClassLevelSpyIT extends ClassLevelSpyBaseIT {

    @PerTestSpy @Qualifier("primaryFx") ExchangeRateClient handle;

    @Test
    void firstTestStubsAndVerifiesItsOwnSpy() {
        doReturn(new BigDecimal("9.1")).when(handle).rate("X", "Y");
        meetInSameContext("class-spy", 2);

        assertThat(primaryFx.rate("X", "Y")).isEqualByComparingTo("9.1");
        verify(handle).rate("X", "Y");
    }

    @Test
    void secondTestNeverSeesTheFirstOnes() {
        doReturn(new BigDecimal("9.2")).when(handle).rate("X", "Y");
        meetInSameContext("class-spy", 2);

        assertThat(primaryFx.rate("X", "Y")).isEqualByComparingTo("9.2");
    }
}
