package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.core.TestBindings.Binding;
import com.hittech.paralleltest.mock.PerTestMockSupport;
import com.hittech.paralleltest.probe.ProbeRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Auto-registered for every Spring test through META-INF/spring.factories: nothing to add to a test.
 * <ul>
 *   <li>beforeTestClass: class rules (PT01-PT09, PT13), before any context is started;</li>
 *   <li>prepareTestInstance, once per new context: context budget PT10, pool size PT11, routed bean bypass PT14;</li>
 *   <li>beforeTestMethod / afterTestMethod: opens the test's binding (fresh @PerTestMock mocks, identity carried
 *       to other threads) and fails the test with violations raised for it on other threads.</li>
 * </ul>
 * {@code enabled=false} switches off the rules only: mocks are still routed, so test behaviour never changes.
 * Spring keeps one TestContext copy per thread, so the attribute below is never shared by concurrent methods.
 */
public class ParallelGuardTestExecutionListener implements TestExecutionListener, Ordered {

    private static final String BINDING = ParallelGuardTestExecutionListener.class.getName() + ".binding";
    private static final Set<Class<?>> ISOLATED_RUNNING = ConcurrentHashMap.newKeySet();

    @Override
    public int getOrder() {
        return 1000; // before DependencyInjection (2000), Transactional (4000) and @Sql (5000) listeners
    }

    @Override
    public void beforeTestClass(TestContext testContext) {
        GuardSettings settings = GuardSettings.get();
        if (!settings.enabled()) {
            return;
        }
        Class<?> testClass = testContext.getTestClass();
        List<Violation> violations = ClassRules.check(testClass, settings);
        if (!violations.isEmpty()) {
            throw new ParallelGuardViolation(testClass.getName() + " is not parallel-safe", violations);
        }
        if (ClassRules.isJUnitIsolated(testClass) && ISOLATED_RUNNING.add(testClass)) {
            IsolationTracker.started();
        }
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        if (ISOLATED_RUNNING.remove(testContext.getTestClass())) {
            IsolationTracker.finished();
        }
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        ApplicationContext ctx = testContext.getApplicationContext();
        ProbeRegistry.recordContext(ctx);
        GuardSettings settings = GuardSettings.get();
        Class<?> testClass = testContext.getTestClass();
        if (!settings.enabled() || !ContextBudget.register(ctx, testClass, settings.maxContexts())) {
            return;
        }
        List<Violation> infra = new ArrayList<>(RoutedBeanCheck.check(ctx, ClassRules.routedTypes(testClass), testClass));
        if (settings.checkPoolSize()) {
            infra.addAll(PoolSizeCheck.check(ctx, testClass, JUnitConfig.parallelism()));
        }
        if (!infra.isEmpty()) {
            throw new ParallelGuardViolation("Test context is not ready for parallel execution", infra);
        }
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        testContext.setAttribute(BINDING, PerTestMockSupport.bind(testContext.getTestInstance(),
                testContext.getTestClass().getSimpleName() + "." + testContext.getTestMethod().getName()));
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        if (testContext.removeAttribute(BINDING) instanceof Binding binding) {
            List<Throwable> foreign = PerTestMockSupport.unbind(binding);
            List<Violation> violations = foreign.stream()
                    .filter(ParallelGuardViolation.class::isInstance)
                    .flatMap(t -> ((ParallelGuardViolation) t).violations().stream())
                    .toList();
            if (!violations.isEmpty()) {
                throw new ParallelGuardViolation("Violations raised for this test on other threads "
                        + "(server / executor; the test may only have seen an HTTP 500 or a swallowed error)", violations);
            }
        }
    }
}
