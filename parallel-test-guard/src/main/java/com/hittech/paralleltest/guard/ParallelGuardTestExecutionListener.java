package com.hittech.paralleltest.guard;

import com.hittech.paralleltest.mock.PerTestMockSupport;
import com.hittech.paralleltest.probe.ProbeRegistry;
import java.util.List;
import org.springframework.context.ApplicationContext;
import org.springframework.core.Ordered;
import org.springframework.test.context.TestContext;
import org.springframework.test.context.TestExecutionListener;

/**
 * Auto-registered for every Spring test through META-INF/spring.factories: nothing to add to a test.
 * <ul>
 *   <li>beforeTestClass: class rules PT01-PT08 + forbidden calls PT07 (fails the class, before any context starts)</li>
 *   <li>prepareTestInstance: context budget PT10, pool size PT11 (once per new context)</li>
 *   <li>beforeTestMethod / afterTestMethod: fresh @PerTestMock mocks bound to the test thread</li>
 * </ul>
 */
public class ParallelGuardTestExecutionListener implements TestExecutionListener, Ordered {

    private static final String BINDING = ParallelGuardTestExecutionListener.class.getName() + ".binding";

    @Override
    public int getOrder() {
        return 1000; // before DependencyInjectionTestExecutionListener (2000)
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
        if (ClassRules.isJUnitIsolated(testClass)) {
            SqlGuard.isolatedClassStarted();
        }
    }

    @Override
    public void afterTestClass(TestContext testContext) {
        if (GuardSettings.get().enabled() && ClassRules.isJUnitIsolated(testContext.getTestClass())
                && ClassRules.check(testContext.getTestClass(), GuardSettings.get()).isEmpty()) {
            SqlGuard.isolatedClassFinished();
        }
    }

    @Override
    public void prepareTestInstance(TestContext testContext) {
        ApplicationContext ctx = testContext.getApplicationContext();
        ProbeRegistry.recordContext(ctx);
        GuardSettings settings = GuardSettings.get();
        if (!settings.enabled()) {
            return;
        }
        if (ContextBudget.register(ctx, testContext.getTestClass(), settings.maxContexts()) && settings.checkPoolSize()) {
            List<Violation> pool = PoolSizeCheck.check(ctx, testContext.getTestClass(), JUnitParallelism.effective());
            if (!pool.isEmpty()) {
                throw new ParallelGuardViolation("Test infrastructure too small for parallel execution", pool);
            }
        }
    }

    @Override
    public void beforeTestMethod(TestContext testContext) throws Exception {
        Object binding = PerTestMockSupport.bind(testContext.getTestInstance(),
                testContext.getTestClass().getSimpleName() + "." + testContext.getTestMethod().getName());
        if (binding != null) {
            testContext.setAttribute(BINDING, binding);
        }
    }

    @Override
    public void afterTestMethod(TestContext testContext) {
        Object binding = testContext.removeAttribute(BINDING);
        if (binding != null) {
            PerTestMockSupport.unbind(binding);
        }
    }
}
