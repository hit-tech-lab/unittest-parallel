package demo.matrix;

import demo.project.Ports;
import io.hit.ptest.PerTestMock;

/** Tầng 3: thêm mock cấp class {@link Ports.MetricsSink}. */
@PerTestMock(Ports.MetricsSink.class)
abstract class TierCBase extends TierBBase {
}
