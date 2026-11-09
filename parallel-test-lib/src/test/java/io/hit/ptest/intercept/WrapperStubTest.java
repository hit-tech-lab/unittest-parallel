package io.hit.ptest.intercept;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.context.ManagedContext;

import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Stub nhầm vào wrapper dùng chung (class và JDK proxy): bắt ở cuối test sai kèm vị trí dòng stub; nhiều thread không ảnh hưởng. */
class WrapperStubTest {

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    private static AnnotationConfigApplicationContext ctx(String... interceptable) {
        return Fixtures.started("stub-" + System.nanoTime(), interceptable, Map.of("ptest.unbound-policy", "real"), Fixtures.Beans.class);
    }

    @Test
    void classWrapperStubIsReportedAtTheEndOfTheOffendingTestWithTheStubLocation() {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        TestBinding binding = TestBinding.open("offender", Map.of()); // REAL: @Intercepted trả về chính wrapper
        TestBinding.bind(binding);

        when(wrapper.value()).thenReturn("leak");

        assertThatThrownBy(binding::verifyWrappersNotStubbed).isInstanceOf(ParallelTestException.Config.class)
                .hasMessageContaining("BEAN_WRAPPER_STUBBED").hasMessageContaining("WrapperStubTest")
                .hasMessageContaining("end of the offending test");
    }

    @Test
    void jdkProxyWrapperStubIsDetectedTheSameWay() {
        AnnotationConfigApplicationContext c = ctx("jdkSvc");
        Fixtures.Svc wrapper = c.getBean("jdkSvc", Fixtures.Svc.class);
        TestBinding binding = TestBinding.open("offender-jdk", Map.of());
        TestBinding.bind(binding);

        when(wrapper.value()).thenReturn("leak");

        assertThatThrownBy(binding::verifyWrappersNotStubbed).hasMessageContaining("BEAN_WRAPPER_STUBBED").hasMessageContaining("jdkSvc");
        assertThat(wrapper.value()).as("the wrapper was reset: the error does not spread to the next test").isEqualTo("real");
    }

    @Test
    void aMatchingCallFromAnotherTestGetsTheErrorOnceNeverTheStubValueAndTheWrapperIsThenClean() throws Exception {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        TestBinding.bind(TestBinding.open("offender", Map.of()));
        when(wrapper.value()).thenReturn("leak");

        Object other = Fixtures.onFreshThread(wrapper::value);

        assertThat(other).as("not the stub value").isInstanceOf(ParallelTestException.Config.class);
        assertThat(((Throwable) other).getMessage()).contains("BEAN_WRAPPER_STUBBED").contains("was removed");
        assertThat(wrapper.value()).as("second call: clean").isEqualTo("real");
        assertThat(Fixtures.onFreshThread(wrapper::value)).isEqualTo("real");
    }

    @Test
    void resetKeepsTheSettingsSoAFreshStubIsDetectedAgain() {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        TestBinding first = TestBinding.open("first", Map.of());
        TestBinding.bind(first);
        when(wrapper.value()).thenReturn("leak1");
        assertThatThrownBy(first::verifyWrappersNotStubbed).hasMessageContaining("BEAN_WRAPPER_STUBBED");
        first.close();

        TestBinding second = TestBinding.open("second", Map.of());
        TestBinding.bind(second);
        assertThat(wrapper.value()).isEqualTo("real");
        when(wrapper.value()).thenReturn("leak2");
        assertThatThrownBy(second::verifyWrappersNotStubbed).hasMessageContaining("BEAN_WRAPPER_STUBBED");
        assertThat(wrapper.value()).isEqualTo("real");
    }

    @Test
    void stubbingTheWrapperIsAMistakeEvenWhenTheTestBoundAMock() {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        TestBinding binding = Fixtures.bindMock(c, "classSvc");

        when(wrapper.value()).thenReturn("mine"); // Mockito gán stub vào lời gọi NGOÀI CÙNG (wrapper), không phải mock của test

        assertThatThrownBy(binding::verifyWrappersNotStubbed).hasMessageContaining("BEAN_WRAPPER_STUBBED");
    }

    @Test
    void controlStubbingTheMockHandleIsNotReported() {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        ManagedContext mc = Fixtures.managed(c);
        Fixtures.ClassSvc handle = (Fixtures.ClassSvc) mc.requireInterceptor("classSvc").newTarget(InterceptMode.MOCK);
        TestBinding binding = TestBinding.open("ok", Map.of(new TestBinding.TargetKey(mc.key, "classSvc"), handle));
        TestBinding.bind(binding);
        when(handle.value()).thenReturn("mine");

        assertThat(wrapper.value()).isEqualTo("mine");
        binding.verifyWrappersNotStubbed();
    }

    @Test
    void manyThreadsSharingOneWrapperEachGetTheirOwnMockAndNothingIsReported() throws Exception {
        AnnotationConfigApplicationContext c = ctx("classSvc");
        Fixtures.ClassSvc wrapper = c.getBean("classSvc", Fixtures.ClassSvc.class);
        ManagedContext mc = Fixtures.managed(c);
        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Integer>> results = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                String mine = "t" + t;
                results.add(pool.submit(() -> {
                    Fixtures.ClassSvc target = (Fixtures.ClassSvc) mc.requireInterceptor("classSvc").newTarget(InterceptMode.MOCK);
                    when(target.value()).thenReturn(mine);
                    TestBinding b = TestBinding.open(mine, Map.of(new TestBinding.TargetKey(mc.key, "classSvc"), target));
                    TestBinding.bind(b);
                    try {
                        start.await();
                        int wrong = 0;
                        for (int i = 0; i < 300; i++) {
                            if (!mine.equals(wrapper.value())) {
                                wrong++;
                            }
                        }
                        b.verifyWrappersNotStubbed();
                        return wrong;
                    } finally {
                        TestBinding.bind(null);
                        b.close();
                    }
                }));
            }
            start.countDown();
            for (Future<Integer> f : results) {
                assertThat(f.get()).as("calls routed to another thread's mock").isZero();
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
