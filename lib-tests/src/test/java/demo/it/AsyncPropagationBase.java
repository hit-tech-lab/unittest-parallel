package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** D: @Async chạy trên thread của executor Spring; binding của test phải đi theo. */
abstract class AsyncPropagationBase extends MainIT {

    protected String callAsync(String kind) throws Exception {
        String tag = getClass().getSimpleName() + "-" + UUID.randomUUID();
        meetInSameContext("async", 3);
        String worker = notificationService.notifyAsync(tag).get(10, TimeUnit.SECONDS);
        assertThat(worker).as("ran on an executor thread").isNotEqualTo(Thread.currentThread().getName());
        demo.support.Rendezvous.share("async:tags:" + kind, tag);
        return tag;
    }
}
