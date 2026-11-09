package demo.support;

import java.util.Set;
import java.util.concurrent.BrokenBarrierException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Điểm hẹn giữa các test: chứng minh chúng THỰC SỰ chạy chồng lên nhau (không chỉ tuần tự).
 *
 * <p>Chờ được khai báo là chặn có quản lý ({@link ForkJoinPool#managedBlock}): test đang chờ giữ một luồng của pool JUnit,
 * nên với thứ tự lớp ngẫu nhiên hoặc parallelism nhỏ, các nhóm điểm hẹn chen nhau có thể chiếm hết luồng khi chưa nhóm nào
 * đủ thành viên và pool kẹt tới khi hết 90 giây. Chặn có quản lý cho pool cấp luồng bù để thành viên còn lại được chạy.
 *
 * <p>GIỚI HẠN CỦA JUNIT (không phải lỗi thư viện, đã tái hiện không dùng thư viện trên JUnit 5.10.5, 5.11.4 và 6.0.3): các test
 * sinh động ({@code @RepeatedTest}, {@code @ParameterizedTest}, {@code @TestFactory}) được {@code submit} lười từng cái; khi hàng đợi
 * của luồng gọi đã có từ "parallelism" tác vụ chưa bị cướp (pool đang tải), JUnit chạy lần lặp kế tiếp NGAY TRÊN luồng đó
 * ({@code ForkJoinPoolHierarchicalTestExecutorService.submit} gọi {@code execSync}). Lần lặp này chặn ở rào chắn nên lần lặp sau
 * không bao giờ được submit. Vì vậy rendezvous giữa các lần lặp / test sinh động chỉ đảm bảo khi số thành viên &lt;= parallelism
 * và pool rảnh; pool nhỏ + tải thì không có cấu hình nào cứu được ({@code max-pool-size}, {@code saturate}, managedBlock đều vô hiệu).
 */
public final class Rendezvous {

    private static final ConcurrentMap<String, CyclicBarrier> BARRIERS = new ConcurrentHashMap<>();
    private static final ConcurrentMap<String, Set<String>> SHARED = new ConcurrentHashMap<>();

    private Rendezvous() {
    }

    /**
     * Hẹn {@code parties} thành viên tại điểm hẹn {@code name}. Barrier tự reset sau khi đủ người nên tên dùng lại được (rerun,
     * {@code @RepeatedTest}); barrier đã vỡ bởi lần trước (timeout/interrupt) bị thay mới để lỗi cũ không lan sang lần sau, và
     * {@code parties} khác lần trước được áp dụng nếu không còn ai đang chờ (còn người chờ mà parties khác nhau là lỗi của test, báo rõ).
     */
    public static void meet(String name, int parties) {
        meet(name, parties, 90_000);
    }

    /** Như {@link #meet(String, int)} nhưng có timeout tùy chọn (mili giây); dùng để kiểm tra chính điểm hẹn mà không phải chờ 90 giây. */
    public static void meet(String name, int parties, long timeoutMillis) {
        CyclicBarrier barrier = BARRIERS.compute(name, (n, old) -> {
            if (old == null || old.isBroken() || (old.getParties() != parties && old.getNumberWaiting() == 0)) {
                return new CyclicBarrier(parties);
            }
            return old;
        });
        if (barrier.getParties() != parties) {
            throw new AssertionError("Barrier '" + name + "' is in use with " + barrier.getParties() + " parties but this call asks for " + parties
                    + " (members of one rendezvous must agree on the number of parties)");
        }
        AwaitBlocker blocker = new AwaitBlocker(barrier, timeoutMillis);
        try {
            ForkJoinPool.managedBlock(blocker);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AssertionError("INTERRUPTED while waiting at barrier '" + name + "' (" + parties + " parties); the barrier is broken for the other members", e);
        }
        if (blocker.failure != null) {
            String cause = blocker.failure instanceof TimeoutException
                    ? "TIMEOUT after " + timeoutMillis + " ms: not all " + parties + " members arrived"
                    : "BROKEN by another member (it timed out or was interrupted); this member did not itself time out";
            throw new AssertionError("TESTS_DID_NOT_RUN_CONCURRENTLY: barrier '" + name + "' (" + parties + " parties, JUnit parallelism="
                    + System.getProperty("junit.jupiter.execution.parallel.config.fixed.parallelism", "?") + "): " + cause
                    + ". If the members are repetitions/dynamic tests and parties > parallelism, JUnit may run the next one inline on the blocked thread.");
        }
    }

    /** Chờ barrier tối đa {@code timeoutMillis} (mặc định 90 giây); lỗi chờ (timeout / barrier vỡ) được giữ lại để báo bằng AssertionError ở {@link #meet}. */
    private static final class AwaitBlocker implements ForkJoinPool.ManagedBlocker {
        private final CyclicBarrier barrier;
        private final long timeoutMillis;
        private volatile boolean done;
        private Exception failure;

        AwaitBlocker(CyclicBarrier barrier, long timeoutMillis) {
            this.barrier = barrier;
            this.timeoutMillis = timeoutMillis;
        }

        @Override
        public boolean block() throws InterruptedException {
            try {
                barrier.await(timeoutMillis, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | BrokenBarrierException e) {
                failure = e;
            }
            done = true;
            return true;
        }

        @Override
        public boolean isReleasable() {
            return done;
        }
    }

    /** Ghi một giá trị vào tập chung của nhóm; đọc lại bằng {@link #shared}. */
    public static void share(String group, String value) {
        SHARED.computeIfAbsent(group, g -> ConcurrentHashMap.newKeySet()).add(value);
    }

    public static Set<String> shared(String group) {
        return SHARED.getOrDefault(group, Set.of());
    }
}
