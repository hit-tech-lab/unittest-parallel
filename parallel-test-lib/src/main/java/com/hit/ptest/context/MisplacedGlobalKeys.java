package com.hit.ptest.context;

import com.hit.ptest.config.PtestKeys;

import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.EnumerablePropertySource;
import org.springframework.core.env.PropertySource;

/**
 * Phát hiện key cấu hình TOÀN CỤC bị đặt nhầm vào Environment của context (ví dụ {@code application.yml}): các key này chỉ đọc qua
 * {@code SpringProperties} nên đặt ở đó không có tác dụng. Ghi WARN đúng một lần mỗi key mỗi JVM; không bao giờ làm context lỗi.
 */
final class MisplacedGlobalKeys {

    private static final Logger LOG = LoggerFactory.getLogger(MisplacedGlobalKeys.class);
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private MisplacedGlobalKeys() {
    }

    /**
     * @param springProperties hàm tra {@code SpringProperties.getProperty}; trả null nghĩa là key KHÔNG có hiệu lực
     */
    static void warnOnce(ConfigurableEnvironment env, Function<String, String> springProperties) {
        for (String key : globalKeysIn(env)) {
            if (springProperties.apply(key) == null && WARNED.add(key)) {
                LOG.warn("{} là cấu hình TOÀN CỤC, không có tác dụng khi đặt trong application.yml/properties của test; "
                        + "hãy đặt trong spring.properties (classpath) hoặc -D{}=...", key, key);
            }
        }
    }

    /** Các key toàn cục có mặt trong Environment (key cố định và mọi key bắt đầu bằng {@code ptest.guard.}). */
    private static Set<String> globalKeysIn(ConfigurableEnvironment env) {
        Set<String> found = new TreeSet<>();
        for (String key : PtestKeys.GLOBAL_KEYS) {
            if (env.containsProperty(key)) {
                found.add(key);
            }
        }
        for (PropertySource<?> source : env.getPropertySources()) {
            if (source instanceof EnumerablePropertySource<?> enumerable) {
                for (String name : enumerable.getPropertyNames()) {
                    if (name.startsWith(PtestKeys.GUARD_PREFIX)) {
                        found.add(name);
                    }
                }
            }
        }
        return found;
    }

    /** Chỉ dùng cho test: xóa trạng thái "đã cảnh báo". */
    static void resetForTest() {
        WARNED.clear();
    }
}
