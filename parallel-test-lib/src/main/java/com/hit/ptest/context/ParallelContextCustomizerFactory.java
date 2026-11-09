package com.hit.ptest.context;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.ParallelTestException;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.test.context.ContextConfigurationAttributes;
import org.springframework.test.context.ContextCustomizer;
import org.springframework.test.context.ContextCustomizerFactory;
import org.springframework.test.context.TestContextAnnotationUtils;

/**
 * Đăng ký qua META-INF/spring.factories; chỉ tạo customizer cho lớp có {@link ParallelSpringTest}.
 *
 * <p>Quy tắc tên (xác định, chỉ dựa trên KHAI BÁO, không phụ thuộc context nào được nạp trước hay thành công):
 * cùng {@code context} mà khác {@code wrap}/{@code exclude}/{@code schemas} là lỗi CONTEXT_NAME_CONFLICT (thường là gõ nhầm
 * tên). Khác cấu hình Spring (properties, profile, bean...) thì KHÔNG lỗi: tên chỉ là nhãn, mỗi cấu hình là một
 * context riêng với khóa tên@hash#số-thứ-tự.
 */
public final class ParallelContextCustomizerFactory implements ContextCustomizerFactory {

    private record Declared(ContextDeclaration declaration, String declaredBy) {
    }

    private static final ConcurrentMap<String, Declared> DECLARED = new ConcurrentHashMap<>();

    @Override
    public ContextCustomizer createContextCustomizer(Class<?> testClass, List<ContextConfigurationAttributes> configAttributes) {
        // TestContextAnnotationUtils theo @NestedTestConfiguration: lớp @Nested dùng khai báo của lớp bao ngoài
        ParallelSpringTest annotation = TestContextAnnotationUtils.findMergedAnnotation(testClass, ParallelSpringTest.class);
        if (annotation == null) {
            return null;
        }
        ContextDeclaration mine = ContextDeclaration.of(annotation.context(), annotation.wrap(), annotation.exclude(),
                annotation.schemas(), BeanDeclarations.classLevelMocks(testClass));
        Declared first = DECLARED.putIfAbsent(mine.name(), new Declared(mine, testClass.getName()));
        if (first != null && !first.declaration().sameNameSemantics(mine)) {
            ContextDeclaration f = first.declaration();
            throw new ParallelTestException.Config("CONTEXT_NAME_CONFLICT: context name '" + mine.name() + "' is declared with different "
                    + "wrap/exclude/schemas by " + first.declaredBy() + " (wrap=" + f.wrap() + ", exclude=" + f.exclude() + ", schemas="
                    + f.schemas() + ") and " + testClass.getName() + " (wrap=" + mine.wrap() + ", exclude=" + mine.exclude() + ", schemas="
                    + mine.schemas() + "). Use one declaration per name, or different names.");
        }
        return new ParallelContextCustomizer(mine);
    }
}
