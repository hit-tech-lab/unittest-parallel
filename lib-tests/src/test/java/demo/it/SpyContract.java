package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import demo.core.Account;
import demo.core.AccountNotFoundException;
import demo.core.AccountRepository;
import demo.core.AuditLog;
import demo.core.FinalPricer;
import demo.core.NotificationService;
import demo.core.RateCache;
import demo.core.TransferService;
import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

/**
 * Hợp đồng ngữ nghĩa của SPY, viết MỘT LẦN: {@code SpyContractPtestIT} chạy nó với {@code @PerTestSpy} của thư viện, các test chạy song song.
 * Ở nhánh Spring Boot 4 hợp đồng này còn chạy trên {@code @MockitoSpyBean} chuẩn (đối chứng); {@code @MockitoSpyBean} chỉ có từ
 * Spring Framework 6.2 nên nhánh Spring Boot 3.3 (Spring 6.1) không có bản đối chứng đó.
 * Mỗi test dùng dữ liệu riêng (tài khoản mới, khóa cache mới) vì bean thật và DB là singleton dùng chung.
 */
interface SpyContract {

    TransferService service();

    TransferService serviceSpy();

    AccountRepository repo();

    AccountRepository repoSpy();

    FinalPricer pricer();

    FinalPricer pricerSpy();

    NotificationService notifications();

    NotificationService notificationsSpy();

    RateCache cache();

    RateCache cacheSpy();

    AuditLog auditLog();

    private Account newAccount(String currency, String balance) {
        return repo().save(new Account("spy-" + UUID.randomUUID(), currency, new BigDecimal(balance)));
    }

    private BigDecimal balanceOf(Account a) {
        return repo().findById(a.getId()).orElseThrow().getBalance();
    }

    // 1: lời gọi nội bộ this.rate() trong transfer() đi qua spy

    @Test
    default void internalCallGoesThroughSpy() {
        Account from = newAccount("USD", "100");
        Account to = newAccount("EUR", "0");
        doReturn(new BigDecimal("2")).when(serviceSpy()).rate("USD", "EUR");

        TransferService.Result result = service().transfer(from.getId(), to.getId(), BigDecimal.TEN);

        assertThat(result.credited()).as("stub of the INTERNAL call applied").isEqualByComparingTo("20");
        verify(serviceSpy()).rate("USD", "EUR");
        verify(serviceSpy()).transfer(from.getId(), to.getId(), BigDecimal.TEN);
    }

    @Test
    default void doThrowOnInternalCallPropagatesAndTransactionRollsBack() {
        Account from = newAccount("USD", "100");
        Account to = newAccount("EUR", "0");
        doThrow(new IllegalStateException("rate down")).when(serviceSpy()).rate(any(), any());

        assertThatThrownBy(() -> service().transfer(from.getId(), to.getId(), BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class).hasMessage("rate down");

        // số dư đã bị ghi nợ + flush trước khi rate() ném lỗi: proxy @Transactional (bọc ngoài spy) phải rollback
        assertThat(balanceOf(from)).isEqualByComparingTo("100");
    }

    // 2: verify / doReturn / doAnswer / ArgumentCaptor / verifyNoMoreInteractions / InOrder

    @Test
    default void doAnswerCaptorAndNoMoreInteractions() {
        doAnswer(inv -> new BigDecimal(inv.<String>getArgument(1).length())).when(serviceSpy()).rate(anyString(), anyString());

        assertThat(service().rate("USD", "EUR")).isEqualByComparingTo("3");

        ArgumentCaptor<String> from = ArgumentCaptor.forClass(String.class);
        verify(serviceSpy()).rate(from.capture(), eq("EUR"));
        assertThat(from.getValue()).isEqualTo("USD");
        verifyNoMoreInteractions(serviceSpy());
    }

    @Test
    default void inOrderSeesOuterThenInternalCall() {
        Account from = newAccount("USD", "100");
        Account to = newAccount("EUR", "0");

        service().transfer(from.getId(), to.getId(), BigDecimal.TEN);   // rate() thật: 1.10

        InOrder order = inOrder(serviceSpy());
        order.verify(serviceSpy()).transfer(from.getId(), to.getId(), BigDecimal.TEN);
        order.verify(serviceSpy()).rate("USD", "EUR");
        verifyNoMoreInteractions(serviceSpy());
        assertThat(balanceOf(to)).isEqualByComparingTo("11.0000");   // thật: spy gọi method thật
    }

    // 3: advice AOP vẫn bao quanh spy

    @Test
    default void transactionalAdviceStillWrapsSpy() {
        Account from = newAccount("USD", "100");
        assertThatThrownBy(() -> service().transfer(from.getId(), -1L, BigDecimal.TEN)).isInstanceOf(AccountNotFoundException.class);
        assertThat(balanceOf(from)).as("rolled back by the @Transactional proxy around the spy").isEqualByComparingTo("100");
        verify(serviceSpy()).transfer(from.getId(), -1L, BigDecimal.TEN);
    }

    @Test
    default void asyncAdviceStillWrapsSpyAndInternalCallReachesIt() throws Exception {
        String tag = "async-" + UUID.randomUUID();
        doReturn("DEC-" + tag).when(notificationsSpy()).decorate(tag);

        String worker = notifications().notifyDecorated(tag).get(10, TimeUnit.SECONDS);

        assertThat(worker).as("@Async proxy still moves the call to an executor thread").isNotEqualTo(Thread.currentThread().getName());
        assertThat(auditLog().events()).contains(new AuditLog.Event("NOTIFY", "DEC-" + tag));
        verify(notificationsSpy()).notifyDecorated(tag);
        verify(notificationsSpy()).decorate(tag);   // gọi nội bộ trên thread executor vẫn tới spy của test này
    }

    @Test
    default void cacheableAdviceStillWrapsSpyAndInternalCallsBypassIt() {
        String to = "T" + UUID.randomUUID();
        assertThat(cache().cachedRate("USD", to)).isEqualByComparingTo("1.10");
        assertThat(cache().cachedRate("USD", to)).isEqualByComparingTo("1.10");
        verify(cacheSpy(), times(1)).cachedRate("USD", to);   // lần hai do proxy cache (ngoài spy) trả lời
        assertThat(cache().computations("USD", to)).isEqualTo(1);

        String internal = "I" + UUID.randomUUID();
        assertThat(cache().cachedRateTwice("USD", internal)).isEqualByComparingTo("2.20");
        verify(cacheSpy(), times(2)).cachedRate("USD", internal);   // this.cachedRate x2: qua spy, không qua cache
        assertThat(cache().computations("USD", internal)).as("internal calls bypass the cache proxy").isEqualTo(2);
    }

    // 4: lớp final và JDK proxy

    @Test
    default void finalClassSpySeesInternalCall() {
        doReturn(BigDecimal.ONE).when(pricerSpy()).price(BigDecimal.TEN);
        assertThat(pricer().priceWithFee(BigDecimal.TEN)).isEqualByComparingTo("2");
        verify(pricerSpy()).price(BigDecimal.TEN);
    }

    @Test
    default void jdkProxyRepositorySpyStubsAndVerifies() {
        Account from = newAccount("USD", "100");
        Account to = newAccount("EUR", "0");
        doThrow(new IllegalStateException("flush failed")).when(repoSpy()).saveAndFlush(any(Account.class));

        assertThatThrownBy(() -> service().transfer(from.getId(), to.getId(), BigDecimal.TEN))
                .isInstanceOf(IllegalStateException.class).hasMessage("flush failed");

        verify(repoSpy()).findById(from.getId());
        assertThat(repo().findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100");
    }
}
