package com.hittech.paralleltest.mock;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Set;
import com.hittech.paralleltest.core.TestBindings;
import org.mockito.Answers;
import org.mockito.Mockito;

/** Creates the router bean registered for each {@link RoutedMocks} type. */
public final class MockRouter {

    /** Methods the container itself may call on the bean (outside any test). */
    private static final Set<String> CONTAINER_TYPES = Set.of(
            "java.lang.Object", "java.lang.AutoCloseable", "java.io.Closeable",
            "org.springframework.beans.factory.DisposableBean", "org.springframework.beans.factory.InitializingBean",
            "org.springframework.context.Lifecycle", "org.springframework.context.SmartLifecycle",
            "org.springframework.beans.factory.Aware", "org.springframework.core.Ordered");

    private MockRouter() {
    }

    public static <T> T routerFor(Class<T> type) {
        return Mockito.mock(type, Mockito.withSettings()
                .name("perTestMockRouter(" + type.getSimpleName() + ")")
                .defaultAnswer(invocation -> {
                    Method method = invocation.getMethod();
                    TestBindings.Binding binding = TestBindings.current();
                    Object target = binding == null ? null : binding.mockFor(type);
                    if (target == null) {
                        if (CONTAINER_TYPES.contains(method.getDeclaringClass().getName())) {
                            return Answers.RETURNS_DEFAULTS.answer(invocation);
                        }
                        throw new IllegalStateException(noBindingMessage(type, method, binding));
                    }
                    try {
                        method.setAccessible(true);
                        return method.invoke(target, invocation.getArguments());
                    } catch (InvocationTargetException e) {
                        throw e.getCause(); // keeps thenThrow(...) semantics
                    }
                }));
    }

    private static String noBindingMessage(Class<?> type, Method method, TestBindings.Binding binding) {
        String where = Thread.currentThread().getName();
        if (binding != null) {
            return "[parallel-test-guard] Test '" + binding.testName() + "' has no @PerTestMock field of type "
                    + type.getName() + " but called " + method.getName() + "(). Declare: @PerTestMock "
                    + type.getSimpleName() + " " + Character.toLowerCase(type.getSimpleName().charAt(0))
                    + type.getSimpleName().substring(1) + ";";
        }
        return "[parallel-test-guard] " + type.getSimpleName() + "." + method.getName()
                + "() was called on thread '" + where + "' which belongs to no test, so it cannot be routed to a "
                + "per-test mock. If the call comes from: HTTP to the embedded server -> add "
                + "PerTestMockHttp.propagation() to your client; an executor/CompletableFuture/@Async -> wrap the "
                + "task with PerTestMocks.wrap(Runnable) / wrapCallable / wrapSupplier; application startup -> use a real bean or a fake instead.";
    }
}
