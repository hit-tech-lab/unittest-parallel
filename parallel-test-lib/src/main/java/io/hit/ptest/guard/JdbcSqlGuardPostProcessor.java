package io.hit.ptest.guard;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;
import javax.sql.DataSource;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ApplicationContextEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ClassUtils;

/**
 * Bọc mọi DataSource của context test để MỌI chuỗi SQL tới JDBC (JPA, JdbcTemplate, script {@code @Sql}, JDBC thuần) đi
 * qua {@link SqlGuard}. SQL chạy lúc context khởi động (schema, seed, migration) và lúc context đóng (Hibernate
 * create-drop) KHÔNG bị kiểm tra: chỉ kiểm tra từ {@code ContextRefreshedEvent} đến {@code ContextClosedEvent}.
 *
 * <p>DataSource trở thành proxy theo INTERFACE (Boot vẫn thấy qua {@code unwrap}). Chỉ tiêm {@code DataSource}; tiêm
 * kiểu cụ thể (vd {@code HikariDataSource}) sẽ lỗi lúc khởi động. Dùng riêng được: không cần cơ chế can thiệp nào khác.
 */
public final class JdbcSqlGuardPostProcessor implements BeanPostProcessor, ApplicationListener<ApplicationContextEvent> {

    /** Đánh dấu để không bọc hai lần. */
    interface SqlGuarded {
    }

    private static final Set<String> STATEMENT_SQL_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch");
    private static final Set<String> CONNECTION_SQL_METHODS = Set.of("prepareStatement", "prepareCall", "nativeSQL");

    private final ApplicationContext context;
    private volatile boolean active;

    public JdbcSqlGuardPostProcessor(ApplicationContext context) {
        this.context = context;
    }

    @Override
    public void onApplicationEvent(ApplicationContextEvent event) {
        if (event.getApplicationContext() != context) {
            return;
        }
        if (event instanceof ContextRefreshedEvent) {
            active = true;
        } else if (event instanceof ContextClosedEvent) {
            active = false;
        }
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (!(bean instanceof DataSource ds) || bean instanceof SqlGuarded) {
            return bean;
        }
        ProxyFactory pf = new ProxyFactory(ds);
        pf.setInterfaces(ClassUtils.getAllInterfaces(ds));
        pf.addInterface(SqlGuarded.class);
        pf.addAdvice((MethodInterceptor) invocation -> {
            Object result = invocation.proceed();
            return result instanceof Connection c && invocation.getMethod().getName().equals("getConnection")
                    ? connection(c) : result;
        });
        return pf.getProxy(ds.getClass().getClassLoader());
    }

    private void check(Object[] args) {
        if (active && args != null && args.length > 0 && args[0] instanceof String sql) {
            SqlGuard.check(sql);
        }
    }

    private Connection connection(Connection target) {
        return (Connection) proxy(Connection.class, target, (proxy, m, args) -> {
            if (CONNECTION_SQL_METHODS.contains(m.getName())) {
                check(args);
            }
            Object r = invoke(target, m, args);
            return r instanceof Statement st && m.getName().equals("createStatement") ? statement(st, (Connection) proxy) : r;
        });
    }

    private Statement statement(Statement target, Connection connectionProxy) {
        return (Statement) proxy(Statement.class, target, (proxy, m, args) -> {
            if (STATEMENT_SQL_METHODS.contains(m.getName())) {
                check(args);
            }
            return m.getName().equals("getConnection") ? connectionProxy : invoke(target, m, args);
        });
    }

    private static Object proxy(Class<?> type, Object target, InvocationHandler handler) {
        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] {type}, (proxy, m, args) -> switch (m.getName()) {
            case "equals" -> proxy == args[0];
            case "hashCode" -> System.identityHashCode(proxy);
            case "toString" -> "SqlGuarded(" + target + ")";
            case "unwrap" -> ((Class<?>) args[0]).isInstance(proxy) ? proxy : invoke(target, m, args);
            case "isWrapperFor" -> ((Class<?>) args[0]).isInstance(proxy) || (boolean) invoke(target, m, args);
            default -> handler.invoke(proxy, m, args);
        });
    }

    private static Object invoke(Object target, Method m, Object[] args) throws Throwable {
        try {
            return m.invoke(target, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
