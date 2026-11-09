package com.hit.ptest.context;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.ParallelTestException;
import com.hit.ptest.UnboundPolicy;
import com.hit.ptest.intercept.InterceptOwner;
import com.hit.ptest.db.DbResource;
import com.hit.ptest.intercept.BeanInterceptor;
import com.hit.ptest.intercept.InterceptPlan;
import com.hit.ptest.intercept.TestBinding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.LoggerFactory;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;

/** Trạng thái thư viện gắn với một ApplicationContext. */
public final class ManagedContext implements InterceptOwner {

    /** {@code tên@hash-cấu-hình#số-thứ-tự}: tên chỉ là nhãn; hash + số thứ tự làm khóa duy nhất. */
    public final String key;
    public final String name;
    final ConfigurableApplicationContext context;
    public final UnboundPolicy policy;
    /** True nếu context nạp bằng SpringBootContextLoader: "sẵn sàng" = ApplicationReadyEvent, ngược lại ContextRefreshedEvent. */
    final boolean bootLoader;
    public final Map<String, BeanInterceptor> interceptors = new ConcurrentHashMap<>();
    final Set<String> warnedUnbound = ConcurrentHashMap.newKeySet();
    volatile boolean ready;
    private volatile boolean destroyed;
    private volatile DbResource db;
    /** Chế độ DB đã giải quyết (h2|postgres|none) và giá trị người dùng yêu cầu (auto nếu không đặt); để báo cáo. */
    volatile String dbMode = "none";
    volatile String dbModeRequested = "auto";
    /** Lỗi cấp DB (nếu có); Boot có thể nuốt ngoại lệ ở nơi đọc thuộc tính nên lifecycle kiểm tra lại sau refresh. */
    volatile RuntimeException provisionFailure;
    /** Thời điểm customizer chạy (bắt đầu dựng context), để log thời gian refresh. */
    final long createdNanos = System.nanoTime();
    /** Lớp test đầu tiên tạo ra context và mô tả cấu hình Spring của nó (để in diff khi vượt ngân sách context). */
    volatile String createdBy = "?";
    volatile Map<String, String> config = Map.of();
    /** Kế hoạch bọc wrapper của context (gán lúc customizer chạy); null chỉ khi context dựng trong test đơn vị không qua customizer. */
    volatile InterceptPlan plan;
    /** Yêu cầu mock/spy của field theo lớp test, đã chọn bean; chỉ tính một lần cho mỗi lớp (khai báo không đổi trong lần chạy). */
    private final ConcurrentMap<Class<?>, List<ResolvedRequest>> fieldRequests = new ConcurrentHashMap<>();

    /** Yêu cầu đã chọn bean (tên chính tắc). */
    public record ResolvedRequest(BeanRequest request, String beanName) {
    }

    ManagedContext(String key, String name, ConfigurableApplicationContext context, UnboundPolicy policy, boolean bootLoader) {
         this.key = key;
         this.name = name;
        this.context = context;
        this.policy = policy;
        this.bootLoader = bootLoader;
    }

