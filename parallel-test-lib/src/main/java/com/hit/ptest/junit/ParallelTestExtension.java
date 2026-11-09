package com.hit.ptest.junit;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.ParallelTestException;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;
import com.hit.ptest.context.BeanDeclarations;
import com.hit.ptest.context.BeanRequest;
import com.hit.ptest.context.ContextRegistry;
import com.hit.ptest.context.ManagedContext;
import com.hit.ptest.context.ManagedContext.ResolvedRequest;
import com.hit.ptest.context.PtestSettings;
import com.hit.ptest.guard.ClassRules;
import com.hit.ptest.guard.GuardEnforcer;
import com.hit.ptest.guard.GuardScope;
import com.hit.ptest.guard.IsolationTracker;
import com.hit.ptest.guard.JUnitConfig;
import com.hit.ptest.guard.PoolSizeCheck;
import com.hit.ptest.guard.Violation;
import com.hit.ptest.intercept.TestBinding;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.api.extension.ExtensionContext.Namespace;
import org.junit.jupiter.api.extension.InvocationInterceptor;
import org.junit.jupiter.api.extension.ReflectiveInvocationContext;
import org.junit.jupiter.api.extension.ParameterContext;
import org.junit.jupiter.api.extension.ParameterResolutionException;
import org.junit.jupiter.api.extension.ParameterResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.annotation.MergedAnnotations;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestContextAnnotationUtils;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.util.ReflectionUtils;

/**
 * Vòng đời của một test: chặn tính năng không hỗ trợ (beforeAll), mở binding theo chế độ hiệu lực của test và gắn
 * vào thread (beforeEach), tiêm mock/spy vào field/tham số, đóng binding (afterEach).
 */
