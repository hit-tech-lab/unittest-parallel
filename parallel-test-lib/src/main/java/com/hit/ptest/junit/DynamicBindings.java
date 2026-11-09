package com.hit.ptest.junit;

import com.hit.ptest.intercept.TestBinding;

import java.util.Collection;
import java.util.Iterator;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.function.Executable;

/**
 * Gắn binding của lần gọi {@code @TestFactory} vào từng dynamic test. JUnit có thể chạy dynamic test trên thread khác thread đã gọi
 * factory (đặc biệt khi chạy song song), mà binding nằm trong ThreadLocal nên thread đó sẽ không thấy mock/spy của test. Hàm ở đây bọc
 * lại kết quả của factory (một {@code DynamicNode}, mảng, {@code Stream}, {@code Collection}, {@code Iterable} hoặc {@code Iterator}) để mỗi
 * {@code Executable} tự bind rồi khôi phục binding trước đó; bọc theo kiểu lười nên không làm Stream bị duyệt sớm.
 */
final class DynamicBindings {

    private DynamicBindings() {
    }

    /** Trả về đối tượng cùng kiểu JUnit chấp nhận; giá trị không phải dynamic node được trả nguyên vẹn. */
    static Object wrap(Object result, TestBinding binding) {
        if (result instanceof DynamicTest test) {
            return DynamicTest.dynamicTest(test.getDisplayName(), test.getTestSourceUri().orElse(null), bound(test.getExecutable(), binding));
        }
        if (result instanceof DynamicContainer container) {
            return DynamicContainer.dynamicContainer(container.getDisplayName(), container.getTestSourceUri().orElse(null),
                    container.getChildren().map(child -> (DynamicNode) wrap(child, binding)));
        }
        if (result instanceof Stream<?> stream) {
            return stream.map(node -> wrap(node, binding));
        }
        if (result instanceof Collection<?> collection) {
            return collection.stream().map(node -> wrap(node, binding)).toList();
        }
        if (result instanceof Iterable<?> iterable) {
            return (Iterable<Object>) () -> StreamSupport.stream(iterable.spliterator(), false).map(node -> wrap(node, binding)).iterator();
        }
        if (result instanceof Iterator<?> iterator) {
            return new Iterator<Object>() {
                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public Object next() {
                    return wrap(iterator.next(), binding);
                }
            };
        }
        if (result instanceof DynamicNode[] nodes) {
            return java.util.Arrays.stream(nodes).map(node -> (DynamicNode) wrap(node, binding)).toArray(DynamicNode[]::new);
        }
        return result;
    }

    private static Executable bound(Executable executable, TestBinding binding) {
        return () -> {
            if (TestBinding.current() == binding) {
                executable.execute();
                return;
            }
            TestBinding previous = TestBinding.bind(binding);
            try {
                executable.execute();
            } finally {
                TestBinding.bind(previous);
            }
        };
    }
}
