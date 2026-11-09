package com.hit.ptest.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.testsupport.Fixtures;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

/**
 * {@code @Qualifier} đặt trên LỚP triển khai của bean bị thay bởi {@code @PerTestMock} cấp class: nơi tiêm khác vẫn phải chọn đúng bean, cả khi
 * kiểu khai báo là supertype (interface) lẫn khi là chính lớp triển khai.
 */
class ClassLevelMockClassQualifierTest {

    interface Greeter {
        String greet();
    }

    @Qualifier("fast")
    static class FastGreeter implements Greeter {
        public String greet() {
            return "real";
        }
    }

    static class User {
        final Greeter greeter;

        User(@Qualifier("fast") Greeter greeter) {
            this.greeter = greeter;
        }
    }

    @Configuration
    @Import(FastGreeter.class)
    static class ImportedConfig {
        @Bean User user(@Qualifier("fast") Greeter greeter) {
            return new User(greeter);
        }
    }

    @PerTestMock(Greeter.class)
    @ParallelSpringTest(context = "cq-super")
    abstract static class SuperTypeBase {
    }

    @PerTestMock(FastGreeter.class)
    @ParallelSpringTest(context = "cq-concrete")
    abstract static class ConcreteBase {
    }

    static class SuperTypeTest extends SuperTypeBase {
    }

    static class ConcreteTest extends ConcreteBase {
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    private static AnnotationConfigApplicationContext start(Class<?> testClass, String name) {
        AnnotationConfigApplicationContext c = Fixtures.context(ContextDeclaration.of(name, new String[] {"*"}, new String[0], new String[0],
                BeanDeclarations.classLevelMocks(testClass)), Map.of(), ImportedConfig.class);
        c.refresh();
        return c;
    }

    @Test
    void classQualifierSurvivesWhenTheDeclaredTypeIsTheImplementation() {
        AnnotationConfigApplicationContext c = start(ConcreteTest.class, "cq-concrete");

        assertThat(c.getBean(User.class).greeter).isNotNull();
    }

    @Test
    void classQualifierSurvivesWhenTheDeclaredTypeIsASupertype() {
        AnnotationConfigApplicationContext c = start(SuperTypeTest.class, "cq-super");

        assertThat(c.getBean(User.class).greeter).isNotNull();
    }
}
