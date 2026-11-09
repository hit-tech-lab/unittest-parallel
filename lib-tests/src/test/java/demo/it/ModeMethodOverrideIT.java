package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * Tham số của method ghi đè field cùng bean chỉ cho method đó: field là MOCK cho cả lớp, method thứ hai đổi sang SPY; hai method
 * chạy đồng thời. Field nhận CHÍNH handle của method (mock ở method 1, spy ở method 2).
 */
class ModeMethodOverrideIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient classMock;

    @Test
    void fieldMockAppliesWhenTheMethodDoesNotOverride() {
        when(classMock.rate("A", "B")).thenReturn(new BigDecimal("4.4"));
        meetInSameContext("mix", 6);
        assertThat(primaryFx.rate("A", "B")).isEqualByComparingTo("4.4");
    }

    @Test
    void parameterOverridesTheFieldModeOnlyForThisMethod(@PerTestSpy(name = "primaryFx") ExchangeRateClient spy) {
        meetInSameContext("mix", 6);
        assertThat(classMock).as("the field receives this method's handle").isSameAs(spy);
        assertThat(primaryFx.rate("A", "B")).as("spy calls the real bean").isEqualByComparingTo("1.10");
        verify(spy).rate("A", "B");
    }
}
