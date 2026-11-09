package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.support.Rendezvous;
import io.hit.ptest.ParallelTest;
import org.junit.jupiter.api.Test;

/** A: không khai báo = REAL, không ném lỗi. */
class ModeRealIT extends MainIT {

    @Test
    void undeclaredBeansAreReal() {
        meetInSameContext("mix", 6);
        assertThat(primaryFx.rate("USD", "EUR")).isEqualByComparingTo("1.10");
        assertThat(backupFx.rate("USD", "EUR")).isEqualByComparingTo("1.20");
        assertThat(ctx.containsBean("extraBean")).isFalse();
    }

    /** B: context "main" và "alt" là hai context khác nhau với DB khác nhau. */
    @Test
    void mainContextIsDistinctFromAlt() {
        Rendezvous.share("ctxcmp:keys", ParallelTest.contextKey(ctx));
        Rendezvous.share("ctxcmp:urls", ctx.getEnvironment().getProperty("spring.datasource.url"));
        Rendezvous.meet("ctxcmp", 3);
        assertThat(Rendezvous.shared("ctxcmp:keys")).hasSize(2);
        assertThat(Rendezvous.shared("ctxcmp:urls")).as("each context has its own DB").hasSize(2);
    }
}
