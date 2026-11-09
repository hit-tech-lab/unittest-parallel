package io.hit.ptest.context;

import io.hit.ptest.intercept.InterceptMode;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTestException;
import io.hit.ptest.PerTestMock;
import io.hit.ptest.PerTestSpy;
import io.hit.ptest.context.ManagedContext.ResolvedRequest;
import io.hit.ptest.intercept.TestBinding;
import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.core.task.SimpleAsyncTaskExecutor;

/** Chọn bean theo kiểu/@Qualifier/tên field, bean mock-only, tập bean được bọc, và các lỗi khai báo có mã ổn định. */
class PerTestDeclarationTest {

    interface Fx {
        String rate();
    }

    static class Primary implements Fx {
        public String rate() {
            return "primary";
        }
    }

    static class Backup implements Fx {
        public String rate() {
            return "backup";
        }
    }

    static class Plain {
        public String hello() {
            return "real";
        }
    }

    @org.springframework.boot.context.properties.ConfigurationProperties("cfg")
    static class ConfigData {
    }

    static class BoundThing {
    }

    static class MissingClient {
        public String call() {
            return "x";
        }
    }

    @Configuration
    static class Beans {
        @Bean Fx primaryFx() {
            return new Primary();
        }

        @Bean Fx backupFx() {
            return new Backup();
        }

        @Bean Plain plain() {
            return new Plain();
        }

        /** Kiểu framework nhưng do ứng dụng định nghĩa: được bọc theo wildcard. */
        @Bean SimpleAsyncTaskExecutor userExecutor() {
            return new SimpleAsyncTaskExecutor();
        }

        /** Kiểu hạ tầng (MessageSource): không bọc theo wildcard. */
        @Bean StaticMessageSource infraMessages() {
            return new StaticMessageSource();
        }

        @Bean ConfigData configData() {
            return new ConfigData();
        }

        @Bean @org.springframework.boot.context.properties.ConfigurationProperties("x") BoundThing boundThing() {
            return new BoundThing();
        }
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
        TestBinding.bind(null);
    }

    // ---- khai báo ở lớp con (chỉ bean ĐÃ có) ----

    static class ByQualifier {
        @PerTestMock @Qualifier("primaryFx") Fx primary;
        @PerTestSpy @Qualifier("backupFx") Fx backup;
        @PerTestMock Plain plain;
    }

    static class ByFieldName {
        @PerTestMock Fx backupFx;
    }

    static class ByNameAttribute {
        @PerTestMock(name = "primaryFx") Fx first;
    }

    static class Ambiguous {
        @PerTestMock Fx fx;
    }

    static class MockAndSpy {
        @PerTestMock Plain mock;
        @PerTestSpy Plain spy;
    }

    static class MockAndSpyAcrossHierarchy extends BaseWithMock {
        @PerTestSpy Plain spy;
    }

    static class BaseWithMock {
        @PerTestMock Plain mock;
    }

    static class SpyOfMissing {
        @PerTestSpy MissingClient missing;
    }

    static class MockOfMissingInSubclass {
        @PerTestMock MissingClient missing;
    }

    static class Excluded {
        @PerTestMock StaticMessageSource messages;
    }

    private static AnnotationConfigApplicationContext started(String name, String[] wrap, String[] exclude) {
        return Fixtures.started("ptd-" + name, wrap, Map.of(), Beans.class);
    }

    private static AnnotationConfigApplicationContext startedWith(ContextDeclaration d) {
        AnnotationConfigApplicationContext c = Fixtures.context(d, Map.of(), Beans.class);
        c.refresh();
        return c;
    }

    @Test
    void wrapAllDefaultWrapsApplicationBeansAndSkipsInfrastructureWithAReason() {
        AnnotationConfigApplicationContext c = started("wrap-all", new String[] {"*"}, new String[0]);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(mc.interceptors.keySet()).contains("primaryFx", "backupFx", "plain");
        assertThat(mc.interceptors).doesNotContainKey("infraMessages");
        assertThat(mc.plan.whyNotWrapped("infraMessages")).contains("framework");
    }

