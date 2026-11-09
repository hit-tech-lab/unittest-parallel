package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Bean ĐÃ có trong context (không phải mock-only): mock chỉ trong lớp này, lớp ProjectExceptionsRealIT cùng context vẫn dùng bản thật. */
class ProjectExceptionsMockIT extends ProjectBaseIT {

    @PerTestMock ExceptionsProvider exceptions;

    @Test
    void mockedProviderOnlyInThisTest() {
        when(exceptions.messageFor("E1")).thenReturn("mocked");
        meetInSameContext("exceptions", 2);

        assertThat(translator.translate("E1")).isEqualTo("[mocked]");
    }
}
