package com.hittech.paralleltest.guard;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;

/** Fails fast when a pooled DataSource is smaller than the JUnit parallelism (rule PT11). */
final class PoolSizeCheck {

    private PoolSizeCheck() {
    }

    static List<Violation> check(ApplicationContext ctx, Class<?> testClass, int parallelism) {
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
        sources.forEach((name, ds) -> {
            Integer max = maxPoolSize(ds);
            if (max != null && max < parallelism) {
                out.add(new Violation(Rule.PT11, "DataSource bean '" + name + "' (context of " + testClass.getName() + ")",
                        "maximum pool size " + max + " < JUnit parallelism " + parallelism));
            }
        });
        return out;
    }

    private static Integer maxPoolSize(DataSource ds) {
        for (String getter : List.of("getMaximumPoolSize", "getMaxTotal", "getMaxActive")) { // Hikari, DBCP2, Tomcat
            try {
                Method m = ds.getClass().getMethod(getter);
                return ((Number) m.invoke(ds)).intValue();
            } catch (ReflectiveOperationException | ClassCastException e) {
                // try next
            }
        }
        return null;
    }
}
