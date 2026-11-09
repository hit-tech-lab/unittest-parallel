package io.hit.ptest.intercept;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;

import io.hit.ptest.context.ManagedContext;
import io.hit.ptest.testsupport.Fixtures;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.ClassFilter;
import org.springframework.aop.MethodMatcher;
import org.springframework.aop.Pointcut;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.framework.autoproxy.DefaultAdvisorAutoProxyCreator;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SPY nằm bên trong proxy AOP và chạy method thật trên chính spy. Dùng advisor tự viết (không cần AspectJ) để kiểm tra
 * được cả proxy CGLIB lẫn JDK proxy trên bean lớp (JDK proxy bọc LỚP: bean có interface, proxyTargetClass=false).
 */
class SpyHostTest {

    public interface Greeter {
        String greet();
    }

    public static class AopGreeter implements Greeter {
        @Override
        public String greet() {
            return "hi " + name();    // gọi nội bộ
        }

        public String name() {
            return "real";
        }
    }

    public static class AopClassSvc {
        public String outer() {
            return "outer:" + inner();    // gọi nội bộ
        }

        public String inner() {
            return "real";
        }
    }

    static final AtomicInteger ADVICE_CALLS = new AtomicInteger();

    @Configuration(proxyBeanMethods = false)
    static class AopConfig {
        @Bean static DefaultAdvisorAutoProxyCreator creator() {
            return new DefaultAdvisorAutoProxyCreator();    // proxyTargetClass=false: bean có interface => JDK proxy
        }

        @Bean static DefaultPointcutAdvisor countingAdvisor() {
            Pointcut onAopBeans = new Pointcut() {
                @Override public ClassFilter getClassFilter() { return c -> c.getSimpleName().startsWith("Aop"); }
                @Override public MethodMatcher getMethodMatcher() { return MethodMatcher.TRUE; }
            };
            return new DefaultPointcutAdvisor(onAopBeans, (MethodInterceptor) inv -> {
                ADVICE_CALLS.incrementAndGet();
                return inv.proceed();
            });
        }

        @Bean AopClassSvc aopClassSvc() { return new AopClassSvc(); }

        @Bean Greeter aopGreeter() { return new AopGreeter(); }
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    private static Object bindSpy(AnnotationConfigApplicationContext ctx, String bean) {
        ManagedContext mc = Fixtures.managed(ctx);
        Object spy = mc.requireInterceptor(bean).newTarget(InterceptMode.SPY);
        TestBinding.bind(TestBinding.open("spy-test", Map.of(new TestBinding.TargetKey(mc.key, bean), spy)));
        return spy;
    }

    @Test
    void cglibProxyAroundSpyInternalCallAndAdviceBothApply() {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-cglib", new String[] {"aopClassSvc"}, Map.of(), AopConfig.class);
        AopClassSvc svc = c.getBean("aopClassSvc", AopClassSvc.class);
        AopClassSvc spy = (AopClassSvc) bindSpy(c, "aopClassSvc");
        doReturn("STUB").when(spy).inner();
        int before = ADVICE_CALLS.get();

        assertThat(svc.outer()).isEqualTo("outer:STUB");

        assertThat(ADVICE_CALLS.get() - before).as("advice ran once, around the spy (internal call bypasses the proxy)").isEqualTo(1);
        verify(spy).outer();
        verify(spy).inner();
    }

    @Test
    void jdkProxyAroundClassBeanSpyInternalCallAndAdviceBothApply() {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-jdk", new String[] {"aopGreeter"}, Map.of(), AopConfig.class);
        Greeter greeter = c.getBean("aopGreeter", Greeter.class);
        AopGreeter spy = (AopGreeter) bindSpy(c, "aopGreeter");
        doReturn("STUB").when(spy).name();
        int before = ADVICE_CALLS.get();

        assertThat(greeter.greet()).isEqualTo("hi STUB");

        assertThat(ADVICE_CALLS.get() - before).isEqualTo(1);
        verify(spy).greet();
        verify(spy).name();
    }

