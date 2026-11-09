package demo.matrix;

import static org.assertj.core.api.Assertions.assertThat;

import demo.support.Rendezvous;
import io.hit.ptest.ParallelTest;
import java.util.HashSet;
import java.util.Set;
import org.springframework.context.ApplicationContext;

/** Hẹn 6 lớp lá (3 base x 2) rồi kiểm: mỗi base đúng MỘT context, 3 base ra đúng 3 context khác nhau. */
final class SetsProbe {

    private SetsProbe() {
    }

    static void check(String base, ApplicationContext ctx) {
        Rendezvous.share("sets:" + base, ParallelTest.contextKey(ctx));
        Rendezvous.meet("sets", 6);
        Set<String> all = new HashSet<>();
        for (String b : new String[] {"one", "two", "three"}) {
            assertThat(Rendezvous.shared("sets:" + b)).as("base %s: both leaves share one context", b).hasSize(1);
            all.addAll(Rendezvous.shared("sets:" + b));
        }
        assertThat(all).as("three bases with different mock sets: three contexts").hasSize(3);
    }
}
