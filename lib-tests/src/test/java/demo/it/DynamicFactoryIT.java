package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;
import static org.mockito.Mockito.when;

import demo.core.ExchangeRateClient;
import demo.support.Rendezvous;
import io.hit.ptest.ParallelTest;
import io.hit.ptest.PerTestMock;
import java.math.BigDecimal;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.springframework.beans.factory.annotation.Qualifier;

/**
 * {@code @TestFactory}: binding và mock của lần gọi factory phủ cả các dynamic test của nó. Hai factory chạy đồng thời (hẹn nhau ngay trong
 * dynamic test), mỗi factory có mock riêng nên thấy đúng stub của mình.
 */
class DynamicFactoryIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient mock;

    @TestFactory
    Stream<DynamicTest> factoryOne() {
        return dynamicTests(1);
    }

    @TestFactory
    Stream<DynamicTest> factoryTwo() {
        return dynamicTests(2);
    }

    private Stream<DynamicTest> dynamicTests(int n) {
        BigDecimal mine = BigDecimal.valueOf(n);
        when(mock.rate("USD", "EUR")).thenReturn(mine);
        Rendezvous.share("dynamic:mocks", Integer.toString(System.identityHashCode(mock)));
        return Stream.of("first", "second").map(step -> dynamicTest(step + "-" + n, () -> {
            assertThat(ParallelTest.hasBinding()).as("dynamic test runs under the factory's binding").isTrue();
            Rendezvous.meet("dynamic-" + step, 2);
            assertThat(Rendezvous.shared("dynamic:mocks")).as("one mock per factory invocation").hasSize(2);
            assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo(mine);
        }));
    }

    /**
     * Container lồng nhau (có URI nguồn): thư viện dựng lại node nên lá ở mọi tầng vẫn phải chạy dưới binding của factory.
     * (Tên/URI của node được giữ nguyên: xem DynamicBindingsTest trong parallel-test-lib.)
     */
    @TestFactory
    Stream<DynamicNode> nestedContainersKeepBindingAndMetadata() {
        BigDecimal mine = BigDecimal.valueOf(7);
        when(mock.rate("USD", "EUR")).thenReturn(mine);
        java.net.URI source = java.net.URI.create("file:///nested-source");
        return Stream.<DynamicNode>of(dynamicContainer("outer", source, Stream.of(
                dynamicContainer("inner", Stream.of(dynamicTest("leaf", source, () -> {
                    assertThat(ParallelTest.hasBinding()).as("leaf of a nested container runs under the factory's binding").isTrue();
                    assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo(mine);
                }))))));
    }
}
