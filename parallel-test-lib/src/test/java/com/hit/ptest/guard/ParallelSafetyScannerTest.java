package com.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.ptest.ParallelTestException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.core.SpringProperties;

/** Quét cả source set (giới hạn vào fixture): tìm hết vi phạm một lần, kể cả lớp tiện ích; cấu hình scan-exclude/FAIL/WARN. */
@Isolated
class ParallelSafetyScannerTest {

    private static final String FIXTURES = GuardFixtures.class.getName();

    @BeforeEach
    void configure() {
        SpringProperties.setProperty("ptest.guard.forbidden-calls", GuardFixtures.Repo.class.getName() + "#deleteAll()");
        System.setProperty("junit.jupiter.execution.parallel.enabled", "true");
        System.setProperty("junit.jupiter.execution.parallel.mode.default", "concurrent");
        JUnitConfig.reset();
    }

    @AfterEach
    void reset() {
        for (String k : List.of("ptest.guard.forbidden-calls", "ptest.guard.scan-exclude", "ptest.guard.PT01")) {
            SpringProperties.setProperty(k, null);
        }
        System.clearProperty("junit.jupiter.execution.parallel.enabled");
        System.clearProperty("junit.jupiter.execution.parallel.mode.default");
        JUnitConfig.reset();
    }

    @Test
    void scanFindsEveryRuleAcrossTheFixtureClassesIncludingHelpers() {
        List<Violation> v = ParallelSafetyScanner.scan(ParallelSafetyScannerTest.class, FIXTURES);
        assertThat(v).extracting(x -> x.rule().name()).contains("PT01", "PT03", "PT06", "PT07", "PT08", "PT13");
        assertThat(v).extracting(Violation::location).anySatisfy(l -> assertThat(l).contains("BadHelper.wipe("))
                .anySatisfy(l -> assertThat(l).contains("BadDirectCall.t("));
        // negative controls: nothing reported for the Good* fixtures
        assertThat(v).extracting(Violation::location).noneSatisfy(l -> assertThat(l).contains("Good"));
        assertThat(v).as("no duplicates").doesNotHaveDuplicates();
    }

    @Test
    void scanAndEnforceFailsWithEveryFailingRuleAndWarnOnlyRulesAreNotInTheFailure() {
        assertThatThrownBy(() -> {
            // PT01 is WARN by default: not part of the failure
            GuardEnforcer.enforce("fixtures", ParallelSafetyScanner.scan(ParallelSafetyScannerTest.class, FIXTURES + "$"));
        }).isInstanceOf(ParallelTestException.Guard.class).satisfies(e -> {
            List<String> codes = ((ParallelTestException.Guard) e).ruleCodes();
            assertThat(codes).contains("PT03", "PT06", "PT07", "PT08", "PT13").doesNotContain("PT01");
        });
    }

    @Test
    void excludedPackagesAreSkippedAndAnAllGoodSelectionPasses() {
        SpringProperties.setProperty("ptest.guard.scan-exclude", FIXTURES);
        assertThat(ParallelSafetyScanner.scan(ParallelSafetyScannerTest.class, FIXTURES)).isEmpty();
        SpringProperties.setProperty("ptest.guard.scan-exclude", null);
        assertThat(ParallelSafetyScanner.scan(ParallelSafetyScannerTest.class, FIXTURES + "$GoodTargetedCalls",
                FIXTURES + "$GoodStaticFinal", FIXTURES + "$GoodManagedSpringTest", FIXTURES + "$GoodPerMethodMutable",
                FIXTURES + "$GoodPerClassOnlyInjected", FIXTURES + "$GoodRandomPort")).isEmpty();
    }

    @Test
    void anchorAndItsSubclassesAreNotScanned() {
        assertThat(ParallelSafetyScanner.scan(ParallelSafetyScannerTest.class, ParallelSafetyScannerTest.class.getName())).isEmpty();
    }
}
