package com.hittech.paralleltest.guard;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

/**
 * Rule PT14: the router is @Primary, which only wins for injection points of the ROUTED type without
 * qualifier. Beans injecting the implementation by concrete class, by @Qualifier/name, or all
 * implementations as a collection, silently receive the REAL bean. Detected through the bean factory's
 * dependency graph once per new context.
 */
final class RoutedBeanCheck {

    static final String ROUTER_PREFIX = "parallelTestGuard.router.";

    private RoutedBeanCheck() {
    }

    static List<Violation> check(ApplicationContext ctx, Set<Class<?>> routedTypes, Class<?> testClass) {
        List<Violation> out = new ArrayList<>();
        if (!(ctx instanceof ConfigurableApplicationContext cac)) {
            return out;
        }
        ConfigurableListableBeanFactory bf = cac.getBeanFactory();
        for (Class<?> type : routedTypes) {
            for (String name : bf.getBeanNamesForType(type, true, false)) {
                if (name.startsWith(ROUTER_PREFIX)) {
                    continue;
                }
                String[] dependents = bf.getDependentBeans(name);
                if (dependents.length > 0) {
                    out.add(new Violation(Rule.PT14, "bean '" + name + "' (" + bf.getType(name) + "), context of "
                            + testClass.getName(),
                            "injected directly into " + Arrays.toString(dependents) + ": those beans call the real "
                                    + type.getSimpleName() + ", not the per-test mock"));
                }
            }
        }
        return out;
    }
}
