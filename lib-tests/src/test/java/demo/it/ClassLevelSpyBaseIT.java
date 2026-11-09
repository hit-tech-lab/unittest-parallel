package demo.it;

import demo.core.ExchangeRateClient;
import io.hit.ptest.PerTestSpy;

/**
 * Lớp cơ sở khai {@code @PerTestSpy} CẤP CLASS: mỗi test của lớp con có sẵn một spy riêng của {@code primaryFx} (không đổi khóa
 * context, vẫn là context "main"); lấy handle bằng field {@code @PerTestSpy} ở lớp con.
 */
@PerTestSpy(name = "primaryFx", classes = ExchangeRateClient.class)
public abstract class ClassLevelSpyBaseIT extends MainIT {
}
