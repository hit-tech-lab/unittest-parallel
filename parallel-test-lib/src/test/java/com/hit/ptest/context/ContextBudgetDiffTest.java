package com.hit.ptest.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.UnboundPolicy;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.SpringProperties;
import org.springframework.test.context.ContextLoader;
import org.springframework.test.context.MergedContextConfiguration;

/** PT10: vượt ngân sách context in ra context nào tạo, context gần nhất và ĐÚNG mục cấu hình khác nhau; WARN/OFF không ném. */
@Isolated
class ContextBudgetDiffTest {

    static class ConfigA {
    }

    static class ConfigB {
    }

    private static MergedContextConfiguration config(Class<?> testClass, Class<?> configClass, String profile, String property) {
        return new MergedContextConfiguration(testClass, new String[0], new Class<?>[] {configClass},
                Set.<Class<? extends ApplicationContextInitializer<?>>>of(), new String[] {profile}, new String[0],
                new String[] {property}, mock(ContextLoader.class), null, null);
    }

    private static ManagedContext managed(String key, MergedContextConfiguration cfg) {
        ManagedContext m = new ManagedContext(key, key, new AnnotationConfigApplicationContext(), UnboundPolicy.FAIL, false);
        m.createdBy = cfg.getTestClass().getName();
        m.config = ContextConfigDescription.describe(cfg);
        return m;
    }

    @AfterEach
    void clear() {
        SpringProperties.setProperty("ptest.guard.PT10", null);
    }

    @Test
    void differencesNameOnlyTheKeysThatDiffer() {
        var a = ContextConfigDescription.describe(config(String.class, ConfigA.class, "p1", "k=1"));
        var sameAsA = ContextConfigDescription.describe(config(Integer.class, ConfigA.class, "p1", "k=1"));
        var other = ContextConfigDescription.describe(config(Long.class, ConfigB.class, "p1", "k=2"));

        assertThat(ContextConfigDescription.differences(a, sameAsA)).as("test class itself is not part of the key").isEmpty();
        assertThat(ContextConfigDescription.distance(a, other)).isEqualTo(2);
        assertThat(ContextConfigDescription.differences(a, other)).hasSize(2)
                .anySatisfy(d -> assertThat(d).contains("configuration classes").contains("[ConfigA]").contains("[ConfigB]"))
                .anySatisfy(d -> assertThat(d).contains("properties").contains("k=1").contains("k=2"))
                .noneSatisfy(d -> assertThat(d).contains("active profiles"));
    }

    private static MergedContextConfiguration withPtest(Class<?> testClass, List<MockOnlyBean> mockOnly) {
        ParallelContextCustomizer customizer = new ParallelContextCustomizer(
                ContextDeclaration.of("same-name", new String[] {"*"}, new String[0], new String[0], mockOnly));
        return new MergedContextConfiguration(testClass, new String[0], new Class<?>[] {ConfigA.class},
                Set.<Class<? extends ApplicationContextInitializer<?>>>of(), new String[0], new String[0], new String[0],
                Set.<org.springframework.test.context.ContextCustomizer>of(customizer), mock(ContextLoader.class), null, null);
    }

    @Test
    void mockOnlySetDifferenceIsPrintedAsItsOwnRowOfTheDiff() {
        var base = ContextConfigDescription.describe(withPtest(String.class, List.of()));
        var extra = ContextConfigDescription.describe(withPtest(Integer.class,
                List.of(new MockOnlyBean("mongoTemplate", false, org.springframework.core.ResolvableType.forClass(Number.class)))));

        assertThat(ContextConfigDescription.differences(base, extra)).as("exactly the one mock that differs")
                .singleElement().satisfies(d -> assertThat(d).contains("class-level @PerTestMock").contains("Number").contains("existing: (not declared)").contains("new:      declared"));
        assertThat(ContextConfigDescription.differences(base, base)).isEmpty();
    }

    @Test
    void exceedingTheBudgetPrintsCreatorsClosestContextAndTheExactDiff() {
        ContextRegistry registry = new ContextRegistry();
        ManagedContext far = managed("far@1#1", config(String.class, ConfigB.class, "other", "x=1"));
        ManagedContext near = managed("near@2#2", config(Integer.class, ConfigA.class, "p1", "k=1"));
        ManagedContext created = managed("new@3#3", config(Long.class, ConfigA.class, "p1", "k=2"));
        registry.register(far, 2);
        registry.register(near, 2);

        assertThatThrownBy(() -> registry.register(created, 2)).isInstanceOf(ParallelTestException.ContextLimit.class)
                .hasMessageContaining("CONTEXT_LIMIT_EXCEEDED: cannot create context 'new@3#3'")
                .hasMessageContaining("ptest.max-contexts=2")
                .hasMessageContaining("new context was requested by java.lang.Long")
                .hasMessageContaining("active contexts were created by: [java.lang.String, java.lang.Integer]")
                .hasMessageContaining("closest active context is 'near@2#2' (created by java.lang.Integer)")
                .hasMessageContaining("* properties:").hasMessageContaining("existing: [k=1]").hasMessageContaining("new:      [k=2]")
                .hasMessageContaining("[PT10]")
                .satisfies(e -> assertThat(e.getMessage()).as("only the differing key is listed").doesNotContain("* active profiles")
                        .doesNotContain("* configuration classes"));
    }

    @Test
    void warnAndOffDoNotThrowAndTheContextIsStillCounted() {
        ContextRegistry registry = new ContextRegistry();
        registry.register(managed("a@1#1", config(String.class, ConfigA.class, "p", "k=1")), 1);

        SpringProperties.setProperty("ptest.guard.PT10", "warn");
        assertThatNoException().isThrownBy(() -> registry.register(managed("b@2#2", config(Integer.class, ConfigA.class, "p", "k=2")), 1));
        SpringProperties.setProperty("ptest.guard.PT10", "off");
        assertThatNoException().isThrownBy(() -> registry.register(managed("c@3#3", config(Long.class, ConfigA.class, "p", "k=3")), 1));
        assertThat(registry.activeCount()).isEqualTo(3);

        SpringProperties.setProperty("ptest.guard.PT10", null);
        assertThatThrownBy(() -> registry.register(managed("d@4#4", config(Short.class, ConfigA.class, "p", "k=4")), 1))
                .isInstanceOf(ParallelTestException.ContextLimit.class);
    }

    @Test
    void withinTheBudgetNothingIsReportedAndSameConfigurationIsNoDifference() {
        ContextRegistry registry = new ContextRegistry();
        ManagedContext one = managed("a@1#1", config(String.class, ConfigA.class, "p", "k=1"));
        registry.register(one, 2);
        assertThatNoException().isThrownBy(() -> registry.register(managed("b@2#2", config(Integer.class, ConfigA.class, "p", "k=1")), 2));
        assertThat(List.of(one.createdBy)).containsExactly("java.lang.String");
        assertThat(Map.copyOf(one.config)).containsKey("properties");
    }
}
