package com.hittech.paralleltest.fixtures;

import com.hittech.paralleltest.annotation.IsolatedContext;
import com.hittech.paralleltest.annotation.SharedContextBase;
import com.hittech.paralleltest.mock.PerTestMock;
import com.hittech.paralleltest.mock.RoutedMocks;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.data.repository.CrudRepository;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;

/**
 * Test classes analysed (never executed: names do not match the Surefire includes) by the library's
 * self-tests. Each *Case is either compliant or breaks exactly the rules listed in ClassRulesTest.
 */
public final class Fixtures {

    private Fixtures() {
    }

    public interface Collaborator {
        String hello();
    }

    public interface ThingRepository extends CrudRepository<Object, Long> {
    }

    @SharedContextBase
    @SpringBootTest
    @RoutedMocks(Collaborator.class)
    public abstract static class Base {
        protected static final String CONSTANT = "ok";
    }

    public static class GoodCase extends Base {
        @PerTestMock Collaborator collaborator;
        ThingRepository repository;

        @Test
        void test() {
            repository.deleteAll(java.util.List.of()); // deleting your own rows is fine
            repository.findById(1L);
        }

        @Nested
        public class Inner {
            @Test
            void nested() {
            }
        }
    }

    @SpringBootTest
    public static class NoBaseCase {
        @Test
        void test() {
        }
    }

    @IsolatedContext(reason = "needs a dedicated feature flag set")
    @SpringBootTest(properties = "feature=on")
    public static class IsolatedContextCase {
        @Test
        void test() {
        }
    }

    @TestPropertySource(properties = "a=b")
    public static class PropertyBelowBaseCase extends Base {
        @Test
        void test() {
        }
    }

    @Import(Object.class)
    public static class ImportBelowBaseCase extends Base {
        @Test
        void test() {
        }
    }

    public static class NestedConfigCase extends Base {
        @TestConfiguration
        static class Extra {
        }

        @Test
        void test() {
        }
    }

    public static class DynamicPropertyCase extends Base {
        @DynamicPropertySource
        static void props(DynamicPropertyRegistry r) {
        }

        @Test
        void test() {
        }
    }

    public static class MockitoBeanCase extends Base {
        @MockitoBean Collaborator collaborator;

        @Test
        void test() {
        }
    }

    @IsolatedContext(reason = "legacy test")
    @SpringBootTest
    @ResourceLock("collaborator")
    public static class MockitoBeanLockedCase {
        @MockitoBean Collaborator collaborator;

        @Test
        void test() {
        }
    }

    public static class DirtiesCase extends Base {
        @Test
        @DirtiesContext
        void test() {
        }
    }

    @Isolated
    public static class DirtiesIsolatedCase extends Base {
        @Test
        @DirtiesContext
        void test() {
        }
    }

    public static class UnroutedMockCase extends Base {
        @PerTestMock Runnable runnable;

        @Test
        void test() {
        }
    }

    public static class StaticStateCase extends Base {
        static int counter;

        @Test
        void test() {
            counter++;
        }
    }

    public static class ForbiddenCallCase extends Base {
        ThingRepository repository;

        @Test
        void test() {
            repository.deleteAll();
            Runnable r = () -> repository.count();
            r.run();
        }
    }

    @Isolated
    public static class ForbiddenCallIsolatedCase extends Base {
        ThingRepository repository;

        @Test
        void test() {
            repository.deleteAll();
        }
    }

    public static class CustomForbiddenCallCase extends Base {
        @PerTestMock Collaborator collaborator;

        @Test
        void test() {
            collaborator.hello();
        }
    }

    @IsolatedContext(reason = "fixed port demo")
    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
    public static class FixedPortCase {
        @Test
        void test() {
        }
    }

    @TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
    public static class ListenersReplacedCase extends Base {
        @Test
        void test() {
        }
    }

    public static class MethodReferenceCase extends Base {
        ThingRepository repository;

        @Test
        void test() {
            Runnable wipe = repository::deleteAll; // invokedynamic, not invokeinterface
            wipe.run();
        }
    }

    public static class LocalClassCase extends Base {
        ThingRepository repository;

        @Test
        void test() {
            class Cleaner {
                void clean() {
                    repository.count();
                }
            }
            new Cleaner().clean();
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    public static class PerClassCase extends Base {
        @PerTestMock Collaborator collaborator;

        @Test
        void test() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    public static class PerClassSameThreadCase extends Base {
        @Test
        void test() {
        }
    }

    /** Test helper (no tests): checked for forbidden calls by the scanner. */
    public static class CleanupHelper {
        public static void wipe(ThingRepository repository) {
            repository.deleteAll();
        }
    }
}
