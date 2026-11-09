package io.hit.ptest.context;

import io.hit.ptest.intercept.InterceptMode;
import io.hit.ptest.ParallelTestException;
import io.hit.ptest.UnboundPolicy;
import io.hit.ptest.intercept.TestBinding;
import io.hit.ptest.junit.ParallelTestExtension;

import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.annotation.DirtiesContext;

/** Thứ tự BFPP, locale, cấu hình toàn cục, đăng ký mode của lớp lồng nhau, dọn dẹp trạng thái tĩnh, dự phòng ready. */
class LifecycleAndSettingsTest {

    private static final String[] NONE = {};
    static final List<String> ORDER = new ArrayList<>();

    @AfterEach
    void cleanup() {
        System.clearProperty("ptest.max-contexts");
        System.clearProperty("ptest.test-only-key");
        Fixtures.closeAll();
    }

    // ---- thứ tự BFPP (comment trong ParallelContextCustomizer) ----

    @Configuration(proxyBeanMethods = false)
    static class OrderConfig {
        @Bean static BeanDefinitionRegistryPostProcessor registryPostProcessor() {
            return new BeanDefinitionRegistryPostProcessor() {
                @Override
                public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) {
                    ORDER.add("registryPP active=" + ContextRegistry.INSTANCE.activeCount());
                }

                @Override
                public void postProcessBeanFactory(org.springframework.beans.factory.config.ConfigurableListableBeanFactory bf) {
                }
            };
        }

        @Bean static BeanFactoryPostProcessor beanDeclaredBfpp() {
            return bf -> ORDER.add("beanBfpp active=" + ContextRegistry.INSTANCE.activeCount());
        }
    }

    @Test
    void programmaticRegistrationRunsAfterRegistryPostProcessorsAndBeforeBeanDeclaredBfpps() {
        ORDER.clear();
        int before = ContextRegistry.INSTANCE.activeCount();
        Fixtures.started("order", NONE, Map.of(), OrderConfig.class);

        assertThat(ORDER).containsExactly("registryPP active=" + before, "beanBfpp active=" + (before + 1));
    }

    // ---- Locale + giá trị sai ----

    @Test
    void enumAndModeParsingIsLocaleIndependent() {
        java.util.Locale old = java.util.Locale.getDefault();
        java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR")); // "I".toLowerCase() = dotless i
        try {
            AnnotationConfigApplicationContext c = Fixtures.started("turkish", new String[] {"classSvc"},
                    Map.of("ptest.unbound-policy", "GUARDED", "ptest.db.mode", "H2"), Fixtures.Beans.class);
            assertThat(Fixtures.managed(c).policy).isEqualTo(UnboundPolicy.GUARDED);
            assertThat(Fixtures.managed(c).dbMode).isEqualTo("h2");
        } finally {
            java.util.Locale.setDefault(old);
        }
    }

    @Test
    void unknownPolicyIsAConfigErrorNotAnIllegalArgumentException() {
        assertThatThrownBy(() -> Fixtures.context("bad-policy", NONE, NONE, Map.of("ptest.unbound-policy", "sometimes"), Fixtures.Beans.class))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("unknown ptest.unbound-policy=sometimes");
        assertThatThrownBy(() -> Fixtures.context("bad-mode", NONE, NONE, Map.of("ptest.db.mode", "oracle"), Fixtures.Beans.class))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("unknown ptest.db.mode=oracle");
    }

    @Test
    void nonNumericMaxContextsIsAConfigError() {
        System.setProperty("ptest.max-contexts", "lots");
        assertThatThrownBy(PtestSettings::maxContexts).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("ptest.max-contexts=lots is not an integer");
    }

    @Test
    void settingsPrecedenceMatchesSpringPropertiesFileBeforeSystemProperty() {
        // src/test/resources/spring.properties: ptest.test-only-key=from-file
        System.setProperty("ptest.test-only-key", "from-system");

        assertThat(PtestSettings.get("ptest.test-only-key", "default")).as("same order as SpringProperties.getProperty").isEqualTo("from-file");
        assertThat(PtestSettings.get("ptest.absent-key", "default")).isEqualTo("default");
    }

    // ---- @Nested ----

    static class Outer {
        class Inner {
            void method() {
            }
        }
    }

    @DirtiesContext
    static class DirtyOuter {
        class Inner {
        }
    }

    @Test
    void checkUnsupportedLooksAtEnclosingClassesOfNestedTests() {
        assertThatThrownBy(() -> ParallelTestExtension.checkUnsupported(DirtyOuter.Inner.class))
                .isInstanceOf(ParallelTestException.Unsupported.class).hasMessageContaining("DIRTIES_CONTEXT_UNSUPPORTED");
        ParallelTestExtension.checkUnsupported(Outer.Inner.class); // đối chứng: không có gì không hỗ trợ
    }

    // ---- dọn dẹp trạng thái tĩnh ----

    @Test
    void closingAContextForgetsItsEverInterceptedKeys() {
        AnnotationConfigApplicationContext c = Fixtures.started("forget", new String[] {"classSvc"}, Map.of(), Fixtures.Beans.class);
        String key = Fixtures.managed(c).key;
        Fixtures.bindMock(c, "classSvc");
        assertThat(TestBinding.hasEverBeenNonReal(key, "classSvc")).isTrue();
        assertThat(TestBinding.trackedKeys(key)).isEqualTo(1);

        c.close();

        assertThat(TestBinding.hasEverBeenNonReal(key, "classSvc")).isFalse();
        assertThat(TestBinding.trackedKeys(key)).isZero();
    }

    // ---- ready dự phòng ----

    @Test
    void readyFallbackEnablesUnboundRulesWhenTheReadyEventNeverArrives() throws Exception {
        AnnotationConfigApplicationContext c = Fixtures.started("no-ready-event", new String[] {"classSvc"}, Map.of(), Fixtures.Beans.class);
        ManagedContext base = Fixtures.managed(c);
        ManagedContext bootLike = new ManagedContext(base.key, base.name, c, UnboundPolicy.FAIL, true);
        assertThat(bootLike.ready).as("Boot loader: waits for ApplicationReadyEvent which was never published").isFalse();

        bootLike.ensureReady(); // beforeEach của một test bảo đảm context đã khởi động xong

        assertThat(bootLike.ready).isTrue();
        bootLike.ensureReady(); // idempotent
    }
}