    @Override
    public String key() {
         return key;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public UnboundPolicy policy() {
        return policy;
    }

    @Override
    public boolean isReady() {
        return ready;
    }

    @Override
    public boolean firstUnboundWarning(String warningKey) {
        return warnedUnbound.add(warningKey);
    }

    @Override
    public void register(String beanName, BeanInterceptor interceptor) {
        interceptors.put(beanName, interceptor);
    }

    synchronized void attachDb(DbResource resource) {
        this.db = resource;
    }

    boolean isDestroyed() {
        return destroyed;
    }

    /**
     * Dự phòng khi {@code ApplicationReadyEvent} không đến (vd context Boot khởi động bằng cách khác): một test đang chạy
     * nghĩa là context đã khởi động xong. Nếu không bật, luật thread không binding không bao giờ có hiệu lực và mọi lời
     * gọi từ thread lạ sẽ âm thầm chạy trên bean thật (fail-open).
     */
    public void ensureReady() {
        if (!ready && !destroyed) {
            ready = true;
            LoggerFactory.getLogger(ManagedContext.class).warn(
                    "Context '{}' never published its ready event before a test used it; unbound-thread rules enabled now", key);
        }
    }

    boolean hasDb() {
        return db != null;
    }

    /** Giải phóng DB (nếu đã cấp); an toàn khi gọi nhiều lần. */
    synchronized void releaseDb() {
        destroyed = true; // sau đây nguồn thuộc tính không cấp thêm DB
        DbResource d = db;
        db = null;
        if (d != null) {
            d.release().run();
        }
    }

    /** Tên chính tắc của bean (alias -> tên thật). */
    @Override
    public String canonical(String beanName) {
        return context.getBeanFactory() instanceof DefaultListableBeanFactory bf ? bf.canonicalName(beanName) : beanName;
    }

    /** Lấy interceptor của bean; bean lazy được khởi tạo theo yêu cầu, với binding rỗng để @PostConstruct luôn thấy bean thật. */
    public BeanInterceptor requireInterceptor(String requested) {
        String beanName = canonical(requested);
        BeanInterceptor bi = interceptors.get(beanName);
        if (bi == null && context.containsBean(beanName)) {
            TestBinding.callNeutral("init:" + beanName, () -> context.getBean(beanName));
            bi = interceptors.get(beanName);
        }
        if (bi == null) {
            String why = plan == null ? null : plan.whyNotWrapped(beanName);
            throw new ParallelTestException.Config("BEAN_NOT_WRAPPED: bean '" + requested + "' of context '" + name + "' has no ptest wrapper"
                    + (why == null ? "" : " (" + why + ")") + ", so a test cannot mock/spy it. Wrapped beans: " + interceptors.keySet()
                    + ". Wrap it explicitly with @ParallelSpringTest(wrap=\"" + requested + "\") or remove it from exclude.");
        }
        return bi;
    }

    /** Chọn bean cho yêu cầu và kiểm tra bean có wrapper và (với spy) có bản thật. */
    public ResolvedRequest resolve(BeanRequest request) {
        String bean = BeanResolver.resolve(context.getBeanFactory(), request, name);
        BeanInterceptor bi = requireInterceptor(bean);
        if (request.mode() == InterceptMode.SPY && bi.isMockOnly()) {
            InterceptPlan.MockOnlyInfo info = plan == null ? null : plan.mockOnlyInfo(bean);
            boolean replaced = info != null && info.replaced();
            throw new ParallelTestException.Config("SPY_NO_BEAN: bean '" + bean + "' only exists as a mock-only bean: a class-level @PerTestMock "
                    + (replaced ? "REPLACED its real definition" : "CREATED it because the context had none") + " for the whole context ("
                    + request.describe() + "), so there is no real bean to spy on. Remove the @PerTestSpy, or remove the class-level "
                    + "@PerTestMock of this bean (and mock it with a field @PerTestMock in the classes that need it).");
        }
        return new ResolvedRequest(request, bean);
    }

    /** Các yêu cầu ở field của lớp test, đã chọn bean; ném MODE_CONFLICT nếu một bean vừa mock vừa spy trong phân cấp. */
    public List<ResolvedRequest> resolveFields(Class<?> testClass) {
        List<ResolvedRequest> cached = fieldRequests.get(testClass);
        if (cached != null) {
            return cached;
        }
        List<ResolvedRequest> resolved = new ArrayList<>();
        Map<String, ResolvedRequest> firstByBean = new HashMap<>();
        for (BeanRequest request : BeanDeclarations.fieldRequests(testClass)) {
            ResolvedRequest r = resolve(request);
            ResolvedRequest first = firstByBean.putIfAbsent(r.beanName(), r);
            if (first != null && first.request().mode() != request.mode()) {
                throw modeConflict(r.beanName(), first.request(), request);
            }
            resolved.add(r);
        }
        fieldRequests.put(testClass, List.copyOf(resolved));
        return fieldRequests.get(testClass);
    }

    public static ParallelTestException.Config modeConflict(String bean, BeanRequest a, BeanRequest b) {
        return new ParallelTestException.Config("MODE_CONFLICT: bean '" + bean + "' is both mocked and spied in the same test hierarchy: "
                + a.describe() + " and " + b.describe() + ". Keep one of them.");
    }
}
