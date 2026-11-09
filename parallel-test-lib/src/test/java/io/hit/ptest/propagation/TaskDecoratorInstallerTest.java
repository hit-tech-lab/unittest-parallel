package io.hit.ptest.propagation;

import io.hit.ptest.ParallelTest;
import io.hit.ptest.intercept.TestBinding;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.SimpleAsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskExecutor;
import org.springframework.scheduling.concurrent.ConcurrentTaskScheduler;
import org.springframework.scheduling.concurrent.SimpleAsyncTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

/**
 * Các executor của Spring ngoài ThreadPoolTaskExecutor. Dùng kết quả THẬT: tác vụ thường (execute/submit) thấy binding; tác vụ
 * LÊN LỊCH của scheduler được ghi nhận riêng (giới hạn: xem Javadoc của {@link TaskDecoratorInstaller}).
 */
class TaskDecoratorInstallerTest {

    @AfterEach
    void cleanup() {
        TestBinding.bind(null);
    }

    private static boolean sawBindingVia(java.util.function.Consumer<Runnable> submit) throws Exception {
        TestBinding b = TestBinding.open("exec-test", Map.of());
        TestBinding.bind(b);
        try {
            CompletableFuture<Boolean> seen = new CompletableFuture<>();
            submit.accept(() -> seen.complete(ParallelTest.hasBinding()));
            return seen.get(5, TimeUnit.SECONDS);
        } finally {
            TestBinding.bind(null);
            b.close();
        }
    }

    @Test
    void simpleAsyncTaskExecutorPropagatesTheBinding() throws Exception {
        SimpleAsyncTaskExecutor exec = new SimpleAsyncTaskExecutor("sat-");
        new TaskDecoratorInstaller().postProcessBeforeInitialization(exec, "sat");

        assertThat(sawBindingVia(exec::execute)).isTrue();
    }

    @Test
    void simpleAsyncTaskSchedulerPropagatesTheBindingForPlainSubmissions() throws Exception {
        SimpleAsyncTaskScheduler exec = new SimpleAsyncTaskScheduler();
        new TaskDecoratorInstaller().postProcessBeforeInitialization(exec, "sats");

        assertThat(sawBindingVia(exec::execute)).isTrue();
    }

    @Test
    void concurrentTaskExecutorPropagatesTheBinding() throws Exception {
        var pool = Executors.newFixedThreadPool(1);
        try {
            ConcurrentTaskExecutor exec = new ConcurrentTaskExecutor(pool);
            new TaskDecoratorInstaller().postProcessBeforeInitialization(exec, "cte");

            assertThat(sawBindingVia(exec::execute)).isTrue();
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void threadPoolTaskSchedulerPropagatesTheBindingForExecuteAndSchedule() throws Exception {
        // ThreadPoolTaskScheduler.setTaskDecorator chỉ có từ Spring 6.2 (Boot 3.3.5 dùng Spring 6.1: không truyền, chỉ WARN)
        org.junit.jupiter.api.Assumptions.assumeTrue(org.springframework.util.ClassUtils.hasMethod(ThreadPoolTaskScheduler.class,
                "setTaskDecorator", org.springframework.core.task.TaskDecorator.class), "needs Spring 6.2+");
        ThreadPoolTaskScheduler sched = new ThreadPoolTaskScheduler();
        new TaskDecoratorInstaller().postProcessBeforeInitialization(sched, "tpts");
        sched.initialize();
        try {
            assertThat(sawBindingVia(sched::execute)).as("execute").isTrue();
            assertThat(sawBindingVia(r -> sched.schedule(r, java.time.Instant.now()))).as("schedule(once)").isTrue();
        } finally {
            sched.shutdown();
        }
    }

    @Test
    void concurrentTaskSchedulerWithoutSetterPathStillGetsDecoratorForPlainExecute() throws Exception {
        var pool = Executors.newSingleThreadScheduledExecutor();
        try {
            ConcurrentTaskScheduler sched = new ConcurrentTaskScheduler(pool);
            new TaskDecoratorInstaller().postProcessBeforeInitialization(sched, "cts");

            assertThat(sawBindingVia(sched::execute)).as("execute").isTrue();
            boolean scheduled = sawBindingVia(r -> sched.schedule(r, java.time.Instant.now()));
            // giới hạn được ghi nhận: nếu false, tác vụ lên lịch của ConcurrentTaskScheduler phải bọc bằng ParallelTest.wrap
            System.out.println("ConcurrentTaskScheduler.schedule propagates binding: " + scheduled);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void executorWithoutSetterIsLeftAloneAndNotPropagating() throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        Executor plain = Runnable::run;
        new TaskDecoratorInstaller().postProcessBeforeInitialization(plain, "plain");
        AtomicReference<Boolean> seen = new AtomicReference<>();
        Thread t = new Thread(() -> {
            plain.execute(() -> seen.set(ParallelTest.hasBinding()));
            done.countDown();
        });
        t.start();
        done.await(5, TimeUnit.SECONDS);

        assertThat(seen.get()).as("no binding on a thread that never had one: nothing invented").isFalse();
    }
}
