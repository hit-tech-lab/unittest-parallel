package com.hittech.paralleltest.guard;

import java.io.InputStream;
import java.util.Locale;
import java.util.Optional;
import java.util.Properties;
import org.junit.platform.engine.ConfigurationParameters;

/**
 * JUnit configuration as the launcher REALLY sees it. {@link com.hittech.paralleltest.probe.ParallelismProbe}
 * captures the TestPlan's ConfigurationParameters (system properties, junit-platform.properties AND
 * build-tool parameters such as Surefire's configurationParameters). Before that (e.g. in the static
 * scanner) it falls back to system properties and junit-platform.properties.
 */
public final class JUnitConfig {

    private static final String P = "junit.jupiter.execution.parallel.";
    private static volatile ConfigurationParameters runtime;
    private static volatile Properties file;

    private JUnitConfig() {
    }

    public static void capture(ConfigurationParameters parameters) {
        runtime = parameters;
    }

    static Optional<String> get(String key) {
        ConfigurationParameters rt = runtime;
        if (rt != null) {
            return rt.get(key).map(String::trim);
        }
        String sys = System.getProperty(key);
        if (sys != null) {
            return Optional.of(sys.trim());
        }
        return Optional.ofNullable(fileProperties().getProperty(key)).map(String::trim);
    }

    private static Properties fileProperties() {
        Properties f = file;
        if (f == null) {
            f = new Properties();
            try (InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream("junit-platform.properties")) {
                if (in != null) {
                    f.load(in);
                }
            } catch (Exception ignored) {
                // treat as absent
            }
            file = f;
        }
        return f;
    }

    static boolean parallelEnabled() {
        return get(P + "enabled").map(Boolean::parseBoolean).orElse(false);
    }

    /** Default execution mode of test METHODS (JUnit's default is same_thread). */
    static boolean methodsConcurrentByDefault() {
        return parallelEnabled()
                && get(P + "mode.default").map(v -> v.toLowerCase(Locale.ROOT).equals("concurrent")).orElse(false);
    }

    static boolean perClassLifecycleByDefault() {
        return get("junit.jupiter.testinstance.lifecycle.default")
                .map(v -> v.toLowerCase(Locale.ROOT).equals("per_class")).orElse(false);
    }

    /** Effective number of worker threads (1 when parallel execution is disabled). */
    static int parallelism() {
        if (!parallelEnabled()) {
            return 1;
        }
        String strategy = get(P + "config.strategy").orElse("dynamic").toLowerCase(Locale.ROOT);
        int cores = Runtime.getRuntime().availableProcessors();
        return switch (strategy) {
            case "fixed" -> Integer.parseInt(get(P + "config.fixed.parallelism").orElse("1"));
            case "dynamic" -> Math.max(1, (int) (cores * Double.parseDouble(get(P + "config.dynamic.factor").orElse("1"))));
            default -> 1; // custom strategy: unknown
        };
    }
}
