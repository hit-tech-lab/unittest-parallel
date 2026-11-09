package io.hit.ptest.intercept;

import io.hit.ptest.context.ManagedContext;
import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wrapper mang giá trị field của bean thật: code đọc thẳng field của bean đã tiêm thấy đúng giá trị như trong Spring thường. */
class WrapperFieldCopyTest {

    public static class Holder {
        public final String label = String.valueOf("from-real");
        public Supplier<String> supplier = () -> "supplied";

        public String hello() {
            return "real";
        }
    }

    static Holder lastCreated;

    @Configuration(proxyBeanMethods = false)
    static class Config {
        @Bean Holder holder() {
            lastCreated = new Holder();
            return lastCreated;
        }
    }

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    @Test
    void directFieldReadsOnTheInjectedWrapperSeeTheRealBeansState() {
        AnnotationConfigApplicationContext c = Fixtures.started("field-copy", new String[] {"holder"}, Map.of(), Config.class);
        Holder wrapper = c.getBean(Holder.class);

        assertThat(wrapper.label).isEqualTo("from-real");
        assertThat(wrapper.supplier.get()).isEqualTo("supplied");
        assertThat(wrapper.hello()).as("method calls are still routed").isEqualTo("real");
    }

    @Test
    void aPerTestMockOfThatBeanStillWinsForMethodCalls() {
        AnnotationConfigApplicationContext c = Fixtures.started("field-copy-mock", new String[] {"holder"}, Map.of(), Config.class);
        ManagedContext mc = Fixtures.managed(c);
        Holder mock = (Holder) mc.requireInterceptor("holder").newTarget(InterceptMode.MOCK);
        TestBinding.bind(TestBinding.open("t", Map.of(new TestBinding.TargetKey(mc.key, "holder"), mock)));
        when(mock.hello()).thenReturn("mocked");

        assertThat(c.getBean(Holder.class).hello()).isEqualTo("mocked");
        assertThat(c.getBean(Holder.class).label).as("field still readable on the wrapper").isEqualTo("from-real");
    }

    /** GIỚI HẠN đã biết (ghi trong LIMITS.md): bản sao field chụp một lần lúc tạo wrapper, gán lại field SAU đó không hiện qua wrapper. */
    @Test
    void knownLimitFieldReassignedAfterTheWrapperWasBuiltIsNotSeenThroughDirectFieldReads() {
        AnnotationConfigApplicationContext c = Fixtures.started("field-copy-limit", new String[] {"holder"}, Map.of(), Config.class);
        Holder wrapper = c.getBean(Holder.class);
        Holder real = lastCreated;

        real.supplier = () -> "changed-after-start";

        assertThat(wrapper.supplier.get()).as("direct field read: snapshot from wrapper creation").isEqualTo("supplied");
        assertThat(real.supplier.get()).isEqualTo("changed-after-start");
    }
}
