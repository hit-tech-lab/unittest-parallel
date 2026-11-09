package demo.mockbean;

import static org.assertj.core.api.Assertions.assertThat;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

/**
 * Mock cấp class TẠO MỚI (không có định nghĩa gốc) không đi qua BeanPostProcessor như {@code @MockBean}: field {@code @Autowired} không
 * được tiêm, {@code @PostConstruct} không chạy, không bị bọc proxy AOP ({@code @Async}).
 */
@PerTestMock(Wired.class)
@SpringBootTest(classes = MockOnlyConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ParallelSpringTest(context = "mockbean-skip-pp-new")
class MockSkipsPostProcessingNewIT {

    @Autowired Wired injected;

    /** Bỏ proxy AOP (nếu có) để đọc field của chính đối tượng bean. */
    private Wired target() {
        return AopTestUtils.getUltimateTargetObject(injected);
    }

    @Test
    void autowiredFieldIsNotInjected() {
        assertThat(target().helper).isNull();
    }

    @Test
    void postConstructDoesNotRun() {
        assertThat(target().initialized).isFalse();
    }

    @Test
    void isNotWrappedInAnAopProxy() {
        assertThat(AopUtils.isAopProxy(injected)).isFalse();
    }
}
