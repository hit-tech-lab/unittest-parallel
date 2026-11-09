package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hit.ptest.PerTestMock;
import io.hit.ptest.PerTestSpy;
import org.springframework.beans.factory.annotation.Qualifier;
import demo.core.Account;
import demo.core.ExchangeRateClient;
import demo.core.TransferService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Hai bean cùng kiểu ExchangeRateClient, hai chế độ khác nhau trong cùng một test. */
class ModeTwoSameTypeIT extends MainIT {

    @PerTestMock @Qualifier("primaryFx") ExchangeRateClient primaryMock;
    @PerTestSpy @Qualifier("backupFx") ExchangeRateClient backupSpy;

    @Test
    void primaryFailsAndBackupIsUsed() {
        when(primaryMock.rate("USD", "EUR")).thenThrow(new IllegalStateException("primary down"));
        meetInSameContext("mix", 6);
        Account from = accounts.save(new Account("two-" + System.nanoTime(), "USD", new BigDecimal("100")));
        Account to = accounts.save(new Account("two-" + System.nanoTime(), "EUR", BigDecimal.ZERO));

        TransferService.Result r = transferService.transfer(from.getId(), to.getId(), new BigDecimal("10"));

        assertThat(r.credited()).isEqualByComparingTo("12.0000");   // 10 * 1.20 của backup thật
        verify(primaryMock).rate("USD", "EUR");
        verify(backupSpy).rate("USD", "EUR");
    }
}
