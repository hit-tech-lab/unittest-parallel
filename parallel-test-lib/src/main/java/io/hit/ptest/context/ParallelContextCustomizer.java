package io.hit.ptest.context;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.UnboundPolicy;
import io.hit.ptest.db.DatabaseProvisioner;
import io.hit.ptest.db.H2Provisioner;
import io.hit.ptest.db.PostgresProvisioner;
import io.hit.ptest.guard.GuardRule;
import io.hit.ptest.guard.GuardSettings;
import io.hit.ptest.guard.JUnitConfig;
import io.hit.ptest.guard.JdbcSqlGuardPostProcessor;
import io.hit.ptest.guard.Severity;
import io.hit.ptest.intercept.InterceptPlan;
import io.hit.ptest.intercept.InterceptingBeanPostProcessor;
import io.hit.ptest.intercept.SpyHostPostProcessor;
import io.hit.ptest.propagation.BindingFilter;
import io.hit.ptest.propagation.TaskDecoratorInstaller;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.DefaultSingletonBeanRegistry;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.boot.test.context.SpringBootContextLoader;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.MergedContextConfiguration;
import org.springframework.util.ClassUtils;

/**
 * Chạy MỘT lần mỗi khi tạo context mới (cache miss): đăng ký vòng đời (slot + dọn dẹp), cài nguồn DB riêng,
 * BeanPostProcessor can thiệp, TaskDecorator và filter binding HTTP. equals/hashCode theo tên + khai báo nên
 * nằm trong khóa cache của Spring Test.
 *
 * <p>Customizer KHÔNG cấp tài nguyên: DB được cấp lúc refresh (xem {@link ProvisionedDbPropertySource}) và slot được
 * giữ lúc refresh bắt đầu, nên không có tài nguyên nào treo nếu customizer khác thất bại trước refresh.
 */
public final class ParallelContextCustomizer implements ContextCustomizer {

    static final String PROP_DB_MODE = "ptest.db.mode";
    static final String PROP_ISOLATION = "ptest.db.isolation";
    static final String PROP_H2_OPTIONS = "ptest.db.h2.options";
    static final String PROP_IMAGE = "ptest.db.postgres.image";
    static final String PROP_MAX_CONNECTIONS = "ptest.db.postgres.max-connections";
    static final int DEFAULT_MAX_CONNECTIONS = 300;
    static final String PROP_POOL = "ptest.db.pool-size";
    static final String PROP_UNBOUND = "ptest.unbound-policy";
    static final int MIN_POOL = 4;
    static final String DEFAULT_IMAGE = "postgres:16.4";
    static final String LIFECYCLE_BEAN = "ptestContextLifecycle";
    private static final AtomicInteger SEQ = new AtomicInteger();

    private final ContextDeclaration declaration;

    public ParallelContextCustomizer(ContextDeclaration declaration) {
        this.declaration = declaration;
    }

    /** Bean mock-only của khai báo (để mô tả diff khóa context). */
    List<MockOnlyBean> mockOnlyBeans() {
        return declaration.mockOnly();
    }

    /** Mô tả khai báo không kèm mock cấp class, cho diff khóa context. */
    String describeWithoutMocks() {
        return declaration.describeWithoutMocks();
    }

