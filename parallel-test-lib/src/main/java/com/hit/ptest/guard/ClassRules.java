package com.hit.ptest.guard;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.SuppressGuard;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import org.springframework.core.annotation.MergedAnnotation;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.core.annotation.MergedAnnotations.SearchStrategy;

/**
 * Các luật TĨNH của một lớp test, kiểm tra được không cần khởi động context: reflection (PT01, PT03, PT06, PT08, PT13)
 * và bytecode (PT07). Kết quả CHƯA áp mức FAIL/WARN/OFF (xem {@link GuardEnforcer}); đã loại các luật bị miễn trừ
 * bằng {@link SuppressGuard} hoặc JUnit {@code @Isolated}/{@code @ResourceLock}/{@code @Execution(SAME_THREAD)}.
 *
 * <p>GIỚI HẠN: PT03/PT06/PT13 chỉ nhìn khai báo trên chính các lớp của người dùng (không nhìn vào thư viện); PT06 chỉ bắt
 * field {@code static} KHÔNG final (object mutable khai báo {@code static final} không bị phát hiện); PT13 coi field
 * không final và không gắn annotation tiêm (Autowired, Value, ...) là state mutable; PT07 là heuristic, xem
 * {@link CallScanner}.
 */
public final class ClassRules {

    static final Set<String> SHARED_MOCK_ANNOTATIONS = Set.of(
            "org.springframework.test.context.bean.override.mockito.MockitoBean",
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBean",
            "org.springframework.test.context.bean.override.mockito.MockitoBeans",
            "org.springframework.test.context.bean.override.mockito.MockitoSpyBeans",
            "org.springframework.boot.test.mock.mockito.MockBean",
            "org.springframework.boot.test.mock.mockito.SpyBean");
    /** Field do Spring/JUnit/thư viện gán (không phải state của test) nên không tính là state mutable của PT13; PER_CLASS + song song với PerTestMock/PerTestSpy có lỗi CONFIG riêng. */
    private static final Set<String> INJECTED = Set.of("Autowired", "Resource", "Inject", "Value", "LocalServerPort",
            "LocalManagementPort", "TempDir", "PersistenceContext", "PersistenceUnit", "PerTestMock", "PerTestSpy");
    /** Field static do JUnit/Testcontainers gán qua annotation (khai báo không final là bắt buộc hoặc hợp lệ) nên PT06 bỏ qua. */
    private static final Set<String> INJECTED_STATIC = Set.of("TempDir", "RegisterExtension", "Container");
    private static final String SPRING_BOOT_TEST = "org.springframework.boot.test.context.SpringBootTest";
    private static final String BOOTSTRAP_WITH = "org.springframework.test.context.BootstrapWith";
    private static final String SPRING_EXTENSION = "org.springframework.test.context.junit.jupiter.SpringExtension";
    private static final String EXTEND_WITH = "org.junit.jupiter.api.extension.ExtendWith";
    private static final String TESTABLE = "org.junit.platform.commons.annotation.Testable";
    private static final String JUNIT_ISOLATED = "org.junit.jupiter.api.parallel.Isolated";
    private static final String JUNIT_RESOURCE_LOCK = "org.junit.jupiter.api.parallel.ResourceLock";
    private static final String JUNIT_RESOURCE_LOCKS = "org.junit.jupiter.api.parallel.ResourceLocks";
    private static final String JUNIT_TEST_INSTANCE = "org.junit.jupiter.api.TestInstance";
    private static final String JUNIT_EXECUTION = "org.junit.jupiter.api.parallel.Execution";

    private ClassRules() {
    }

    private static boolean injectedByFramework(Field f) {
        return Arrays.stream(f.getDeclaredAnnotations()).anyMatch(a -> INJECTED_STATIC.contains(a.annotationType().getSimpleName()));
    }

