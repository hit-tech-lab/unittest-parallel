package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Bean mock-only được gọi trong @PostConstruct (chưa có test/binding): nhận mặc định Mockito và mock của test sau đó không thấy lời gọi đó. */
class ProjectStartupIT extends ProjectBaseIT {

    @PerTestMock Ports.Mailer mailer;
    @PerTestMock Ports.FeatureFlags flags;

    @Test
    void startupCallsGotMockitoDefaultsAndThisTestsMocksSawNothing() {
        assertThat(bootstrap.startupMail()).as("@PostConstruct called the mock-only mailer: default null").isNull();
        assertThat(bootstrap.startupFlag()).as("default false").isFalse();
        verifyNoInteractions(mailer, flags);
    }
}
