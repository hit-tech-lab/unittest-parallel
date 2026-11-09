package io.hit.ptest.guard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.guard.GuardFixtures.*;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.core.SpringProperties;

/**
 * Luật tĩnh: mỗi lớp Bad* phải bị bắt ĐÚNG luật (kèm vị trí), mỗi lớp Good* (cùng mã nhưng đúng) không được báo nhầm.
 * PT07 dùng kiểu mẫu (Repo) qua ptest.guard.forbidden-calls vì Spring Data không nằm trong classpath của thư viện;
 * danh sách mặc định được kiểm tra riêng (GuardSettingsTest) và chạy với repository thật ở lib-tests.
 */
@Isolated
class ClassRulesTest {

    @BeforeEach
    void configure() {
        String repo = GuardFixtures.Repo.class.getName();
        SpringProperties.setProperty("ptest.guard.forbidden-calls", repo + "#deleteAll(), " + repo + "#count()");
        System.setProperty("junit.jupiter.execution.parallel.enabled", "true");
        System.setProperty("junit.jupiter.execution.parallel.mode.default", "concurrent");
        JUnitConfig.reset();
    }

    @AfterEach
    void reset() {
        SpringProperties.setProperty("ptest.guard.forbidden-calls", null);
        System.clearProperty("junit.jupiter.execution.parallel.enabled");
        System.clearProperty("junit.jupiter.execution.parallel.mode.default");
        JUnitConfig.reset();
    }

    private static List<String> rules(Class<?> c) {
        return ClassRules.check(c).stream().map(v -> v.rule().name()).toList();
    }

    @io.hit.ptest.SuppressGuard(rules = "PT07", reason = " ")
    static class BlankReason {
        @Test
        void t() {
        }
    }

    // ---- PT07 ----

    @Test
    void forbiddenDirectCallIsReportedWithClassMethodAndLine() {
        List<Violation> v = ClassRules.check(BadDirectCall.class);
        assertThat(v).hasSize(1);
        assertThat(v.get(0).rule()).isEqualTo(GuardRule.PT07);
        assertThat(v.get(0).location()).contains("GuardFixtures.BadDirectCall.t(GuardFixtures.java:").doesNotContain(":-1");
        assertThat(v.get(0).detail()).contains("calls").contains("Repo.deleteAll").contains("forbidden:");
    }

    @Test
    void forbiddenMethodReferenceLambdaNestedAndAnonymousClassesAreAllFound() {
        assertThat(ClassRules.check(BadMethodReference.class)).singleElement()
                .satisfies(v -> assertThat(v.detail()).contains("references").contains("deleteAll"));
        assertThat(ClassRules.check(BadInLambdaAndNested.class)).extracting(Violation::detail)
                .anySatisfy(d -> assertThat(d).contains("Repo.count")).anySatisfy(d -> assertThat(d).contains("Repo.deleteAll"));
        assertThat(ClassRules.check(BadAnonymous.class)).hasSize(1);
    }

    @Test
    void targetedCallsUnrelatedReceiversIsolatedAndSuppressedClassesAreNotReported() {
        assertThat(rules(GoodTargetedCalls.class)).isEmpty();
        assertThat(rules(GoodIsolatedDeleteAll.class)).isEmpty();
        assertThat(rules(GoodSuppressedDeleteAll.class)).isEmpty();
    }

    @Test
    void suppressingAnotherRuleDoesNotHideTheViolation() {
        assertThat(rules(BadSuppressedWrongRule.class)).containsExactly("PT07");
    }

