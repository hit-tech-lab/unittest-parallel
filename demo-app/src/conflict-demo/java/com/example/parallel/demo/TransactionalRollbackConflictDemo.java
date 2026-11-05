package com.example.parallel.demo;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.support.Rendezvous;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * NEGATIVE CONTROL (expected to FAIL). "Every test is @Transactional and rolled back, so tests are
 * isolated" -- not for UNIQUE constraints: B's insert of the same key blocks on A's uncommitted row
 * and fails with a lock timeout (or duplicate key), although A will roll back.
 * Fix: unique keys per test even in rolled-back tests (AccountRepositoryIT).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:demo-tx-${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=500")
class TransactionalRollbackConflictDemo {

    @Autowired AccountRepository accounts;

    @Test
    void a_insertsAliceAndHoldsTransaction() throws InterruptedException {
        accounts.saveAndFlush(new Account("alice", "USD", BigDecimal.ONE));
        Rendezvous.await("tx-alice-inserted", 2);
        Thread.sleep(2_000); // transaction still open, rolled back afterwards
    }

    @Test
    void b_insertsAliceToo() {
        Rendezvous.await("tx-alice-inserted", 2);
        accounts.saveAndFlush(new Account("alice", "USD", BigDecimal.ONE));
    }
}
