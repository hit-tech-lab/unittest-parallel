package demo.project;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class ProjectLimitsRealIT extends ProjectBaseIT {

    @Autowired LimitService service;

    @Test
    void realPropertiesWhileAnotherClassMocksThemInTheSameContext() {
        meetInSameContext("limits", 2);

        assertThat(service.limit()).isEqualTo(10);
    }
}