    @Test
    void suppressionWithoutAReasonIsAConfigurationError() {
        assertThatThrownBy(() -> ClassRules.check(BlankReason.class)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("needs a non-blank reason");
    }

    @Test
    void helperClassIsScannedForForbiddenCalls() {
        assertThat(ClassRules.checkHelper(BadHelper.class)).singleElement()
                .satisfies(v -> assertThat(v.location()).contains("BadHelper.wipe("));
        assertThat(ClassRules.checkHelper(GoodTargetedCalls.class)).isEmpty();
    }

    // ---- PT06 / PT03 / PT08 ----

    @Test
    void mutableStaticFieldsAreReportedButFinalOnesAreNot() {
        assertThat(ClassRules.check(BadStaticField.class)).extracting(Violation::rule).containsOnly(GuardRule.PT06).hasSize(2);
        assertThat(ClassRules.check(BadStaticField.class)).extracting(Violation::location)
                .anySatisfy(l -> assertThat(l).endsWith("BadStaticField.counter"))
                .anySatisfy(l -> assertThat(l).endsWith("BadStaticField.seen"));
        assertThat(rules(GoodStaticFinal.class)).isEmpty();
        assertThat(rules(StaticInjectedByJUnit.class)).as("@TempDir/@RegisterExtension static do JUnit tiêm: không PT06").isEmpty();
    }

    @Test
    void sharedMockitoBeansSuggestPerTestMockAndIsolatedClassesAreExempt() {
        List<Violation> v = ClassRules.check(BadMockitoBean.class);
        assertThat(v).extracting(Violation::rule).containsOnly(GuardRule.PT03).hasSize(2);
        assertThat(v.get(0).rule().fix()).contains("@PerTestMock/@PerTestSpy");
        assertThat(rules(GoodIsolatedMockitoBean.class)).isEmpty();
    }

    @Test
    void definedPortIsReportedRandomPortIsNot() {
        assertThat(rules(BadDefinedPort.class)).containsExactly("PT08");
        assertThat(rules(GoodRandomPort.class)).isEmpty();
    }

    // ---- PT13 ----

    @Test
    void perClassWithMutableFieldsAndConcurrentMethodsIsReportedListingTheFields() {
        assertThat(ClassRules.check(BadPerClassMutable.class)).singleElement().satisfies(v -> {
            assertThat(v.rule()).isEqualTo(GuardRule.PT13);
            assertThat(v.detail()).contains("BadPerClassMutable.last").doesNotContain("injected");
        });
    }

    @Test
    void perClassIsFineWithOnlyInjectedOrFinalFieldsSameThreadOrPerMethodLifecycle() {
        assertThat(rules(GoodPerClassOnlyInjected.class)).isEmpty();
        assertThat(rules(GoodPerClassSameThread.class)).isEmpty();
        assertThat(rules(GoodPerMethodMutable.class)).isEmpty();
    }

    @Test
    void perClassIsFineWhenMethodsAreNotRunConcurrently() {
        System.setProperty("junit.jupiter.execution.parallel.mode.default", "same_thread");
        JUnitConfig.reset();
        assertThat(rules(BadPerClassMutable.class)).isEmpty();
        System.clearProperty("junit.jupiter.execution.parallel.enabled");
        JUnitConfig.reset();
        System.setProperty("junit.jupiter.execution.parallel.mode.default", "concurrent");
        assertThat(rules(BadPerClassMutable.class)).as("parallel disabled").isEmpty();
    }

    @Test
    void methodsMayRunConcurrentlyFollowsJUnitModeAndForcedSameThread() {
        assertThat(ClassRules.methodsMayRunConcurrently(BadPerClassMutable.class)).isTrue();
        assertThat(ClassRules.methodsMayRunConcurrently(GoodPerClassSameThread.class)).isFalse();
        System.clearProperty("junit.jupiter.execution.parallel.enabled");
        JUnitConfig.reset();
        assertThat(ClassRules.methodsMayRunConcurrently(BadPerClassMutable.class)).as("parallel disabled").isFalse();
    }

    // ---- PT01 ----

    @Test
    void springTestWithoutParallelSpringTestIsReportedAndManagedOneIsNot() {
        assertThat(ClassRules.checkManaged(BadUnmanagedSpringTest.class)).singleElement()
                .satisfies(v -> assertThat(v.rule()).isEqualTo(GuardRule.PT01));
        assertThat(ClassRules.checkManaged(GoodManagedSpringTest.class)).isEmpty();
        assertThat(ClassRules.checkManaged(GoodTargetedCalls.class)).as("not a Spring test").isEmpty();
    }
}
