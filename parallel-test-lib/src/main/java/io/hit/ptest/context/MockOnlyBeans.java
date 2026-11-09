package io.hit.ptest.context;

import io.hit.ptest.intercept.InterceptMode;
import io.hit.ptest.ParallelTestException;
import io.hit.ptest.intercept.BeanInterceptor;
import io.hit.ptest.intercept.InterceptOwner;
import io.hit.ptest.intercept.InterceptPlan;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.AutowireCandidateQualifier;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.ResolvableType;

/**
 * Áp các khai báo {@code @PerTestMock} cấp class vào context SAU khi cấu hình của người dùng đã được nạp (chạy trong
 * BeanFactoryPostProcessor lập trình, sau mọi BeanDefinitionRegistryPostProcessor), nên biết chính xác bean nào đã có. Giống
 * {@code @MockBean}: bean đã có thì THAY định nghĩa bằng bean mock, chưa có thì TẠO bean mock. Tất cả khai báo được xét "đã có hay
 * chưa" TRƯỚC khi sửa bất kỳ định nghĩa nào, để hai khai báo cùng kiểu không coi nhau là bean có sẵn.
 */
final class MockOnlyBeans {

    private MockOnlyBeans() {
    }

    static void register(ConfigurableListableBeanFactory beanFactory, InterceptOwner owner, InterceptPlan plan, List<MockOnlyBean> declared) {
        if (declared.isEmpty()) {
            return;
        }
        BeanDefinitionRegistry registry = (BeanDefinitionRegistry) beanFactory;
        List<MockOnlyBean> missing = new ArrayList<>();
        Map<String, MockOnlyBean> replacements = new LinkedHashMap<>();
        for (MockOnlyBean bean : declared) {
            boolean namedFactoryBean = bean.qualified() && registry.containsBeanDefinition(bean.name()) && beanFactory.isFactoryBean(bean.name());
            if (!namedFactoryBean && BeanResolver.candidates(beanFactory, bean.type(), bean.qualified() ? bean.name() : null).isEmpty()) {
                missing.add(bean);
                continue;
            }
            // FactoryBean mà Spring không đoán được kiểu sản phẩm: chỉ tên bean tường minh mới nhận ra nó là bean cần thay
            String target = namedFactoryBean ? bean.name() : BeanResolver.resolve(beanFactory, new BeanRequest(null, "class-level @PerTestMock " + bean.type(), bean.name(),
                    bean.type(), bean.qualified() ? bean.name() : null, InterceptMode.MOCK), owner.name());
            MockOnlyBean other = replacements.putIfAbsent(target, bean);
            if (other != null) {
                throw new ParallelTestException.Config("MOCK_ONLY_CONFLICT: class-level @PerTestMock " + other.describe() + " and "
                        + bean.describe() + " both replace bean '" + target + "'. Declare it once.");
            }
        }
        replacements.forEach((target, bean) -> replace(beanFactory, registry, owner, plan, target, bean));
        for (MockOnlyBean bean : missing) {
            Class<?> raw = bean.type().resolve();
            if (raw == null) {
                throw new ParallelTestException.Config("MOCK_ONLY_CONFLICT: cannot mock type " + bean.type() + " of mock-only bean '" + bean.name() + "'");
            }
            String name = bean.name();
            if (registry.isBeanNameInUse(name)) {
                if (bean.qualified()) {
                    throw new ParallelTestException.Config("MOCK_ONLY_CONFLICT: bean name '" + name + "' (from class-level @PerTestMock(name = \"" + name
                            + "\", classes = " + raw.getSimpleName() + ".class)) is already used by a bean that is not of that type. Choose another name.");
                }
                // chọn theo kiểu: không ai tham chiếu tên mặc định nên đổi sang tên duy nhất (như @MockBean: tên lớp đầy đủ + #n)
                int n = 0;
                while (registry.isBeanNameInUse(raw.getName() + "#" + n)) {
                    n++;
                }
                name = raw.getName() + "#" + n;
            }
            registry.registerBeanDefinition(name, mockOnlyDefinition(owner, plan, name, bean.type(), raw, false,
                    Set.of(beanFactory.getBeanNamesForType(bean.type(), true, false))));
        }
    }