    @Test
    void frameworkTypedBeanDefinedByTheApplicationIsWrappedButConfigurationPropertiesAreNot() {
        ManagedContext mc = Fixtures.managed(started("framework-typed", new String[] {"*"}, new String[0]));

        assertThat(mc.interceptors).as("RestTemplate/ObjectMapper/executor style beans of the application").containsKey("userExecutor");
        assertThat(mc.interceptors).doesNotContainKeys("configData", "boundThing");
        assertThat(mc.plan.whyNotWrapped("configData")).contains("@ConfigurationProperties");
        assertThat(mc.plan.whyNotWrapped("boundThing")).as("@ConfigurationProperties on the @Bean method").contains("@ConfigurationProperties");
    }

    @Test
    void resolvesByQualifierSingleTypeAndFieldName() {
        AnnotationConfigApplicationContext c = started("resolve", new String[] {"*"}, new String[0]);
        ManagedContext mc = Fixtures.managed(c);

        List<ResolvedRequest> r = mc.resolveFields(ByQualifier.class);
        assertThat(r).extracting(ResolvedRequest::beanName).containsExactly("primaryFx", "backupFx", "plain");
        assertThat(r.get(1).request().mode()).isEqualTo(InterceptMode.SPY);
        assertThat(mc.resolveFields(ByFieldName.class)).extracting(ResolvedRequest::beanName).containsExactly("backupFx");
    }

    @Test
    void nameAttributeSelectsTheBeanLikeAQualifier() throws Exception {
        ManagedContext mc = Fixtures.managed(started("by-value", new String[] {"*"}, new String[0]));

        assertThat(mc.resolveFields(ByNameAttribute.class)).extracting(ResolvedRequest::beanName).containsExactly("primaryFx");
        BeanRequest param = BeanDeclarations.parameterRequest(ParamHolder.class.getDeclaredMethod("t", Fx.class), 
                ParamHolder.class.getDeclaredMethod("t", Fx.class).getParameters()[0], 0);
        assertThat(mc.resolve(param).beanName()).isEqualTo("backupFx");
    }

    static class ParamHolder {
        void t(@PerTestSpy(name = "backupFx") Fx fx) {
        }
    }

