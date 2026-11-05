package com.example.parallel.guarddemo;

import com.hittech.paralleltest.annotation.IsolatedContext;
import org.junit.jupiter.api.Test;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** NEGATIVE CONTROL (expected: PT11). 2 connections for 8 parallel transactional tests. */
@IsolatedContext(reason = "negative control for the pool size check")
@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@TestPropertySource(properties = "spring.datasource.hikari.maximum-pool-size=2")
class PoolTooSmallGuardDemo {

    @Test
    void anything() {
    }
}
