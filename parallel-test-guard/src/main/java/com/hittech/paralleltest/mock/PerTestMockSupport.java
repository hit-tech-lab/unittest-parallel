package com.hittech.paralleltest.mock;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;
import org.mockito.Mockito;

/** Creates and binds the @PerTestMock mocks of a test instance (used by the guard listener). */
public final class PerTestMockSupport {

    private PerTestMockSupport() {
    }

    /** @return an opaque binding handle, or null when the test has no @PerTestMock fields. */
    public static Object bind(Object testInstance, String testName) throws IllegalAccessException {
        Map<Class<?>, Object> mocks = new LinkedHashMap<>();
        for (Class<?> c = testInstance.getClass(); c != null && c != Object.class; c = c.getSuperclass()) {
            for (Field f : c.getDeclaredFields()) {
                if (!f.isAnnotationPresent(PerTestMock.class)) {
                    continue;
                }
                if (mocks.containsKey(f.getType())) {
                    throw new IllegalStateException("[parallel-test-guard] two @PerTestMock fields of type "
                            + f.getType().getName() + " in " + testInstance.getClass().getName());
                }
                Object mock = Mockito.mock(f.getType(), testName + "." + f.getName());
                f.setAccessible(true);
                f.set(testInstance, mock);
                mocks.put(f.getType(), mock);
            }
        }
        return mocks.isEmpty() ? null : PerTestMocks.open(testName, mocks);
    }

    public static void unbind(Object binding) {
        PerTestMocks.close((PerTestMocks.Binding) binding);
    }
}
