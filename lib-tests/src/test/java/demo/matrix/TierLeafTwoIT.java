package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Lá thứ hai dưới tầng C, không khai field: vẫn cùng context với {@link TierLeafOneIT}. */
class TierLeafTwoIT extends TierCBase {

    @Test
    void sharesTheContextOfTheOtherLeaf() {
        probe("c2");
        assertThat(ctx.containsBean("metricsSink")).isTrue();
    }
}