    /** Luật tĩnh của một lớp test (lớp được extension ptest quản lý hoặc lớp Spring test trong quét source set). */
    public static List<Violation> check(Class<?> testClass) {
        GuardStats.CLASSES_CHECKED.increment();
        List<Violation> v = new ArrayList<>();
        boolean isolated = isJUnitIsolated(testClass);
        boolean resourceLocked = has(testClass, JUNIT_RESOURCE_LOCK) || has(testClass, JUNIT_RESOURCE_LOCKS);
        List<Class<?>> chain = userHierarchy(testClass);

        for (Class<?> c : chain) {
            if (!isolated && !resourceLocked) {
                for (String a : SHARED_MOCK_ANNOTATIONS) {
                    if (MergedAnnotations.from(c, SearchStrategy.DIRECT).isPresent(a)) {
                        v.add(new Violation(GuardRule.PT03, c.getName(), "@" + simple(a) + " on the class"));
                    }
                }
                for (Field f : c.getDeclaredFields()) {
                    for (String a : SHARED_MOCK_ANNOTATIONS) {
                        if (MergedAnnotations.from(f).isPresent(a)) {
                            v.add(new Violation(GuardRule.PT03, c.getName() + "." + f.getName(),
                                    "@" + simple(a) + " " + f.getType().getSimpleName() + " " + f.getName()));
                        }
                    }
                }
            }
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (!isolated && Modifier.isStatic(mod) && !Modifier.isFinal(mod) && !f.isSynthetic() && !f.getName().startsWith("$")
                        && !injectedByFramework(f)) {
                    v.add(new Violation(GuardRule.PT06, c.getName() + "." + f.getName(),
                            "static " + f.getType().getSimpleName() + " " + f.getName() + " is not final"));
                }
            }
            MergedAnnotation<?> sbt = MergedAnnotations.from(c, SearchStrategy.DIRECT).get(SPRING_BOOT_TEST);
            if (sbt.isPresent() && "DEFINED_PORT".equals(String.valueOf(sbt.getValue("webEnvironment").orElse("")))) {
                v.add(new Violation(GuardRule.PT08, c.getName(), "@SpringBootTest(webEnvironment = DEFINED_PORT)"));
            }
        }

        if (perClassLifecycle(testClass) && methodsMayRunConcurrently(testClass, isolated || resourceLocked)) {
            List<String> mutable = mutableInstanceFields(chain);
            if (!mutable.isEmpty()) {
                v.add(new Violation(GuardRule.PT13, testClass.getName(),
                        "@TestInstance(PER_CLASS) (or junit.jupiter.testinstance.lifecycle.default=per_class) while methods run "
                                + "concurrently; mutable instance fields: " + mutable));
            }
        }

