package demo.mockbean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Bean {@code @ConfigurationProperties} chỉ có getter, mock CẤP CLASS tạo bean mới (context không có bản thật): context phải khởi động
 * (mock không được đi qua ConfigurationPropertiesBindingPostProcessor) và handle stub được, như {@code @MockBean}.
 */
@PerTestMock(CodeProvider.class)
@SpringBootTest(classes = MockOnlyConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "mbx.code=abc")
@ParallelSpringTest(context = "mockbean-props-getter-new")
class ConfigPropsGetterOnlyMockOnlyIT {

    @Autowired CodeProvider injected;
    @PerTestMock CodeProvider handle;

    @Test
    void contextStartsAndTheHandleStubsTheGetter() {
        when(handle.getCode()).thenReturn("stubbed");

        assertThat(injected.getCode()).isEqualTo("stubbed");
    }
}
