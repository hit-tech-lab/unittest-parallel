package com.hittech.paralleltest.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hittech.paralleltest.annotation.IsolatedContext;
import com.hittech.paralleltest.fixtures.Fixtures.Collaborator;
import com.hittech.paralleltest.mock.PerTestMocks;
import com.hittech.paralleltest.mock.RoutedMocks;
import com.hittech.paralleltest.spring.AbstractLibrarySpringTest;
import com.hittech.paralleltest.spring.LibraryTestConfig;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** Test classes executed programmatically by GuardEndToEndTest (names do not match the Surefire includes). */
public final class E2eFixtures {

    private E2eFixtures() {
    }

    /** The app swallows the error on a worker thread: the guard must still fail THIS test with PT12. */
    public static class SwallowedOnOtherThreadCase extends AbstractLibrarySpringTest {
        @Test
        void wipesOnAnotherThreadAndSwallows() throws Exception {
            CompletableFuture.runAsync(PerTestMocks.wrap(() -> {
                try {
                    jdbc.update("delete from item");
                } catch (Throwable swallowed) {
                    // e.g. a catch-all in application code, or an HTTP 500 the test does not check
                }
            })).get();
        }
    }

    /** @Nested tests use the mocks declared in the enclosing class. */
    public static class NestedCase extends AbstractLibrarySpringTest {
        @Nested
        public class Inner {
            @Test
            void usesOuterMock() {
                when(collaborator.hello()).thenReturn("outer mock");
                assertThat(greetingService.greet()).isEqualTo("greeting:outer mock");
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    @Import(LibraryTestConfig.class)
    public static class BypassConfig {
        /** Injects the implementation by its CONCRETE class: gets the real bean, not the router. */
        public record LegacyService(LibraryTestConfig.RealCollaborator collaborator) {
        }

        @Bean
        LegacyService legacyService(LibraryTestConfig.RealCollaborator real) {
            return new LegacyService(real);
        }
    }

    @IsolatedContext(reason = "end-to-end check of rule PT14")
    @SpringJUnitConfig(BypassConfig.class)
    @RoutedMocks(Collaborator.class)
    public static class RoutedBeanBypassCase {
        @Test
        void anything() {
        }
    }
}
