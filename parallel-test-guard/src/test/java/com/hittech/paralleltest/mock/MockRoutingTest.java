package com.hittech.paralleltest.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hittech.paralleltest.core.TestBindings;
import com.hittech.paralleltest.core.TestBindings.Binding;
import com.hittech.paralleltest.fixtures.Fixtures.Collaborator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

class MockRoutingTest {

    static final Collaborator ROUTER = MockRouter.routerFor(Collaborator.class);

    static class FakeTest {
        @PerTestMock Collaborator collaborator;
    }

    /** Concurrent repetitions stub the same method differently through ONE shared router. */
    @RepeatedTest(50)
    void routesToTheMockOfTheCallingTest() throws Exception {
        FakeTest t = new FakeTest();
        Binding binding = PerTestMockSupport.bind(t, "rep");
        try {
            String mine = "hello-" + System.nanoTime();
            when(t.collaborator.hello()).thenReturn(mine);
            Thread.sleep(1);
            assertThat(ROUTER.hello()).isEqualTo(mine);
            verify(t.collaborator).hello();
        } finally {
            PerTestMockSupport.unbind(binding);
        }
    }

    @Test
    void otherThreadsFailLoudlyUnlessWrapped() throws Exception {
        FakeTest t = new FakeTest();
        Binding binding = PerTestMockSupport.bind(t, "x");
        try {
            when(t.collaborator.hello()).thenReturn("stubbed");
            assertThatThrownBy(() -> CompletableFuture.supplyAsync(ROUTER::hello).get())
                    .isInstanceOf(ExecutionException.class)
                    .hasMessageContaining("belongs to no test").hasMessageContaining("PerTestMocks.wrap");
            try (var pool = Executors.newSingleThreadExecutor()) {
                assertThat(pool.submit(PerTestMocks.wrapCallable(() -> ROUTER.hello())).get()).isEqualTo("stubbed");
            }
            assertThat(CompletableFuture.supplyAsync(PerTestMocks.wrapSupplier(() -> ROUTER.hello())).get()).isEqualTo("stubbed");
        } finally {
            PerTestMockSupport.unbind(binding);
        }
        assertThatThrownBy(ROUTER::hello).hasMessageContaining("belongs to no test");
        assertThat(ROUTER.toString()).isNotBlank(); // container calls never fail
    }

    @Test
    void thenThrowIsPropagatedUnwrapped() throws Exception {
        FakeTest t = new FakeTest();
        Binding binding = PerTestMockSupport.bind(t, "y");
        try {
            when(t.collaborator.hello()).thenThrow(new IllegalArgumentException("boom"));
            assertThatThrownBy(ROUTER::hello).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
        } finally {
            PerTestMockSupport.unbind(binding);
        }
    }

    /** B6: a wrapped task that outlives its test must not keep using the dead test's mocks. */
    @Test
    void wrappedTaskRunningAfterItsTestFailsLoudly() throws Exception {
        FakeTest t = new FakeTest();
        Binding binding = PerTestMockSupport.bind(t, "finished");
        Runnable late = PerTestMocks.wrap(ROUTER::hello);
        PerTestMockSupport.unbind(binding);

        assertThatThrownBy(late::run).hasMessageContaining("has already finished");
    }

    class Outer {
        @PerTestMock Collaborator outerMock;

        class UsesOuter {
            @PerTestMock Runnable nestedMock;

            Collaborator outer() {
                return outerMock; // keeps this$N: the enclosing instance is reachable
            }
        }

        class IgnoresOuter {
            // javac (JDK 18+) emits no this$N field here
        }
    }

    /** B7: @Nested test instances also get the @PerTestMock fields of their enclosing instances. */
    @Test
    void nestedInstancesBindEnclosingMocks() throws Exception {
        Outer outer = new Outer();
        Outer.UsesOuter nested = outer.new UsesOuter();
        Binding binding = PerTestMockSupport.bind(nested, "nested");
        try {
            assertThat(nested.outer()).isNotNull();
            assertThat(nested.nestedMock).isNotNull();
            when(nested.outer().hello()).thenReturn("from outer");
            assertThat(ROUTER.hello()).isEqualTo("from outer");
        } finally {
            PerTestMockSupport.unbind(binding);
        }
    }

    /** Without a reachable enclosing instance the outer mock is still bound, so the router never fails. */
    @Test
    void nestedWithoutOuterReferenceStillRoutes() throws Exception {
        Outer.IgnoresOuter nested = new Outer().new IgnoresOuter();
        Binding binding = PerTestMockSupport.bind(nested, "nested-no-ref");
        try {
            assertThat(ROUTER.hello()).isNull(); // the (unstubbed) per-test mock answered
        } finally {
            PerTestMockSupport.unbind(binding);
        }
    }

    /** B8: an error raised on another thread for this test is handed back to the test on unbind. */
    @Test
    void violationsFromOtherThreadsAreAttributedToTheTest() throws Exception {
        Binding binding = PerTestMockSupport.bind(new FakeTest(), "attributed");
        AssertionError raisedOnServer = new AssertionError("whole-table delete");
        try (var pool = Executors.newSingleThreadExecutor()) {
            pool.submit(PerTestMocks.wrap(() -> TestBindings.reportFromAnyThread(raisedOnServer))).get();
        }
        TestBindings.reportFromAnyThread(new AssertionError("on the test thread: propagates by itself"));

        List<Throwable> foreign = PerTestMockSupport.unbind(binding);
        assertThat(foreign).containsExactly(raisedOnServer);
    }
}
