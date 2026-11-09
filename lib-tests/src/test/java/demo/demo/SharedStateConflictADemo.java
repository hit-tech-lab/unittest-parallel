package demo.demo;

import demo.it.MainIT_Shared_Public;
import org.junit.jupiter.api.Test;

/** Negative control E: hai test cùng expect "AuditLog của riêng mình" trên bean REAL dùng chung => phải FAIL. */
class SharedStateConflictADemo extends MainIT_Shared_Public {
    @Test
    void expectsExclusiveStateOnSharedRealBean() {
        scenario();
    }

    @Override protected String group() { return "state-demo"; }
    @Override protected boolean expectsExclusiveState() { return true; }
}
