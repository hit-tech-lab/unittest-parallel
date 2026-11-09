package io.hit.ptest.intercept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.context.ManagedContext;
import io.hit.ptest.testsupport.Fixtures;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Hai nhánh định tuyến của bean có spy trong {@link BeanInterceptor}: bean KHÔNG có proxy AOP ({@code hostIsDirectTarget}: wrapper ngoài gọi
 * thẳng spy hoặc bản thật, bỏ qua một lần dispatch của host) và bean CÓ proxy AOP (spy nằm bên trong proxy, đi qua advice). Hai nhánh phải cho
 * CÙNG kết quả quan sát được: nội dung trả về, bộ đếm INTERCEPTED/REAL/UNBOUND, chính sách thread không binding và spy thấy {@code this.x()}.
 * Test chạy tuần tự trong module này nên đo được độ lệch bộ đếm toàn JVM chính xác.
 */
class SpyDispatchBranchesTest {

    public static class PlainSvc {
        public String outer() {
            return "outer:" + inner();
        }

        public String inner() {
            return "real";
        }
    }

    public static class AopSvc {
        public String outer() {
            return "outer:" + inner();
        }

        public String inner() {
            return "real";
        }
    }

    static final AtomicInteger ADVICE_CALLS = new AtomicInteger();

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean static DefaultAdvisorAutoProxyCreator creator() {
            return new DefaultAdvisorAutoProxyCreator();
        }

        @Bean static DefaultPointcutAdvisor advisor() {
            Pointcut onAop = new Pointcut() {
                @Override public ClassFilter getClassFilter() { return c -> c == AopSvc.class; }
                @Override public MethodMatcher getMethodMatcher() { return MethodMatcher.TRUE; }
            };
            return new DefaultPointcutAdvisor(onAop, (MethodInterceptor) inv -> {
                ADVICE_CALLS.incrementAndGet();
                return inv.proceed();
            });
        }

        @Bean PlainSvc plainSvc() { return new PlainSvc(); }

        @Bean AopSvc aopSvc() { return new AopSvc(); }
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    private static AnnotationConfigApplicationContext started(String policy, String bean) {
        return Fixtures.started("branches-" + bean + "-" + policy + System.nanoTime(), new String[] {bean}, Map.of("ptest.unbound-policy", policy), Config.class);
    }

    private static Object bean(AnnotationConfigApplicationContext c, String name) {
        return c.getBean(name);
    }

    private static String outer(Object bean) {
        return bean instanceof PlainSvc p ? p.outer() : ((AopSvc) bean).outer();
    }

    private static Object bind(AnnotationConfigApplicationContext c, String bean, InterceptMode mode) {
        ManagedContext mc = Fixtures.managed(c);
        Object target = mc.requireInterceptor(bean).newTarget(mode);
        TestBinding.bind(TestBinding.open("branches", Map.of(new TestBinding.TargetKey(mc.key, bean), target)));
        return target;
    }

    private record Stats(long intercepted, long real, long unbound) {
        static Stats now() {
            return new Stats(InterceptStats.INTERCEPTED.sum(), InterceptStats.REAL.sum(), InterceptStats.UNBOUND.sum());
        }

        Stats since(Stats before) {
            return new Stats(intercepted - before.intercepted, real - before.real, unbound - before.unbound);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void spyIsReachedByInternalCallsAndCountedAsOneInterceptedCall(String name) {
        AnnotationConfigApplicationContext c = started("guarded", name);
        Object bean = bean(c, name);
        Object spy = bind(c, name, InterceptMode.SPY);
        if (spy instanceof PlainSvc p) {
            doReturn("STUB").when(p).inner();
        } else {
            doReturn("STUB").when((AopSvc) spy).inner();
        }
        int adviceBefore = ADVICE_CALLS.get();
        Stats before = Stats.now();

        assertThat(outer(bean)).isEqualTo("outer:STUB");

        assertThat(Stats.now().since(before)).as("one wrapper dispatch routed to the test's spy; host dispatch is not counted")
                .isEqualTo(new Stats(1, 0, 0));
        assertThat(ADVICE_CALLS.get() - adviceBefore).as("advice only for the AOP bean").isEqualTo(name.equals("aopSvc") ? 1 : 0);
        if (spy instanceof PlainSvc p) {
            verify(p).outer();
            verify(p).inner();
        } else {
            verify((AopSvc) spy).outer();
            verify((AopSvc) spy).inner();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void bindingWithoutATargetForTheBeanRunsTheRealBeanAndCountsReal(String name) {
        AnnotationConfigApplicationContext c = started("guarded", name);
        Object bean = bean(c, name);
        TestBinding.bind(TestBinding.open("no-target", Map.of()));
        Stats before = Stats.now();

        assertThat(outer(bean)).isEqualTo("outer:real");

        assertThat(Stats.now().since(before)).isEqualTo(new Stats(0, 1, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void mockTargetIsUsedAsIsAndCountedAsIntercepted(String name) {
        AnnotationConfigApplicationContext c = started("guarded", name);
        Object bean = bean(c, name);
        bind(c, name, InterceptMode.MOCK);
        Stats before = Stats.now();

        assertThat(outer(bean)).as("unstubbed mock").isNull();

        assertThat(Stats.now().since(before)).isEqualTo(new Stats(1, 0, 0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void unboundThreadIsGuardedOnceASpyExistedAndCountedAsOneUnboundCall(String name) throws Exception {
        AnnotationConfigApplicationContext c = started("guarded", name);
        Object bean = bean(c, name);
        assertThat(Fixtures.onFreshThread(() -> outer(bean))).as("nobody spied it yet: GUARDED runs the real bean").isEqualTo("outer:real");
        bind(c, name, InterceptMode.SPY);
        TestBinding.bind(null);
        Stats before = Stats.now();

        Object result = Fixtures.onFreshThread(() -> outer(bean));

        assertThat((Throwable) result).isInstanceOf(ParallelTestException.UnboundThread.class).hasMessageContaining(name);
        assertThat(Stats.now().since(before)).as("failed once, at the wrapper, before reaching the host").isEqualTo(new Stats(0, 0, 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void unboundThreadUnderRealPolicyRunsTheRealBeanNotTheSpy(String name) throws Exception {
        AnnotationConfigApplicationContext c = started("real", name);
        Object bean = bean(c, name);
        Object spy = bind(c, name, InterceptMode.SPY);
        if (spy instanceof PlainSvc p) {
            doReturn("STUB").when(p).inner();
        } else {
            doReturn("STUB").when((AopSvc) spy).inner();
        }
        TestBinding.bind(null);

        assertThat(Fixtures.onFreshThread(() -> outer(bean))).isEqualTo("outer:real");
    }

    @ParameterizedTest
    @ValueSource(strings = {"plainSvc", "aopSvc"})
    void closedBindingFailsUnderFailPolicy(String name) {
        AnnotationConfigApplicationContext c = started("fail", name);
        Object bean = bean(c, name);
        bind(c, name, InterceptMode.SPY);
        TestBinding.current().close();

        assertThatThrownBy(() -> outer(bean)).isInstanceOf(ParallelTestException.UnboundThread.class).hasMessageContaining("already closed");
    }
}
