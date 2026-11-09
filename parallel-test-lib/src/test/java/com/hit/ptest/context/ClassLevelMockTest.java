package com.hit.ptest.context;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTestException;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestMocks;
import com.hit.ptest.PerTestSpy;
import com.hit.ptest.intercept.TestBinding;
import com.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * {@code @PerTestMock} CẤP CLASS (như {@code @MockBean}): bean đã có thì định nghĩa bị thay bằng mock (bean thật không bao giờ được
 * tạo), chưa có thì tạo bean mock; thuộc khóa context.
 */
class ClassLevelMockTest {

    static final AtomicInteger REAL_CREATED = new AtomicInteger();
    static final AtomicInteger DEPENDENCY_CREATED = new AtomicInteger();

    interface Rate {
        String rate();
    }

    static class RealRate implements Rate {
        RealRate() {
            REAL_CREATED.incrementAndGet();
        }

        public String rate() {
            return "real";
        }
    }

    static class FromBeanMethod extends RealRate {
    }

    @Component
    public static class Scanned extends RealRate {
    }

    public static class Imported extends RealRate {
    }

    static class First extends RealRate {
    }

    static class Second extends RealRate {
    }

    static class Missing {
        public String call() {
            return "x";
        }
    }

    static class Consumer {
        private final Rate rate;

        Consumer(Rate rate) {
            this.rate = rate;
        }

        String viaRate() {
            return rate.rate();
        }
    }

    @Configuration
    static class MethodConfig {
        @Bean
        FromBeanMethod fromMethod() {
            return new FromBeanMethod();
        }

        @Bean
        Consumer consumer(FromBeanMethod fromMethod) {
            return new Consumer(fromMethod);
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = ClassLevelMockTest.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = Scanned.class))
    static class ScanConfig {
    }

    @Configuration
    @Import(Imported.class)
    static class ImportConfig {
    }

    @Configuration
    static class TwoBeansConfig {
        @Bean @Qualifier("first") @Primary Rate first() {
            return new First();
        }

        @Bean @Qualifier("second") Rate second() {
            return new Second();
        }
    }

    @Configuration
    static class NoPrimaryConfig {
        @Bean Rate one() {
            return new First();
        }

        @Bean Rate two() {
            return new Second();
        }
    }

    @Configuration
    static class EmptyConfig {
    }

    /** {@code @Qualifier} đặt trên method {@code @Bean} KHÁC tên bean; config này nạp qua quét classpath (metadata đọc bằng ASM). */
    @Configuration
    static class MethodQualifierConfig {
        @Bean @Qualifier("fast") Rate fastRate() {
            return new First();
        }

        @Bean @Qualifier("slow") Rate slowRate() {
            return new Second();
        }
    }

