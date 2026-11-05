package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.annotation.IsolatedContext;
import com.hittech.paralleltest.annotation.SharedContextBase;
import com.hittech.paralleltest.mock.PerTestMock;
import com.hittech.paralleltest.mock.RoutedMocks;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/** Rules checkable by reflection on a Spring test class (PT01-PT09) plus bytecode call rules (PT07). */
public final class ClassRules {

    /** Class-level annotations (directly or meta-present) that change the context cache key. */
    static final List<String> CONTEXT_SHAPING = List.of(
            "org.springframework.test.context.BootstrapWith",              // @SpringBootTest, @DataJpaTest, @WebMvcTest...
            "org.springframework.test.context.ContextConfiguration",
            "org.springframework.test.context.ContextHierarchy",
            "org.springframework.test.context.TestPropertySource",
            "org.springframework.test.context.TestPropertySources",
            "org.springframework.test.context.ActiveProfiles",
            "org.springframework.test.context.ContextCustomizerFactories",
            "org.springframework.test.context.web.WebAppConfiguration",
            "org.springframework.context.annotation.Import",
            "org.springframework.boot.autoconfigure.ImportAutoConfiguration", // @AutoConfigureMockMvc & co
            RoutedMocks.class.getName());

    static final String BEAN_OVERRIDE = "org.springframework.test.context.bean.override.BeanOverride";
    static final Set<String> SHARED_MOCK_ANNOTATIONS = Set.of(
            "org.springframework.test.context.bean.override.mockito.MockitoBean",
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBean",
            "org.springframework.test.context.bean.override.mockito.MockitoBeans",
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBeans",
            "org.springframework.boot.test.mock.mockito.MockBean",
            "org.springframework.boot.test.mock.mockito.SpyBean");
    static final String DIRTIES_CONTEXT = "org.springframework.test.annotation.DirtiesContext";
    static final String DYNAMIC_PROPERTY_SOURCE = "org.springframework.test.context.DynamicPropertySource";
    static final String CONFIGURATION = "org.springframework.context.annotation.Configuration";
    static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    static final String TEST_EXECUTION_LISTENERS = "org.springframework.test.context.TestExecutionListeners";
    static final String JUNIT_ISOLATED = "org.junit.jupiter.api.parallel.Isolated";
    static final String JUNIT_RESOURCE_LOCK = "org.junit.jupiter.api.parallel.ResourceLock";
    static final String JUNIT_RESOURCE_LOCKS = "org.junit.jupiter.api.parallel.ResourceLocks";

    private static final Map<Class<?>, List<Violation>> CACHE = new ConcurrentHashMap<>();

    private ClassRules() {
    }

    /** Cached: a class is analysed once per JVM. */
    public static List<Violation> check(Class<?> testClass, GuardSettings settings) {
        return CACHE.computeIfAbsent(testClass, c -> doCheck(c, settings));
    }