    @Test
    void withoutASpyTheRealBeanRunsBehindTheSameProxy() {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-none", new String[] {"aopClassSvc"}, Map.of(), AopConfig.class);
        AopClassSvc svc = c.getBean("aopClassSvc", AopClassSvc.class);
        TestBinding.bind(TestBinding.open("no-spy", Map.of()));
        int before = ADVICE_CALLS.get();

        assertThat(svc.outer()).isEqualTo("outer:real");
        assertThat(ADVICE_CALLS.get() - before).isEqualTo(1);
        assertThat(c.getBean("aopClassSvc")).isNotInstanceOf(Advised.class);   // bean của context là wrapper dùng chung, không phải proxy
    }

    public static class HandOffSvc {
        public String who() {
            return "real";
        }
    }

    /** Advice chuyển lời gọi sang thread mới KHÔNG mang binding (như @Async với executor không có TaskDecorator). */
    @Configuration(proxyBeanMethods = false)
    static class HandOffConfig {
        @Bean static DefaultAdvisorAutoProxyCreator creator() {
            return new DefaultAdvisorAutoProxyCreator();
        }

        @Bean static DefaultPointcutAdvisor handOffAdvisor() {
            Pointcut onHandOff = new Pointcut() {
                @Override public ClassFilter getClassFilter() { return c -> c.getSimpleName().startsWith("HandOff"); }
                @Override public MethodMatcher getMethodMatcher() { return MethodMatcher.TRUE; }
            };
            return new DefaultPointcutAdvisor(onHandOff, (MethodInterceptor) inv -> {
                java.util.concurrent.atomic.AtomicReference<Object> out = new java.util.concurrent.atomic.AtomicReference<>();
                Thread t = new Thread(() -> {
                    try {
                        out.set(inv.proceed());
                    } catch (Throwable e) {
                        out.set(e);
                    }
                }, "no-binding-worker");
                t.start();
                t.join();
                if (out.get() instanceof Throwable e) {
                    throw e;
                }
                return out.get();
            });
        }

        @Bean HandOffSvc handOffSvc() { return new HandOffSvc(); }
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"guarded", "fail"})
    void aWorkerThreadThatLostItsBindingInsideTheProxyIsNotSilentlyRoutedToTheRealBean(String policy) {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-handoff-" + policy, new String[] {"handOffSvc"},
                Map.of("ptest.unbound-policy", policy), HandOffConfig.class);
        HandOffSvc svc = c.getBean("handOffSvc", HandOffSvc.class);
        HandOffSvc spy = (HandOffSvc) bindSpy(c, "handOffSvc");
        doReturn("STUB").when(spy).who();

        org.assertj.core.api.Assertions.assertThatThrownBy(svc::who).isInstanceOf(io.hit.ptest.ParallelTestException.UnboundThread.class)
                .hasMessageContaining("UNBOUND_THREAD").hasMessageContaining("no-binding-worker").hasMessageContaining("handOffSvc")
                .hasMessageContaining("policy=" + policy.toUpperCase());
    }

    @Test
    void realPolicyKeepsTheDocumentedWarningAndRunsTheRealBean() {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-handoff-real", new String[] {"handOffSvc"},
                Map.of("ptest.unbound-policy", "real"), HandOffConfig.class);
        HandOffSvc svc = c.getBean("handOffSvc", HandOffSvc.class);
        HandOffSvc spy = (HandOffSvc) bindSpy(c, "handOffSvc");
        doReturn("STUB").when(spy).who();

        assertThat(svc.who()).as("policy=real is the explicit opt-in to the real bean on unbound threads").isEqualTo("real");
    }

    @Test
    void guardedPolicyStillRunsTheRealBeanWhenNoTestEverSpiedIt() {
        AnnotationConfigApplicationContext c = Fixtures.started("spy-handoff-never", new String[] {"handOffSvc"}, Map.of(), HandOffConfig.class);
        TestBinding.bind(TestBinding.open("no-spy", Map.of()));

        assertThat(c.getBean("handOffSvc", HandOffSvc.class).who()).isEqualTo("real");
    }
}
