package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import demo.project.Ports;
import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Lá dưới tầng C, có field handle; thấy đủ mock của cả ba tầng. */
class TierLeafOneIT extends TierCBase {

    @PerTestMock Ports.FeatureFlags flags;

    @Test
    void sees3TiersOfDeclarations() {
        when(flags.enabled("x")).thenReturn(true);
        probe("c1");
        assertThat(ctx.containsBean("featureFlags")).isTrue();
        assertThat(ctx.containsBean("metricsSink")).isTrue();
        assertThat(flags.enabled("x")).isTrue();
    }
}
