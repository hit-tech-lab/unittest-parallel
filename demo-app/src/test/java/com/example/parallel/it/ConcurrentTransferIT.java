package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.example.parallel.account.Account;
import com.example.parallel.account.InsufficientFundsException;
import com.example.parallel.transfer.TransferService;
import com.hittech.paralleltest.mock.PerTestMocks;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Database concurrency of the APPLICATION itself: many threads transferring between the same accounts.
 * Pessimistic, ordered locking must conserve money, never go negative and never deadlock.
 */
class ConcurrentTransferIT extends AbstractIntegrationTest {

    @Autowired TransferService transferService;

    @RepeatedTest(3)
    void moneyIsConservedUnderConcurrentTransfers() throws Exception {
        when(exchangeRates.rate("USD", "USD")).thenReturn(BigDecimal.ONE); // never used: same currency
        List<Account> accts = List.of(newAccount("USD", "100"), newAccount("USD", "100"), newAccount("USD", "100"));
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                futures.add(pool.submit(PerTestMocks.wrap(() -> {
                    ThreadLocalRandom r = ThreadLocalRandom.current();
                    int from = r.nextInt(3);
                    int to = (from + 1 + r.nextInt(2)) % 3;
                    try {
                        transferService.transfer(accts.get(from).getId(), accts.get(to).getId(),
                                BigDecimal.valueOf(r.nextInt(1, 40)));
                        ok.incrementAndGet();
                    } catch (InsufficientFundsException e) {
                        rejected.incrementAndGet();
                    }
                })));
            }
            for (Future<?> f : futures) {
                f.get();
            }
        }

        BigDecimal total = accts.stream().map(this::balanceOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("300");
        assertThat(accts).allSatisfy(a -> assertThat(balanceOf(a)).isNotNegative());
        assertThat(ok.get() + rejected.get()).isEqualTo(120);
        assertThat(ok.get()).isPositive();
    }
}
