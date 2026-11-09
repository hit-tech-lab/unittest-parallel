package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/** Bean {@code @ConfigurationProperties} bật wrap ở lớp cơ sở: lớp này mock riêng, lớp ProjectLimitsRealIT cùng context dùng bản thật. */
class ProjectLimitsMockIT extends ProjectBaseIT {

    @PerTestMock LimitProperties limits;
    @Autowired LimitService service;

    @Test
    void mockedPropertiesOnlyInThisTest() {
        when(limits.getMaxItems()).thenReturn(3);
        meetInSameContext("limits", 2);

        assertThat(service.limit()).isEqualTo(3);
    }
}
