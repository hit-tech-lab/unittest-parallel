package com.hit.ptest.intercept;


import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Binding của MỘT test: ánh xạ (context, tên bean) sang mock/spy riêng của test đó. Thread hiện tại trỏ tới
 * binding qua ThreadLocal; thread khác nhận binding nhờ TaskDecorator, header HTTP hoặc {@code ParallelTest.wrap}.
 *
 * <p>Binding đã đóng ({@link #isClosed()}) bị coi như KHÔNG có binding: một tác vụ "rò rỉ" chạy sau khi test kết thúc
 * không bao giờ được định tuyến vào mock cũ của test đó. Lớp này nội bộ; API công khai là {@code ParallelTest}.
 */
public final class TestBinding {

    public static final String HEADER = "X-PTest-Binding";

    /** Khóa (context, bean) dạng record, không nối chuỗi nên tên chứa ký tự phân cách vẫn không nhầm. */
    public record TargetKey(String contextKey, String beanName) {
    }

    private static final ThreadLocal<TestBinding> CURRENT = new ThreadLocal<>();
    private static final ConcurrentMap<String, TestBinding> ACTIVE = new ConcurrentHashMap<>();
    private static final Set<TargetKey> EVER_NON_REAL = ConcurrentHashMap.newKeySet();
    private static final AtomicLong SEQ = new AtomicLong();

    private final String id;
    private final String label;
    private final Map<TargetKey, Object> targets;
    private final AtomicBoolean closed = new AtomicBoolean();
    /** Wrapper mà binding này đã gọi (để kiểm tra stub nhầm cuối test); chết cùng binding nên không rò rỉ. */
    private final Set<BeanInterceptor> wrapperCalls = ConcurrentHashMap.newKeySet();

    private TestBinding(String id, String label, Map<TargetKey, Object> targets) {
         this.id = id;
        this.label = label;
        this.targets = targets;
    }

    /** Mở binding mới với các target; chưa gắn vào thread nào. */
    public static TestBinding open(String label, Map<TargetKey, Object> targets) {
        TestBinding b = new TestBinding("pt-" + SEQ.incrementAndGet(), label, Map.copyOf(targets));
        ACTIVE.put(b.id, b);
        EVER_NON_REAL.addAll(b.targets.keySet());
        return b;
    }

    public void close() {
        if (closed.compareAndSet(false, true)) {
            ACTIVE.remove(id);
        }
    }

    void noteWrapperCall(BeanInterceptor wrapper) {
        wrapperCalls.add(wrapper);
    }

    /** Cuối test: ném BEAN_WRAPPER_STUBBED nếu một wrapper mà test này đã gọi bị stub. */
    public void verifyWrappersNotStubbed() {
        wrapperCalls.forEach(BeanInterceptor::checkNotStubbed);
    }

    boolean isClosed() {
        return closed.get();
    }

    /** True nếu bean từng bị test nào trong context này khai báo MOCK/SPY (đơn điệu: không quay lại false). */
    public static boolean hasEverBeenNonReal(String contextKey, String beanName) {
         return EVER_NON_REAL.contains(new TargetKey(contextKey, beanName));
    }

    /** Quên trạng thái "từng bị mock/spy" của một context đã đóng (khóa chứa contextKey duy nhất nên không bao giờ quay lại). */
    public static void forgetContext(String contextKey) {
        EVER_NON_REAL.removeIf(k -> k.contextKey().equals(contextKey));
    }

    /** Số khóa đang theo dõi của một context (để test dọn dẹp). */
    public static long trackedKeys(String contextKey) {
        return EVER_NON_REAL.stream().filter(k -> k.contextKey().equals(contextKey)).count();
    }

    /** Binding gắn với thread hiện tại, hoặc null. */
    public static TestBinding current() {
        return CURRENT.get();
    }

    public static TestBinding find(String id) {
        return ACTIVE.get(id);
    }

    /** Gắn binding cho thread hiện tại, trả về binding trước đó để khôi phục. */
    public static TestBinding bind(TestBinding binding) {
        TestBinding previous = CURRENT.get();
        if (binding == null) {
            CURRENT.remove();
        } else {
            CURRENT.set(binding);
        }
        return previous;
    }

    /** Bọc Runnable để chạy với binding của thread nộp việc (không có binding thì trả nguyên task). */
    public static Runnable wrap(Runnable task) {
         TestBinding captured = current();
        if (captured == null) {
            return task;
        }
        return () -> {
             TestBinding previous = bind(captured);
            try {
                task.run();
            } finally {
                 bind(previous);
            }
        };
    }

    /** Bọc Callable để chạy với binding của thread nộp việc (không có binding thì trả nguyên task). */
    public static <T> java.util.concurrent.Callable<T> wrap(java.util.concurrent.Callable<T> task) {
         TestBinding captured = current();
        if (captured == null) {
            return task;
        }
        return () -> {
             TestBinding previous = bind(captured);
            try {
                return task.call();
            } finally {
                 bind(previous);
            }
        };
    }

    /** Chạy {@code action} với binding rỗng (mọi bean là REAL) rồi khôi phục; dùng khi khởi tạo bean lazy. */
    public static <T> T callNeutral(String label, java.util.function.Supplier<T> action) {
         TestBinding neutral = open(label, Map.of());
         TestBinding previous = bind(neutral);
        try {
            return action.get();
        } finally {
             bind(previous);
             neutral.close();
        }
    }

    Object target(TargetKey key) {
        return targets.get(key);
    }

    public String id() {
         return id;
    }

    String label() {
        return label;
    }

    @Override
    public String toString() {
        return "TestBinding[" + id + " " + label + "]";
    }
}
