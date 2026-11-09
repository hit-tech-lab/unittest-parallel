package io.hit.ptest.context;

import io.hit.ptest.intercept.InterceptMode;
import io.hit.ptest.ParallelTestException;

import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.BeanFactoryAnnotationUtils;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.ResolvableType;

/**
 * Chọn bean cho một field/tham số theo thứ tự của Spring ({@code @Autowired}/{@code @MockitoBean}, Framework 6.1+ và 7): bean cùng kiểu,
 * lọc theo {@code @Qualifier}; còn nhiều thì bean {@code @Primary}; rồi tên field/tham số trùng tên bean; không xác định thì ném
 * lỗi cấu hình {@code BEAN_AMBIGUOUS}. Không xét {@code @Priority} và {@code @Fallback} (Spring 6.2+).
 */
final class BeanResolver {

    private BeanResolver() {
    }

    /** Tên chính tắc của bean mà yêu cầu chỉ tới. */
    static String resolve(ConfigurableListableBeanFactory beanFactory, BeanRequest request, String contextName) {
        List<String> candidates = candidates(beanFactory, request.type(), request.qualifier());
        if (candidates.size() == 1) {
            return candidates.get(0);
        }
        if (candidates.isEmpty()) {
            String code = request.mode() == InterceptMode.SPY ? "SPY_NO_BEAN" : "BEAN_NOT_FOUND";
            throw new ParallelTestException.Config(code + ": no bean of type " + request.type()
                    + (request.qualifier() == null ? "" : " matching @Qualifier(\"" + request.qualifier() + "\")") + " in context '"
                    + contextName + "' for " + request.describe() + ". " + hint(request));
        }
        String primary = primary(beanFactory, candidates, request, contextName);
        if (primary != null) {
            return primary;
        }
        List<String> byName = candidates.stream().filter(n -> n.equals(request.member())
                || Arrays.asList(beanFactory.getAliases(n)).contains(request.member())).toList();
        if (byName.size() == 1) {
            return byName.get(0);
        }
        throw new ParallelTestException.Config("BEAN_AMBIGUOUS: " + candidates.size() + " beans of type " + request.type() + " in context '"
                + contextName + "': " + candidates + " for " + request.describe() + ". " + ambiguityFix(request));
    }

    private static String ambiguityFix(BeanRequest request) {
        if (request.classLevel()) { // không có field để đặt tên: chỉ còn @Primary hoặc name = "..." trên khai báo
            return "Mark one bean @Primary or add name = \"<bean name>\" to the class-level @PerTestMock/@PerTestSpy declaration.";
        }
        return "Mark one bean @Primary, add @Qualifier(\"<bean name>\") to the field/parameter, or name the field like the bean.";
    }

    /** {@code @Primary} như {@code DefaultListableBeanFactory.determinePrimaryCandidate}: bean primary duy nhất; hai primary là {@code BEAN_AMBIGUOUS}. */
    private static String primary(ConfigurableListableBeanFactory beanFactory, List<String> candidates, BeanRequest request,
                                  String contextName) {
        List<String> primaries = candidates.stream()
                .filter(n -> beanFactory.containsBeanDefinition(n) && beanFactory.getBeanDefinition(n).isPrimary()).toList();
        if (primaries.size() > 1) {
            throw new ParallelTestException.Config("BEAN_AMBIGUOUS: more than one @Primary bean of type " + request.type() + " in context '"
                    + contextName + "': " + primaries + " for " + request.describe());
        }
        return primaries.isEmpty() ? null : primaries.get(0);
    }

    /** Bean cùng kiểu (và khớp {@code @Qualifier} nếu có); không khởi tạo FactoryBean lazy chỉ để đoán kiểu. */
    static List<String> candidates(ConfigurableListableBeanFactory beanFactory, ResolvableType type, String qualifier) {
        return Arrays.stream(beanFactory.getBeanNamesForType(type, true, false))
                .filter(n -> qualifier == null || BeanFactoryAnnotationUtils.isQualifierMatch(qualifier::equals, n, beanFactory))
                .toList();
    }

    private static String hint(BeanRequest request) {
        return request.mode() == InterceptMode.SPY
                ? "A spy needs an existing real bean; use @PerTestMock if the bean does not exist."
                : "A field/parameter only points to a bean that exists. To mock a bean the context does not have, declare it at class level "
                        + "(@PerTestMock(MyBean.class), or name = \"...\", classes = MyBean.class) on the class carrying @ParallelSpringTest or "
                        + "a superclass: the library then registers it as a mock for the whole context.";
    }
}