    static List<Violation> doCheck(Class<?> testClass, GuardSettings settings) {
        List<Violation> v = new ArrayList<>();
        Class<?> configOwner = outermostForNested(testClass);
        boolean isolated = isJUnitIsolated(testClass);
        boolean resourceLocked = hasInHierarchyOrEnclosing(testClass, JUNIT_RESOURCE_LOCK)
                || hasInHierarchyOrEnclosing(testClass, JUNIT_RESOURCE_LOCKS);
        boolean isolatedContext = findInHierarchyOrEnclosing(testClass, IsolatedContext.class) != null;

        List<Class<?>> chain = userHierarchy(testClass);
        Class<?> base = null;
        for (Class<?> c : userHierarchy(configOwner)) {
            if (c.isAnnotationPresent(SharedContextBase.class)) {
                base = c;
                break;
            }
        }

        // PT01 / PT02: the context configuration must come from the shared base only
        if (!isolatedContext) {
            if (base == null) {
                v.add(new Violation(Rule.PT01, testClass.getName(),
                        "no superclass annotated @SharedContextBase (checked: " + names(userHierarchy(configOwner)) + ")"));
            } else {
                for (Class<?> c : chain) {
                    if (c == base || isSuperclassOf(c, base)) {
                        break;
                    }
                    contextChangesDeclaredOn(c, v);
                }
            }
        }

        Set<Class<?>> routed = routedTypes(testClass);
        for (Class<?> c : chain) {
            // PT03 shared Mockito bean (field- or type-level)
            if (!isolated && !resourceLocked) {
                for (String a : SHARED_MOCK_ANNOTATIONS) {
                    if (MergedAnnotations.from(c, SearchStrategy.DIRECT).isPresent(a)) {
                        v.add(new Violation(Rule.PT03, c.getName(), "@" + simple(a) + " on the class"));
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    for (String a : SHARED_MOCK_ANNOTATIONS) {
                        if (MergedAnnotations.from(f).isPresent(a)) {
                            v.add(new Violation(Rule.PT03, c.getName() + "." + f.getName(),
                                    "@" + simple(a) + " " + f.getType().getSimpleName() + " " + f.getName()));
                        }
                    }
                }
            }
            // PT04 @DirtiesContext
            if (!isolated) {
                if (MergedAnnotations.from(c, SearchStrategy.DIRECT).isPresent(DIRTIES_CONTEXT)) {
                    v.add(new Violation(Rule.PT04, c.getName(), "@DirtiesContext on the class"));
                }
                for (Method m : c.getDeclaredMethods()) {
                    if (MergedAnnotations.from(m).isPresent(DIRTIES_CONTEXT)) {
                        v.add(new Violation(Rule.PT04, c.getName() + "." + m.getName() + "()", "@DirtiesContext on the method"));
                    }
                }
            }
            // PT05 / PT06 fields
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(PerTestMock.class) && !routed.contains(f.getType())) {
                    v.add(new Violation(Rule.PT05, c.getName() + "." + f.getName(),
                            f.getType().getName() + " is not listed in @RoutedMocks " + names(new ArrayList<>(routed))));
                }
                int mod = f.getModifiers();
                if (!isolated && Modifier.isStatic(mod) && !Modifier.isFinal(mod) && !f.isSynthetic()
                        && !f.getName().startsWith("$")) {
                    v.add(new Violation(Rule.PT06, c.getName() + "." + f.getName(),
                            "static " + f.getType().getSimpleName() + " " + f.getName() + " is not final"));
                }
            }
            // PT08 fixed port
            MergedAnnotation<?> sbt = MergedAnnotations.from(c, SearchStrategy.DIRECT).get(SPRING_BOOT_TEST);
            if (sbt.isPresent() && "DEFINED_PORT".equals(String.valueOf(sbt.getValue("webEnvironment").orElse("")))) {
                v.add(new Violation(Rule.PT08, c.getName(), "@SpringBootTest(webEnvironment = DEFINED_PORT)"));
            }
            // PT09 listeners replaced (only reachable by the scanner: at runtime our listener would not run)
            MergedAnnotation<?> tel = MergedAnnotations.from(c, SearchStrategy.DIRECT).get(TEST_EXECUTION_LISTENERS);
            if (tel.isPresent() && !"MERGE_WITH_DEFAULTS".equals(String.valueOf(tel.getValue("mergeMode").orElse("")))) {
                v.add(new Violation(Rule.PT09, c.getName(), "@TestExecutionListeners without mergeMode = MERGE_WITH_DEFAULTS"));
            }
        }

