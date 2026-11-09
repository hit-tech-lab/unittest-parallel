package demo.mockbean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.ParallelSpringTest;
import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/** Bean {@code @ConfigurationProperties} có setter, mock cấp class tạo bean mới: khởi động được, handle stub được (hồi quy cho mọi kiểu properties). */
@PerTestMock(SettableLimits.class)
@SpringBootTest(classes = MockOnlyConfig.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "mbs.limit=5")
@ParallelSpringTest(context = "mockbean-props-setter")
class ConfigPropsWithSetterMockIT {

    @Autowired SettableLimits injected;
    @PerTestMock SettableLimits handle;

    @Test
    void handleStubsTheGetterOfTheBoundType() {
        when(handle.getLimit()).thenReturn(9);

        assertThat(injected.getLimit()).isEqualTo(9);
    }
}
