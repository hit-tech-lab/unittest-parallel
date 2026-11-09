package io.hit.ptest.guard;

import io.hit.ptest.ParallelTestException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.core.SpringProperties;

/**
 * Cấu hình guard, đọc LẠI mỗi lần gọi qua {@link SpringProperties} (cùng nguồn và thứ tự ưu tiên với
 * {@code PtestSettings}: {@code spring.properties} trước, rồi system property).
 *
 * <ul>
 *   <li>{@code ptest.guard.enabled=true|false}: false tắt MỌI luật (cơ chế can thiệp mock/spy vẫn chạy như thường);</li>
 *   <li>{@code ptest.guard.<PTxx>=fail|warn|off}: mức của từng luật (mặc định xem {@link GuardRule#defaultSeverity()});</li>
 *   <li>{@code ptest.guard.forbidden-calls=Type#method(params), ...}: thêm vào danh sách gọi bị cấm của PT07;</li>
 *   <li>{@code ptest.guard.scan-exclude=pkg.a, pkg.b}: package bỏ qua khi quét cả source set (negative control).</li>
 * </ul>
 */
public final class GuardSettings {

    static final String PREFIX = "ptest.guard.";
    static final List<String> DEFAULT_FORBIDDEN_CALLS = List.of(
            "org.springframework.data.repository.CrudRepository#deleteAll()",
            "org.springframework.data.repository.CrudRepository#count()",
            "org.springframework.data.repository.CrudRepository#findAll()",
            "org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Sort)",
            "org.springframework.data.repository.PagingAndSortingRepository#findAll(org.springframework.data.domain.Pageable)",
            "org.springframework.data.jpa.repository.JpaRepository#deleteAllInBatch()",
            "org.springframework.test.jdbc.JdbcTestUtils#deleteFromTables(*)",
            "org.springframework.test.jdbc.JdbcTestUtils#countRowsInTable(*)",
            "org.springframework.test.jdbc.JdbcTestUtils#dropTables(*)");

    private GuardSettings() {
    }

    private static String get(String key) {
        String v = SpringProperties.getProperty(PREFIX + key);
        return v == null ? null : v.trim();
    }

    public static boolean enabled() {
        return !"false".equalsIgnoreCase(get("enabled"));
    }

    /** Mức hiệu lực của luật: OFF nếu guard tắt; sai giá trị => lỗi cấu hình rõ ràng (không âm thầm đổi về mặc định). */
    public static Severity severity(GuardRule rule) {
        if (!enabled()) {
            return Severity.OFF;
        }
        String v = get(rule.name());
        if (v == null || v.isEmpty()) {
            return rule.defaultSeverity();
        }
        try {
            return Severity.valueOf(v.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new ParallelTestException.Config("CONFIG: " + PREFIX + rule.name() + "=" + v + " (expected fail|warn|off)");
        }
    }

    public static List<ForbiddenCall> forbiddenCalls() {
        List<String> specs = new ArrayList<>(DEFAULT_FORBIDDEN_CALLS);
        String extra = get("forbidden-calls");
        if (extra != null) {
            Arrays.stream(extra.split(",(?![^(]*\\))")).map(String::trim).filter(s -> !s.isEmpty()).forEach(specs::add);
        }
        return specs.stream().map(ForbiddenCall::parse).toList();
    }

    public static List<String> scanExclude() {
        String v = get("scan-exclude");
        return v == null ? List.of() : Arrays.stream(v.split(",")).map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
