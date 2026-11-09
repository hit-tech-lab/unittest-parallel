package demo.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/** Chính điểm hẹn phải đáng tin: barrier vỡ không làm hỏng lần dùng sau cùng tên, parties mới được áp dụng, lỗi nêu đúng nguyên nhân. */
class RendezvousIT {

    @Test
    void timeoutIsReportedAsTimeoutAndDoesNotPoisonTheNextUseOfTheSameName() {
        assertThatThrownBy(() -> Rendezvous.meet("rv-timeout", 2, 100))
                .isInstanceOf(AssertionError.class).hasMessageContaining("TIMEOUT after 100 ms").hasMessageContaining("not all 2 members arrived");
        // cùng tên, barrier cũ đã vỡ: lần sau phải dùng barrier mới (rerun / @RepeatedTest), không bị báo sai nguyên nhân
        Rendezvous.meet("rv-timeout", 1, 1000);
    }

    @Test
    void partiesOfALaterUseAreHonouredWhenNobodyIsWaiting() {
        Rendezvous.meet("rv-parties", 1, 1000);
        assertThatThrownBy(() -> Rendezvous.meet("rv-parties", 2, 100)).hasMessageContaining("2 parties").hasMessageContaining("TIMEOUT");
    }

    @Test
    void interruptIsReportedAsInterruptNotAsTimeout() throws Exception {
        AtomicReference<Throwable> error = new AtomicReference<>();
        Thread waiter = new Thread(() -> {
            try {
                Rendezvous.meet("rv-interrupt", 2, 30_000);
            } catch (Throwable t) {
                error.set(t);
            }
        });
        waiter.start();
        while (waiter.getState() != Thread.State.WAITING && waiter.getState() != Thread.State.TIMED_WAITING) {
            Thread.sleep(5);
        }
        waiter.interrupt();
        waiter.join(10_000);
        assertThat(error.get()).isInstanceOf(AssertionError.class).hasMessageContaining("INTERRUPTED").hasMessageContaining("rv-interrupt");
    }

    @Test
    void membersOfOneBarrierDisagreeingOnPartiesIsReported() throws Exception {
        Thread waiter = new Thread(() -> {
            try {
                Rendezvous.meet("rv-mismatch", 2, 3000);
            } catch (AssertionError expected) {
                // hết giờ khi thread chính kết thúc phép thử
            }
        });
        waiter.start();
        while (waiter.getState() != Thread.State.WAITING && waiter.getState() != Thread.State.TIMED_WAITING) {
            Thread.sleep(5);
        }
        Thread.sleep(100);
        assertThatThrownBy(() -> Rendezvous.meet("rv-mismatch", 3, 100)).hasMessageContaining("in use with 2 parties").hasMessageContaining("asks for 3");
        waiter.interrupt();
        waiter.join(10_000);
    }
}
