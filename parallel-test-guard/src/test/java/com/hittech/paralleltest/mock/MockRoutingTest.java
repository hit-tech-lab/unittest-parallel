package com.hittech.paralleltest.mock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hittech.paralleltest.fixtures.Fixtures.Collaborator;
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
        Object binding = PerTestMockSupport.bind(t, "rep");
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
        Object binding = PerTestMockSupport.bind(t, "x");
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
        Object binding = PerTestMockSupport.bind(t, "y");
        try {
            when(t.collaborator.hello()).thenThrow(new IllegalArgumentException("boom"));
            assertThatThrownBy(ROUTER::hello).isInstanceOf(IllegalArgumentException.class).hasMessage("boom");
        } finally {
            PerTestMockSupport.unbind(binding);
        }
    }
}
