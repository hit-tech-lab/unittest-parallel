package com.example.parallel.unit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.RepeatedTest;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

/**
 * Mockito.mockStatic / mockConstruction are THREAD-LOCAL: a static mock active in one test does not
 * leak into tests running at the same time on other threads. This test proves it, and is itself
 * repeated concurrently.
 */
class MockitoThreadConfinementTest {

    static final class Ids {
        static String next() {
            return "real";
        }
    }

    @RepeatedTest(20)
    void staticMockIsVisibleOnlyOnTheCurrentThread() throws Exception {
        String mine = "mocked-" + Thread.currentThread().getName() + "-" + System.nanoTime();
        try (MockedStatic<Ids> ids = Mockito.mockStatic(Ids.class)) {
            ids.when(Ids::next).thenReturn(mine);

            assertThat(Ids.next()).isEqualTo(mine);
            // Another thread (as a concurrently running test would be) still sees the real method.
            String otherThread = CompletableFuture.supplyAsync(Ids::next).get();
            assertThat(otherThread).isEqualTo("real");
        }
        assertThat(Ids.next()).isEqualTo("real");
    }
}
