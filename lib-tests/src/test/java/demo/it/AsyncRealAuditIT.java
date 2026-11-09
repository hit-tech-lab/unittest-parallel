package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.AuditLog;
import demo.support.Rendezvous;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** auditLog REAL: nhận sự kiện của mình và của test SPY (spy ghi xuống bean thật), KHÔNG nhận sự kiện của test MOCK. */
class AsyncRealAuditIT extends AsyncPropagationBase {

    @Test
    void realLogSeesOnlyOwnEvents() throws Exception {
        String tag = callAsync("real");
        Rendezvous.meet("async-done", 3);
        TimeUnit.MILLISECONDS.sleep(300);   // chờ các task async của hai test kia chạy hết
        var details = auditLog.events().stream().map(AuditLog.Event::detail).toList();
        assertThat(details).contains(tag);
        String mockTag = Rendezvous.shared("async:tags:mock").iterator().next();
        String spyTag = Rendezvous.shared("async:tags:spy").iterator().next();
        assertThat(details).as("MOCK swallowed the event: it must not reach the real log").doesNotContain(mockTag);
        assertThat(details).as("SPY delegates to the real bean").contains(spyTag);
    }
}
