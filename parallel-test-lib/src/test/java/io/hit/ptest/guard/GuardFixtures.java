package io.hit.ptest.guard;

import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.SuppressGuard;

import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

/**
 * Lớp mẫu cho test của guard: mỗi lớp "Bad*" vi phạm ĐÚNG MỘT luật (chứng minh bắt được vi phạm thật), mỗi lớp "Good*"
 * là cùng mã nhưng đúng (chứng minh không báo nhầm). Không phải test: tên không khớp mẫu Surefire.
 */
final class GuardFixtures {

    private GuardFixtures() {
    }

    interface Repo {
        void deleteAll();

        void deleteById(long id);

        long count();
    }

    interface SubRepo extends Repo {
    }

    /** Lớp không liên quan nhưng cũng có deleteAll(): không được khớp. */
    static class Unrelated {
        void deleteAll() {
        }
    }

    // ---- PT07 ----

    static class BadDirectCall {
        SubRepo repo;

        @Test
        void t() {
            repo.deleteAll();
        }
    }

    static class BadMethodReference {
        SubRepo repo;

        @Test
        void t() {
            Runnable r = repo::deleteAll;
            r.run();
        }
    }

    static class BadInLambdaAndNested {
        SubRepo repo;

        @Test
        void t() {
            Consumer<SubRepo> c = r -> r.count();
            c.accept(repo);
        }

        class Inner {
            void x() {
                repo.deleteAll();
            }
        }
    }

    static class BadAnonymous {
        SubRepo repo;

        @Test
        void t() {
            new Object() {
                void x() {
                    repo.deleteAll();
                }
            }.x();
        }
    }

    static class GoodTargetedCalls {
        SubRepo repo;
        Unrelated unrelated = new Unrelated();

        @Test
        void t() {
            repo.deleteById(1L);
            unrelated.deleteAll();
        }
    }

    @Isolated
    static class GoodIsolatedDeleteAll {
        SubRepo repo;

        @Test
        void t() {
            repo.deleteAll();
        }
    }

    @SuppressGuard(rules = "PT07", reason = "fixture: documented exception")
    static class GoodSuppressedDeleteAll {
        SubRepo repo;

        @Test
        void t() {
            repo.deleteAll();
        }
    }

    @SuppressGuard(rules = "PT03", reason = "fixture: wrong rule suppressed")
    static class BadSuppressedWrongRule {
        SubRepo repo;

        @Test
        void t() {
            repo.deleteAll();
        }
    }

    // ---- PT06 ----

    static class BadStaticField {
        static int counter;
        static volatile List<String> seen;

        @Test
        void t() {
        }
    }

    static class StaticInjectedByJUnit {
        @org.junit.jupiter.api.io.TempDir
        static java.nio.file.Path dir;
        @org.junit.jupiter.api.extension.RegisterExtension
        static org.junit.jupiter.api.extension.Extension ext = new org.junit.jupiter.api.extension.Extension() {
        };

        @Test
        void t() {
        }
    }

    static class GoodStaticFinal {
        static final List<String> NAMES = List.of("a");
        static final int MAX = 3;
        int perInstance;

        @Test
        void t() {
        }
    }

    // ---- PT03 ----

    static class BadMockitoBean {
        @MockitoBean Repo repo;
        @MockitoSpyBean Unrelated spy;

        @Test
        void t() {
        }
    }

    @Isolated
    static class GoodIsolatedMockitoBean {
        @MockitoBean Repo repo;

        @Test
        void t() {
        }
    }

    // ---- PT08 ----

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT)
    @ParallelSpringTest(context = "fixture")
    static class BadDefinedPort {
        @Test
        void t() {
        }
    }

    @SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
    @ParallelSpringTest(context = "fixture")
    static class GoodRandomPort {
        @Test
        void t() {
        }
    }

    // ---- PT13 ----

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class BadPerClassMutable {
        String last;
        @Autowired Object injected;

        @Test
        void t() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    static class GoodPerClassOnlyInjected {
        @Autowired Object injected;
        final String fixed = "x";

        @Test
        void t() {
        }
    }

    @TestInstance(TestInstance.Lifecycle.PER_CLASS)
    @Execution(ExecutionMode.SAME_THREAD)
    static class GoodPerClassSameThread {
        String last;

        @Test
        void t() {
        }
    }

    static class GoodPerMethodMutable {
        String last;

        @Test
        void t() {
        }
    }

    // ---- PT01 / scanner ----

    @SpringBootTest
    static class BadUnmanagedSpringTest {
        @Test
        void t() {
        }
    }

    @SpringBootTest
    @ParallelSpringTest(context = "fixture")
    static class GoodManagedSpringTest {
        @Test
        void t() {
        }
    }

    /** Lớp hàm tiện ích (không có @Test) gọi deleteAll: scanner phải bắt. */
    static class BadHelper {
        void wipe(SubRepo repo) {
            repo.deleteAll();
        }
    }
}
