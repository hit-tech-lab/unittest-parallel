package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.account.Account;
import com.example.parallel.account.InsufficientFundsException;
import com.example.parallel.transfer.TransferService;
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
 * Database conflicts in the APPLICATION itself (not between tests): many threads transferring
 * between the same accounts at once. Pessimistic locks + ordered locking must keep money
 * conserved, never go negative, and never deadlock. Runs in parallel with the other ITs.
 */
class ConcurrentTransferIT extends AbstractIntegrationTest {

    @Autowired TransferService transferService;

    @RepeatedTest(3)
    void moneyIsConservedUnderConcurrentTransfers() throws Exception {
        List<Account> accts = List.of(newAccount("USD", "100"), newAccount("USD", "100"), newAccount("USD", "100"));
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger rejected = new AtomicInteger();

        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 120; i++) {
                futures.add(pool.submit(() -> {
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
                }));
            }
            for (Future<?> f : futures) {
                f.get(); // propagates deadlocks / lock timeouts / unexpected errors
            }
        }

        BigDecimal total = accts.stream().map(this::balanceOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(total).isEqualByComparingTo("300");
        assertThat(accts).allSatisfy(a -> assertThat(balanceOf(a)).isNotNegative());
        assertThat(ok.get() + rejected.get()).isEqualTo(120);
        assertThat(ok.get()).isPositive();
    }
}
