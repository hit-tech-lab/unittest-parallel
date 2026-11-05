package com.example.parallel.demo;

import com.example.parallel.account.AccountRepository;
import com.example.parallel.support.Rendezvous;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * NEGATIVE CONTROL (expected to FAIL). @DirtiesContext closes the cached context as soon as A
 * finishes -- while B, running concurrently in the same context, still uses its beans.
 * Fix: avoid @DirtiesContext in parallel suites (or isolate that class: own context + SAME_THREAD + lock).
 * The extra property gives this demo its own context so it cannot break the other demos.
 */
@SpringBootTest(properties = "demo.context=dirties")
@ActiveProfiles("test")
class DirtiesContextConflictDemo {

    @Autowired AccountRepository accounts;
    @Autowired ConfigurableApplicationContext context;

    @Test
    @DirtiesContext
    void a_dirtiesTheContext() {
        Rendezvous.await("dirties-started", 2);
    }

    @Test
    void b_keepsUsingTheContext() throws InterruptedException {
        Rendezvous.await("dirties-started", 2);
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        while (context.isActive() && Instant.now().isBefore(deadline)) {
            Thread.sleep(20);
        }
        accounts.count(); // boom: EntityManagerFactory closed underneath us
    }
}
