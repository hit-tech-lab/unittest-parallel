package io.hit.ptest.intercept;

import io.hit.ptest.ParallelTest;
import io.hit.ptest.junit.ParallelTestExtension;
import io.hit.ptest.propagation.BindingTaskDecorator;
import io.hit.ptest.propagation.TaskDecoratorInstaller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.slf4j.LoggerFactory;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/** Điểm 4 (binding cấp lớp, @Nested/work-stealing, ThreadLocal sạch) và 6 (TaskDecorator của người dùng). */
class BindingLifecycleTest {

    static class Outer {
    }

    static class Nested {
    }

    private final ParallelTestExtension extension = new ParallelTestExtension();

    @AfterEach
    void cleanup() {
        TestBinding.bind(null);
    }

    /** ExtensionContext tối thiểu: store trong bộ nhớ + lớp test. */
    private static ExtensionContext contextFor(Class<?> testClass) {
        Map<Object, Object> data = new HashMap<>();
        ExtensionContext.Store store = mock(ExtensionContext.Store.class);
        org.mockito.Mockito.doAnswer(i -> data.put(i.getArgument(0), i.getArgument(1))).when(store).put(any(), any());
        when(store.remove(any(), any(Class.class))).thenAnswer(i -> i.getArgument(1, Class.class).cast(data.remove(i.getArgument(0))));
        ExtensionContext ec = mock(ExtensionContext.class);
        when(ec.getStore(any(ExtensionContext.Namespace.class))).thenReturn(store);
        when(ec.getRequiredTestClass()).thenAnswer(i -> testClass);
        return ec;
    }

    @Test
    void nestedClassRestoresTheOuterClassBindingInsteadOfClearingTheThread() {
        ExtensionContext outer = contextFor(Outer.class);
        ExtensionContext nested = contextFor(Nested.class);

        extension.beforeAll(outer);
        TestBinding outerBinding = TestBinding.current();
        extension.beforeAll(nested);
        assertThat(TestBinding.current()).isNotSameAs(outerBinding);
        extension.afterAll(nested);

        assertThat(TestBinding.current()).as("outer binding is back (previously the thread was left with none)").isSameAs(outerBinding);
        extension.afterAll(outer);
        assertThat(TestBinding.current()).as("thread is clean after the outermost afterAll").isNull();
        assertThat(outerBinding.isClosed()).isTrue();
    }

    @Test
    void afterAllClosesItsBindingEvenIfTheThreadNowHoldsAnotherOne() {
        ExtensionContext ec = contextFor(Outer.class);
        extension.beforeAll(ec);
        TestBinding mine = TestBinding.current();
        TestBinding stolen = TestBinding.open("other-class", Map.of());
        TestBinding.bind(stolen); // work-stealing: thread đã chuyển sang việc khác

        extension.afterAll(ec);

        assertThat(mine.isClosed()).as("closed, not leaked in ACTIVE").isTrue();
        assertThat(TestBinding.find(mine.id())).isNull();
        assertThat(TestBinding.current()).as("another class's binding on this thread is left alone").isSameAs(stolen);
        stolen.close();
    }

    @Test
    void pooledThreadsKeepNoBindingAfterWrappedTasksAndDecoratedTasks() throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(1);
        try {
            TestBinding b = TestBinding.open("pool-test", Map.of());
            TestBinding.bind(b);
            AtomicReference<Boolean> inside = new AtomicReference<>();
            pool.submit(ParallelTest.wrap(() -> inside.set(ParallelTest.hasBinding()))).get();
            Runnable decorated = new BindingTaskDecorator(null).decorate(() -> inside.set(ParallelTest.hasBinding() && inside.get()));
            pool.submit(decorated).get();
            assertThat(inside.get()).as("the task saw the submitter's binding").isTrue();

            AtomicReference<Boolean> after = new AtomicReference<>();
            pool.submit(() -> after.set(ParallelTest.hasBinding())).get();

            assertThat(after.get()).as("same pool thread, plain task: no binding left behind").isFalse();
            b.close();
        } finally {
            pool.shutdownNow();
        }
    }

    // ---- TaskDecoratorInstaller ----

    private ListAppender<ILoggingEvent> captureWarnings() {
        Logger logger = (Logger) LoggerFactory.getLogger(TaskDecoratorInstaller.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        return appender;
    }

    @Test
    void userDecoratorIsComposedNotOverwritten() throws Exception {
        CopyOnWriteArrayList<String> order = new CopyOnWriteArrayList<>();
        ThreadPoolTaskExecutor exec = new ThreadPoolTaskExecutor();
        TaskDecorator user = r -> () -> {
            order.add("user:" + ParallelTest.hasBinding());
            r.run();
        };
        exec.setTaskDecorator(user);

        new TaskDecoratorInstaller().postProcessBeforeInitialization(exec, "exec");
        exec.initialize();
        TestBinding b = TestBinding.open("compose", Map.of());
        TestBinding.bind(b);
        try {
            exec.submit(() -> order.add("task:" + ParallelTest.hasBinding())).get();
        } finally {
            exec.shutdown();
            b.close();
        }

        assertThat(order).as("user decorator still runs, and with the binding").containsExactly("user:true", "task:true");
    }

    /** Executor có setter nhưng không có field đọc được: không đụng vào + WARN. */
    static class OpaqueExecutor implements Executor {
        private TaskDecorator hidden;

        public void setTaskDecorator(TaskDecorator d) {
            this.hidden = d;
        }

        @Override
        public void execute(Runnable command) {
            command.run();
        }

        TaskDecorator hidden() {
            return hidden;
        }
    }

    static class PlainExecutor implements Executor {
        @Override
        public void execute(Runnable command) {
            command.run();
        }
    }

    @Test
    void unreadableDecoratorIsLeftUntouchedWithAWarning() {
        ListAppender<ILoggingEvent> warnings = captureWarnings();
        OpaqueExecutor exec = new OpaqueExecutor();
        TaskDecorator userSet = r -> r;
        exec.setTaskDecorator(userSet);

        new TaskDecoratorInstaller().postProcessBeforeInitialization(exec, "opaque");

        assertThat(exec.hidden()).as("user decorator not overwritten").isSameAs(userSet);
        assertThat(warnings.list).extracting(ILoggingEvent::getFormattedMessage).anyMatch(m -> m.contains("Cannot read the current TaskDecorator") && m.contains("opaque"));
    }

    @Test
    void executorWithoutSetterIsReportedAndOnlyOncePerBean() {
        ListAppender<ILoggingEvent> warnings = captureWarnings();
        var installer = new TaskDecoratorInstaller();
        PlainExecutor plain = new PlainExecutor();

        installer.postProcessBeforeInitialization(plain, "plainExec");
        installer.postProcessBeforeInitialization(plain, "plainExec");
        installer.postProcessBeforeInitialization("not an executor", "str");

        assertThat(warnings.list).extracting(ILoggingEvent::getFormattedMessage)
                .filteredOn(m -> m.contains("plainExec")).hasSize(1).allMatch(m -> m.contains("no setTaskDecorator"));
        assertThat(warnings.list).extracting(ILoggingEvent::getFormattedMessage).noneMatch(m -> m.contains("'str'"));
    }
}
