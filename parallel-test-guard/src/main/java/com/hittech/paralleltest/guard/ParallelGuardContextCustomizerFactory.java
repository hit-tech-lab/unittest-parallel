package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.mock.MockRouter;
import com.hittech.paralleltest.mock.PerTestMockPropagationFilter;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Auto-registered through META-INF/spring.factories. Adds to every test context: a @Primary router per
 * {@code @RoutedMocks} type, the binding propagation filter (servlet contexts) and the JDBC SQL guard.
 * Routing does NOT depend on {@code enabled}: switching the rules off must never change test behaviour.
 * The customizer is equal for equal routed types, so it never splits the context cache.
 */
public class ParallelGuardContextCustomizerFactory implements ContextCustomizerFactory {

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> attrs) {
        return new GuardCustomizer(Set.copyOf(ClassRules.routedTypes(testClass)));
    }

    record GuardCustomizer(Set<Class<?>> routedTypes) implements ContextCustomizer {

        @Override
        public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration mcc) {
            if (!(context.getBeanFactory() instanceof BeanDefinitionRegistry registry)) {
                return;
            }
            for (Class<?> type : routedTypes) {
                registerRouter(registry, type);
            }
            ClassLoader cl = context.getClassLoader();
            if (ClassUtils.isPresent("jakarta.servlet.Filter", cl)
                    && ClassUtils.isPresent("org.springframework.web.context.WebApplicationContext", cl)
                    && WebContexts.isWeb(context)) {
                registry.registerBeanDefinition("parallelTestGuard.propagationFilter",
                        infrastructure(new RootBeanDefinition(PerTestMockPropagationFilter.class)));
            }
            // always registered: the guard checks enabled / sql-guard at execution time
            registry.registerBeanDefinition("parallelTestGuard.jdbcSqlGuard",
                    infrastructure(new RootBeanDefinition(JdbcSqlGuardPostProcessor.class)));
        }

        private static <T> void registerRouter(BeanDefinitionRegistry registry, Class<T> type) {
            RootBeanDefinition bd = new RootBeanDefinition(type, () -> MockRouter.routerFor(type));
            bd.setPrimary(true);
            registry.registerBeanDefinition(RoutedBeanCheck.ROUTER_PREFIX + type.getName(), bd);
        }

        private static RootBeanDefinition infrastructure(RootBeanDefinition bd) {
            bd.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
            return bd;
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
