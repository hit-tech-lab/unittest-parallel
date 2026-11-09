package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hit.ptest.PerTestMock;
import demo.core.NotificationService;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/** E: proxy @Async nằm dưới wrapper: REAL vẫn bất đồng bộ, MOCK thay thế hoàn toàn (đồng bộ, không qua executor). */
class AopAsyncIT extends MainIT {

    @Test
    void realIsStillAsync() throws Exception {
        String worker = notificationService.notifyAsync("aop-" + System.nanoTime()).get(10, TimeUnit.SECONDS);
        assertThat(worker).isNotEqualTo(Thread.currentThread().getName());
    }

    @Test
    void mockBypassesAsyncProxy(@PerTestMock NotificationService mock) throws Exception {
        when(mock.notifyAsync("x")).thenReturn(CompletableFuture.completedFuture("stub"));
        assertThat(notificationService.notifyAsync("x").get()).isEqualTo("stub");
    }
}
