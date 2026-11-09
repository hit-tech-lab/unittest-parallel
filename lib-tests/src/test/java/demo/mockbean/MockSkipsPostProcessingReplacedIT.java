package demo.mockbean;

import static org.assertj.core.api.Assertions.assertThat;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.AopTestUtils;

/** Như {@link MockSkipsPostProcessingNewIT} nhưng mock THAY định nghĩa đã có: cũng không qua BeanPostProcessor. */
@PerTestMock(Wired.class)
@SpringBootTest(classes = ReplacedConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "mbx.code=abc")
@ParallelSpringTest(context = "mockbean-skip-pp-replaced")
class MockSkipsPostProcessingReplacedIT {

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