        if (!isolated) {
            for (Class<?> c : chain) {
                v.addAll(CallScanner.scan(c, GuardSettings.forbiddenCalls()));
            }
        }
        return filterSuppressed(testClass, v);
    }

    /** Lớp trong mã nguồn test chỉ đóng vai hàm tiện ích: chỉ quét lời gọi bị cấm (PT07), bỏ qua miễn trừ và isolated. */
    public static List<Violation> checkHelper(Class<?> helper) {
        if (isJUnitIsolated(helper)) {
            return List.of();
        }
        return filterSuppressed(helper, CallScanner.scan(helper, GuardSettings.forbiddenCalls()));
    }

    /** PT01: lớp Spring test không có {@code @ParallelSpringTest} (qua cơ chế quét source set). */
    public static List<Violation> checkManaged(Class<?> testClass) {
        if (!isSpringTest(testClass) || hasParallelSpringTest(testClass)) {
            return List.of();
        }
        return filterSuppressed(testClass, List.of(new Violation(GuardRule.PT01, testClass.getName(),
                "Spring test without @ParallelSpringTest (checked: " + names(userHierarchy(outermost(testClass))) + ")")));
    }

    public static List<Violation> filterSuppressed(Class<?> testClass, List<Violation> all) {
        Set<String> suppressed = suppressedRules(testClass);
        return all.stream().filter(v -> !suppressed.contains(v.rule().name())).distinct().toList();
    }

    public static Set<String> suppressedRules(Class<?> testClass) {
        Set<String> out = new java.util.HashSet<>();
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            SuppressGuard s = c.getDeclaredAnnotation(SuppressGuard.class);
            if (s != null) {
                if (s.reason().isBlank()) {
                    throw new com.hit.ptest.ParallelTestException.Config("CONFIG: @SuppressGuard on " + c.getName()
                            + " needs a non-blank reason");
                }
                out.addAll(Arrays.asList(s.rules()));
            }
        }
        return out;
    }

    public static boolean isSpringTest(Class<?> c) {
        for (Class<?> h : hierarchyAndEnclosing(c)) {
            MergedAnnotations a = MergedAnnotations.from(h, SearchStrategy.DIRECT);
            if (a.isPresent(BOOTSTRAP_WITH) || a.isPresent(ParallelSpringTest.class)) {
                return true;
            }
            if (a.stream(EXTEND_WITH).anyMatch(e -> Arrays.stream(e.getClassArray("value"))
                    .anyMatch(x -> x.getName().equals(SPRING_EXTENSION)))) {
                return true;
            }
        }
        return false;
    }

    static boolean hasParallelSpringTest(Class<?> c) {
        for (Class<?> h : hierarchyAndEnclosing(c)) {
            if (MergedAnnotations.from(h, SearchStrategy.DIRECT).isPresent(ParallelSpringTest.class)) {
                return true;
            }
        }
        return false;
    }

    /** Lớp có method test (kể cả trong lớp @Nested). */
    public static boolean hasTests(Class<?> c) {
        for (Class<?> h = c; h != null && h != Object.class; h = h.getSuperclass()) {
            for (Method m : h.getDeclaredMethods()) {
                if (MergedAnnotations.from(m).isPresent(TESTABLE)) {
                    return true;
                }
            }
        }
        for (Class<?> nested : c.getDeclaredClasses()) {
            if (!Modifier.isStatic(nested.getModifiers()) && hasTests(nested)) {
                return true;
            }
        }
        return false;
    }

    public static boolean isJUnitIsolated(Class<?> testClass) {
        return has(testClass, JUNIT_ISOLATED);
    }

    private static List<String> mutableInstanceFields(List<Class<?>> chain) {
        List<String> out = new ArrayList<>();
        for (Class<?> c : chain) {
            for (Field f : c.getDeclaredFields()) {
                int mod = f.getModifiers();
                if (Modifier.isStatic(mod) || Modifier.isFinal(mod) || f.isSynthetic() || f.getName().startsWith("$")) {
                    continue;
                }
                boolean injected = Arrays.stream(f.getDeclaredAnnotations())
                        .anyMatch(a -> INJECTED.contains(a.annotationType().getSimpleName()));
                if (!injected) {
                    out.add(c.getSimpleName() + "." + f.getName());
                }
            }
        }
        return out;
    }

    private static boolean perClassLifecycle(Class<?> testClass) {
        MergedAnnotation<?> ti = firstOf(testClass, JUNIT_TEST_INSTANCE);
        return ti.isPresent() ? "PER_CLASS".equals(String.valueOf(ti.getValue("value").orElse("")))
                : JUnitConfig.perClassLifecycleByDefault();
    }

    /**
     * Các method của lớp (kể cả lớp {@code @Nested} lồng nhau) có thể chạy đồng thời hay không; dùng chung cho PT13 và cho extension
     * khi quyết định PER_CLASS có được gán mock/spy vào field không. Lớp {@code @Nested} theo {@code mode.default} như method (JUnit chỉ dùng {@code mode.classes.default} cho lớp cấp cao nhất).
     */
    public static boolean methodsMayRunConcurrently(Class<?> testClass) {
        return methodsMayRunConcurrently(testClass, isJUnitIsolated(testClass) || has(testClass, JUNIT_RESOURCE_LOCK)
                || has(testClass, JUNIT_RESOURCE_LOCKS));
    }

    /** Giống JUnit: lock và @Isolated ép SAME_THREAD cho con, nếu không thì @Execution, nếu không thì mặc định toàn cục. */
    private static boolean methodsMayRunConcurrently(Class<?> testClass, boolean lockedOrIsolated) {
        if (lockedOrIsolated || !JUnitConfig.parallelEnabled()) {
            return false;
        }
        MergedAnnotation<?> ex = firstOf(testClass, JUNIT_EXECUTION);
        if (ex.isPresent()) {
            return "CONCURRENT".equals(String.valueOf(ex.getValue("value").orElse("")));
        }
        return JUnitConfig.methodsConcurrentByDefault();
    }

    private static MergedAnnotation<?> firstOf(Class<?> testClass, String annotation) {
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            MergedAnnotation<?> a = MergedAnnotations.from(c, SearchStrategy.DIRECT).get(annotation);
            if (a.isPresent()) {
                return a;
            }
        }
        return MergedAnnotation.missing();
    }

    private static boolean has(Class<?> testClass, String annotation) {
        for (Class<?> c : hierarchyAndEnclosing(testClass)) {
            if (MergedAnnotations.from(c, SearchStrategy.DIRECT).isPresent(annotation)) {
                return true;
            }
        }
        return false;
    }

    static List<Class<?>> hierarchyAndEnclosing(Class<?> testClass) {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c = testClass; c != null; c = isInner(c) ? c.getEnclosingClass() : null) {
            out.addAll(userHierarchy(c));
        }
        return out;
    }

    private static Class<?> outermost(Class<?> c) {
        Class<?> current = c;
        while (isInner(current)) {
            current = current.getEnclosingClass();
        }
        return current;
    }

    private static boolean isInner(Class<?> c) {
        return c.getEnclosingClass() != null && !Modifier.isStatic(c.getModifiers());
    }

    /** Lớp và các lớp cha, bỏ lớp JDK / framework. */
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

    private static String simple(String fqcn) {
        return fqcn.substring(fqcn.lastIndexOf('.') + 1);
    }

    private static String names(List<Class<?>> classes) {
        return classes.stream().map(Class::getSimpleName).toList().toString();
    }
}
