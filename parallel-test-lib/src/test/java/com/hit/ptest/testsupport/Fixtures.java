package com.hit.ptest.testsupport;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.context.ContextRegistry;
import com.hit.ptest.context.ManagedContext;
import com.hit.ptest.context.ContextDeclaration;
import com.hit.ptest.context.ParallelContextCustomizer;
import com.hit.ptest.intercept.TestBinding;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.util.TestPropertyValues;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.context.support.SimpleThreadScope;
import org.springframework.test.context.MergedContextConfiguration;
import static org.mockito.Mockito.mock;

/** Dựng context Spring thật + customizer thật, không cần SpringExtension, để test hành vi trên API gọi từ thread tùy ý. */
public final class Fixtures {

    private static final List<AnnotationConfigApplicationContext> OPEN = new ArrayList<>();

    private Fixtures() {
    }

    public interface Svc {
        String value();
    }

    public static class ClassSvc {
        public String value() {
            return "real";
        }
    }

    public static class LazyBean {
        final String seen;

        LazyBean(String seen) {
            this.seen = seen;
        }
    }

    public static final class SvcFactory implements FactoryBean<ClassSvc> {
        private final boolean singleton;

        SvcFactory(boolean singleton) {
            this.singleton = singleton;
        }

        @Override
        public ClassSvc getObject() {
            return new ClassSvc();
        }

        @Override
        public Class<?> getObjectType() {
            return ClassSvc.class;
        }

        @Override
        public boolean isSingleton() {
            return singleton;
        }
    }

    /** Ghi lại giá trị @PostConstruct của bean lazy thấy được từ bean can thiệp "other". */
    public static final AtomicReference<Object> LAZY_SEEN = new AtomicReference<>();

    @Configuration(proxyBeanMethods = false)
    public static class Beans {
        @Bean ClassSvc classSvc() {
            return new ClassSvc();
        }

        @Bean ClassSvc other() {
            return new ClassSvc();
        }

        @Bean Svc jdkSvc() {
            ProxyFactory jdk = new ProxyFactory();
            jdk.addInterface(Svc.class);
            jdk.setTarget((Svc) () -> "real");
            return (Svc) jdk.getProxy();
        }

        @Bean(name = {"aliased", "aliasedAlt"}) ClassSvc aliased() {
            return new ClassSvc();
        }

        @Bean("fb") SvcFactory fb() {
            return new SvcFactory(true);
        }

        @Bean("protoFb") SvcFactory protoFb() {
            return new SvcFactory(false);
        }

        @Bean @Scope("prototype") ClassSvc proto() {
            return new ClassSvc();
        }

        @Bean @Scope("thread") ClassSvc threaded() {
            return new ClassSvc();
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class LazyConfig {
        @Bean ClassSvc other() {
            return new ClassSvc();
        }

        @Bean @Lazy LazyHolder lazyHolder(@Autowired ClassSvc other) {
            return new LazyHolder(other);
        }
    }

    public static class LazyHolder {
        private final ClassSvc other;
        volatile String seen;

        LazyHolder(ClassSvc other) {
            this.other = other;
        }

        @PostConstruct
        void init() {
            seen = other.value();
            LAZY_SEEN.set(seen);
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FailingConfig {
        public static final AtomicReference<String> URL = new AtomicReference<>();

        @Bean ClassSvc classSvc(org.springframework.core.env.Environment env) {
            URL.set(env.getProperty("spring.datasource.url")); // hỏi DataSource => thư viện cấp DB
            throw new IllegalStateException("boom during startup");
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class FailingBfppConfig {
        @Bean static org.springframework.beans.factory.config.BeanFactoryPostProcessor failing() {
            return bf -> {
                throw new IllegalStateException("boom in BFPP");
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class SwallowingConfig {
        public static final java.util.concurrent.atomic.AtomicInteger ATTEMPTS = new java.util.concurrent.atomic.AtomicInteger();

        @Bean String swallowed(org.springframework.core.env.Environment env) {
            ATTEMPTS.set(0);
            for (int i = 0; i < 2; i++) {
                ATTEMPTS.incrementAndGet();
                try {
                    env.getProperty("spring.datasource.url"); // như Boot: nuốt ngoại lệ
                } catch (RuntimeException ignored) {
                    // nuốt
                }
            }
            return "started anyway";
        }
    }

    @Configuration(proxyBeanMethods = false)
    public static class AskForUrlConfig {
        @Bean String url(org.springframework.core.env.Environment env) {
            return String.valueOf(env.getProperty("spring.datasource.url"));
        }
    }

    /** Hỏi password trước, rồi url, username, pool: như Boot có thể đọc các khóa theo thứ tự bất kỳ. Trả về "user|password|pool". */
    @Configuration(proxyBeanMethods = false)
    public static class AskForCredentialsConfig {
        @Bean String credentials(org.springframework.core.env.Environment env) {
            String password = env.getProperty("spring.datasource.password");
            String url = env.getProperty("spring.datasource.url");
            String user = env.getProperty("spring.datasource.username");
            String pool = env.getProperty("spring.datasource.hikari.maximum-pool-size");
            return user + "|" + password + "|" + pool + "|" + url;
        }
    }

    /** {@code wrap}: mẫu bean cần bọc (rỗng = không bọc bean nào). */
    public static AnnotationConfigApplicationContext context(String name, String[] wrap, String[] schemas,
                                                      Map<String, String> props, Class<?>... configs) {
        return context(ContextDeclaration.of(name, wrap, new String[0], schemas, List.of()), props, configs);
    }

    public static AnnotationConfigApplicationContext context(ContextDeclaration declaration, Map<String, String> props, Class<?>... configs) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        ctx.getBeanFactory().registerScope("thread", new SimpleThreadScope());
        ctx.register(configs);
        props.forEach((k, v) -> TestPropertyValues.of(k + "=" + v).applyTo(ctx));
        new ParallelContextCustomizer(declaration).customizeContext(ctx, mock(MergedContextConfiguration.class));
        OPEN.add(ctx);
        return ctx;
    }

    public static AnnotationConfigApplicationContext started(String name, String[] wrap, Map<String, String> props, Class<?>... configs) {
        AnnotationConfigApplicationContext ctx = context(name, wrap, new String[0], props, configs);
        ctx.refresh();
        return ctx;
    }

    public static void closeAll() {
        TestBinding.bind(null);
        OPEN.forEach(c -> {
            try {
                c.close();
            } catch (RuntimeException ignored) {
                // đã đóng
            }
        });
        OPEN.clear();
    }

    public static ManagedContext managed(AnnotationConfigApplicationContext ctx) {
        return ContextRegistry.INSTANCE.of(ctx);
    }

    /** Mở binding với target MOCK cho bean và gắn vào thread hiện tại. */
    public static TestBinding bindMock(AnnotationConfigApplicationContext ctx, String bean) {
        ManagedContext mc = managed(ctx);
        Object target = mc.requireInterceptor(bean).newTarget(InterceptMode.MOCK);
        TestBinding b = TestBinding.open("test", Map.of(new TestBinding.TargetKey(mc.key, mc.canonical(bean)), target));
        TestBinding.bind(b);
        return b;
    }

    /** Chạy trên thread mới KHÔNG có binding; trả về kết quả hoặc Throwable. */
    public static Object onFreshThread(Callable<?> call) throws InterruptedException {
        AtomicReference<Object> out = new AtomicReference<>();
        Thread t = new Thread(() -> {
            try {
                out.set(call.call());
            } catch (Throwable e) {
                out.set(e);
            }
        }, "unbound-test-thread");
        t.start();
        t.join();
        return out.get();
    }
}
