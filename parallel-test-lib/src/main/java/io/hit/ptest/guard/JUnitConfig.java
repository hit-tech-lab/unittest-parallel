package io.hit.ptest.guard;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;

/**
 * Cấu hình JUnit như launcher THẬT SỰ thấy: khi chạy, {@link #capture} nhận {@code ExtensionContext::getConfigurationParameter}
 * (gồm system property, junit-platform.properties và configurationParameters của Surefire). Trước đó (vd quét tĩnh) chỉ còn
 * system property và junit-platform.properties.
 */
public final class JUnitConfig {

    private static final String P = "junit.jupiter.execution.parallel.";
    private static final String LIFECYCLE = "junit.jupiter.testinstance.lifecycle.default";
    private static final java.util.List<String> KEYS = java.util.List.of(P + "enabled", P + "mode.default",
            P + "config.strategy", P + "config.fixed.parallelism", P + "config.dynamic.factor", LIFECYCLE);
    private static volatile Map<String, String> runtime;
    private static volatile Properties file;

    private JUnitConfig() {
    }

    /** Chụp giá trị hiệu lực của các khóa liên quan (một lần); gọi từ extension khi có ExtensionContext. */
    public static void capture(Function<String, Optional<String>> parameters) {
        if (runtime != null) {
            return;
        }
        Map<String, String> m = new HashMap<>();
        for (String k : KEYS) {
            parameters.apply(k).ifPresent(v -> m.put(k, v.trim()));
        }
        runtime = Map.copyOf(m);
    }

    /** Chỉ cho test của thư viện: xóa giá trị đã chụp. */
    static void reset() {
        runtime = null;
        file = null;
    }

    static Optional<String> get(String key) {
        Map<String, String> rt = runtime;
        if (rt != null) {
            return Optional.ofNullable(rt.get(key));
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
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            try (InputStream in = cl == null ? null : cl.getResourceAsStream("junit-platform.properties")) {
                if (in != null) {
                    f.load(in);
                }
            } catch (Exception ignored) {
                // coi như không có
            }
            file = f;
        }
        return f;
    }

    static boolean parallelEnabled() {
        return get(P + "enabled").map(Boolean::parseBoolean).orElse(false);
    }

    /**
     * Chế độ mặc định của mọi node trừ lớp cấp cao nhất: method và lớp {@code @Nested} (mặc định của JUnit là same_thread).
     * {@code mode.classes.default} chỉ áp cho lớp CẤP CAO NHẤT nên không liên quan tới method và lớp lồng nhau; vì vậy không được chụp.
     */
    static boolean methodsConcurrentByDefault() {
        return parallelEnabled() && get(P + "mode.default").map(v -> v.toLowerCase(Locale.ROOT).equals("concurrent")).orElse(false);
    }

    static boolean perClassLifecycleByDefault() {
        return get(LIFECYCLE).map(v -> v.toLowerCase(Locale.ROOT).equals("per_class")).orElse(false);
    }

    /** Số worker thread hiệu lực (1 nếu không song song hoặc strategy tự viết không biết được). */
    public static int parallelism() {
        if (!parallelEnabled()) {
            return 1;
        }
        String strategy = get(P + "config.strategy").orElse("dynamic").toLowerCase(Locale.ROOT);
        int cores = Runtime.getRuntime().availableProcessors();
        return switch (strategy) {
            case "fixed" -> Integer.parseInt(get(P + "config.fixed.parallelism").orElse("1"));
            case "dynamic" -> Math.max(1, (int) (cores * Double.parseDouble(get(P + "config.dynamic.factor").orElse("1"))));
            default -> 1;
        };
    }
}
