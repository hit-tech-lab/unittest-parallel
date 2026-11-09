package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.support.Rendezvous;
import com.hit.ptest.ParallelTest;
import org.junit.jupiter.api.Test;

class AltContextAIT extends AltBaseIT {

    @Test
    void altContextHasExtraBeanAndOwnDatabase() {
        Rendezvous.share("ctxcmp:keys", ParallelTest.contextKey(ctx));
        Rendezvous.share("ctxcmp:urls", ctx.getEnvironment().getProperty("spring.datasource.url"));
        Rendezvous.meet("ctxcmp", 3);
        assertThat(ParallelTest.contextName(ctx)).isEqualTo("alt");
        assertThat(ctx.containsBean("extraBean")).isTrue();
        assertThat(Rendezvous.shared("ctxcmp:keys")).hasSize(2);   // alt A và alt B chung 1 context, khác main
        assertThat(Rendezvous.shared("ctxcmp:urls")).hasSize(2);
    }
}
