package com.hittech.paralleltest.mock;

import com.hittech.paralleltest.core.TestBindings;
import com.hittech.paralleltest.core.TestBindings.Binding;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.mockito.Mockito;

/** Creates the @PerTestMock mocks of a test instance and opens its binding (used by the guard listener). */
public final class PerTestMockSupport {

    private PerTestMockSupport() {
    }

    /**
     * Always opens a binding (also without mocks: it identifies the test on other threads).
     * @Nested classes also get mocks for the @PerTestMock fields of their enclosing classes. The enclosing
     * instance is injected when reachable; since JDK 18 javac omits the {@code this$0} field of inner classes
     * that never use it -- such a test cannot touch the outer fields anyway, so the mock is only bound for routing.
     */
    public static Binding bind(Object testInstance, String testName) throws IllegalAccessException {
        Map<Class<?>, Object> mocks = new LinkedHashMap<>();
        List<Object> instances = withEnclosingInstances(testInstance);
        Class<?> declaring = testInstance.getClass();
        for (int i = 0; declaring != null; i++, declaring = isInner(declaring) ? declaring.getEnclosingClass() : null) {
            Object instance = i < instances.size() ? instances.get(i) : null;
            for (Class<?> c = declaring; c != null && c != Object.class; c = c.getSuperclass()) {
                for (Field f : c.getDeclaredFields()) {
                    if (!f.isAnnotationPresent(PerTestMock.class)) {
                        continue;
                    }
                    if (mocks.containsKey(f.getType())) {
                        throw new IllegalStateException("[parallel-test-guard] two @PerTestMock fields of type "
                                + f.getType().getName() + " in " + testInstance.getClass().getName());
                    }
                    Object mock = Mockito.mock(f.getType(), testName + "." + f.getName());
                    if (instance != null) {
                        f.setAccessible(true);
                        f.set(instance, mock);
                    }
                    mocks.put(f.getType(), mock);
                }
            }
        }
        return TestBindings.open(testName, mocks);
    }

    private static boolean isInner(Class<?> c) {
        return c.getEnclosingClass() != null && !Modifier.isStatic(c.getModifiers());
    }

    /** @return violations raised for this test on other threads (to be re-thrown by the caller). */
    public static List<Throwable> unbind(Binding binding) {
        return TestBindings.close(binding);
    }

    /** {@code this$N} chain of inner (@Nested) class instances, as far as javac kept it. */
    static List<Object> withEnclosingInstances(Object instance) throws IllegalAccessException {
        List<Object> out = new ArrayList<>();
        Object current = instance;
        while (current != null) {
            out.add(current);
            Object outer = null;
            for (Field f : current.getClass().getDeclaredFields()) {
                if (f.isSynthetic() && f.getName().startsWith("this$")) {
                    f.setAccessible(true);
                    outer = f.get(current);
                    break;
                }
            }
            current = outer;
        }
        return out;
    }
}
