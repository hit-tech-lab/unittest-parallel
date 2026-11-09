package demo.support;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.AuditLog;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Autowired;

/** E: state singleton của bean THẬT dùng chung giữa các test cùng context (giới hạn đã biết của chế độ REAL). */
public abstract class SharedStateBase {

    @Autowired protected AuditLog auditLog;

    protected abstract String group();

    protected abstract boolean expectsExclusiveState();

    protected void scenario() {
        String tag = getClass().getSimpleName() + "-" + UUID.randomUUID();
        auditLog.record(new AuditLog.Event("SHARED", tag));
        Rendezvous.share(group() + ":tags", tag);
        Rendezvous.meet(group(), 2);
        var details = auditLog.events().stream().map(AuditLog.Event::detail).toList();
        for (String other : Rendezvous.shared(group() + ":tags")) {
            if (other.equals(tag)) {
                continue;
            }
            if (expectsExclusiveState()) {
                assertThat(details).as("expected my own AuditLog, but it has the other test's event").doesNotContain(other);
            } else {
                assertThat(details).as("REAL singleton state is shared").contains(other);
            }
        }
    }
}
