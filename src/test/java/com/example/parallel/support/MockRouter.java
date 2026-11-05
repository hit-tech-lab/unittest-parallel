package com.example.parallel.support;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.Map;
import org.mockito.Mockito;

/**
 * Lets MANY concurrent test classes share ONE Spring context while each test has its OWN mock.
 *
 * The bean registered in the context is a "router" (itself a Mockito mock, so it works for
 * interfaces and classes) whose every call is forwarded to the mock bound to the CURRENT THREAD
 * by {@link PerTestMockExtension}. Stubbing, verify() and reset therefore never cross tests.
 *
 * Limitation (checked, not silent): only calls made on the test's own thread are routed --
 * direct service calls and MockMvc (webEnvironment=MOCK) qualify; calls from embedded-server
 * threads (RANDOM_PORT), @Async or custom executors throw instead of hitting the wrong mock.
 * For those, use a fake keyed by per-test data (FakeExchangeRateClient).
 */
public final class MockRouter {

    private static final ThreadLocal<Map<Class<?>, Object>> BOUND = ThreadLocal.withInitial(HashMap::new);

    private MockRouter() {
    }

    public static <T> T routerFor(Class<T> type) {
        return Mockito.mock(type, Mockito.withSettings()
                .name("router(" + type.getSimpleName() + ")")
                .defaultAnswer(invocation -> {
                    Object target = BOUND.get().get(type);
                    if (target == null) {
                        if (invocation.getMethod().getDeclaringClass() == Object.class) {
                            return invocation.callRealMethod(); // toString/hashCode used by Spring itself
                        }
                        throw new IllegalStateException("No @PerTestMock " + type.getSimpleName()
                                + " bound to thread '" + Thread.currentThread().getName()
                                + "': the call did not happen on the test thread (server thread, @Async, executor?)");
                    }
                    try {
                        return invocation.getMethod().invoke(target, invocation.getArguments());
                    } catch (InvocationTargetException e) {
                        throw e.getCause(); // keep thenThrow(...) semantics
                    }
                }));
    }

    static void bind(Class<?> type, Object mock) {
        BOUND.get().put(type, mock);
    }

    static void unbindAll() {
        BOUND.remove();
    }
}
