package com.hittech.paralleltest.mock;

import com.hittech.paralleltest.core.TestBindings;
import com.hittech.paralleltest.core.TestBindings.Binding;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * Carries the current test's binding (its @PerTestMock mocks and identity) to other threads:
 * <ul>
 *   <li>HTTP to the embedded server: {@code PerTestMockHttp.propagation()} on your client (header {@value #HEADER});</li>
 *   <li>executors / CompletableFuture / @Async: {@link #wrap(Runnable)}, {@link #wrapCallable(Callable)},
 *       {@link #wrapSupplier(Supplier)}.</li>
 * </ul>
 * Calls on a thread without binding fail loudly; a wrapped task running after its test ended fails too.
 */
public final class PerTestMocks {

    public static final String HEADER = "X-Parallel-Test-Id";

    private PerTestMocks() {
    }

    public static Optional<String> currentTestId() {
        return Optional.ofNullable(TestBindings.current()).map(Binding::id);
    }

    private static Binding requireCurrent() {
        Binding b = TestBindings.current();
        if (b == null) {
            throw new IllegalStateException("PerTestMocks.wrap*(...) must be called on the test thread");
        }
        return b;
    }

    public static Runnable wrap(Runnable task) {
        Binding b = requireCurrent();
        return () -> unchecked(() -> TestBindings.callWith(b, () -> {
            task.run();
            return null;
        }));
    }

    public static <T> Callable<T> wrapCallable(Callable<T> task) {
        Binding b = requireCurrent();
        return () -> TestBindings.callWith(b, task);
    }

    public static <T> Supplier<T> wrapSupplier(Supplier<T> task) {
        Binding b = requireCurrent();
        return () -> unchecked(() -> TestBindings.callWith(b, task::get));
    }

    private static <T> T unchecked(Callable<T> c) {
        try {
            return c.call();
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
