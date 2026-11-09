package demo.it;

import demo.support.SharedStateBase;
import org.junit.jupiter.api.Test;

class SharedStateRealBIT extends MainIT_Shared_Public {
    @Test
    void realSingletonStateIsShared() {
        scenario();
    }

    @Override protected String group() { return "state-real"; }
    @Override protected boolean expectsExclusiveState() { return false; }
}
