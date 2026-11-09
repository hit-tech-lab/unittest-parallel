package demo.it;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import io.hit.ptest.PerTestSpy;
import demo.core.AuditLog;
import demo.support.Rendezvous;
import org.junit.jupiter.api.Test;

class AsyncSpyAuditIT extends AsyncPropagationBase {

    @PerTestSpy AuditLog spy;

    @Test
    void asyncThreadWritesThroughThisTestsSpy() throws Exception {
        String tag = callAsync("spy");
        verify(spy, timeout(5000)).record(argThat(e -> e.detail().equals(tag)));
        Rendezvous.meet("async-done", 3);
    }
}
