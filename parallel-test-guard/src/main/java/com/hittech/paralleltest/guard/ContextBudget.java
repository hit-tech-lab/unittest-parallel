package com.hittech.paralleltest.guard;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.BootstrapUtils;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Counts the distinct live ApplicationContexts of the JVM. When a test class causes one too many,
 * it fails with the list of existing contexts and a DIFF of what makes its configuration different
 * from the closest one -- the exact thing to change to reuse it.
 */
final class ContextBudget {

    private record Entry(WeakReference<ApplicationContext> ctx, String firstClass, Map<String, String> config) {
        boolean alive() {
            ApplicationContext c = ctx.get();
            return c != null && (!(c instanceof ConfigurableApplicationContext cac) || cac.isActive());
        }

        boolean is(ApplicationContext other) {
            return ctx.get() == other;
        }
    }

    private static final List<Entry> CONTEXTS = new ArrayList<>(); // a handful of entries: a list is enough

    private ContextBudget() {
    }

    /** @return true if this context is seen for the first time. Called for every test instance: kept cheap. */
    static synchronized boolean register(ApplicationContext ctx, Class<?> testClass, int max) {
        for (Entry e : CONTEXTS) {
            if (e.is(ctx)) {
                return false;
            }
        }
        CONTEXTS.removeIf(e -> !e.alive());
        Map<String, String> config = describe(testClass);
        List<Entry> others = List.copyOf(CONTEXTS);
        CONTEXTS.add(new Entry(new WeakReference<>(ctx), testClass.getName(), config));
        if (CONTEXTS.size() > max) {
            StringBuilder detail = new StringBuilder(testClass.getName() + " started context #" + CONTEXTS.size()
                    + " (max-contexts=" + max + "). Existing contexts were started by:");
            others.forEach(e -> detail.append("\n          - ").append(e.firstClass()));
            others.stream().min(Comparator.comparingInt(e -> diff(e.config(), config).size())).ifPresent(closest ->
                    detail.append("\n          Closest is the one of ").append(closest.firstClass())
                            .append("; it differs only in:").append(String.join("", diff(closest.config(), config))));
            throw new ParallelGuardViolation("Context budget exceeded",
                    List.of(new Violation(Rule.PT10, testClass.getName(), detail.toString())));
        }
        return true;
    }

    private static Map<String, String> describe(Class<?> testClass) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            MergedContextConfiguration mcc = BootstrapUtils.resolveTestContextBootstrapper(testClass)
                    .buildMergedContextConfiguration();
            m.put("configuration classes", Arrays.stream(mcc.getClasses()).map(Class::getSimpleName).sorted().toList().toString());
            m.put("locations", Arrays.toString(mcc.getLocations()));
            m.put("active profiles", Arrays.toString(mcc.getActiveProfiles()));
            m.put("properties", Arrays.stream(mcc.getPropertySourceProperties()).sorted().toList().toString());
            m.put("property files", mcc.getPropertySourceDescriptors().toString());
            m.put("initializers", mcc.getContextInitializerClasses().stream().map(Class::getSimpleName).sorted().toList().toString());
            m.put("context customizers (bean overrides, @Import, auto-config...)",
                    mcc.getContextCustomizers().stream().map(Object::toString).sorted().toList().toString());
            m.put("loader", mcc.getContextLoader().getClass().getSimpleName());
            m.put("parent", String.valueOf(mcc.getParent()));
        } catch (RuntimeException e) {
            m.put("error", e.toString());
        }
        return m;
    }

    private static List<String> diff(Map<String, String> a, Map<String, String> b) {
        List<String> out = new ArrayList<>();
        for (String k : b.keySet()) {
            if (!Objects.equals(a.get(k), b.get(k))) {
                out.add("\n            * " + k + ":\n                existing: " + a.get(k) + "\n                this:     " + b.get(k));
            }
        }
        return out;
    }
}
