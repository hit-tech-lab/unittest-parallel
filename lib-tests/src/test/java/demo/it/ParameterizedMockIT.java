package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import com.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * {@code @ParameterizedTest}: tham số {@code @PerTestMock} đặt SAU các tham số do nguồn đối số cấp (JUnit chỉ giao cho resolver của nó các
 * chỉ số có đối số), nên không có hai ParameterResolver cạnh tranh; mỗi lần gọi là một test với mock mới và binding riêng.
 */
class ParameterizedMockIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient fieldMock;

    @ParameterizedTest
    @ValueSource(strings = {"2.5", "3.5", "4.5"})
    void eachInvocationHasItsOwnMock(String value, @PerTestMock(name = "primaryFx") ExchangeRateClient mock) {
        when(mock.rate("A", "B")).thenReturn(new BigDecimal(value));

        assertThat(fieldMock).as("the field receives this invocation's handle").isSameAs(mock);
        assertThat(primaryFx.rate("A", "B")).isEqualByComparingTo(value);
        verify(mock).rate("A", "B");
    }
}
