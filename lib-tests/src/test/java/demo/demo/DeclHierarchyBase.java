package demo.demo;

import demo.project.PricingService;
import demo.project.ProjectBaseIT;
import io.hit.ptest.PerTestMock;

/** Lớp cơ sở (không chạy): đã khai MOCK cho PricingService; lớp con khai SPY => MODE_CONFLICT theo phân cấp. */
abstract class DeclHierarchyBase extends ProjectBaseIT {
    @PerTestMock PricingService baseMock;
}
