package demo.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import demo.core.Account;
import demo.core.AccountNotFoundException;
import demo.core.AccountRepository;
import demo.core.TransferService;
import com.hit.ptest.PerTestMock;
import com.hit.ptest.PerTestSpy;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** E: proxy @Transactional nằm DƯỚI wrapper; tiêm theo lớp cụ thể (TransferService, CGLIB) và interface (JDK proxy repository). */
class AopTransactionalIT extends MainIT {

    @Test
    void rollbackStillHappensUnderSpies(@PerTestSpy TransferService transferHandle, @PerTestSpy AccountRepository repoHandle) {
        Account from = accounts.save(new Account("tx-" + System.nanoTime(), "USD", new BigDecimal("100")));
        assertThatThrownBy(() -> transferService.transfer(from.getId(), -1L, BigDecimal.TEN))
                .isInstanceOf(AccountNotFoundException.class);
        // số dư đã bị ghi nợ + flush trong giao dịch, rồi rollback: nếu proxy @Transactional bị mất, số dư sẽ là 90
        assertThat(accounts.findById(from.getId()).orElseThrow().getBalance()).isEqualByComparingTo("100");
        verify(transferHandle).transfer(from.getId(), -1L, BigDecimal.TEN);
        verify(repoHandle).saveAndFlush(any(Account.class));
    }

    @Test
    void mockedServiceNeverTouchesDatabase(@PerTestMock TransferService transferHandle) {
        when(transferHandle.transfer(1L, 2L, BigDecimal.ONE)).thenReturn(new TransferService.Result(BigDecimal.ONE, BigDecimal.ONE));
        assertThat(transferService.transfer(1L, 2L, BigDecimal.ONE).credited()).isEqualByComparingTo("1");
    }
}
