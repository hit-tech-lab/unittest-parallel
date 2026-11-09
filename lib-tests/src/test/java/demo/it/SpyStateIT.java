package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import io.hit.ptest.PerTestSpy;
import demo.core.StatefulCounter;
import demo.support.Rendezvous;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 5: state của spy. Spy chuẩn ({@code Mockito.spy}, {@code @MockitoSpyBean}) SAO CHÉP field lúc tạo: field nguyên thủy tách
 * khỏi bản gốc, field tham chiếu (AtomicInteger, collection) trỏ cùng đối tượng. Spy của thư viện (tạo cho từng test từ
 * bản thật) có đúng hành vi đó; {@link #mockitoSpyReference()} là đối chứng bằng chính {@code Mockito.spy}.
 */
class SpyStateIT extends MainIT {

    @Test
    void mockitoSpyReference() {
        StatefulCounter raw = new StatefulCounter();
        StatefulCounter spy = Mockito.spy(raw);
        spy.incrementPlain();
        spy.incrementPlain();
        spy.incrementAtomic();
        spy.note("x");
        assertThat(raw.plain()).as("primitive field diverges").isZero();
        assertThat(spy.plain()).isEqualTo(2);
        assertThat(raw.atomic()).as("reference field shared").isEqualTo(1);
        assertThat(raw.log()).as("collection shared").containsExactly("x");
    }

    @Test
    void spyOfThisTestHasOwnPrimitiveStateAndSharesReferenceState(@PerTestSpy StatefulCounter spy) {
        Rendezvous.meet("state-start", 2);
        spy.incrementPlain();
        spy.incrementPlain();
        spy.incrementPlain();
        spy.incrementAtomic();
        spy.incrementAtomic();
        spy.incrementAtomic();
        spy.note("from-spy");
        Rendezvous.meet("state-mutated", 2);
        assertThat(counter.plain()).as("the bean in the context routes to this test's spy").isEqualTo(3);
        Rendezvous.meet("state-done", 2);
    }

    @Autowired StatefulCounter counter;

    @Test
    void realBeanKeepsItsOwnPrimitiveStateButSeesSharedReferences() {
        Rendezvous.meet("state-start", 2);
        counter.incrementPlain();
        Rendezvous.meet("state-mutated", 2);
        assertThat(counter.plain()).as("spy's increments did not touch the real bean's primitive").isEqualTo(1);
        assertThat(counter.atomic()).as("shared AtomicInteger saw the spy's increments").isEqualTo(3);
        assertThat(counter.log()).contains("from-spy");
        Rendezvous.meet("state-done", 2);
    }
}
