package demo.matrix;

import demo.project.Ports;
import com.hit.ptest.PerTestMock;

/** Tầng 2: thêm mock cấp class {@link Ports.FeatureFlags}. */
@PerTestMock(Ports.FeatureFlags.class)
abstract class TierBBase extends TierABase {
}
