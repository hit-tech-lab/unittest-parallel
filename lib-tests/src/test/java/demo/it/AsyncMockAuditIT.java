package demo.it;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.hit.ptest.PerTestMock;
import demo.core.AuditLog;
import demo.support.Rendezvous;
import org.junit.jupiter.api.Test;

class AsyncMockAuditIT extends AsyncPropagationBase {

    @PerTestMock AuditLog mock;

    @Test
    void asyncThreadWritesToThisTestsMock() throws Exception {
        String tag = callAsync("mock");
        verify(mock, timeout(5000)).record(argThat(e -> e.detail().equals(tag)));
        Rendezvous.meet("async-done", 3);
    }
}
