package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.mock.MockRouter;
import com.hittech.paralleltest.mock.PerTestMockPropagationFilter;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Auto-registered through META-INF/spring.factories. Adds to every test context:
 * a @Primary router per {@code @RoutedMocks} type, the per-test-mock HTTP propagation filter (web
 * contexts) and the Hibernate SQL guard. The customizer is equal for equal routed types, so it
 * never splits the context cache.
 */
public class ParallelGuardContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attrs) {
        if (!GuardSettings.get().enabled()) {
            return null;
        }
        return new GuardCustomizer(Set.copyOf(ClassRules.routedTypes(testClass)), GuardSettings.get().sqlGuard());
    }

    record GuardCustomizer(Set<Class<?>> routedTypes, boolean sqlGuard) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mcc) {
            if (!(context.getBeanFactory() instanceof BeanDefinitionRegistry registry)) {
                return;
            }
            ClassLoader cl = context.getClassLoader();
            for (Class<?> type : routedTypes) {
                registerRouter(registry, type);
            }
            if (!routedTypes.isEmpty()
                    && ClassUtils.isPresent("jakarta.servlet.Filter", cl)
                    && ClassUtils.isPresent("org.springframework.web.context.WebApplicationContext", cl)
                    && WebContexts.isWeb(context)) {
                registry.registerBeanDefinition("parallelTestGuard.propagationFilter",
                        new RootBeanDefinition(PerTestMockPropagationFilter.class));
            }
            if (sqlGuard
                    && ClassUtils.isPresent("org.hibernate.resource.jdbc.spi.StatementInspector", cl)
                    && ClassUtils.isPresent("org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer", cl)) {
                registry.registerBeanDefinition("parallelTestGuard.hibernateSqlGuard",
                        new RootBeanDefinition(HibernateSqlGuard.class));
            }
        }

        private static <T> void registerRouter(BeanDefinitionRegistry registry, Class<T> type) {
            RootBeanDefinition bd = new RootBeanDefinition(type, () -> MockRouter.routerFor(type));
            bd.setPrimary(true);
            registry.registerBeanDefinition("parallelTestGuard.router." + type.getName(), bd);
        }

        @Override
        public String toString() {
            return "ParallelGuard" + routedTypes.stream().map(Class::getSimpleName).sorted().toList();
        }
    }

    /** Isolated so that spring-web is only loaded when present. */
    static final class WebContexts {
        static boolean isWeb(ConfigurableApplicationContext ctx) {
            return ctx instanceof org.springframework.web.context.WebApplicationContext;
        }
    }
}