public final class ParallelTestExtension implements BeforeAllCallback, AfterAllCallback, BeforeEachCallback,
        AfterEachCallback, ParameterResolver, InvocationInterceptor {

    private static final Logger LOG = LoggerFactory.getLogger(ParallelTestExtension.class);
    private static final Namespace NS = Namespace.create(ParallelTestExtension.class);
    private static final String KEY_STATE = "state";
    private static final String KEY_CLASS_BINDING = "classBinding";
    private static final String TX_SPRING = "org.springframework.transaction.annotation.Transactional";
    private static final String TX_JAKARTA = "jakarta.transaction.Transactional";

    private static final String KEY_ISOLATED = "isolatedTracked";
    /** (lớp, khóa context) đã kiểm tra pool: mỗi cặp chỉ kiểm tra một lần. */
    private static final Set<String> POOL_CHECKED = ConcurrentHashMap.newKeySet();

    static {
        // Guard không biết cơ chế can thiệp: chỉ hỏi "thread này thuộc test nào" qua id của binding.
        GuardScope.useOwnerResolver(() -> {
            TestBinding b = TestBinding.current();
            return b == null ? null : b.id();
        });
    }

    private record State(TestBinding binding, TestBinding previous, Map<String, Object> handles, List<ResolvedRequest> fields) {
    }

    /** Binding cấp lớp và binding đã gắn trước đó trên thread (để khôi phục khi lớp lồng nhau/work-stealing). */
    private record ClassState(TestBinding binding, TestBinding previous) {
    }

    // ---- không hỗ trợ ----

    @Override
    public void beforeAll(ExtensionContext ec) {
        Class<?> testClass = ec.getRequiredTestClass();
        JUnitConfig.capture(ec::getConfigurationParameter);
        checkUnsupported(testClass);
        GuardEnforcer.enforce(testClass.getName() + " is not parallel-safe", ClassRules.check(testClass));
        if (ClassRules.isJUnitIsolated(testClass)) {
            IsolationTracker.started(); // lớp @Isolated chạy một mình: SQL cả bảng không ảnh hưởng test khác
            ec.getStore(NS).put(KEY_ISOLATED, Boolean.TRUE);
        }
        // Trong @BeforeAll chưa có test cụ thể: binding rỗng => mọi bean là REAL, xác định và không phụ thuộc thread trước đó.
        TestBinding classBinding = TestBinding.open("class:" + testClass.getSimpleName(), Map.of());
        TestBinding previous = TestBinding.bind(classBinding);
        ec.getStore(NS).put(KEY_CLASS_BINDING, new ClassState(classBinding, previous));
    }

    @Override
    public void afterAll(ExtensionContext ec) {
        if (ec.getStore(NS).remove(KEY_ISOLATED) != null) {
            IsolationTracker.finished();
        }
        ClassState cs = ec.getStore(NS).remove(KEY_CLASS_BINDING, ClassState.class);
        if (cs != null) {
            // khôi phục binding trước đó của thread (lớp cha của @Nested, hoặc lớp khác mà thread đang "steal"); luôn đóng binding của mình
            if (TestBinding.current() == cs.binding()) {
                TestBinding.bind(cs.previous());
            }
            cs.binding().close();
        }
    }

    public static void checkUnsupported(Class<?> testClass) {
        boolean warnOnly = PtestSettings.unsupportedWarnOnly();
        Method[] methods = ReflectionUtils.getUniqueDeclaredMethods(testClass);

        boolean dirties = false;
        for (Class<?> c : classAndEnclosing(testClass)) { // @Nested: @DirtiesContext/@Transactional của lớp bao ngoài có hiệu lực
            dirties |= AnnotatedElementUtils.hasAnnotation(c, DirtiesContext.class);
        }
        for (Method m : methods) {
            dirties |= AnnotatedElementUtils.hasAnnotation(m, DirtiesContext.class);
        }
        if (dirties) {
            report(warnOnly, "DIRTIES_CONTEXT_UNSUPPORTED: @DirtiesContext on " + testClass.getName()
                    + " closes a context other parallel tests are using. Use a separate @ParallelSpringTest(context=...) "
                    + "name with its own configuration instead.");
        }

        SpringBootTest boot = TestContextAnnotationUtils.findMergedAnnotation(testClass, SpringBootTest.class);
        if (boot != null) {
            SpringBootTest.WebEnvironment env = boot.webEnvironment();
            boolean realPort = env == SpringBootTest.WebEnvironment.RANDOM_PORT || env == SpringBootTest.WebEnvironment.DEFINED_PORT;
            boolean tx = false;
            for (Class<?> c : classAndEnclosing(testClass)) {
                tx |= hasTransactional(c);
            }
            for (Method m : methods) {
                tx |= hasTransactional(m);
            }
            if (realPort && tx) {
                report(warnOnly, "TRANSACTIONAL_RANDOM_PORT_UNSUPPORTED: @Transactional on " + testClass.getName()
                        + " with webEnvironment=" + env + ": requests run on server threads outside the test transaction, "
                        + "so nothing is rolled back and the test only appears isolated. Create unique data per test and clean it explicitly.");
            }
        }
    }

    private static List<Class<?>> classAndEnclosing(Class<?> testClass) {
        return BeanDeclarations.classAndEnclosing(testClass);
    }

    private static boolean hasTransactional(java.lang.reflect.AnnotatedElement element) {
        var annotations = MergedAnnotations.from(element, MergedAnnotations.SearchStrategy.TYPE_HIERARCHY);
        return annotations.isPresent(TX_SPRING) || annotations.isPresent(TX_JAKARTA);
    }

    private static void report(boolean warnOnly, String message) {
        if (warnOnly) {
            LOG.warn(message);
        } else {
            throw new ParallelTestException.Unsupported(message);
        }
    }

    // ---- binding mỗi test ----

    @Override
    public void beforeEach(ExtensionContext ec) {
        ApplicationContext ctx = SpringExtension.getApplicationContext(ec);
        ManagedContext mc = ContextRegistry.INSTANCE.of(ctx);
        mc.ensureReady();
        Class<?> testClass = ec.getRequiredTestClass();
        JUnitConfig.capture(ec::getConfigurationParameter);
        checkPoolSize(ctx, mc, testClass);

        List<ResolvedRequest> fields = mc.resolveFields(testClass);
        Map<String, ResolvedRequest> parameters = parameterRequests(mc, ec.getRequiredTestMethod());
        Map<String, InterceptMode> modes = new LinkedHashMap<>();
        fields.forEach(r -> modes.put(r.beanName(), r.request().mode()));
        parameters.forEach((bean, r) -> modes.put(bean, r.request().mode())); // tham số ghi đè field cho method này
        Map<TestBinding.TargetKey, Object> targets = new HashMap<>();
        Map<String, Object> handles = new HashMap<>();
        modes.forEach((bean, mode) -> {
            Object target = mc.requireInterceptor(bean).newTarget(mode);
            targets.put(new TestBinding.TargetKey(mc.key, bean), target);
            handles.put(bean, target);
        });

        TestBinding binding = TestBinding.open(testClass.getSimpleName() + "." + ec.getRequiredTestMethod().getName(), targets);
        TestBinding previous = TestBinding.bind(binding);
        State state = new State(binding, previous, handles, fields);
        ec.getStore(NS).put(KEY_STATE, state);
        GuardScope.open(binding.id(), testClass.getSimpleName() + "." + ec.getRequiredTestMethod().getName(),
                ClassRules.suppressedRules(testClass));

        injectFields(ec, fields, state);
    }

    @Override
    public void afterEach(ExtensionContext ec) {
        State state = ec.getStore(NS).remove(KEY_STATE, State.class);
        if (state != null) {
            TestBinding.bind(state.previous());
            if (isPerClass(ec) && state.fields().stream().anyMatch(r -> r.request().field() != null)) {
                assignFields(ec, state.fields(), name -> null); // instance dùng chung: không để handle đã đóng cho method sau
            }
            List<Violation> foreign = List.of();
            RuntimeException failure = null;
            // binding.close() PHẢI luôn chạy kể cả khi GuardScope.close hoặc kiểm tra stub ném lỗi bất ngờ: nếu không, binding rò rỉ và
            // mọi bean của test này vẫn định tuyến tới mock đã chết.
            try {
                foreign = GuardScope.close(state.binding().id());
                state.binding().verifyWrappersNotStubbed();
            } catch (RuntimeException e) {
                failure = e;
            } finally {
                state.binding().close();
            }
            if (!foreign.isEmpty()) {
                // Vi phạm xảy ra trên thread khác (server, executor) được gán lại cho test này: không bị nuốt sau HTTP 500.
                ParallelTestException.Guard error = GuardEnforcer.error("Violations raised for this test on other threads "
                        + "(server / executor; the test may only have seen an HTTP 500 or a swallowed error)", foreign);
                if (failure != null) {
                    error.addSuppressed(failure);
                }
                throw error;
            }
            if (failure != null) {
                throw failure;
            }
        }
    }

    /**
     * PT11: kiểm tra pool của DataSource của context với số thread song song; mỗi cặp (lớp, context) chỉ được đánh dấu đã kiểm SAU KHI
     * thành công (hoặc chỉ cảnh báo), nên khi mức FAIL thì mọi test của lớp đều nhận lỗi chứ không chỉ test chạy đầu tiên.
     */
    private static void checkPoolSize(ApplicationContext ctx, ManagedContext mc, Class<?> testClass) {
        String id = testClass.getName() + "@" + mc.key;
        if (POOL_CHECKED.contains(id)) {
            return;
        }
        List<Violation> violations = ClassRules.filterSuppressed(testClass,
                PoolSizeCheck.check(ctx, testClass, JUnitConfig.parallelism()));
        GuardEnforcer.enforce("Test context is not ready for parallel execution", violations);
        POOL_CHECKED.add(id);
    }

    /** Yêu cầu trên tham số của method test (ghi đè field chỉ cho method này); hai tham số cùng bean khác chế độ là MODE_CONFLICT. */
    private static Map<String, ResolvedRequest> parameterRequests(ManagedContext mc, Method method) {
        Map<String, ResolvedRequest> out = new LinkedHashMap<>();
        java.lang.reflect.Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            BeanRequest request = BeanDeclarations.parameterRequest(method, parameters[i], i);
            if (request == null) {
                continue;
            }
            ResolvedRequest resolved = mc.resolve(request);
            ResolvedRequest first = out.putIfAbsent(resolved.beanName(), resolved);
            if (first != null && first.request().mode() != request.mode()) {
                throw ManagedContext.modeConflict(resolved.beanName(), first.request(), request);
            }
        }
        return out;
    }

    /**
     * Gán mock/spy của test vào field. PER_CLASS dùng chung một instance cho các method nên chỉ an toàn khi các method không chạy
     * đồng thời ({@link ClassRules#methodsMayRunConcurrently}); khi đó mỗi method nhận handle mới (xóa ở {@link #afterEach}).
     */
    private void injectFields(ExtensionContext ec, List<ResolvedRequest> allRequests, State state) {
        List<ResolvedRequest> fields = allRequests.stream().filter(r -> r.request().field() != null).toList(); // spy cấp class không có field
        if (fields.isEmpty()) {
            return;
        }
        if (isPerClass(ec) && ClassRules.methodsMayRunConcurrently(ec.getRequiredTestClass())) {
            throw new ParallelTestException.Config("CONFIG: @PerTestMock/@PerTestSpy field " + fields.get(0).request().where() + " in "
                    + ec.getRequiredTestClass().getName() + " uses TestInstance.Lifecycle.PER_CLASS while its methods may run concurrently "
                    + "(the instance is shared by concurrent tests). Use PER_METHOD, @Execution(SAME_THREAD), @Isolated or @ResourceLock");
        }
        assignFields(ec, fields, name -> state.handles().get(name));
    }

    private static boolean isPerClass(ExtensionContext ec) {
        return ec.getTestInstanceLifecycle().orElse(TestInstance.Lifecycle.PER_METHOD) == TestInstance.Lifecycle.PER_CLASS;
    }

    private static void assignFields(ExtensionContext ec, List<ResolvedRequest> fields, java.util.function.Function<String, Object> handle) {
        for (Object instance : ec.getRequiredTestInstances().getAllInstances()) {
            for (ResolvedRequest r : fields) {
                java.lang.reflect.Field f = r.request().field();
                if (f != null && f.getDeclaringClass().isInstance(instance)) { // khai báo spy cấp class không có field
                    ReflectionUtils.makeAccessible(f);
                    ReflectionUtils.setField(f, instance, handle.apply(r.beanName()));
                }
            }
        }
    }

    // ---- method test chạy trên thread khác ----

    /**
     * {@code @Timeout(threadMode = SEPARATE_THREAD)} (hoặc {@code junit.jupiter.execution.timeout.thread.mode.default}) chạy chính method test
     * trên thread khác thread đã gắn binding ở {@link #beforeEach}; bọc lại ở đây (chạy bên trong chuỗi interceptor, nên trên thread đó) để
     * method vẫn thấy mock/spy của mình thay vì rơi vào chính sách thread không binding. Không đổi gì khi cùng thread.
     */
    @Override
    public void interceptTestMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> context, ExtensionContext ec) throws Throwable {
        proceedWithBinding(invocation, ec);
    }

    @Override
    public void interceptTestTemplateMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> context, ExtensionContext ec) throws Throwable {
        proceedWithBinding(invocation, ec);
    }

    /** {@code @BeforeEach}/{@code @AfterEach}/{@code @TestFactory} cũng có thể bị {@code @Timeout(SEPARATE_THREAD)} chuyển sang thread khác. */
    @Override
    public void interceptBeforeEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> context, ExtensionContext ec) throws Throwable {
        proceedWithBinding(invocation, ec);
    }

    @Override
    public void interceptAfterEachMethod(Invocation<Void> invocation, ReflectiveInvocationContext<Method> context, ExtensionContext ec) throws Throwable {
        proceedWithBinding(invocation, ec);
    }

    /**
     * Factory chạy dưới binding của test; kết quả (các dynamic test) được bọc để mỗi dynamic test tự bind lại, vì JUnit có thể chạy chúng trên
     * thread khác thread gọi factory (xem {@link DynamicBindings}).
     */
    @Override
    @SuppressWarnings("unchecked")
    public <T> T interceptTestFactoryMethod(Invocation<T> invocation, ReflectiveInvocationContext<Method> context, ExtensionContext ec) throws Throwable {
        State state = ec.getStore(NS).get(KEY_STATE, State.class);
        if (state == null) {
            return invocation.proceed();
        }
        T result;
        if (TestBinding.current() == state.binding()) {
            result = invocation.proceed();
        } else {
            TestBinding previous = TestBinding.bind(state.binding());
            try {
                result = invocation.proceed();
            } finally {
                TestBinding.bind(previous);
            }
        }
        return (T) DynamicBindings.wrap(result, state.binding());
    }

    private static void proceedWithBinding(Invocation<Void> invocation, ExtensionContext ec) throws Throwable {
        State state = ec.getStore(NS).get(KEY_STATE, State.class);
        if (state == null || TestBinding.current() == state.binding()) {
            invocation.proceed();
            return;
        }
        TestBinding previous = TestBinding.bind(state.binding());
        try {
            invocation.proceed();
        } finally {
            TestBinding.bind(previous);
        }
    }

    // ---- tham số ----

    @Override
    public boolean supportsParameter(ParameterContext pc, ExtensionContext ec) {
        return pc.isAnnotated(PerTestMock.class) || pc.isAnnotated(PerTestSpy.class);
    }

    @Override
    public Object resolveParameter(ParameterContext pc, ExtensionContext ec) {
        State state = ec.getStore(NS).get(KEY_STATE, State.class);
        if (state == null || !pc.getDeclaringExecutable().equals(ec.getRequiredTestMethod())) {
            throw new ParameterResolutionException("@PerTestMock/@PerTestSpy parameter is only supported on the test method itself");
        }
        ManagedContext mc = ContextRegistry.INSTANCE.of(SpringExtension.getApplicationContext(ec));
        BeanRequest request = BeanDeclarations.parameterRequest(ec.getRequiredTestMethod(), pc.getParameter(), pc.getIndex());
        return state.handles().get(mc.resolve(request).beanName());
    }
}
