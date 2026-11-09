package com.hit.ptest.config;

import java.util.List;

/**
 * Tên mọi key cấu hình {@code ptest.*} của thư viện. Chỉ là hằng số tên; mặc định và cách đọc nằm ở nơi dùng.
 *
 * <p>Có hai nhóm với nơi đặt KHÁC NHAU:
 * <ul>
 *   <li><b>Theo context</b>: đọc từ Environment của chính context, đặt được trong {@code application.yml},
 *       {@code @SpringBootTest(properties=...)} hay {@code -D}. Có trong {@code additional-spring-configuration-metadata.json}.</li>
 *   <li><b>Toàn cục</b>: đọc qua {@code SpringProperties} nên CHỈ có hiệu lực khi đặt trong {@code spring.properties}
 *       (classpath) hoặc {@code -Dptest.xxx=...}; đặt trong {@code application.yml} bị bỏ qua (thư viện ghi WARN).
 *       Không đưa vào file metadata.</li>
 * </ul>
 *
 * <p>Xem docs/configuration.md.
 */
public final class PtestKeys {

    // ---- Nhóm theo context ----

    /** Chế độ cấp DB: {@code auto|h2|postgres|none}. Mặc định {@code auto}. [theo context; application.yml hoặc -D]. */
    public static final String DB_MODE = "ptest.db.mode";

    /** Cách cô lập DB khi dùng postgres: {@code database|schema}. Mặc định {@code database}. [theo context; application.yml hoặc -D]. */
    public static final String DB_ISOLATION = "ptest.db.isolation";

    /** Phần options nối vào URL H2 (ví dụ {@code ;MODE=PostgreSQL;}). Mặc định rỗng. [theo context; application.yml hoặc -D]. */
    public static final String DB_H2_OPTIONS = "ptest.db.h2.options";

    /** Image Docker của Postgres (Testcontainers). Mặc định {@code postgres:16.4}; chỉ context đầu tiên của JVM quyết định. [theo context]. */
    public static final String DB_POSTGRES_IMAGE = "ptest.db.postgres.image";

    /** {@code max_connections} của container Postgres. Mặc định 300. [theo context; application.yml hoặc -D]. */
    public static final String DB_POSTGRES_MAX_CONNECTIONS = "ptest.db.postgres.max-connections";

    /** Cỡ pool kết nối mỗi context. Mặc định {@code max(4, số thread song song của JUnit)} (không phải hằng số). [theo context]. */
    public static final String DB_POOL_SIZE = "ptest.db.pool-size";

    /** Chính sách thread không binding gọi bean can thiệp: {@code guarded|fail|real}. Mặc định {@code guarded}. [theo context]. */
    public static final String UNBOUND_POLICY = "ptest.unbound-policy";

    // ---- Nhóm toàn cục ----

    /** Số context tối đa đang hoạt động trong JVM. Mặc định 8. [toàn cục; spring.properties hoặc -D]. */
    public static final String MAX_CONTEXTS = "ptest.max-contexts";

    /** Cấu hình không hỗ trợ: {@code fail|warn}. Mặc định {@code fail}. [toàn cục; spring.properties hoặc -D]. */
    public static final String UNSUPPORTED = "ptest.unsupported";

    /** Thư mục ghi báo cáo. Mặc định không đặt. [toàn cục; spring.properties hoặc -D]. */
    public static final String REPORT_DIR = "ptest.report.dir";

    /** Thời gian tối đa (ms) shutdown hook của báo cáo chờ context đóng. Mặc định 20000. [toàn cục; spring.properties hoặc -D]. */
    public static final String REPORT_EXIT_WAIT_MILLIS = "ptest.report.exit-wait-millis";

    /** Tiền tố của các key guard {@code ptest.guard.*}. [toàn cục; spring.properties hoặc -D]. */
    public static final String GUARD_PREFIX = "ptest.guard.";

    /** {@code true|false}: false tắt mọi luật guard. Mặc định true. [toàn cục; spring.properties hoặc -D]. */
    public static final String GUARD_ENABLED = GUARD_PREFIX + "enabled";

    /** Danh sách thêm vào các lời gọi bị cấm của PT07, dạng {@code Type#method(params), ...}. [toàn cục; spring.properties hoặc -D]. */
    public static final String GUARD_FORBIDDEN_CALLS = GUARD_PREFIX + "forbidden-calls";

    /** Package bỏ qua khi quét cả source set, phân tách bằng dấu phẩy. [toàn cục; spring.properties hoặc -D]. */
    public static final String GUARD_SCAN_EXCLUDE = GUARD_PREFIX + "scan-exclude";

    /** Mức từng luật: {@code ptest.guard.<PTxx>=fail|warn|off}. [toàn cục; spring.properties hoặc -D]. */
    public static String guardRule(String ruleName) {
        return GUARD_PREFIX + ruleName;
    }

    // ---- Nội bộ ----

    /** NỘI BỘ: khóa context, thư viện tự ghi vào Environment (nguồn {@code ptestContext}); không phải cấu hình của người dùng. */
    public static final String CONTEXT_KEY = "ptest.context.key";

    /** NỘI BỘ: tên context, thư viện tự ghi vào Environment; không phải cấu hình của người dùng. */
    public static final String CONTEXT_NAME = "ptest.context.name";

    /** Các key nhóm theo context (khớp additional-spring-configuration-metadata.json). */
    public static final List<String> CONTEXT_KEYS = List.of(DB_MODE, DB_ISOLATION, DB_H2_OPTIONS, DB_POSTGRES_IMAGE,
            DB_POSTGRES_MAX_CONNECTIONS, DB_POOL_SIZE, UNBOUND_POLICY);

    /** Các key toàn cục cố định (ngoài {@link #GUARD_PREFIX}{@code <PTxx>}); dùng cùng {@link #GUARD_PREFIX} để nhận diện toàn bộ nhóm. */
    public static final List<String> GLOBAL_KEYS = List.of(MAX_CONTEXTS, UNSUPPORTED, REPORT_DIR, REPORT_EXIT_WAIT_MILLIS,
            GUARD_ENABLED, GUARD_FORBIDDEN_CALLS, GUARD_SCAN_EXCLUDE);

    private PtestKeys() {
    }

    /** True nếu key thuộc nhóm toàn cục (key cố định hoặc bắt đầu bằng {@link #GUARD_PREFIX}). */
    public static boolean isGlobal(String key) {
        return GLOBAL_KEYS.contains(key) || key.startsWith(GUARD_PREFIX);
    }
}
