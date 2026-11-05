package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.support.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;

/**
 * JPA slice. Each test runs in its own transaction (bound to the test's thread) that is rolled back,
 * so methods running concurrently do not see each other's uncommitted rows.
 * BUT a rollback does not protect UNIQUE constraints of concurrent transactions -> owners are still
 * unique per test (TransactionalRollbackConflictDemo shows the failure with a fixed owner).
 */
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE) // use our per-context URL
class AccountRepositoryIT {

    @Autowired AccountRepository repository;
    @Autowired TestEntityManager em;

    @RepeatedTest(10)
    void findByOwner() {
        String owner = TestData.uniqueOwner("repo");
        repository.save(new Account(owner, "USD", BigDecimal.ONE));
        em.flush();
        em.clear();

        assertThat(repository.findByOwner(owner)).get()
                .extracting(Account::getCurrency).isEqualTo("USD");
    }

    @Test
    void versionIsIncrementedOnUpdate() {
        Account a = repository.saveAndFlush(new Account(TestData.uniqueOwner("repo"), "USD", BigDecimal.TEN));
        long v0 = a.getVersion();
        a.credit(BigDecimal.ONE);
        repository.saveAndFlush(a);

        assertThat(repository.findForUpdate(a.getId())).get().extracting(Account::getVersion).isEqualTo(v0 + 1);
    }
}
