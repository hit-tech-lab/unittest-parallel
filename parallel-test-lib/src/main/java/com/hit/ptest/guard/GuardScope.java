package com.hit.ptest.guard;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gán vi phạm xảy ra trên THREAD KHÁC (server, executor) cho đúng test sở hữu nó, để test đó fail với thông điệp guard
 * dù ứng dụng đã nuốt lỗi hoặc test chỉ thấy HTTP 500. Guard KHÔNG tự biết "test hiện tại là ai": cơ chế định danh test
 * (ở đây là binding của ptest, nối qua {@link #useOwnerResolver}) trả về id của test cho thread hiện tại; guard chỉ
 * dùng id đó, nên không phụ thuộc cơ chế can thiệp.
 *
 * <p>Vòng đời: extension gọi {@link #open} lúc bắt đầu test trên thread của test và {@link #close} ở cuối test.
 * Vi phạm trên chính thread của test được ném thẳng (không ghi thêm để không báo hai lần).
 */
public final class GuardScope {

    private static final Logger LOG = LoggerFactory.getLogger(GuardScope.class);

    private record Scope(String label, Thread owner, ConcurrentLinkedQueue<Violation> foreign, Set<String> suppressed) {
    }

    private static final Map<String, Scope> OPEN = new ConcurrentHashMap<>();
    private static volatile Supplier<String> ownerResolver = () -> null;

    private GuardScope() {
    }

    /** Đặt hàm trả về id test của thread hiện tại (hoặc null nếu thread không thuộc test nào). */
    public static void useOwnerResolver(Supplier<String> resolver) {
        ownerResolver = resolver;
    }

    /** Mở phạm vi cho một test, gắn với thread hiện tại (thread của test). */
    public static void open(String ownerId) {
        open(ownerId, ownerId, Set.of());
    }

    /**
     * Như {@link #open(String)}, kèm nhãn dễ đọc (vd {@code Lớp.method}) và các mã luật lúc chạy ({@code PT12}) mà lớp test
     * đã miễn trừ bằng {@code @SuppressGuard}.
     */
    public static void open(String ownerId, String label, Set<String> suppressedRules) {
        OPEN.put(ownerId, new Scope(label, Thread.currentThread(), new ConcurrentLinkedQueue<>(), Set.copyOf(suppressedRules)));
    }

    /** Nhãn test sở hữu thread hiện tại (hoặc null) để ghi vào thông điệp. */
    static String currentLabel() {
        String id = ownerResolver.get();
        Scope scope = id == null ? null : OPEN.get(id);
        return scope == null ? null : scope.label();
    }

    /** True nếu test sở hữu thread hiện tại đã miễn trừ luật này (chỉ biết được trên thread có gắn test). */
    static boolean isSuppressed(GuardRule rule) {
        String id = ownerResolver.get();
        Scope scope = id == null ? null : OPEN.get(id);
        return scope != null && scope.suppressed().contains(rule.name());
    }

    /** Đóng phạm vi và trả về các vi phạm được ghi cho test này trên thread khác. */
    public static List<Violation> close(String ownerId) {
        Scope s = OPEN.remove(ownerId);
        return s == null ? List.of() : List.copyOf(s.foreign());
    }

    /** Ghi vi phạm cho test sở hữu thread hiện tại nếu đó là thread khác thread của test. */
    static void report(Violation violation) {
        String id = ownerResolver.get();
        Scope scope = id == null ? null : OPEN.get(id);
        if (scope == null) {
            LOG.warn("Guard violation on thread '{}' that belongs to no running test:\n{}", Thread.currentThread().getName(), violation);
        } else if (scope.owner() != Thread.currentThread()) {
            GuardStats.FOREIGN_THREAD.increment();
            scope.foreign().add(violation);
        }
    }
}