    /**
     * Thay định nghĩa bean đã có bằng bean mock cùng tên (như {@code @MockBean}): định nghĩa thật bị gỡ trước khi bất kỳ
     * singleton nào được tạo, nên bean thật không bao giờ khởi tạo. Giữ tên, alias, {@code @Primary}, {@code @Fallback}, khả năng làm
     * ứng viên autowire và mọi thông tin {@code @Qualifier} (đặt ở lớp triển khai, ở method {@code @Bean}, hoặc trong metadata; xem {@link #copyClassLevelQualifiers}) để các chỗ
     * tiêm khác vẫn chọn đúng bean. {@code @DependsOn}/init-method/scope của bản thật KHÔNG giữ: mock không cần các phụ thuộc đó.
     */
    private static void replace(ConfigurableListableBeanFactory beanFactory, BeanDefinitionRegistry registry, InterceptOwner owner,
                                InterceptPlan plan, String target, MockOnlyBean bean) {
        Class<?> raw = bean.type().resolve();
        if (!registry.containsBeanDefinition(target)) {
            throw new ParallelTestException.Config("MOCK_REPLACE_UNSUPPORTED: bean '" + target + "' of type " + bean.type()
                    + " has no bean definition (registered as a ready singleton), so its definition cannot be replaced.");
        }
        BeanDefinition original = beanFactory.getBeanDefinition(target);
        RootBeanDefinition definition = mockOnlyDefinition(owner, plan, target, bean.type(), raw, true, Set.of());
        definition.setPrimary(original.isPrimary());
        definition.setAutowireCandidate(original.isAutowireCandidate());
        copyFallback(original, definition);
        if (original instanceof AbstractBeanDefinition abstractOriginal) {
            definition.copyQualifiersFrom(abstractOriginal);
        }
        Method factoryMethod = null;
        if (original instanceof RootBeanDefinition root) {
            definition.setQualifiedElement(root.getQualifiedElement());
            factoryMethod = root.getResolvedFactoryMethod();
        }
        if (factoryMethod == null && beanFactory.getMergedBeanDefinition(target) instanceof RootBeanDefinition merged) {
            factoryMethod = merged.getResolvedFactoryMethod(); // bản gộp đã phân giải method khi Spring đoán kiểu bean
        }
        // @Qualifier trên method @Bean được Spring đọc từ method factory đã phân giải; nếu chưa có (metadata đọc bằng ASM) thì lấy từ metadata
        definition.setResolvedFactoryMethod(factoryMethod);
        if (factoryMethod == null && original instanceof AnnotatedBeanDefinition annotated && annotated.getFactoryMethodMetadata() != null) {
            var qualifier = annotated.getFactoryMethodMetadata().getAnnotations().get(Qualifier.class);
            if (qualifier.isPresent() && !qualifier.getString("value").isBlank()
                    && !definition.hasQualifier(Qualifier.class.getName())) {
                definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, qualifier.getString("value")));
            }
        }
        copyClassLevelQualifiers(beanFactory.getType(target, false), definition);
        registry.removeBeanDefinition(target);
        registry.registerBeanDefinition(target, definition);
    }

    /**
     * {@code @Qualifier} (và annotation tự đánh dấu {@code @Qualifier}) đặt trên LỚP triển khai của bean bị thay. Spring đọc nó từ kiểu của bean; định
     * nghĩa mock chỉ có kiểu đã khai báo (có thể là supertype/interface không mang annotation đó) nên phải chép sang định nghĩa mới, nếu không các
     * chỗ tiêm {@code @Qualifier("...")} không còn thấy bean.
     */
    private static void copyClassLevelQualifiers(Class<?> originalType, RootBeanDefinition definition) {
        if (originalType == null) {
            return;
        }
        for (java.lang.annotation.Annotation annotation : originalType.getAnnotations()) {
            Class<? extends java.lang.annotation.Annotation> type = annotation.annotationType();
            if (definition.hasQualifier(type.getName())) {
                continue;
            }
            if (type == Qualifier.class) {
                String value = ((Qualifier) annotation).value();
                if (!value.isBlank()) {
                    definition.addQualifier(new AutowireCandidateQualifier(Qualifier.class, value));
                }
            } else if (org.springframework.core.annotation.AnnotationUtils.findAnnotation(type, Qualifier.class) != null) {
                AutowireCandidateQualifier qualifier = new AutowireCandidateQualifier(type);
                org.springframework.core.annotation.AnnotationUtils.getAnnotationAttributes(annotation).forEach(qualifier::setAttribute);
                definition.addQualifier(qualifier);
            }
        }
    }

    /** {@code @Fallback} (Spring 6.2+): đọc bằng reflection vì thư viện cũng biên dịch với Spring 6.1, nơi API này chưa có. */
    private static void copyFallback(BeanDefinition original, RootBeanDefinition definition) {
        try {
            boolean fallback = (boolean) BeanDefinition.class.getMethod("isFallback").invoke(original);
            if (fallback) {
                RootBeanDefinition.class.getMethod("setFallback", boolean.class).invoke(definition, true);
            }
        } catch (ReflectiveOperationException ignored) {
            // Spring cũ không có @Fallback: không có gì để giữ
        }
    }

    private static RootBeanDefinition mockOnlyDefinition(InterceptOwner owner, InterceptPlan plan, String name, ResolvableType type, Class<?> raw,
                                                         boolean replaced, Set<String> knownBefore) {
        plan.markMockOnly(name, replaced, raw, knownBefore);
        RootBeanDefinition definition = new RootBeanDefinition();
        definition.setTargetType(type);
        definition.setRole(BeanDefinition.ROLE_APPLICATION);
        definition.setInstanceSupplier(() -> {
            BeanInterceptor interceptor = BeanInterceptor.createMockOnly(owner, name, raw);
            owner.register(name, interceptor);
            return interceptor.wrapper;
        });
        return definition;
    }
}