        // PT07 forbidden calls in the bytecode of the test class and its superclasses (user code only)
        if (!isolated) {
            for (Class<?> c : chain) {
                v.addAll(CallScanner.scan(c, settings.forbiddenCalls()));
            }
        }
        return List.copyOf(v);
    }

    private static void contextChangesDeclaredOn(Class<?> c, List<Violation> v) {
        MergedAnnotations direct = MergedAnnotations.from(c, SearchStrategy.DIRECT);
        for (String a : CONTEXT_SHAPING) {
            if (direct.isPresent(a)) {
                v.add(new Violation(Rule.PT02, c.getName(), "declares (or is meta-annotated with) @" + simple(a)));
            }
        }
        if (direct.isPresent(BEAN_OVERRIDE)) {
            v.add(new Violation(Rule.PT02, c.getName(), "declares a bean override annotation on the class"));
        }
        for (Field f : c.getDeclaredFields()) {
            if (MergedAnnotations.from(f).isPresent(BEAN_OVERRIDE)) {
                v.add(new Violation(Rule.PT02, c.getName() + "." + f.getName(),
                        "bean override field (@MockitoBean/@TestBean...) adds a context customizer"));
            }
        }
        for (Method m : c.getDeclaredMethods()) {
            if (MergedAnnotations.from(m).isPresent(DYNAMIC_PROPERTY_SOURCE)) {
                v.add(new Violation(Rule.PT02, c.getName() + "." + m.getName() + "()", "@DynamicPropertySource method"));
            }
        }
        for (Class<?> nested : c.getDeclaredClasses()) {
            if (Modifier.isStatic(nested.getModifiers())
                    && MergedAnnotations.from(nested, SearchStrategy.DIRECT).isPresent(CONFIGURATION)) {
                v.add(new Violation(Rule.PT02, nested.getName(),
                        "nested @Configuration/@TestConfiguration is picked up into the test context"));
            }
        }
    }

    static Set<Class<?>> routedTypes(Class<?> testClass) {
        Set<Class<?>> types = new LinkedHashSet<>();
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            RoutedMocks r = c.getAnnotation(RoutedMocks.class);
            if (r != null) {
                types.addAll(List.of(r.value()));
            }
        }
        return types;
    }

    static boolean isJUnitIsolated(Class<?> testClass) {
        return hasInHierarchyOrEnclosing(testClass, JUNIT_ISOLATED);
    }

    private static boolean hasInHierarchyOrEnclosing(Class<?> testClass, String annotation) {
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            if (MergedAnnotations.from(c, SearchStrategy.DIRECT).isPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    private static <A extends java.lang.annotation.Annotation> A findInHierarchyOrEnclosing(Class<?> testClass, Class<A> type) {
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            A a = c.getAnnotation(type);
            if (a != null) {
                return a;
            }
        }
        return null;
    }

    static List<Class<?>> hierarchyAndEnclosing(Class<?> testClass) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c = testClass; c != null; c = isInner(c) ? c.getEnclosingClass() : null) {
            out.addAll(userHierarchy(c));
        }
        return out;
    }

    /** @Nested (inner) classes inherit the configuration of their enclosing class. */
    private static Class<?> outermostForNested(Class<?> c) {
        Class<?> current = c;
        while (isInner(current)) {
            current = current.getEnclosingClass();
        }
        return current;
    }

    private static boolean isInner(Class<?> c) {
        return c.getEnclosingClass() != null && !Modifier.isStatic(c.getModifiers());
    }

    /** The class and its superclasses, excluding JDK / framework classes. */
    static List<Class<?>> userHierarchy(Class<?> testClass) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c = testClass; c != null && c != Object.class; c = c.getSuperclass()) {
            String n = c.getName();
            if (n.startsWith("java.") || n.startsWith("org.springframework.") || n.startsWith("org.junit.")) {
                break;
            }
            out.add(c);
        }
        return out;
    }

    private static boolean isSuperclassOf(Class<?> c, Class<?> base) {
        return c.isAssignableFrom(base) && c != base;
    }

    private static String simple(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static String names(List<Class<?>> classes) {
        return classes.stream().map(Class::getSimpleName).toList().toString();
    }
}
