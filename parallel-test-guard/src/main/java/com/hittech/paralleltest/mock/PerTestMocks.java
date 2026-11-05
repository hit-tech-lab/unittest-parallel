package com.hittech.paralleltest.mock;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Registry of the per-test mock bindings. A binding is attached to the test thread automatically;
 * use the helpers below to carry it to other threads:
 * <ul>
 *   <li>HTTP to the embedded server: {@code PerTestMockHttp.propagation()} on your client
 *       (adds header {@value #HEADER}; the guard's servlet filter re-binds it on the server thread);</li>
 *   <li>executors / CompletableFuture / @Async: {@link #wrap(Runnable)}, {@link #wrapCallable(Callable)},
 *       {@link #wrapSupplier(Supplier)}.</li>
 * </ul>
 * Calls from a thread without binding fail loudly (never routed to another test's mock).
 */
public final class PerTestMocks {

    public static final String HEADER = "X-Parallel-Test-Id";

    record Binding(String id, String testName, Map<Class<?>, Object> mocks) {
    }

    private static final ThreadLocal<Binding> CURRENT = new ThreadLocal<>();
    private static final Map<String, Binding> BY_ID = new ConcurrentHashMap<>();

    private PerTestMocks() {
    }

    static Binding open(String testName, Map<Class<?>, Object> mocks) {
        Binding b = new Binding(UUID.randomUUID().toString(), testName, Map.copyOf(mocks));
        BY_ID.put(b.id(), b);
        CURRENT.set(b);
        return b;
    }

    static void close(Binding b) {
        BY_ID.remove(b.id());
        CURRENT.remove();
    }

    static Binding current() {
        return CURRENT.get();
    }

    static Binding byId(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** Runs {@code action} with {@code binding} attached to the current thread, restoring the previous one. */
    static <T> T callWith(Binding binding, Callable<T> action) throws Exception {
        Binding previous = CURRENT.get();
        CURRENT.set(binding);
        try {
            return action.call();
        } finally {
            if (previous == null) {
                CURRENT.remove();
            } else {
                CURRENT.set(previous);
            }
        }
    }

    public static Optional<String> currentTestId() {
        return Optional.ofNullable(CURRENT.get()).map(Binding::id);
    }

    private static Binding requireCurrent() {
        Binding b = CURRENT.get();
        if (b == null) {
            throw new IllegalStateException("PerTestMocks.wrap(...) must be called on the test thread");
        }
        return b;
    }

    public static Runnable wrap(Runnable task) {
        Binding b = requireCurrent();
        return () -> {
            try {
                callWith(b, () -> {
                    task.run();
                    return null;
                });
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }

    public static <T> Callable<T> wrapCallable(Callable<T> task) {
        Binding b = requireCurrent();
        return () -> callWith(b, task);
    }

    public static <T> Supplier<T> wrapSupplier(Supplier<T> task) {
        Binding b = requireCurrent();
        return () -> {
            try {
                return callWith(b, task::get);
            } catch (RuntimeException | Error e) {
                throw e;
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        };
    }
}
