package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.hit.ptest.PerTestSpy;
import demo.core.Account;
import demo.core.TransferService;
import demo.support.Rendezvous;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** 5: hai test chạy cùng lúc trên CÙNG bean singleton, mỗi test có spy, stub và lịch sử gọi riêng (kể cả gọi nội bộ). */
class SpyIsolationIT extends MainIT {

    @PerTestSpy TransferService spy;

    private void transferWith(String otherCurrency, String rate, String expectedCredit) {
        Account from = accounts.save(new Account("iso-" + UUID.randomUUID(), "USD", new BigDecimal("100")));
        Account to = accounts.save(new Account("iso-" + UUID.randomUUID(), otherCurrency, BigDecimal.ZERO));
        doReturn(new BigDecimal(rate)).when(spy).rate("USD", otherCurrency);
        meetInSameContext("spy-isolation", 2);    // cả hai đã stub xong trước khi bất kỳ test nào gọi

        assertThat(transferService.transfer(from.getId(), to.getId(), BigDecimal.TEN).credited()).isEqualByComparingTo(expectedCredit);

        verify(spy, times(1)).transfer(from.getId(), to.getId(), BigDecimal.TEN);
        verify(spy, times(1)).rate("USD", otherCurrency);
        verify(spy, never()).rate("USD", otherCurrency.equals("EUR") ? "GBP" : "EUR");   // không thấy lời gọi của test kia
        Rendezvous.meet("spy-isolation-done", 2);
    }

    @Test
    void testOneSeesOnlyItsOwnStubAndCalls() {
        transferWith("EUR", "2", "20");
    }

    @Test
    void testTwoSeesOnlyItsOwnStubAndCalls() {
        transferWith("GBP", "3", "30");
    }
}
