package com.hit.ptest.context;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.test.context.MergedContextConfiguration;

/**
 * Mô tả cấu hình của một context (các thành phần làm nên khóa cache Spring) và so sánh hai cấu hình, để thông điệp
 * CONTEXT_LIMIT_EXCEEDED chỉ ra ĐÚNG thứ cần đổi để dùng lại context có sẵn.
 */
final class ContextConfigDescription {

    /** Mỗi {@code @PerTestMock} cấp class là một mục riêng, nên diff chỉ đúng bean thừa/thiếu giữa hai lớp. */
    static final String MOCK_KEY_PREFIX = "class-level @PerTestMock (the bean is a mock for the whole context: ";
    private static final Pattern DEFAULT_TO_STRING = Pattern.compile("[\\w.$]+@[0-9a-f]+");

    private ContextConfigDescription() {
    }

    static Map<String, String> describe(MergedContextConfiguration mcc) {
        Map<String, String> m = new LinkedHashMap<>();
        try {
            fill(m, mcc);
        } catch (RuntimeException e) {
            m.put("description error", e.toString()); // mô tả chỉ phục vụ thông điệp: không bao giờ làm hỏng việc tạo context
        }
        return m;
    }

    private static void fill(Map<String, String> m, MergedContextConfiguration mcc) {
        m.put("configuration classes", Arrays.stream(mcc.getClasses()).map(Class::getSimpleName).sorted().toList().toString());
        m.put("locations", Arrays.toString(mcc.getLocations()));
        m.put("active profiles", Arrays.toString(mcc.getActiveProfiles()));
        m.put("properties", Arrays.stream(mcc.getPropertySourceProperties()).sorted().toList().toString());
        m.put("property files", mcc.getPropertySourceDescriptors().toString());
        m.put("initializers", mcc.getContextInitializerClasses().stream().map(Class::getSimpleName).sorted().toList().toString());
        m.put("context customizers (ptest declaration, bean overrides, @Import, auto-config...)",
                mcc.getContextCustomizers().stream().map(ContextConfigDescription::customizerName).sorted().toList().toString());
        mcc.getContextCustomizers().stream().filter(ParallelContextCustomizer.class::isInstance).map(ParallelContextCustomizer.class::cast)
                .findFirst().ifPresent(c -> c.mockOnlyBeans().forEach(b -> m.put(MOCK_KEY_PREFIX + b.describe() + ")", "declared")));
        m.put("loader", mcc.getContextLoader().getClass().getSimpleName());
        m.put("parent", String.valueOf(mcc.getParent() == null ? null : mcc.getParent().getTestClass().getName()));
    }

    /** toString mặc định của Object (tên@hash) dao động mỗi lần chạy: thay bằng tên lớp để diff ổn định. */
    private static String customizerName(Object customizer) {
        if (customizer instanceof ParallelContextCustomizer ptest) {
            return ptest.describeWithoutMocks();
        }
        String s = customizer.toString();
        return DEFAULT_TO_STRING.matcher(s).matches() ? customizer.getClass().getSimpleName() : s;
    }

    /** Số mục khác nhau giữa hai cấu hình. */
    static int distance(Map<String, String> a, Map<String, String> b) {
        return differences(a, b).size();
    }

    /** Các mục khác nhau, đã định dạng cho thông điệp ({@code existing} = context đang chạy, {@code new} = context mới). */
    static List<String> differences(Map<String, String> existing, Map<String, String> created) {
        List<String> out = new ArrayList<>();
        java.util.Set<String> keys = new java.util.LinkedHashSet<>(created.keySet());
        keys.addAll(existing.keySet());
        for (String k : keys) {
            if (!Objects.equals(existing.get(k), created.get(k))) {
                out.add("    * " + k + ":\n        existing: " + (existing.containsKey(k) ? existing.get(k) : "(not declared)")
                        + "\n        new:      " + (created.containsKey(k) ? created.get(k) : "(not declared)"));
            }
        }
        return out;
    }
}
