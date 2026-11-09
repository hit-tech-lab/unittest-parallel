package com.hit.ptest.context;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.intercept.InterceptPlan;
import com.hit.ptest.intercept.TestBinding;

import java.util.Arrays;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;

/**
 * Vòng đời hạ tầng của mỗi context. KHÔNG phải bean khai báo: customizer đăng ký nó như singleton có sẵn và đăng ký
 * {@link DisposableBean} TRƯỚC KHI refresh, nên:
 * <ul>
 *   <li>{@link #destroy()} chạy trên mọi đường kết thúc, kể cả refresh lỗi trước khi bean nào được tạo
 *   (Spring gọi destroySingletons khi refresh thất bại);</li>
 *   <li>được đăng ký đầu tiên nên bị hủy CUỐI CÙNG, sau mọi bean có hàm hủy (DataSource, EntityManagerFactory),
 *   không phụ thuộc DataSource khai báo thế nào (kể cả FactoryBean).</li>
 * </ul>
 * Nó cũng kiểm tra tên bean khai báo tồn tại, đánh dấu "đã khởi động xong" (từ đó luật thread không binding
 * có hiệu lực) và nhả slot + DB khi đóng.
 */
public final class ContextLifecycle implements DisposableBean, ApplicationListener<ApplicationEvent> {

    private static final Logger LOG = LoggerFactory.getLogger(ContextLifecycle.class);

    private final ManagedContext owner;
    private final String[] patterns;
    private final InterceptPlan plan;

    ContextLifecycle(ManagedContext owner, String[] patterns, InterceptPlan plan) {
        this.owner = owner;
        this.patterns = patterns;
        this.plan = plan;
    }

    @Override
    public void onApplicationEvent(ApplicationEvent event) {
        if (event instanceof ContextRefreshedEvent refreshed && refreshed.getApplicationContext() == owner.context) {
            if (owner.provisionFailure != null) {
                throw owner.provisionFailure;
            }
            checkDeclaredBeansExist();
            checkCreatedMocksNotDuplicated();
            LOG.info("Context {}: refreshed {} ms after customization; {}", owner.key,
                    (System.nanoTime() - owner.createdNanos) / 1_000_000, plan.summary());
            logClassLevelMocks();
            if (!owner.hasDb()) {
                LOG.debug("Context {}: no DB provisioned by ptest (datasource configured elsewhere, not needed, or mode none)", owner.key);
            }
            if (!owner.bootLoader) {
                owner.ready = true; // không có ApplicationReadyEvent khi không nạp bằng SpringBootContextLoader
            }
        } else if (event instanceof ApplicationReadyEvent ready && ready.getApplicationContext() == owner.context) {
            owner.ready = true;
        } else if (event instanceof ContextClosedEvent closed && closed.getApplicationContext() == owner.context) {
            owner.ready = false; // đóng context: các @PreDestroy gọi bean can thiệp từ thread không binding được coi như giai đoạn tắt
        }
    }

    /** Cho người dùng thấy bean nào bị THAY (định nghĩa thật không được tạo) và bean nào được TẠO bởi khai báo {@code @PerTestMock} cấp class. */
    private void logClassLevelMocks() {
        var beans = plan.mockOnlyBeans();
        if (!beans.isEmpty()) {
            LOG.info("Context {}: class-level @PerTestMock replaced {} and created {}", owner.key,
                    beans.entrySet().stream().filter(e -> e.getValue().replaced()).map(java.util.Map.Entry::getKey).sorted().toList(),
                    beans.entrySet().stream().filter(e -> !e.getValue().replaced()).map(java.util.Map.Entry::getKey).sorted().toList());
        }
    }

    private void checkDeclaredBeansExist() {
        Arrays.stream(patterns).filter(p -> !InterceptPlan.isWildcard(p))
                .filter(p -> !owner.context.containsBean(p))
                .findFirst().ifPresent(p -> {
                    throw new ParallelTestException.Config("BEAN_NOT_INTERCEPTABLE: wrap bean '" + p
                            + "' does not exist in context '" + owner.key + "'");
                });
    }

    /**
     * Bean mock do thư viện TẠO vì khi đăng ký context chưa có bean cùng kiểu. Nếu sau refresh vẫn có bean KHÁC cùng kiểu thì đó là sản phẩm
     * của FactoryBean mà Spring không đoán được kiểu khi chưa khởi tạo (nên lúc đăng ký bị coi là "chưa có"): hai bean cùng kiểu sẽ làm
     * chỗ tiêm không kèm tên bị mơ hồ, nên báo lỗi rõ thay vì để Spring báo NoUniqueBeanDefinitionException khó hiểu.
     */
    private void checkCreatedMocksNotDuplicated() {
        plan.mockOnlyBeans().forEach((name, info) -> {
            if (info.replaced()) {
                return;
            }
            for (String other : owner.context.getBeanNamesForType(info.type(), true, false)) {
                if (!other.equals(name) && plan.mockOnlyInfo(other) == null && !info.knownBefore().contains(other)) {
                    throw new ParallelTestException.Config("MOCK_ONLY_CONFLICT: class-level @PerTestMock " + info.type().getSimpleName()
                            + " created bean '" + name + "' because the context had no bean of that type at registration time, but bean '"
                            + other + "' of that type exists now (probably the product of a FactoryBean whose type Spring cannot determine "
                            + "before creating it). Use @PerTestMock(name = \"" + other + "\", classes = " + info.type().getSimpleName()
                            + ".class) to replace that bean instead.");
                }
            }
        });
    }

    @Override
    public void destroy() {
        owner.ready = false;
        try {
            owner.releaseDb();
        } finally {
            TestBinding.forgetContext(owner.key);
            ContextRegistry.INSTANCE.release(owner);
        }
    }
}
