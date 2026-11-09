package io.hit.ptest.guard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.context.ApplicationContext;

/**
 * Luật PT11: DataSource có pool nhỏ hơn số thread song song của JUnit. Chỉ kiểm tra được pool lộ kích thước qua getter
 * ({@code getMaximumPoolSize} Hikari, {@code getMaxTotal} DBCP2, {@code getMaxActive} Tomcat); DataSource khác bị bỏ qua.
 */
public final class PoolSizeCheck {

    private PoolSizeCheck() {
    }

    /** @return vi phạm (rỗng nếu không có hoặc parallelism &lt;= 1); chưa áp mức FAIL/WARN. */
    public static List<Violation> check(ApplicationContext ctx, Class<?> testClass, int parallelism) {
        List<Violation> out = new ArrayList<>();
        if (parallelism <= 1) {
            return out;
        }
        Map<String, DataSource> sources;
        try {
            sources = ctx.getBeansOfType(DataSource.class, false, false);
        } catch (RuntimeException e) {
            return out;
        }
        GuardStats.POOLS_CHECKED.increment();
        sources.forEach((name, ds) -> {
            Integer max = maxPoolSize(ds);
            if (max != null && max < parallelism) {
                out.add(new Violation(GuardRule.PT11, "DataSource bean '" + name + "' (context of " + testClass.getName() + ")",
                        "maximum pool size " + max + " < JUnit parallelism " + parallelism));
            }
        });
        return out;
    }

    private static Integer maxPoolSize(DataSource proxyOrTarget) {
        Object target = AopProxyUtils.getSingletonTarget(proxyOrTarget);
        Object ds = target != null ? target : proxyOrTarget; // gỡ proxy SQL guard
        for (String getter : List.of("getMaximumPoolSize", "getMaxTotal", "getMaxActive")) {
            try {
                Method m = ds.getClass().getMethod(getter);
                return ((Number) m.invoke(ds)).intValue();
            } catch (ReflectiveOperationException | ClassCastException e) {
                // thử getter tiếp theo
            }
        }
        return null;
    }
}
