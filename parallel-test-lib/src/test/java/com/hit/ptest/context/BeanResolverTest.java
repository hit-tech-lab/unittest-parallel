package com.hit.ptest.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hit.ptest.intercept.InterceptMode;
import com.hit.ptest.ParallelTestException;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.ResolvableType;

/** Thứ tự chọn bean của {@link BeanResolver} giống Spring: kiểu, lọc @Qualifier, @Primary, tên field, lỗi BEAN_AMBIGUOUS. */
class BeanResolverTest {

    interface Fx {
    }

    static class A implements Fx {
    }

    static class B implements Fx {
    }

    private static ConfigurableListableBeanFactory factory(Consumer<AnnotationConfigApplicationContext> setup) {
        AnnotationConfigApplicationContext ctx = new AnnotationConfigApplicationContext();
        setup.accept(ctx);
        return ctx.getBeanFactory();
    }

    private static void register(AnnotationConfigApplicationContext ctx, String name, Class<?> type, boolean primary) {
        RootBeanDefinition def = new RootBeanDefinition(type);
        def.setPrimary(primary);
        ctx.registerBeanDefinition(name, def);
    }

    private static BeanRequest request(String member, String qualifier) {
        return new BeanRequest(null, "T.m(parameter 0, " + member + ")", member, ResolvableType.forClass(Fx.class), qualifier, InterceptMode.MOCK);
    }

    @Test
    void primaryIsChosenWhenNoQualifier() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, true);
        });

        assertThat(BeanResolver.resolve(f, request("other", null), "ctx")).isEqualTo("b");
    }

    @Test
    void primaryBeatsFieldNameLikeAutowired() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, true);
        });

        assertThat(BeanResolver.resolve(f, request("a", null), "ctx")).isEqualTo("b");
    }

    @Test
    void qualifierBeatsPrimary() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, true);
        });

        assertThat(BeanResolver.resolve(f, request("other", "a"), "ctx")).isEqualTo("a");
    }

    @Test
    void fieldNameIsUsedWithoutPrimary() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, false);
        });

        assertThat(BeanResolver.resolve(f, request("b", null), "ctx")).isEqualTo("b");
    }

    @Test
    void noPrimaryAndSeveralBeansIsAmbiguous() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, false);
        });

        assertThatThrownBy(() -> BeanResolver.resolve(f, request("other", null), "ctx"))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("BEAN_AMBIGUOUS");
    }

    @Test
    void twoPrimariesAreAmbiguous() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, true);
            register(c, "b", B.class, true);
        });

        assertThatThrownBy(() -> BeanResolver.resolve(f, request("other", null), "ctx"))
                .hasMessageContaining("BEAN_AMBIGUOUS").hasMessageContaining("@Primary");
    }

    @Test
    void classLevelAmbiguityHintSuggestsNameOnTheDeclarationNotAFieldName() {
        ConfigurableListableBeanFactory f = factory(c -> {
            register(c, "a", A.class, false);
            register(c, "b", B.class, false);
        });
        BeanRequest classLevel = new BeanRequest(null, "class-level @PerTestMock Fx", "fx", ResolvableType.forClass(Fx.class), null, InterceptMode.MOCK);

        assertThatThrownBy(() -> BeanResolver.resolve(f, classLevel, "ctx")).hasMessageContaining("BEAN_AMBIGUOUS")
                .hasMessageMatching("(?s).*add name = \"<bean name>\" to the class-level.*")
                .hasMessageNotContaining("name the field");
        assertThatThrownBy(() -> BeanResolver.resolve(f, request("other", null), "ctx")).hasMessageContaining("name the field like the bean");
    }
}
