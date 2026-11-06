package com.hittech.paralleltest.guard;

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
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.util.ClassUtils;

/**
 * Wraps every DataSource bean of a test context so that EVERY SQL string reaching JDBC -- from JPA,
 * JdbcTemplate, @Sql scripts or plain JDBC -- goes through {@link SqlGuard}. Statements executed while
 * the context starts (schema/seed scripts, Flyway/Liquibase) are not checked.
 *
 * The DataSource becomes an interface-based Spring AOP proxy, which Spring Boot's DataSourceUnwrapper
 * (metrics, health) sees through. Injection points of a concrete pool type (e.g. HikariDataSource) would
 * fail at startup -- inject {@code DataSource} instead.
 */
class JdbcSqlGuardPostProcessor implements BeanPostProcessor, ApplicationListener<ContextRefreshedEvent>,
        ApplicationContextAware {

    /** Marker so a DataSource is never wrapped twice. */
    interface SqlGuarded {
    }

    private static final Set<String> STATEMENT_SQL_METHODS = Set.of(
            "execute", "executeQuery", "executeUpdate", "executeLargeUpdate", "addBatch");
    private static final Set<String> CONNECTION_SQL_METHODS = Set.of("prepareStatement", "prepareCall", "nativeSQL");

    private ApplicationContext context;
    private volatile boolean active;

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.context = applicationContext;
    }

    @Override
    public void onApplicationEvent(ContextRefreshedEvent event) {
        if (event.getApplicationContext() == context) {
            active = true;
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
