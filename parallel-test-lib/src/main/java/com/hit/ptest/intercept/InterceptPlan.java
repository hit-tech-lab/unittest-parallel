package com.hit.ptest.intercept;

import com.hit.ptest.ParallelTestException;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.util.PatternMatchUtils;

/**
 * Quyết định bean nào được bọc wrapper và nơi chia sẻ trạng thái giữa hai BeanPostProcessor của một context:
 * {@link SpyHostPostProcessor} (trước AOP, bọc bản thật bằng "host" của spy) và {@link InterceptingBeanPostProcessor}
 * (sau AOP, bọc bean đầy đủ thành wrapper dùng chung).
 *
 * <p>Tập bean được bọc = khớp một mẫu {@code wrap} (mặc định {@code "*"}) và không khớp mẫu {@code exclude}. Mẫu wildcard chỉ
 * chọn bean do ứng dụng định nghĩa (xem {@link BeanCandidates}); tên tường minh không qua bộ lọc đó. Hỗ trợ: singleton
 * thường, sản phẩm singleton của FactoryBean, alias (quy về tên chính tắc). Không hỗ trợ (BEAN_NOT_INTERCEPTABLE nếu tên khai
 * báo tường minh, bỏ qua nếu chỉ khớp wildcard): prototype/scope khác singleton, BeanPostProcessor/BeanFactoryPostProcessor.
 * Bean mock-only (thư viện tự đăng ký) không qua hai BPP này: wrapper của nó tạo thẳng lúc khởi tạo bean.
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
public final class InterceptPlan {

    private static final Logger LOG = LoggerFactory.getLogger(InterceptPlan.class);

    private final InterceptOwner owner;
    private final String[] declaredWrap;
    private final String[] declaredExclude;
    /** Bean do khai báo {@code @PerTestMock} cấp class thay hoặc tạo (không có bản thật, không bọc). */
    public record MockOnlyInfo(boolean replaced, Class<?> type, Set<String> knownBefore) {
    }

    private final ConcurrentMap<String, MockOnlyInfo> mockOnly = new ConcurrentHashMap<>();
    private volatile String[] wrap;
    private volatile String[] exclude;
    private final ConcurrentMap<String, SpyHost> hosts = new ConcurrentHashMap<>();
    /** Tên bean khớp mẫu wrap nhưng bị bỏ qua, kèm lý do (để thông điệp BEAN_NOT_WRAPPED chỉ đúng nguyên nhân). */
    private final ConcurrentMap<String, String> skipped = new ConcurrentHashMap<>();
    private final Set<String> wrapped = ConcurrentHashMap.newKeySet();
    private final AtomicLong wrapNanos = new AtomicLong();

    public InterceptPlan(InterceptOwner owner, String[] wrapPatterns, String[] excludePatterns) {
        this.owner = owner;
        this.declaredWrap = wrapPatterns.clone();
        this.declaredExclude = excludePatterns.clone();
    }

    InterceptOwner owner() {
        return owner;
    }

    public static boolean isWildcard(String pattern) {
        return pattern.indexOf('*') >= 0;
    }

    /**
     * Đánh dấu tên bean mock-only (không bọc lần nữa). Gọi trước khi context tạo bean. {@code knownBefore}: tên các bean cùng kiểu đã có
     * lúc đăng ký (để phát hiện bean cùng kiểu xuất hiện sau, vd sản phẩm FactoryBean).
     */
    public void markMockOnly(String beanName, boolean replaced, Class<?> type, Set<String> knownBefore) {
        mockOnly.put(beanName, new MockOnlyInfo(replaced, type, Set.copyOf(knownBefore)));
    }

    /** Thông tin bean mock-only, hoặc null nếu bean không do khai báo cấp class tạo ra. */
    public MockOnlyInfo mockOnlyInfo(String beanName) {
        return mockOnly.get(beanName);
    }

    public java.util.Map<String, MockOnlyInfo> mockOnlyBeans() {
        return java.util.Map.copyOf(mockOnly);
    }

    /** Mẫu sau khi quy alias về tên chính tắc; tính lazy vì định nghĩa bean (alias) chỉ đầy đủ sau khi nạp cấu hình. */
    private String[] wrapPatterns() {
        String[] p = wrap;
        if (p == null) {
            p = canonical(declaredWrap);
            wrap = p;
        }
        return p;
    }

    private String[] excludePatterns() {
        String[] p = exclude;
        if (p == null) {
            p = canonical(declaredExclude);
            exclude = p;
        }
        return p;
    }

    private String[] canonical(String[] declared) {
        return Arrays.stream(declared).map(d -> isWildcard(d) ? d : owner.canonical(d)).toArray(String[]::new);
    }

    private boolean isExplicit(String beanName) {
        return Arrays.asList(wrapPatterns()).contains(beanName);
    }

    /**
     * True nếu bean phải được bọc. Ném BEAN_NOT_INTERCEPTABLE nếu bean được khai báo tường minh trong {@code wrap} nhưng
     * không thể bọc; bean chỉ khớp wildcard thì bị bỏ qua (lý do ghi lại trong {@link #whyNotWrapped}).
     */
    boolean select(Object bean, String beanName, ConfigurableListableBeanFactory beanFactory) {
        if (mockOnly.containsKey(beanName)) {
            return false;
        }
        if (hosts.containsKey(beanName)) {
            return true; // đã có host: bản đã qua proxy AOP phải được bọc dù bộ lọc có đổi kết quả trên kiểu proxy
        }
        if (!PatternMatchUtils.simpleMatch(wrapPatterns(), beanName)) {
            skipped.putIfAbsent(beanName, "does not match any ptest wrap pattern " + Arrays.toString(declaredWrap));
            return false;
        }
        if (PatternMatchUtils.simpleMatch(excludePatterns(), beanName)) {
            skipped.put(beanName, "excluded by @ParallelSpringTest(exclude=" + Arrays.toString(declaredExclude) + ")");
            return false;
        }
        if (bean instanceof FactoryBean) {
            return false; // chính FactoryBean không bọc; sản phẩm của nó đi qua BPP riêng (cùng tên bean) và bị bọc nếu singleton
        }
        boolean explicit = isExplicit(beanName);
        if (!explicit) {
            String why = BeanCandidates.rejection(beanFactory, bean, beanName);
            if (why != null) {
                skipped.putIfAbsent(beanName, why);
                return false;
            }
        }
        if (bean instanceof BeanPostProcessor || bean instanceof BeanFactoryPostProcessor) {
            if (explicit) {
                throw new ParallelTestException.Config("BEAN_NOT_INTERCEPTABLE: '" + beanName + "' is infrastructure (" + bean.getClass().getName() + ")");
            }
            skipped.put(beanName, "BeanPostProcessor/BeanFactoryPostProcessor");
            return false;
        }
        if (!beanFactory.isSingleton(beanName)) {
            String scope = scopeOf(beanFactory, beanName);
            if (explicit) {
                throw new ParallelTestException.Config("BEAN_NOT_INTERCEPTABLE: '" + beanName + "' is not a singleton ("
                        + scope + "): a wrapper shared by every test cannot stand for per-scope instances");
            }
            skipped.put(beanName, "not a singleton (" + scope + ")");
            return false;
        }
        return true;
    }

    /** Lỗi khi tạo wrapper/host: bean khai báo tường minh thì ném Config, bean chỉ khớp wildcard thì bỏ qua. */
    void wrapFailed(String beanName, Object bean, RuntimeException e) {
        if (isExplicit(beanName)) {
            throw new ParallelTestException.Config("BEAN_NOT_INTERCEPTABLE: cannot wrap bean '" + beanName + "' ("
                    + bean.getClass().getName() + "): " + e);
        }
        skipped.put(beanName, "cannot be wrapped: " + e);
        LOG.info("Skip wildcard wrapping of '{}': {}", beanName, e.toString());
    }

    void registerHost(String beanName, SpyHost host) {
        hosts.put(beanName, host);
    }

    /** Host của bean (null nếu bean không có host: JDK proxy hoặc không thuộc phạm vi). */
    SpyHost host(String beanName) {
        return hosts.get(beanName);
    }

    void noteWrapped(String beanName, long nanos) {
        wrapped.add(beanName);
        skipped.remove(beanName);
        wrapNanos.addAndGet(nanos);
    }

    /** Lý do bean không được bọc, hoặc null nếu không biết (bean chưa từng đi qua BPP, vd lazy chưa khởi tạo). */
    public String whyNotWrapped(String beanName) {
        return skipped.get(beanName);
    }

    /** Tóm tắt để log: số bean đã bọc, số bị bỏ qua và tổng thời gian tạo wrapper/host (đo chi phí của việc bọc rộng). */
    public String summary() {
        return "wrapped " + wrapped.size() + " beans, skipped " + skipped.size() + ", wrapper/host creation "
                + wrapNanos.get() / 1_000_000 + " ms";
    }

    public int wrappedCount() {
        return wrapped.size();
    }

    public long wrapMillis() {
        return wrapNanos.get() / 1_000_000;
    }

    private static String scopeOf(ConfigurableListableBeanFactory beanFactory, String beanName) {
        if (beanFactory.containsBeanDefinition(beanName) && !beanFactory.isFactoryBean(beanName)) {
            String scope = beanFactory.getBeanDefinition(beanName).getScope();
            return "scope '" + (scope.isEmpty() ? "singleton" : scope) + "'";
        }
        return "FactoryBean product that is not a singleton";
    }
}
