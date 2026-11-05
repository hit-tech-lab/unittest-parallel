package com.example.parallel.demo;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.account.Account;
import com.example.parallel.account.AccountRepository;
import com.example.parallel.support.Rendezvous;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * NEGATIVE CONTROL (expected to FAIL). The classic "clean the table, insert, assert the whole
 * table" pattern: fine sequentially, broken when tests share the DB concurrently.
 * Fix: unique data per test + assert only on your own rows (see AbstractIntegrationTest).
 */
@SpringBootTest
@ActiveProfiles("test")
class SharedDatabaseConflictDemo {

    @Autowired AccountRepository accounts;

    private void cleanInsertAndAssertWholeTable(String owner) {
        accounts.deleteAll();
        accounts.save(new Account(owner, "USD", BigDecimal.ONE));
        Rendezvous.await("db-inserted", 2);
        assertThat(accounts.findAll()).extracting(Account::getOwner).containsExactly(owner);
    }

    @Test
    void alice() {
        cleanInsertAndAssertWholeTable("alice");
    }

    @Test
    void bob() {
        cleanInsertAndAssertWholeTable("bob");
    }
}