    @Test
    void severalBeansOfTheTypeWithoutQualifierIsAmbiguousAndListsCandidates() {
        ManagedContext mc = Fixtures.managed(started("ambiguous", new String[] {"*"}, new String[0]));

        assertThatThrownBy(() -> mc.resolveFields(Ambiguous.class)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("BEAN_AMBIGUOUS").hasMessageContaining("primaryFx").hasMessageContaining("backupFx");
    }

    @Test
    void mockAndSpyOfTheSameBeanInOneHierarchyIsAConfigurationError() {
        ManagedContext mc = Fixtures.managed(started("mode-conflict", new String[] {"*"}, new String[0]));

        assertThatThrownBy(() -> mc.resolveFields(MockAndSpy.class)).hasMessageContaining("MODE_CONFLICT").hasMessageContaining("'plain'");
        assertThatThrownBy(() -> mc.resolveFields(MockAndSpyAcrossHierarchy.class)).hasMessageContaining("MODE_CONFLICT");
    }

    @Test
    void spyOfMissingBeanAndMockOfMissingBeanBelowTheContextClassFailWithStableCodes() {
        ManagedContext mc = Fixtures.managed(started("missing", new String[] {"*"}, new String[0]));

        assertThatThrownBy(() -> mc.resolveFields(SpyOfMissing.class)).hasMessageContaining("SPY_NO_BEAN");
        assertThatThrownBy(() -> mc.resolveFields(MockOfMissingInSubclass.class)).hasMessageContaining("BEAN_NOT_FOUND")
                .hasMessageContaining("class carrying @ParallelSpringTest");
    }

    @Test
    void mockingABeanThatIsNotWrappedNamesTheReason() {
        ManagedContext mc = Fixtures.managed(started("not-wrapped", new String[] {"*"}, new String[0]));

        assertThatThrownBy(() -> mc.resolveFields(Excluded.class)).hasMessageContaining("BEAN_NOT_WRAPPED")
                .hasMessageContaining("infraMessages").hasMessageContaining("framework");
    }

    @Test
    void excludeWinsOverWrap() {
        ManagedContext mc = Fixtures.managed(startedWith(ContextDeclaration.of("ptd-exclude", new String[] {"*"}, new String[] {"plain"},
                new String[0], List.of())));

        assertThat(mc.interceptors).containsKeys("primaryFx").doesNotContainKey("plain");
        assertThatThrownBy(() -> mc.resolveFields(ByQualifier.class)).hasMessageContaining("BEAN_NOT_WRAPPED").hasMessageContaining("excluded");
    }

    // ---- bean khai cấp class (mock cho cả context) ----

    @PerTestMock(MissingClient.class)
    @PerTestMock(name = "thirdFx", classes = Fx.class)
    @PerTestMock(Plain.class)
    @ParallelSpringTest(context = "class-level")
    abstract static class ClassLevelBase {
        /** Field: chỉ trỏ tới bean đã có, không thuộc khóa context. */
        @PerTestMock @Qualifier("primaryFx") Fx plainReference;
    }

    static class ClassLevelChild extends ClassLevelBase {
    }

    @Test
    void classLevelDeclarationsComeFromTheWholeHierarchyAndFieldsAreNotPartOfTheKey() {
        List<MockOnlyBean> declared = BeanDeclarations.classLevelMocks(ClassLevelChild.class);

        assertThat(declared).extracting(MockOnlyBean::name).containsExactlyInAnyOrder("missingClient", "thirdFx", "plain");
        assertThat(BeanDeclarations.classLevelMocks(ByQualifier.class)).as("no class-level declaration").isEmpty();
    }

    @Test
    void absentIsCreatedExistingIsReplacedAndTheMockAnswersWithDefaultsForTestsWithoutAHandle() {
        ContextDeclaration d = ContextDeclaration.of("ptd-class-level", new String[] {"*"}, new String[0], new String[0],
                BeanDeclarations.classLevelMocks(ClassLevelChild.class));
        AnnotationConfigApplicationContext c = startedWith(d);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(c.containsBean("missingClient")).as("absent type: created").isTrue();
        assertThat(c.containsBean("thirdFx")).as("name of an absent bean of an existing type: created").isTrue();
        assertThat(c.getBeansOfType(Fx.class)).containsOnlyKeys("primaryFx", "backupFx", "thirdFx");
        assertThat(mc.interceptors.get("plain").isMockOnly()).as("existing Plain: definition replaced").isTrue();
        assertThat(c.getBean(Plain.class).hello()).as("real Plain never ran").isNull();
        assertThat(mc.interceptors.get("missingClient").isMockOnly()).isTrue();
        assertThat(c.getBean(MissingClient.class).call()).as("no handle, no NPE, Mockito default").isNull();

        ManagedContext.ResolvedRequest r = mc.resolveFields(ClassLevelChild.class).stream()
                .filter(x -> x.beanName().equals("primaryFx")).findFirst().orElseThrow();
        Object target = mc.requireInterceptor("primaryFx").newTarget(InterceptMode.MOCK);
        TestBinding.bind(TestBinding.open("t", Map.of(new TestBinding.TargetKey(mc.key, r.beanName()), target)));
        org.mockito.Mockito.when(((Fx) target).rate()).thenReturn("stubbed");
        assertThat(c.getBean("primaryFx", Fx.class).rate()).isEqualTo("stubbed");
    }

    @ParallelSpringTest(context = "shared-by-reference")
    static class OnlyReferencesA {
        @PerTestMock @Qualifier("primaryFx") Fx a;
    }

    @ParallelSpringTest(context = "shared-by-reference")
    static class OnlyReferencesB {
        @PerTestMock @Qualifier("backupFx") Fx b;
        @PerTestMock Plain plain;
    }

    @ParallelSpringTest(context = "shared-by-reference")
    static class MissingWithoutDeclaration {
        @PerTestMock MissingClient missing;
    }

    @Test
    void classesDifferingOnlyInFieldMocksShareTheContextKey() {
        var factory = new ParallelContextCustomizerFactory();

        assertThat(BeanDeclarations.classLevelMocks(OnlyReferencesB.class)).as("fields are not in the key").isEmpty();
        assertThat(factory.createContextCustomizer(OnlyReferencesA.class, List.of()))
                .isEqualTo(factory.createContextCustomizer(OnlyReferencesB.class, List.of()))
                .isEqualTo(factory.createContextCustomizer(MissingWithoutDeclaration.class, List.of()));
    }

    @Test
    void fieldMockOfAbsentBeanIsBeanNotFoundAndHintsTheClassLevelForm() {
        ManagedContext mc = Fixtures.managed(started("no-declaration", new String[] {"*"}, new String[0]));

        assertThatThrownBy(() -> mc.resolveFields(MissingWithoutDeclaration.class)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("BEAN_NOT_FOUND").hasMessageContaining("@PerTestMock(MyBean.class)");
    }
}