    @Override
    public void customizeContext(ConfigurableApplicationContext context, MergedContextConfiguration config) {
        ConfigurableEnvironment env = context.getEnvironment();
        String name = declaration.name();
        int max = PtestSettings.maxContexts();
        requireWithinSpringCache(max);
        PtestSettings.checkMockitoAgentOnce();
        UnboundPolicy policy = unboundPolicy(env);

        String key = name + "@" + Integer.toHexString(config.hashCode()) + "#" + SEQ.incrementAndGet();
        ManagedContext mc = new ManagedContext(key, name, context, policy,
                config.getContextLoader() instanceof SpringBootContextLoader);
        mc.createdBy = config.getTestClass() == null ? "?" : config.getTestClass().getName();
        mc.config = ContextConfigDescription.describe(config);
        String[] patterns = declaration.wrap().toArray(String[]::new);
        InterceptPlan plan = new InterceptPlan(mc, patterns, declaration.exclude().toArray(String[]::new));
        mc.plan = plan;

        // Vòng đời: singleton có sẵn + disposable đăng ký TRƯỚC refresh (xem ContextLifecycle)
        ContextLifecycle lifecycle = new ContextLifecycle(mc, patterns, plan);
        if (!(context.getBeanFactory() instanceof DefaultSingletonBeanRegistry singletons)) {
            throw new ParallelTestException.Config("CONFIG: unsupported bean factory " + context.getBeanFactory().getClass().getName());
        }
        singletons.registerSingleton(LIFECYCLE_BEAN, lifecycle);
        singletons.registerDisposableBean(LIFECYCLE_BEAN, lifecycle);
        context.addApplicationListener(lifecycle);
        // BFPP lập trình (không phải BeanDefinitionRegistryPostProcessor) chạy SAU mọi BeanDefinitionRegistryPostProcessor
        // (kể cả ConfigurationClassPostProcessor) và TRƯỚC các BFPP khai báo bằng bean và trước khi tạo singleton thường
        // (PostProcessorRegistrationDelegate; có test). Lifecycle đã đăng ký nên lỗi từ đây trở đi đi qua destroy của refresh.
        context.addBeanFactoryPostProcessor(bf -> ContextRegistry.INSTANCE.register(mc, max));

        Map<String, Object> info = new HashMap<>();
        info.put("ptest.context.key", key);
        info.put("ptest.context.name", name);
        env.getPropertySources().addFirst(new MapPropertySource("ptestContext", info));
        DatabaseProvisioner provisioner = provisioner(env);
        mc.dbModeRequested = env.getProperty(PROP_DB_MODE, "auto").toLowerCase(Locale.ROOT);
        mc.dbMode = provisioner == null ? "none" : provisioner.mode();
        if (provisioner != null) {
            env.getPropertySources().addLast(new ProvisionedDbPropertySource(mc, provisioner, declaration.schemas(),
                    env.getProperty(PROP_POOL, Integer.toString(defaultPoolSize()))));
        }

        if (GuardSettings.severity(GuardRule.PT12) != Severity.OFF) {
            // Guard JDBC: bộ lọc SQL cả bảng. Đăng ký lập trình (không qua bean) để kích hoạt đúng sau refresh và tắt trước khi đóng.
            JdbcSqlGuardPostProcessor sqlGuard = new JdbcSqlGuardPostProcessor(context);
            context.addApplicationListener(sqlGuard);
            context.addBeanFactoryPostProcessor(bf -> bf.addBeanPostProcessor(sqlGuard));
        }

        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) context;
        register(registry, "ptestTaskDecoratorInstaller", TaskDecoratorInstaller.class, new TaskDecoratorInstaller());
        register(registry, "ptestSpyHostPostProcessor", SpyHostPostProcessor.class, new SpyHostPostProcessor(plan));
        register(registry, "ptestInterceptingPostProcessor", InterceptingBeanPostProcessor.class,
                new InterceptingBeanPostProcessor(plan));
        // Bean mock-only được đăng ký sau khi cấu hình người dùng đã nạp (BFPP lập trình chạy sau mọi BDRPP) để biết bean nào đã có
        context.addBeanFactoryPostProcessor(bf -> MockOnlyBeans.register(bf, mc, plan, declaration.mockOnly()));
        if (ClassUtils.isPresent("jakarta.servlet.Filter", null)
                && ClassUtils.isPresent("org.springframework.web.filter.OncePerRequestFilter", null)) {
            WebSupport.register(registry);
        }
    }

    /**
     * Cỡ pool mặc định: bằng số thread song song của JUnit nhưng không dưới {@value #MIN_POOL}. Pool nhỏ hơn số test chạy đồng thời làm test chờ
     * kết nối (và PT11 báo lỗi). Đọc cấu hình JUnit lúc customizer chạy: extension đã chụp nó ở {@code beforeAll}, trước khi Spring tạo context
     * (context chỉ được nạp khi tạo instance test); đã đặt {@code ptest.db.pool-size} thì giá trị đó thắng.
     */
    static int defaultPoolSize() {
        return Math.max(MIN_POOL, JUnitConfig.parallelism());
    }

    private static UnboundPolicy unboundPolicy(ConfigurableEnvironment env) {
        String value = env.getProperty(PROP_UNBOUND, "guarded").trim();
        try {
            return UnboundPolicy.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParallelTestException.Config("CONFIG: unknown " + PROP_UNBOUND + "=" + value + " (fail|guarded|real)");
        }
    }

    /**
     * ptest.db.mode: auto (mặc định: H2 nếu có trên classpath, ngược lại không cấp), h2, postgres, none. Mỗi chế độ chỉ
     * cấp DB khi context hỏi spring.datasource.url mà người dùng chưa tự đặt.
     */
    private DatabaseProvisioner provisioner(ConfigurableEnvironment env) {
        String mode = env.getProperty(PROP_DB_MODE, "auto").trim().toLowerCase(Locale.ROOT);
        return switch (mode) {
            case "none" -> null;
            case "auto" -> ClassUtils.isPresent("org.h2.Driver", null) ? new H2Provisioner(env.getProperty(PROP_H2_OPTIONS, "")) : null;
            case "h2" -> new H2Provisioner(env.getProperty(PROP_H2_OPTIONS, ""));
            case "postgres" -> {
                if (!ClassUtils.isPresent("org.testcontainers.postgresql.PostgreSQLContainer", null)) {
                    throw new ParallelTestException.Config("CONFIG: ptest.db.mode=postgres needs Testcontainers >= 2.0 (artifact org.testcontainers:testcontainers-postgresql, class org.testcontainers.postgresql.PostgreSQLContainer; "
                            + "Testcontainers 1.x is not supported) and the postgresql driver on the test classpath");
                }
                int maxConnections = Integer.parseInt(env.getProperty(PROP_MAX_CONNECTIONS, Integer.toString(DEFAULT_MAX_CONNECTIONS)).trim());
                warnIfConnectionsMayExceed(env, maxConnections);
                yield new PostgresProvisioner(env.getProperty(PROP_IMAGE, DEFAULT_IMAGE),
                        "schema".equalsIgnoreCase(env.getProperty(PROP_ISOLATION, "database")), maxConnections);
            }
            default -> throw new ParallelTestException.Config("CONFIG: unknown " + PROP_DB_MODE + "=" + mode + " (auto|h2|postgres|none)");
        };
    }

    /**
     * Cảnh báo nếu số context tối đa nhân cỡ pool tối đa vượt {@code max_connections} (trường hợp xấu nhất: mọi pool đầy; mặc định
     * thư viện đặt {@code minimum-idle=1} nên thực tế thấp hơn nhiều).
     */
    private static void warnIfConnectionsMayExceed(ConfigurableEnvironment env, int maxConnections) {
        int pool = Integer.parseInt(env.getProperty(PROP_POOL, Integer.toString(defaultPoolSize())).trim());
        long worst = (long) PtestSettings.maxContexts() * pool;
        if (worst > maxConnections) {
            LoggerFactory.getLogger(ParallelContextCustomizer.class).warn("ptest.max-contexts ({}) x pool size ({}) = {} may exceed "
                    + "Postgres max_connections={}; raise {} or lower ptest.db.pool-size / ptest.max-contexts",
                    PtestSettings.maxContexts(), pool, worst, maxConnections, PROP_MAX_CONNECTIONS);
        }
    }

    /** Ném lỗi (không chỉ cảnh báo) nếu ngưỡng lớn hơn cache Spring: Spring sẽ đóng context còn đang dùng. */
    static void requireWithinSpringCache(int max) {
        int springMax = PtestSettings.springCacheMaxSize();
        if (max > springMax) {
            throw new ParallelTestException.Config("CONFIG: ptest.max-contexts=" + max
                    + " exceeds spring.test.context.cache.maxSize=" + springMax
                    + ": Spring would evict (close) contexts that are still in use");
        }
    }

    private static <T> void register(BeanDefinitionRegistry registry, String beanName, Class<T> type, T instance) {
        GenericBeanDefinition def = new GenericBeanDefinition();
        def.setBeanClass(type);
        def.setInstanceSupplier(() -> instance);
        def.setRole(BeanDefinition.ROLE_INFRASTRUCTURE);
        registry.registerBeanDefinition(beanName, def);
    }

    /** Tách riêng để lớp servlet chỉ được nạp khi có trên classpath. */
    private static final class WebSupport {
        static void register(BeanDefinitionRegistry registry) {
            ParallelContextCustomizer.register(registry, "ptestBindingFilter", BindingFilter.class, new BindingFilter());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParallelContextCustomizer c && declaration.equals(c.declaration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(declaration);
    }

    @Override
    public String toString() {
        return declaration.toString();
    }
}
