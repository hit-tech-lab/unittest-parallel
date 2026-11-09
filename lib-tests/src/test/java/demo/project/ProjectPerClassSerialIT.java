package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

/**
 * PER_CLASS khi các method của lớp KHÔNG chạy đồng thời: một instance chung nhưng mỗi method nhận mock mới (không có stub/tương tác
 * của method trước), bất kể thứ tự chạy. Lớp khác vẫn chạy song song với lớp này trên cùng context.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@Execution(ExecutionMode.SAME_THREAD)
class ProjectPerClassSerialIT extends ProjectBaseIT {

    @PerTestMock Ports.Mailer mailer;

    @Test
    void firstStubStaysInThisMethod() {
        assertThat(mockingDetails(mailer).getInvocations()).isEmpty();
        when(mailer.send("x")).thenReturn("first");

        assertThat(mailer.send("x")).isEqualTo("first");
    }

    @Test
    void secondStubDoesNotSeeTheFirst() {
        assertThat(mockingDetails(mailer).getInvocations()).isEmpty();
        when(mailer.send("x")).thenReturn("second");

        assertThat(mailer.send("x")).isEqualTo("second");
    }
}