    @Configuration
    @ComponentScan(basePackageClasses = ClassLevelMockTest.class, useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = MethodQualifierConfig.class))
    static class ScanQualifierConfig {
    }

    static class SlowDependency {
        SlowDependency() {
            DEPENDENCY_CREATED.incrementAndGet();
        }
    }

    @Configuration
    static class DependsOnConfig {
        @Bean SlowDependency slowDependency() {
            return new SlowDependency();
        }

        @Bean @DependsOn("slowDependency") FromBeanMethod dependent() {
            return new FromBeanMethod();
        }

        @Bean Rate regular() {
            return new Second();
        }
    }

    /** FactoryBean thô: Spring không đoán được kiểu sản phẩm khi chưa khởi tạo nó. */
    @SuppressWarnings("rawtypes")
    static class OpaqueFactory implements FactoryBean {
        @Override
        public Object getObject() {
            return new Missing();
        }

        @Override
        public Class<?> getObjectType() {
            return Missing.class;
        }
    }

    @Configuration
    static class OpaqueFactoryConfig {
        @SuppressWarnings("rawtypes")
        @Bean FactoryBean opaque() {
            return new OpaqueFactory();
        }
    }

    // ---- khai báo cấp class ----

    @PerTestMock(FromBeanMethod.class)
    @ParallelSpringTest(context = "cl-method")
    abstract static class MethodBase {
    }

    static class MethodTest extends MethodBase {
        @PerTestMock FromBeanMethod handle;
    }

    @PerTestMock(Scanned.class)
    @ParallelSpringTest(context = "cl-scan")
    abstract static class ScanBase {
    }

    @PerTestMock(Imported.class)
    @ParallelSpringTest(context = "cl-import")
    abstract static class ImportBase {
    }

    @PerTestMock(name = "second", classes = Rate.class)
    @ParallelSpringTest(context = "cl-qualified")
    abstract static class QualifiedBase {
    }

    @PerTestMock(name = "first", classes = Rate.class)
    @ParallelSpringTest(context = "cl-primary")
    abstract static class PrimaryBase {
    }

    @PerTestMock(Rate.class)
    @ParallelSpringTest(context = "cl-by-type")
    abstract static class ByTypeBase {
    }

    @ParallelSpringTest(context = "cl-keep")
    abstract static class KeepBase {
        @PerTestMock FromBeanMethod fromMethod;
    }

    @PerTestMocks({@PerTestMock(name = "first", classes = Rate.class), @PerTestMock(Rate.class)})
    @ParallelSpringTest(context = "cl-twice")
    abstract static class TwiceBase {
    }

    @PerTestMock(name = "fast", classes = Rate.class)
    @ParallelSpringTest(context = "cl-method-qualifier")
    abstract static class MethodQualifierBase {
    }

    @PerTestMock(FromBeanMethod.class)
    @ParallelSpringTest(context = "cl-depends")
    abstract static class DependsBase {
    }

    @PerTestMock(Missing.class)
    @ParallelSpringTest(context = "cl-opaque")
    abstract static class OpaqueBase {
    }

    @BeforeEach
    void reset() {
        REAL_CREATED.set(0);
        DEPENDENCY_CREATED.set(0);
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
        TestBinding.bind(null);
    }

    private static ContextDeclaration declaration(Class<?> testClass, String name) {
        return ContextDeclaration.of(name, new String[] {"*"}, new String[0], new String[0], BeanDeclarations.classLevelMocks(testClass));
    }

    private static AnnotationConfigApplicationContext start(Class<?> testClass, String name, Class<?>... configs) {
        AnnotationConfigApplicationContext c = Fixtures.context(declaration(testClass, name), Map.of(), configs);
        c.refresh();
        return c;
    }

    private static AnnotationConfigApplicationContext unstarted(Class<?> testClass, String name, Class<?>... configs) {
        return Fixtures.context(declaration(testClass, name), Map.of(), configs);
    }

    // ---- thay định nghĩa đã có ----

    @Test
    void beanMethodDefinitionIsReplacedAndTheRealBeanIsNeverCreated() {
        AnnotationConfigApplicationContext c = start(MethodTest.class, "cl-method", MethodConfig.class);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(REAL_CREATED).as("the real FromBeanMethod constructor never ran").hasValue(0);
        assertThat(mc.interceptors.get("fromMethod").isMockOnly()).isTrue();
        assertThat(c.getBeanNamesForType(FromBeanMethod.class)).containsExactly("fromMethod");
        assertThat(c.getBean(Consumer.class).viaRate()).as("dependents receive the replacement, not the real bean").isNull();
        assertThat(c.getBean(FromBeanMethod.class).rate()).as("no handle: Mockito default").isNull();
    }

    @Test
    void aFieldHandleStubsTheReplacementPerTest() {
        AnnotationConfigApplicationContext c = start(MethodTest.class, "cl-method-stub", MethodConfig.class);
        ManagedContext mc = Fixtures.managed(c);
        ManagedContext.ResolvedRequest r = mc.resolveFields(MethodTest.class).get(0);
        Object target = mc.requireInterceptor(r.beanName()).newTarget(InterceptMode.MOCK);
        TestBinding.bind(TestBinding.open("t", Map.of(new TestBinding.TargetKey(mc.key, r.beanName()), target)));
        org.mockito.Mockito.when(((FromBeanMethod) target).rate()).thenReturn("stubbed");

        assertThat(r.beanName()).isEqualTo("fromMethod");
        assertThat(c.getBean(Consumer.class).viaRate()).isEqualTo("stubbed");
    }

    @Test
    void componentScanDefinitionIsReplaced() {
        AnnotationConfigApplicationContext c = start(ScanBase.class, "cl-scan", ScanConfig.class);

        assertThat(REAL_CREATED).hasValue(0);
        assertThat(c.getBeanNamesForType(Scanned.class)).containsExactly("classLevelMockTest.Scanned");
        assertThat(Fixtures.managed(c).interceptors.get("classLevelMockTest.Scanned").isMockOnly()).isTrue();
    }

    @Test
    void importedClassDefinitionIsReplaced() {
        AnnotationConfigApplicationContext c = start(ImportBase.class, "cl-import", ImportConfig.class);

        assertThat(REAL_CREATED).hasValue(0);
        assertThat(Fixtures.managed(c).interceptors.get(Imported.class.getName()).isMockOnly()).isTrue();
    }

    @Test
    void nameReplacesOnlyThatBeanAndTheOtherStaysRealAndWrapped() {
        AnnotationConfigApplicationContext c = start(QualifiedBase.class, "cl-qualified", TwoBeansConfig.class);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(mc.interceptors.get("second").isMockOnly()).isTrue();
        assertThat(mc.interceptors.get("first").isMockOnly()).isFalse();
        assertThat(REAL_CREATED).as("only First was created").hasValue(1);
        assertThat(c.getBean("first", Rate.class).rate()).isEqualTo("real");
        assertThat(c.getBean("second", Rate.class).rate()).isNull();
    }

    static class AnnotatedHolder {
        @Autowired @Qualifier("first") Rate first;
        @Autowired @Qualifier("second") Rate second;
    }

    @Test
    void primaryAndQualifierMetadataSurviveReplacement() {
        AnnotationConfigApplicationContext c = start(PrimaryBase.class, "cl-primary", TwoBeansConfig.class);

        assertThat(c.getBeanFactory().getBeanDefinition("first").isPrimary()).isTrue();
        assertThat(c.getBean(Rate.class)).as("by type resolves to the replaced @Primary").isSameAs(c.getBean("first"));
        assertThat(Fixtures.managed(c).interceptors.get("first").isMockOnly()).isTrue();
        assertThat(REAL_CREATED).as("only Second was created").hasValue(1);
        AnnotatedHolder h = c.getBeanFactory().createBean(AnnotatedHolder.class);
        assertThat(h.first).isSameAs(c.getBean("first"));
        assertThat(h.second).isSameAs(c.getBean("second"));
    }

    @Test
    void declarationByTypeFollowsPrimaryLikeSpring() {
        AnnotationConfigApplicationContext c = start(ByTypeBase.class, "cl-by-type", TwoBeansConfig.class);

        assertThat(Fixtures.managed(c).interceptors.get("first").isMockOnly()).isTrue();
        assertThat(Fixtures.managed(c).interceptors.get("second").isMockOnly()).isFalse();
    }

    @Test
    void severalBeansOfTheTypeWithoutPrimaryIsAStartupConfigError() {
        AnnotationConfigApplicationContext c = unstarted(ByTypeBase.class, "cl-truly-ambiguous", NoPrimaryConfig.class);

        assertThatThrownBy(c::refresh).hasStackTraceContaining("BEAN_AMBIGUOUS").hasStackTraceContaining("one").hasStackTraceContaining("two");
    }

    @Test
    void sameBeanReplacedByTwoDeclarationsIsAConflict() {
        AnnotationConfigApplicationContext c = unstarted(TwiceBase.class, "cl-twice", TwoBeansConfig.class);

        assertThatThrownBy(c::refresh).hasStackTraceContaining("MOCK_ONLY_CONFLICT").hasStackTraceContaining("both replace bean 'first'");
    }

    @Test
    void withoutAClassLevelDeclarationTheRealBeanIsKeptAndWrapped() {
        AnnotationConfigApplicationContext c = start(KeepBase.class, "cl-keep", MethodConfig.class);

        assertThat(REAL_CREATED).hasValue(1);
        assertThat(Fixtures.managed(c).interceptors.get("fromMethod").isMockOnly()).isFalse();
        assertThat(c.getBean(FromBeanMethod.class).rate()).isEqualTo("real");
    }

    @Test
    void aSingletonWithoutDefinitionCannotBeReplaced() {
        AnnotationConfigApplicationContext c = unstarted(MethodTest.class, "cl-nodef", EmptyConfig.class);
        c.getBeanFactory().registerSingleton("fromMethod", new FromBeanMethod());

        assertThatThrownBy(c::refresh).hasStackTraceContaining("MOCK_REPLACE_UNSUPPORTED");
    }

    // ---- M4: metadata của định nghĩa bị thay ----

    @Test
    void qualifierOnTheBeanMethodWithADifferentBeanNameSurvivesReplacement() {
        AnnotationConfigApplicationContext c = unstarted(MethodQualifierBase.class, "cl-method-qualifier", ScanQualifierConfig.class);
        c.refresh();

        // Config đọc bằng ASM: method factory của định nghĩa gốc có thể chưa phân giải; qualifier "fast" vẫn phải chọn đúng bean đã thay
        assertThat(Fixtures.managed(c).interceptors.get("fastRate").isMockOnly()).as("fastRate replaced by name 'fast' (the @Bean method qualifier)").isTrue();
        assertThat(Fixtures.managed(c).interceptors.get("slowRate").isMockOnly()).isFalse();
        assertThat(REAL_CREATED).as("only the slow bean was created").hasValue(1);
        assertThat(BeanFactoryAnnotationUtils.isQualifierMatch("fast"::equals, "fastRate", c.getBeanFactory())).isTrue();
        assertThat(BeanFactoryAnnotationUtils.isQualifierMatch("fast"::equals, "slowRate", c.getBeanFactory())).isFalse();
        AnnotatedFastSlow h = c.getBeanFactory().createBean(AnnotatedFastSlow.class);
        assertThat(h.fast).as("@Qualifier(\"fast\") injection point still receives the replacement").isSameAs(c.getBean("fastRate"));
        assertThat(h.slow).isSameAs(c.getBean("slowRate"));
    }

    static class AnnotatedFastSlow {
        @Autowired @Qualifier("fast") Rate fast;
        @Autowired @Qualifier("slow") Rate slow;
    }

    @Test
    void dependsOnOfTheRealBeanIsNotKeptBecauseAMockDoesNotNeedIt() {
        AnnotationConfigApplicationContext c = start(DependsBase.class, "cl-depends", DependsOnConfig.class);

        assertThat(c.getBeanFactory().getBeanDefinition("dependent").getDependsOn()).isNullOrEmpty();
    }

    // ---- tạo bean chưa có ----

    @PerTestMock(Missing.class)
    @PerTestMock(name = "extraRate", classes = Rate.class)
    @ParallelSpringTest(context = "cl-create")
    abstract static class CreateBase {
    }

    @Test
    void absentBeanIsCreatedWithTheDefaultNameOrTheGivenName() {
        AnnotationConfigApplicationContext c = start(CreateBase.class, "cl-create", EmptyConfig.class);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(c.containsBean("missing")).as("default name = decapitalised simple name").isTrue();
        assertThat(c.containsBean("extraRate")).isTrue();
        assertThat(mc.interceptors.get("missing").isMockOnly()).isTrue();
        assertThat(c.getBean(Missing.class).call()).as("no handle: Mockito default, no NPE").isNull();
    }

    @Test
    void createdBeanNameUsedByAnotherTypeIsAConflict() {
        AnnotationConfigApplicationContext c = unstarted(CreateBase.class, "cl-create-name", EmptyConfig.class);
        c.getBeanFactory().registerSingleton("extraRate", "not a Rate");

        assertThatThrownBy(c::refresh).hasStackTraceContaining("MOCK_ONLY_CONFLICT").hasStackTraceContaining("extraRate");
    }

    @PerTestMock(Missing.class)
    @ParallelSpringTest(context = "cl-default-name-taken")
    abstract static class DefaultNameTakenBase {
    }

    @Test
    void defaultNameUsedByAnotherTypeFallsBackToAUniqueNameBecauseNobodyReferencesIt() {
        AnnotationConfigApplicationContext c = unstarted(DefaultNameTakenBase.class, "cl-default-name-taken", EmptyConfig.class);
        c.getBeanFactory().registerSingleton("missing", "a String named like the default");
        c.refresh();

        assertThat(c.getBeansOfType(Missing.class)).containsOnlyKeys(Missing.class.getName() + "#0");
        assertThat(c.getBean("missing")).isEqualTo("a String named like the default");
    }

    // ---- M5: FactoryBean mà Spring không đoán được kiểu ----

    @Test
    void aFactoryBeanWithUnknownProductTypeIsReportedInsteadOfSilentlyDuplicating() {
        AnnotationConfigApplicationContext c = unstarted(OpaqueBase.class, "cl-opaque", OpaqueFactoryConfig.class);

        // Spring không đoán được kiểu nên bean mock được tạo song song với sản phẩm FactoryBean: phải báo lỗi rõ, và chỉ cách sửa
        assertThatThrownBy(c::refresh).hasStackTraceContaining("MOCK_ONLY_CONFLICT").hasStackTraceContaining("FactoryBean")
                .hasStackTraceContaining("@PerTestMock(name = \"opaque\"");
    }

    @PerTestMock(name = "opaque", classes = Missing.class)
    @ParallelSpringTest(context = "cl-opaque-named")
    abstract static class OpaqueNamedBase {
    }

    @Test
    void namingTheFactoryProductReplacesItAndThereIsNoDuplicate() {
        AnnotationConfigApplicationContext c = start(OpaqueNamedBase.class, "cl-opaque-named", OpaqueFactoryConfig.class);

        assertThat(Fixtures.managed(c).interceptors.get("opaque").isMockOnly()).isTrue();
        assertThat(c.getBeansOfType(Missing.class)).hasSize(1);
    }

    // ---- cú pháp khai báo ----

    @PerTestMock(Missing.class)
    abstract static class ParentDeclares {
    }

    @PerTestMock(Missing.class)
    @PerTestMock(Consumer.class)
    @ParallelSpringTest(context = "cl-dedupe")
    static class ChildRepeats extends ParentDeclares {
    }

    @PerTestMocks({@PerTestMock(Missing.class), @PerTestMock(name = "n", classes = Rate.class)})
    @PerTestMock({First.class, Second.class})
    @ParallelSpringTest(context = "cl-forms")
    static class AllForms {
        @PerTestMock FromBeanMethod field;
    }

    @Test
    void declarationsAreReadFromTheWholeHierarchyRepeatableContainerValueAndClassesAndDeduplicated() {
        assertThat(BeanDeclarations.classLevelMocks(ChildRepeats.class)).as("Missing declared twice (parent+child) counts once")
                .extracting(MockOnlyBean::name).containsExactlyInAnyOrder("missing", "consumer");
        assertThat(BeanDeclarations.classLevelMocks(AllForms.class)).extracting(MockOnlyBean::name)
                .containsExactlyInAnyOrder("missing", "n", "first", "second");
    }

    @Test
    void valueAndClassesAreMerged() {
        @PerTestMock(value = First.class, classes = Second.class)
        @ParallelSpringTest(context = "cl-merge")
        class Local {
        }

        assertThat(BeanDeclarations.classLevelMocks(Local.class)).extracting(MockOnlyBean::name).containsExactlyInAnyOrder("first", "second");
    }

    @PerTestMock
    @ParallelSpringTest
    static class NoType {
    }

    @PerTestMock(name = "x", classes = {First.class, Second.class})
    @ParallelSpringTest
    static class NameWithTwoTypes {
    }

    @PerTestMock(name = "dup", classes = First.class)
    @PerTestMock(name = "dup", classes = Second.class)
    @ParallelSpringTest
    static class SameNameTwoTypes {
    }

    @PerTestMock(First.class)
    @PerTestSpy(First.class)
    @ParallelSpringTest
    static class MockAndSpyOfTheSameType {
    }

    /** Cùng tên, khác KIỂU và khác CHẾ ĐỘ: nguyên nhân là hai kiểu cho một tên (không phải "spy của bean bị mock"). */
    @PerTestMock(name = "dup", classes = First.class)
    @PerTestSpy(name = "dup", classes = Second.class)
    @ParallelSpringTest
    static class SameNameTwoTypesAndModes {
    }

    @Configuration
    static class TwoUnqualifiedBeansConfig {
        @Bean Rate a() {
            return new First();
        }

        @Bean Rate b() {
            return new Second();
        }
    }

    @PerTestMock(Rate.class)
    @ParallelSpringTest(context = "cl-ambiguous")
    abstract static class AmbiguousBase {
    }

    static class AmbiguousTest extends AmbiguousBase {
    }

    @Test
    void sameNameWithDifferentTypeAndModeReportsTheTwoTypesNotASpyProblem() {
        assertThatThrownBy(() -> BeanDeclarations.classLevelMocks(SameNameTwoTypesAndModes.class)).hasMessageContaining("MOCK_ONLY_CONFLICT")
                .hasMessageContaining("two types").hasMessageNotContaining("SPY_NO_BEAN");
    }

    @Test
    void resolverErrorsAtClassLevelNameTheContextByItsDeclaredNameNotItsInternalKey() {
        AnnotationConfigApplicationContext c = unstarted(AmbiguousTest.class, "cl-ambiguous", TwoUnqualifiedBeansConfig.class);

        assertThatThrownBy(c::refresh).hasStackTraceContaining("BEAN_AMBIGUOUS").hasStackTraceContaining("in context 'cl-ambiguous'")
                .satisfies(e -> {
                    java.io.StringWriter text = new java.io.StringWriter();
                    e.printStackTrace(new java.io.PrintWriter(text));
                    assertThat(text.toString()).doesNotContain("cl-ambiguous@");
                });
    }

    static class FieldWithTypes {
        @PerTestMock(First.class) Rate rate;
    }

    static class ParameterWithTypes {
        void t(@PerTestMock(classes = First.class) Rate rate) {
        }
    }

    @Test
    void invalidClassLevelDeclarationsFailWithStableCodes() throws Exception {
        assertThatThrownBy(() -> BeanDeclarations.classLevelMocks(NoType.class)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("INVALID_DECLARATION").hasMessageContaining("names no type");
        assertThatThrownBy(() -> BeanDeclarations.classLevelMocks(NameWithTwoTypes.class)).hasMessageContaining("INVALID_DECLARATION")
                .hasMessageContaining("2 types");
        assertThatThrownBy(() -> BeanDeclarations.classLevelMocks(SameNameTwoTypes.class)).hasMessageContaining("MOCK_ONLY_CONFLICT")
                .hasMessageContaining("'dup'");
        assertThatThrownBy(() -> BeanDeclarations.classLevelMocks(MockAndSpyOfTheSameType.class)).hasMessageContaining("SPY_NO_BEAN");
        assertThatThrownBy(() -> BeanDeclarations.fieldRequests(FieldWithTypes.class)).hasMessageContaining("INVALID_DECLARATION")
                .hasMessageContaining("value/classes");
        Method m = ParameterWithTypes.class.getDeclaredMethod("t", Rate.class);
        assertThatThrownBy(() -> BeanDeclarations.parameterRequest(m, m.getParameters()[0], 0)).hasMessageContaining("INVALID_DECLARATION")
                .hasMessageContaining("parameter");
    }

    // ---- quy tắc: thật ở lớp này, mock ở lớp kia (không khai cấp class) ----

    @ParallelSpringTest(context = "cl-real-here-mock-there")
    abstract static class SharedBase {
    }

    static class RealHere extends SharedBase {
    }

    static class MockThere extends SharedBase {
        @PerTestMock FromBeanMethod handle;
    }

    @Test
    void realInOneClassAndMockedInAnotherShareOneContextWhenNothingIsDeclaredAtClassLevel() {
        var factory = new ParallelContextCustomizerFactory();

        assertThat(factory.createContextCustomizer(RealHere.class, List.of())).isEqualTo(factory.createContextCustomizer(MockThere.class, List.of()));
        AnnotationConfigApplicationContext c = start(MockThere.class, "cl-real-here-mock-there", MethodConfig.class);
        ManagedContext mc = Fixtures.managed(c);
        assertThat(mc.interceptors.get("fromMethod").isMockOnly()).as("real bean kept: the MockThere handle mocks it per test only").isFalse();
        assertThat(mc.resolveFields(MockThere.class)).extracting(ManagedContext.ResolvedRequest::beanName).containsExactly("fromMethod");
        assertThat(c.getBean(FromBeanMethod.class).rate()).isEqualTo("real");
    }

    // ---- spy: cấp class và trên bean đã bị thay ----

    @PerTestSpy(FromBeanMethod.class)
    @ParallelSpringTest(context = "cl-spy")
    abstract static class SpyBase {
    }

    static class SpyChild extends SpyBase {
        @PerTestSpy FromBeanMethod handle;
    }

    @Test
    void classLevelSpyIsAPerTestRequestWithoutAFieldAndDoesNotChangeTheContextKey() {
        var factory = new ParallelContextCustomizerFactory();

        assertThat(BeanDeclarations.classLevelMocks(SpyChild.class)).isEmpty();
        assertThat(BeanDeclarations.fieldRequests(SpyChild.class)).hasSize(2).extracting(BeanRequest::mode).containsOnly(InterceptMode.SPY);
        assertThat(BeanDeclarations.fieldRequests(SpyChild.class)).extracting(r -> r.field() == null).containsExactlyInAnyOrder(true, false);
        assertThat(factory.createContextCustomizer(SpyChild.class, List.of())).as("spy is per test, not part of the key")
                .isEqualTo(factory.createContextCustomizer(NoSpyInSameContext.class, List.of()));
    }

    @ParallelSpringTest(context = "cl-spy")
    static class NoSpyInSameContext {
    }

    @Test
    void classLevelSpyOnAnExistingBeanResolvesAndOnAMissingBeanFailsWithSpyNoBean() {
        ManagedContext mc = Fixtures.managed(start(SpyBase.class, "cl-spy", MethodConfig.class));

        assertThat(mc.resolveFields(SpyChild.class)).extracting(ManagedContext.ResolvedRequest::beanName).containsOnly("fromMethod");
        ManagedContext empty = Fixtures.managed(start(SpyBase.class, "cl-spy-missing", EmptyConfig.class));
        assertThatThrownBy(() -> empty.resolveFields(SpyChild.class)).hasMessageContaining("SPY_NO_BEAN");
    }

    @PerTestMock(FromBeanMethod.class)
    @ParallelSpringTest(context = "cl-spy-replaced")
    abstract static class ReplacedBase {
    }

    static class SpiesTheReplacedBean extends ReplacedBase {
        @PerTestSpy FromBeanMethod handle;
    }

    @PerTestSpy(FromBeanMethod.class)
    static class ClassSpiesTheReplacedBean extends ReplacedBase {
    }

    @Test
    void spyOnABeanReplacedByAClassLevelMockExplainsThatThereIsNoRealBean() {
        ManagedContext mc = Fixtures.managed(start(ReplacedBase.class, "cl-spy-replaced", MethodConfig.class));

        assertThatThrownBy(() -> mc.resolveFields(SpiesTheReplacedBean.class)).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("SPY_NO_BEAN").hasMessageContaining("REPLACED its real definition").hasMessageContaining("class-level @PerTestMock");
        assertThatThrownBy(() -> BeanDeclarations.fieldRequests(ClassSpiesTheReplacedBean.class)).as("same bean mocked and spied at class level")
                .hasMessageContaining("SPY_NO_BEAN").hasMessageContaining("no real bean");
    }

    @Test
    void spyOnABeanCreatedByAClassLevelMockSaysItWasCreated() {
        ManagedContext mc = Fixtures.managed(start(CreateBase.class, "cl-spy-created", EmptyConfig.class));
        BeanRequest spy = new BeanRequest(null, "T.spy", "missing", org.springframework.core.ResolvableType.forClass(Missing.class), null, InterceptMode.SPY);

        assertThatThrownBy(() -> mc.resolve(spy)).hasMessageContaining("SPY_NO_BEAN").hasMessageContaining("CREATED it");
    }

    // ---- tham số: ghi đè theo name hoặc theo kiểu ----

    static class ParamHolder {
        void byName(@PerTestMock(name = "second") Rate rate) {
        }

        void byType(@PerTestMock Missing missing) {
        }
    }

    @Test
    void aParameterOverridesByNameOrByItsOwnType() throws Exception {
        ManagedContext mc = Fixtures.managed(start(CreateBase.class, "cl-params", TwoBeansConfig.class));
        Method byName = ParamHolder.class.getDeclaredMethod("byName", Rate.class);
        Method byType = ParamHolder.class.getDeclaredMethod("byType", Missing.class);

        assertThat(mc.resolve(BeanDeclarations.parameterRequest(byName, byName.getParameters()[0], 0)).beanName()).isEqualTo("second");
        assertThat(mc.resolve(BeanDeclarations.parameterRequest(byType, byType.getParameters()[0], 0)).beanName()).isEqualTo("missing");
    }

    // ---- khóa context ----

    @PerTestMock(Missing.class)
    @ParallelSpringTest(context = "cl-key")
    static class KeyA {
    }

    @PerTestMock({Missing.class, Consumer.class})
    @ParallelSpringTest(context = "cl-key")
    static class KeyB {
    }

    @PerTestMock(Missing.class)
    @ParallelSpringTest(context = "cl-key")
    static class KeyAgain extends ParentDeclares {
    }

    @Test
    void classLevelMockSetIsPartOfTheContextKeyButNotOfTheNameConflict() {
        var factory = new ParallelContextCustomizerFactory();

        assertThat(factory.createContextCustomizer(KeyA.class, List.of())).isNotEqualTo(factory.createContextCustomizer(KeyB.class, List.of()));
        assertThat(factory.createContextCustomizer(KeyA.class, List.of())).as("same set declared in a superclass too").isEqualTo(factory.createContextCustomizer(KeyAgain.class, List.of()));
        assertThat(factory.createContextCustomizer(KeyA.class, List.of())).isEqualTo(factory.createContextCustomizer(KeyA.class, List.of()));
    }

    @Test
    void contextKeyDiffNamesTheBeanThatDiffers() {
        Map<String, String> a = ContextConfigDescription.describe(configOf(KeyA.class));
        Map<String, String> b = ContextConfigDescription.describe(configOf(KeyB.class));

        assertThat(ContextConfigDescription.differences(a, b)).hasSize(1).first().asString()
                .contains("Consumer").contains("existing: (not declared)").contains("new:      declared");
        assertThat(ContextConfigDescription.differences(b, a)).hasSize(1).first().asString().contains("existing: declared").contains("new:      (not declared)");
    }

    private static org.springframework.test.context.MergedContextConfiguration configOf(Class<?> testClass) {
        var customizer = new ParallelContextCustomizerFactory().createContextCustomizer(testClass, List.of());
        return new org.springframework.test.context.MergedContextConfiguration(testClass, new String[0], new Class<?>[0],
                java.util.Set.<Class<? extends org.springframework.context.ApplicationContextInitializer<?>>>of(), new String[0], new String[0],
                new String[0], java.util.Set.<org.springframework.test.context.ContextCustomizer>of(customizer),
                org.mockito.Mockito.mock(org.springframework.test.context.ContextLoader.class), null, null);
    }
}
