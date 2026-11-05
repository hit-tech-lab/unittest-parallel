package com.example.parallel.support;

import java.lang.reflect.Field;
import org.junit.jupiter.api.extension.AfterEachCallback;
import org.junit.jupiter.api.extension.BeforeEachCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.mockito.Mockito;

/**
 * beforeEach / test method / afterEach run on the same thread in JUnit Jupiter, so a mock bound
 * in beforeEach is exactly the one the test's calls are routed to.
 */
public class PerTestMockExtension implements BeforeEachCallback, AfterEachCallback {

    @Override
    public void beforeEach(ExtensionContext context) throws IllegalAccessException {
        Object instance = context.getRequiredTestInstance();
        for (Class<?> c = instance.getClass(); c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (f.isAnnotationPresent(PerTestMock.class)) {
                    Object mock = Mockito.mock(f.getType(), f.getName());
                    f.setAccessible(true);
                    f.set(instance, mock);
                    MockRouter.bind(f.getType(), mock);
                }
            }
        }
    }

    @Override
    public void afterEach(ExtensionContext context) {
        MockRouter.unbindAll(); // pool threads are reused by other tests: never leak a binding
    }
}
