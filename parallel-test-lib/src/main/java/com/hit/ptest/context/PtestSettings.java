package com.hit.ptest.context;

import com.hit.ptest.ParallelTestException;

import java.lang.management.ManagementFactory;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.SpringProperties;

/**
 * Cấu hình TOÀN CỤC của thư viện (không theo từng context): đọc qua {@link SpringProperties}, tức cùng thứ tự ưu tiên
 * của các cài đặt Spring khác: {@code spring.properties} TRƯỚC, rồi mới đến system property. Cấu hình THEO CONTEXT ({@code ptest.db.*}, {@code ptest.unbound-policy}) đọc từ
 * Environment của chính context đó, nên có thể đặt bằng {@code @SpringBootTest(properties=...)}.
 *
 * <p>Mặc định: ptest.max-contexts=8; ptest.unsupported=fail (hoặc warn); fork.number=0.
 */
public final class PtestSettings {

    static final String PROP_MAX = "ptest.max-contexts";
    static final String PROP_UNSUPPORTED = "ptest.unsupported";
    static final String PROP_FORK = "fork.number";
    static final String PROP_REPORT_DIR = "ptest.report.dir";
    static final String PROP_SPRING_CACHE = "spring.test.context.cache.maxSize";
    static final String PROP_EXIT_WAIT = "ptest.report.exit-wait-millis";
    static final long DEFAULT_EXIT_WAIT_MILLIS = 20_000;
    static final int DEFAULT_MAX = 8;
    static final int DEFAULT_SPRING_CACHE = 32;

    private static final Logger LOG = LoggerFactory.getLogger(PtestSettings.class);
    private static volatile boolean agentChecked;

    private PtestSettings() {
    }

    static String get(String name, String defaultValue) {
        String v = SpringProperties.getProperty(name);
        return v == null ? defaultValue : v;
    }

    private static int getInt(String name, int defaultValue) {
        String v = get(name, Integer.toString(defaultValue)).trim();
        try {
            return Integer.parseInt(v);
        } catch (NumberFormatException e) {
            throw new ParallelTestException.Config("CONFIG: " + name + "=" + v + " is not an integer");
        }
    }

    /** Số context lib tối đa ĐANG HOẠT ĐỘNG (đã tạo, chưa đóng) trong JVM; đọc lại mỗi lần tạo context. */
    static int maxContexts() {
        return getInt(PROP_MAX, DEFAULT_MAX);
    }

    static int springCacheMaxSize() {
        return getInt(PROP_SPRING_CACHE, DEFAULT_SPRING_CACHE);
    }

    /** Thời gian tối đa shutdown hook của báo cáo chờ các context đóng xong (ptest.report.exit-wait-millis, mặc định 20000). */
    public static long exitWaitMillis() {
        return getInt(PROP_EXIT_WAIT, (int) DEFAULT_EXIT_WAIT_MILLIS);
    }

    public static boolean unsupportedWarnOnly() {
        return "warn".equalsIgnoreCase(get(PROP_UNSUPPORTED, "fail"));
    }

    public static String forkNumber() {
        return get(PROP_FORK, "0");
    }

    public static String reportDir() {
        return get(PROP_REPORT_DIR, null);
    }

    /** Mockito inline agent nên nạp bằng -javaagent; nếu không, Mockito tự attach động (JDK 21+ cảnh báo, có thể bị cấm). */
    static boolean mockitoAgentMissing(List<String> jvmArguments) {
        return jvmArguments.stream().noneMatch(a -> a.startsWith("-javaagent:") && a.contains("mockito"));
    }

    /** Kiểm tra sớm, một lần mỗi JVM: cảnh báo rõ thay vì để lỗi Mockito khó hiểu ở lần tạo mock đầu tiên. */
    static void checkMockitoAgentOnce() {
        if (agentChecked) {
            return;
        }
        agentChecked = true;
        requireInlineMockMaker();
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        if (mockitoAgentMissing(args)) {
            LOG.warn("Mockito is not loaded with -javaagent:<mockito-core.jar>; ptest relies on dynamic self-attach, which "
                    + "JDK 21+ warns about and a future JDK may deny. Add -javaagent:<path to mockito-core.jar> (and -Xshare:off "
                    + "to silence the CDS warning) to the test JVM argLine.");
        }
    }

    /** Lớp final riêng chỉ để dò mock maker inline (mock maker subclass không mock được lớp final). */
    private static final class FinalProbe {
    }

    /**
     * Wrapper của bean can thiệp và host của spy là Mockito mock của lớp CỤ THỂ (kể cả final), nên cần Mockito &gt;= 5 (mặc định
     * mock maker inline) hoặc mock maker inline cấu hình thủ công. Kiểm tra bằng hành vi (mock được lớp final hay không) để lỗi
     * rõ ràng ngay lúc tạo context thay vì thông điệp khó hiểu ở lần tạo mock đầu tiên.
     */
    static void requireInlineMockMaker() {
        try {
            org.mockito.Mockito.mock(FinalProbe.class, org.mockito.Mockito.withSettings().stubOnly());
        } catch (RuntimeException | LinkageError e) {
            throw new com.hit.ptest.ParallelTestException.Config("CONFIG: ptest needs Mockito >= 5 (or an inline mock maker, "
                    + "mockito-extensions/org.mockito.plugins.MockMaker = mock-maker-inline) to mock final/concrete classes: " + e);
        }
    }
}
