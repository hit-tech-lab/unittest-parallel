package io.hit.ptest.junit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.DynamicContainer.dynamicContainer;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

import io.hit.ptest.intercept.TestBinding;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DynamicContainer;
import org.junit.jupiter.api.DynamicNode;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

/** Dynamic test chạy trên thread khác vẫn thấy binding của factory, và thread đó được trả về binding cũ sau khi chạy. */
class DynamicBindingsTest {

    private final TestBinding binding = TestBinding.open("dynamic-bindings-test", Map.of());
    private final List<TestBinding> seen = new ArrayList<>();

    @AfterEach
    void close() {
        binding.close();
    }

    private Executable recording() {
        return () -> seen.add(TestBinding.current());
    }

    /** Chạy executable trên thread mới (như JUnit giao dynamic test cho worker khác), trả binding còn lại trên thread đó sau khi chạy. */
    private static TestBinding runOnAnotherThread(Executable executable) throws Exception {
        TestBinding[] after = new TestBinding[1];
        Throwable[] error = new Throwable[1];
        Thread t = new Thread(() -> {
            try {
                executable.execute();
            } catch (Throwable e) {
                error[0] = e;
            }
            after[0] = TestBinding.current();
        });
        t.start();
        t.join();
        assertThat(error[0]).isNull();
        return after[0];
    }

    @Test
    void dynamicTestBindsTheFactoryBindingOnAnyThreadAndRestoresIt() throws Exception {
        DynamicTest wrapped = (DynamicTest) DynamicBindings.wrap(dynamicTest("t", recording()), binding);

        TestBinding left = runOnAnotherThread(wrapped.getExecutable());

        assertThat(seen).containsExactly(binding);
        assertThat(left).as("thread goes back to having no binding").isNull();
    }

    @Test
    void streamIsWrappedLazilyAndContainersRecursively() throws Exception {
        AtomicBoolean consumed = new AtomicBoolean();
        Stream<DynamicNode> source = Stream.of(dynamicTest("a", recording()),
                dynamicContainer("c", Stream.of(dynamicTest("b", recording())))).peek(n -> consumed.set(true));

        @SuppressWarnings("unchecked")
        Stream<DynamicNode> wrapped = (Stream<DynamicNode>) DynamicBindings.wrap(source, binding);
        assertThat(consumed).as("lazy: nothing consumed before JUnit iterates").isFalse();
        List<DynamicNode> nodes = wrapped.toList();

        runOnAnotherThread(((DynamicTest) nodes.get(0)).getExecutable());
        runOnAnotherThread(((DynamicTest) ((DynamicContainer) nodes.get(1)).getChildren().findFirst().orElseThrow()).getExecutable());
        assertThat(seen).containsExactly(binding, binding);
    }

    @Test
    void collectionsArraysIterablesAndIteratorsKeepTheirShape() throws Exception {
        DynamicTest test = dynamicTest("x", recording());

        assertThat(DynamicBindings.wrap(List.of(test), binding)).isInstanceOf(java.util.Collection.class);
        assertThat(DynamicBindings.wrap(new DynamicNode[] {test}, binding)).isInstanceOf(DynamicNode[].class);
        assertThat(DynamicBindings.wrap(List.of(test).iterator(), binding)).isInstanceOf(java.util.Iterator.class);
        Iterable<?> iterable = (Iterable<?>) DynamicBindings.wrap((Iterable<DynamicNode>) () -> List.<DynamicNode>of(test).iterator(), binding);
        runOnAnotherThread(((DynamicTest) iterable.iterator().next()).getExecutable());
        assertThat(seen).containsExactly(binding);
        assertThat(DynamicBindings.wrap("not a node", binding)).isEqualTo("not a node");
    }

    @Test
    void wrappedNodesKeepNameAndSourceUriAtEveryLevel() {
        java.net.URI uri = java.net.URI.create("file:///x");
        DynamicTest test = (DynamicTest) DynamicBindings.wrap(dynamicTest("t", uri, recording()), binding);
        DynamicContainer container = (DynamicContainer) DynamicBindings.wrap(dynamicContainer("c", uri, Stream.of(dynamicTest("child", recording()))), binding);

        assertThat(test.getDisplayName()).isEqualTo("t");
        assertThat(test.getTestSourceUri()).contains(uri);
        assertThat(container.getDisplayName()).isEqualTo("c");
        assertThat(container.getTestSourceUri()).contains(uri);
        DynamicNode child = container.getChildren().findFirst().orElseThrow();
        assertThat(child.getDisplayName()).isEqualTo("child");
        assertThat(child.getTestSourceUri()).isEmpty();
    }

    /**
     * Canary: DynamicBindings dựng lại node qua factory công khai của JUnit nên chỉ giữ được những gì API công khai cung cấp. Trên JUnit
     * 5.10.5 và 6.0.3 chỉ có tên hiển thị + URI nguồn (+ executable/con). Nếu một bản JUnit mới thêm metadata cho node (vd chế độ thực thi
     * theo node), test này hỏng để buộc cập nhật {@code DynamicBindings} thay vì lặng lẽ làm mất metadata đó.
     */
    @Test
    void junitDynamicNodeApiHasNoMetadataBeyondWhatDynamicBindingsPreserves() {
        assertThat(publicGetters(DynamicNode.class)).containsExactlyInAnyOrder("getDisplayName", "getTestSourceUri");
        assertThat(publicGetters(DynamicTest.class)).containsExactlyInAnyOrder("getExecutable");
        assertThat(publicGetters(DynamicContainer.class)).containsExactlyInAnyOrder("getChildren");
    }

    private static java.util.Set<String> publicGetters(Class<?> type) {
        return java.util.Arrays.stream(type.getDeclaredMethods())
                .filter(m -> java.lang.reflect.Modifier.isPublic(m.getModifiers()) && !java.lang.reflect.Modifier.isStatic(m.getModifiers()))
                .map(java.lang.reflect.Method::getName).filter(n -> !n.equals("toString")).collect(java.util.stream.Collectors.toSet());
    }
}
