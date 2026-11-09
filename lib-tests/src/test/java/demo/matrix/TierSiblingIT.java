package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lá chỉ dưới tầng B (không có MetricsSink): tập khai báo khác nên context khác với hai lá dưới tầng C. */
class TierSiblingIT extends TierBBase {

    @Test
    void hasAnotherContextWithoutTheThirdTier() {
        probe("b");
        assertThat(ctx.containsBean("featureFlags")).isTrue();
        assertThat(ctx.containsBean("metricsSink")).isFalse();
    }
}
