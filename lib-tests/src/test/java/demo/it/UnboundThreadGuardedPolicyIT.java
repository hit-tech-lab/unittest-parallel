package demo.it;

import static org.assertj.core.api.Assertions.assertThat;

import demo.core.DemoApp;
import demo.core.ExchangeRateClient;
import demo.core.UntouchedService;
import io.hit.ptest.PerTestMock;
import io.hit.ptest.ParallelSpringTest;
import io.hit.ptest.ParallelTestException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Chính sách GUARDED là đơn điệu theo (context, bean): thread không binding gọi bean THẬT cho đến khi BẤT KỲ test nào
 * từng khai báo MOCK/SPY cho bean, từ đó luôn lỗi, kể cả khi test đó đã kết thúc. Chạy tuần tự trong context riêng để
 * kiểm tra ĐÚNG nhánh phụ thuộc thời điểm (trước / trong / sau khi bean bị can thiệp).
 */
@SpringBootTest(classes = DemoApp.class, webEnvironment = SpringBootTest.WebEnvironment.NONE, properties = "ptest.unbound-policy=guarded")
@ParallelSpringTest(context = "policy-guarded")
@Execution(ExecutionMode.SAME_THREAD)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class UnboundThreadGuardedPolicyIT {

    @Autowired @Qualifier("primaryFx") ExchangeRateClient primaryFx;
    @Autowired UntouchedService untouched;

    private boolean unboundCallFails() throws Exception {
        try {
            CompletableFuture.supplyAsync(() -> primaryFx.rate("USD", "EUR")).get();
            return false;
        } catch (ExecutionException e) {
            assertThat(e).hasRootCauseInstanceOf(ParallelTestException.UnboundThread.class);
            return true;
        }
    }

    @Test
    @Order(1)
    void beforeAnyoneInterceptsTheBeanUnboundCallsAreReal() throws Exception {
        assertThat(unboundCallFails()).isFalse();
    }

    @Test
    @Order(2)
    void whileATestInterceptsTheBeanUnboundCallsFail(@PerTestMock(name = "primaryFx") ExchangeRateClient mock) throws Exception {
        assertThat(unboundCallFails()).isTrue();
    }

    @Test
    @Order(3)
    void afterThatTestEndedUnboundCallsStillFailMonotonic() throws Exception {
        assertThat(unboundCallFails()).as("no flip back to REAL").isTrue();
    }

    @Test
    @Order(4)
    void controlBeanNobodyEverInterceptedStaysRealForUnboundThreads() throws Exception {
        assertThat(CompletableFuture.supplyAsync(() -> untouched.hello()).get()).isEqualTo("hello");
    }
}
