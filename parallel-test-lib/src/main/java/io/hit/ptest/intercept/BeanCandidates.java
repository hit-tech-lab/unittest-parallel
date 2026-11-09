package io.hit.ptest.intercept;

import java.util.Set;
import org.springframework.aop.framework.AopProxyUtils;
import org.springframework.beans.factory.annotation.AnnotatedBeanDefinition;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.AnnotatedElementUtils;

/**
 * Nhận diện "bean do ứng dụng định nghĩa" cho mẫu wildcard của {@code wrap} (mặc định {@code "*"}). Một bean là ứng viên nếu:
 * <ul>
 *   <li>có bean definition vai trò APPLICATION (không phải bean hạ tầng, không phải singleton đăng ký tay);</li>
 *   <li>kiểu thật của nó (với JDK proxy, các interface được proxy: vd repository Spring Data) không thuộc thư viện hạ tầng
 *   ({@link #FRAMEWORK_PREFIXES});</li>
 *   <li>không phải lớp {@code @Configuration};</li>
 *   <li>(trừ JDK proxy của FactoryBean) lớp định nghĩa bean ({@code @Bean} method hoặc chính lớp) không thuộc
 *   {@code org.springframework.}: bean của auto-config như DataSource, EntityManagerFactory, executor, ObjectMapper bị loại.</li>
 * </ul>
 * Tên bean khai báo tường minh trong {@code wrap} không qua bộ lọc này. Đây là heuristic (bean của starter bên thứ ba nằm trong
 * package khác vẫn được bọc); loại trừ bằng {@code exclude}.
 *
 * @apiNote internal: không phải API công khai của thư viện.
 */
final class BeanCandidates {

    /** Tiền tố package của lớp hạ tầng: bean có kiểu thuộc các package này không bao giờ được bọc theo wildcard. */
    static final Set<String> FRAMEWORK_PREFIXES = Set.of("java.", "javax.", "jakarta.", "org.springframework.", "org.hibernate.",
            "com.zaxxer.", "com.fasterxml.", "tools.jackson.", "org.apache.", "ch.qos.", "org.slf4j.", "io.micrometer.",
            "org.h2.", "org.postgresql.", "com.mongodb.");

    private BeanCandidates() {
    }

    /** Lý do bean không phải ứng viên, hoặc null nếu là ứng viên. */
    static String rejection(ConfigurableListableBeanFactory beanFactory, Object bean, String beanName) {
        if (beanName.startsWith("ptest") || !beanFactory.containsBeanDefinition(beanName)) {
            return "not a bean definition of the application (programmatic singleton or ptest infrastructure)";
        }
        BeanDefinition def = beanFactory.getBeanDefinition(beanName);
        if (def.getRole() != BeanDefinition.ROLE_APPLICATION) {
            return "infrastructure bean definition (role " + def.getRole() + ")";
        }
        boolean jdkProxy = java.lang.reflect.Proxy.isProxyClass(bean.getClass());
        Class<?>[] types = jdkProxy ? AopProxyUtils.proxiedUserInterfaces(bean) : new Class<?>[] {AopProxyUtils.ultimateTargetClass(bean)};
        if (types.length == 0) {
            return "framework/library type " + bean.getClass().getName();
        }
        if (isConfigurationProperties(def, types[0])) {
            return "@ConfigurationProperties bean (configuration data bound from the environment; mock it with a class-level @PerTestMock "
                    + "or list its name in wrap)";
        }
        if (java.util.Arrays.stream(types).allMatch(BeanCandidates::isFrameworkType)) {
            // Bean do ứng dụng tự định nghĩa (@Bean ở lớp của ứng dụng) nhưng kiểu thuộc framework (RestTemplate, ObjectMapper, executor...)
            // vẫn được bọc, trừ kiểu hạ tầng mà framework gọi trực tiếp (DataSource, EntityManagerFactory, transaction manager...)
            if (jdkProxy || isFrameworkName(originClass(beanFactory, def)) || java.util.Arrays.stream(types).anyMatch(BeanCandidates::isInfrastructureType)) {
                return "framework/library type " + types[0].getName();
            }
        }
        if (AnnotatedElementUtils.hasAnnotation(types[0], Configuration.class)) {
            return "@Configuration class";
        }
        if (!jdkProxy && isFrameworkName(originClass(beanFactory, def))) {
            return "defined by framework configuration " + originClass(beanFactory, def);
        }
        return null;
    }

    /** Kiểu hạ tầng mà framework dùng trực tiếp: không bọc theo wildcard dù do ứng dụng định nghĩa; vẫn mock được bằng {@code @PerTestMock} cấp class. */
    static final Set<String> INFRASTRUCTURE_TYPES = Set.of("javax.sql.DataSource", "jakarta.persistence.EntityManagerFactory",
            "jakarta.persistence.EntityManager", "org.springframework.transaction.PlatformTransactionManager",
            "org.springframework.transaction.ReactiveTransactionManager", "org.springframework.context.MessageSource",
            "org.springframework.core.convert.ConversionService");

    private static final String CONFIGURATION_PROPERTIES = "org.springframework.boot.context.properties.ConfigurationProperties";

    private static boolean isInfrastructureType(Class<?> type) {
        return INFRASTRUCTURE_TYPES.stream().anyMatch(name -> {
            try {
                return Class.forName(name, false, type.getClassLoader()).isAssignableFrom(type);
            } catch (ClassNotFoundException | LinkageError e) {
                return false; // kiểu hạ tầng không có trên classpath thì không có bean nào thuộc kiểu đó
            }
        });
    }

    /** {@code @ConfigurationProperties} ở lớp hoặc ở method {@code @Bean}. Dùng tên chuỗi để không phụ thuộc cứng vào spring-boot. */
    private static boolean isConfigurationProperties(BeanDefinition def, Class<?> type) {
        if (org.springframework.core.annotation.MergedAnnotations.from(type, org.springframework.core.annotation.MergedAnnotations.SearchStrategy.TYPE_HIERARCHY)
                .isPresent(CONFIGURATION_PROPERTIES)) {
            return true;
        }
        return def instanceof AnnotatedBeanDefinition annotated && annotated.getFactoryMethodMetadata() != null
                && annotated.getFactoryMethodMetadata().isAnnotated(CONFIGURATION_PROPERTIES);
    }

    private static boolean isFrameworkType(Class<?> type) {
        return isFrameworkName(type.getName());
    }

    private static boolean isFrameworkName(String className) {
        return className != null && FRAMEWORK_PREFIXES.stream().anyMatch(className::startsWith);
    }

    /** Lớp định nghĩa bean: lớp chứa {@code @Bean} method, lớp của factory-bean, hoặc chính lớp bean. */
    private static String originClass(ConfigurableListableBeanFactory beanFactory, BeanDefinition def) {
        if (def instanceof AnnotatedBeanDefinition annotated && annotated.getFactoryMethodMetadata() != null) {
            return annotated.getFactoryMethodMetadata().getDeclaringClassName();
        }
        String factoryBean = def.getFactoryBeanName();
        if (factoryBean != null && beanFactory.containsBeanDefinition(factoryBean)) {
            return beanFactory.getBeanDefinition(factoryBean).getBeanClassName();
        }
        return def.getBeanClassName();
    }
}
