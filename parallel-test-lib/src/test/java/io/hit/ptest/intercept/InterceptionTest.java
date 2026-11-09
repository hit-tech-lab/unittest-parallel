package io.hit.ptest.intercept;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.UnboundPolicy;
import io.hit.ptest.context.ManagedContext;

import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Điểm 2, 5, 7, 8: chính sách thread không binding, binding đã đóng, FactoryBean/prototype/scope/lazy/alias, wrapper bị stub, toString. */
class InterceptionTest {

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    private static AnnotationConfigApplicationContext ctx(String policy, String... interceptable) {
        return Fixtures.started("t-" + policy + System.nanoTime(), interceptable, Map.of("ptest.unbound-policy", policy), Fixtures.Beans.class);
    }

    // ---- policy ----

    @Test
    void guardedIsTheDefaultBecauseWrapAllMakesMostWrappedBeansNeverMocked() throws Exception {
        AnnotationConfigApplicationContext c = Fixtures.started("default-policy", new String[] {"classSvc"}, Map.of(), Fixtures.Beans.class);
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);

        assertThat(Fixtures.managed(c).policy).isEqualTo(UnboundPolicy.GUARDED);
        assertThat(Fixtures.onFreshThread(svc::value)).as("nobody mocked it: REAL").isEqualTo("real");
        Fixtures.bindMock(c, "classSvc");
        assertThat((Throwable) Fixtures.onFreshThread(svc::value)).isInstanceOf(ParallelTestException.UnboundThread.class)
                .hasMessageContaining("UNBOUND_THREAD");
    }

    @Test
    void failRejectsEveryUnboundThreadEvenForBeansNobodyMocked() throws Exception {
        AnnotationConfigApplicationContext c = ctx("fail", "classSvc");
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);

        assertThat((Throwable) Fixtures.onFreshThread(svc::value)).isInstanceOf(ParallelTestException.UnboundThread.class);
    }

    @Test
    void guardedIsMonotonicOnceABeanWasEverInterceptedUnboundCallsFailEvenAfterTheTestEnded() throws Exception {
        AnnotationConfigApplicationContext c = ctx("guarded", "classSvc", "other");
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);
        Fixtures.ClassSvc other = c.getBean("other", Fixtures.ClassSvc.class);

        assertThat(Fixtures.onFreshThread(svc::value)).as("never intercepted yet: REAL").isEqualTo("real");

        TestBinding b = Fixtures.bindMock(c, "classSvc");
        assertThat(Fixtures.onFreshThread(svc::value)).as("a test intercepts it now").isInstanceOf(ParallelTestException.UnboundThread.class);

        b.close();
        TestBinding.bind(null);
        assertThat(Fixtures.onFreshThread(svc::value)).as("test ended: STILL fails (monotonic, no flip back to REAL)")
                .isInstanceOf(ParallelTestException.UnboundThread.class);
        assertThat(Fixtures.onFreshThread(other::value)).as("control: bean nobody ever intercepted stays REAL").isEqualTo("real");
    }

    @Test
    void realPolicyAlwaysCallsTheRealBean() throws Exception {
        AnnotationConfigApplicationContext c = ctx("real", "classSvc");
        Fixtures.bindMock(c, "classSvc");
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);

        assertThat(Fixtures.onFreshThread(svc::value)).isEqualTo("real");
    }

    // ---- binding đã đóng ----

    @Test
    void closedBindingNoLongerRoutesToItsOldMockAndFailsLoudly() throws Exception {
        AnnotationConfigApplicationContext c = ctx("fail", "classSvc");
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);
        ManagedContext mc = Fixtures.managed(c);
        Fixtures.ClassSvc target = (Fixtures.ClassSvc) mc.requireInterceptor("classSvc").newTarget(InterceptMode.MOCK);
        when(target.value()).thenReturn("from-old-mock");
        TestBinding b = TestBinding.open("leaky", Map.of(new TestBinding.TargetKey(mc.key, "classSvc"), target));
        TestBinding.bind(b);

        assertThat(svc.value()).as("control: while open the binding routes to its mock").isEqualTo("from-old-mock");
        b.close();
        Object result = Fixtures.onFreshThread(() -> {
            TestBinding.bind(b); // tác vụ rò rỉ giữ binding đã đóng
            return svc.value();
        });

        assertThat((Throwable) result).isInstanceOf(ParallelTestException.UnboundThread.class).hasMessageContaining("already closed");
    }

    @Test
    void closedBindingFallsBackToRealUnderRealPolicy() throws Exception {
        AnnotationConfigApplicationContext c = ctx("real", "classSvc");
        Fixtures.ClassSvc svc = c.getBean("classSvc", Fixtures.ClassSvc.class);
        TestBinding b = Fixtures.bindMock(c, "classSvc");
        b.close();

        assertThat(svc.value()).isEqualTo("real");
    }

    // ---- toString ----

    @Test
    void toStringNeverThrowsOnUnboundThreadForClassAndJdkProxyWrappers() throws Exception {
        AnnotationConfigApplicationContext c = ctx("fail", "classSvc", "jdkSvc");
        Object classWrapper = c.getBean("classSvc");
        Object jdkWrapper = c.getBean("jdkSvc");

        assertThat(Fixtures.onFreshThread(classWrapper::toString)).isEqualTo("Interceptor[classSvc]");
        assertThat(Fixtures.onFreshThread(jdkWrapper::toString)).isEqualTo("Interceptor[jdkSvc]");
        assertThat(Fixtures.onFreshThread(() -> ((Fixtures.Svc) jdkWrapper).value())).as("control: real calls still fail")
                .isInstanceOf(ParallelTestException.UnboundThread.class);
    }

    // ---- stub nhầm vào wrapper ----

    @Test
    void stubbingTheSharedWrapperIsDetectedOnTheNextCall() {
        AnnotationConfigApplicationContext c = ctx("real", "classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);

        when(wrapper.value()).thenReturn("leaks-into-every-test");

        assertThatThrownBy(wrapper::value).hasMessageContaining("BEAN_WRAPPER_STUBBED");
    }

    @Test
    void controlStubbingTheInterceptedHandleIsFine() {
        AnnotationConfigApplicationContext c = ctx("real", "classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        Fixtures.bindMock(c, "classSvc");
        Fixtures.ClassSvc handle = (Fixtures.ClassSvc) Fixtures.managed(c).requireInterceptor("classSvc")
                .newTarget(InterceptMode.MOCK);
        when(handle.value()).thenReturn("private");

        assertThat(wrapper.value()).as("wrapper never stubbed: no error, routed to the test mock (unstubbed => null)").isNull();
        assertThat(handle.value()).isEqualTo("private");
    }

    // ---- loại bean ----

    @Test
    void aliasDeclaredAsInterceptableIsResolvedToTheCanonicalBean() {
        AnnotationConfigApplicationContext c = Fixtures.started("alias", new String[] {"aliasedAlt"}, Map.of(), Fixtures.Beans.class);
        ManagedContext mc = Fixtures.managed(c);

        assertThat(mc.interceptors).containsKey("aliased");
        assertThat(mc.requireInterceptor("aliasedAlt")).isSameAs(mc.requireInterceptor("aliased"));
    }

    @Test
    void factoryBeanProductIsInterceptedButTheFactoryItselfIsNot() {
        AnnotationConfigApplicationContext c = Fixtures.started("fb", new String[] {"fb"}, Map.of(), Fixtures.Beans.class);

        assertThat(Fixtures.managed(c).requireInterceptor("fb")).as("FactoryBean products are created lazily; created on demand").isNotNull();
        assertThat(c.getBean("&fb")).isInstanceOf(Fixtures.SvcFactory.class);
        assertThat(c.getBean("fb", Fixtures.ClassSvc.class).toString()).isEqualTo("Interceptor[fb]");
    }

    @Test
    void nonSingletonFactoryProductPrototypeAndCustomScopeGiveAccurateErrors() {
        assertRefreshFails("protoFb", "not a singleton", "FactoryBean product");
        assertRefreshFails("proto", "not a singleton", "scope 'prototype'");
        assertRefreshFails("threaded", "not a singleton", "scope 'thread'");
    }

    private static void assertRefreshFails(String bean, String... fragments) {
        AnnotationConfigApplicationContext c = Fixtures.context("bad-" + bean, new String[] {bean}, new String[0], Map.of(), Fixtures.Beans.class);
        // prototype/thread không được khởi tạo lúc refresh: BPP không chạy => lỗi ở lần getBean
        c.refresh();
        assertThatThrownBy(() -> c.getBean(bean)).hasRootCauseInstanceOf(ParallelTestException.Config.class)
                .rootCause().hasMessageContaining("BEAN_NOT_INTERCEPTABLE").hasMessageContaining(fragments[0]).hasMessageContaining(fragments[1]);
    }

    // ---- bean lazy ----

    @Test
    void lazyBeanInitialisedThroughTheLibraryRunsPostConstructAgainstTheRealBeanEvenOnAnUnboundThread() throws Exception {
        AnnotationConfigApplicationContext c = Fixtures.started("lazy", new String[] {"other", "lazyHolder"}, Map.of("ptest.unbound-policy", "fail"), Fixtures.LazyConfig.class);
        ManagedContext mc = Fixtures.managed(c);
        assertThat(mc.interceptors).as("lazy bean not created yet").doesNotContainKey("lazyHolder");

        Object result = Fixtures.onFreshThread(() -> mc.requireInterceptor("lazyHolder"));

        assertThat(result).isInstanceOf(BeanInterceptor.class);
        assertThat(Fixtures.LAZY_SEEN.get()).as("@PostConstruct saw the REAL bean").isEqualTo("real");
    }

    @Test
    void controlLazyInitialisationOutsideTheLibraryOnAnUnboundThreadSeesTheUnboundRule() throws Exception {
        AnnotationConfigApplicationContext c = Fixtures.started("lazy-raw", new String[] {"other", "lazyHolder"}, Map.of("ptest.unbound-policy", "fail"), Fixtures.LazyConfig.class);

        Object result = Fixtures.onFreshThread(() -> c.getBean("lazyHolder"));

        assertThat((Throwable) result).as("without the neutral binding the PostConstruct call is rejected")
                .hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);
    }
}
