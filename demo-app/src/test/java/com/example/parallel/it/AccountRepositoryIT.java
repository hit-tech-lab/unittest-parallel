package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.hittech.paralleltest.data.TestData;
import java.math.BigDecimal;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;

/**
 * Rolled-back transaction per test (thread-bound) -> concurrent methods do not see each other's rows.
 * Unique keys are still required: concurrent transactions collide on UNIQUE constraints even if both roll back.
 */
class AccountRepositoryIT extends AbstractJpaTest {

    @Autowired AccountRepository repository;
    @Autowired TestEntityManager em;

    @RepeatedTest(10)
    void findByOwner() {
        String owner = TestData.unique("repo");
        repository.save(new Account(owner, "USD", BigDecimal.ONE));
        em.flush();
        em.clear();

        assertThat(repository.findByOwner(owner)).get().extracting(Account::getCurrency).isEqualTo("USD");
    }

    @Test
    void versionIsIncrementedOnUpdate() {
        Account a = repository.saveAndFlush(new Account(TestData.unique("repo"), "USD", BigDecimal.TEN));
        long v0 = a.getVersion();
        a.credit(BigDecimal.ONE);
        repository.saveAndFlush(a);

        assertThat(repository.findForUpdate(a.getId())).get().extracting(Account::getVersion).isEqualTo(v0 + 1);
    }
}
