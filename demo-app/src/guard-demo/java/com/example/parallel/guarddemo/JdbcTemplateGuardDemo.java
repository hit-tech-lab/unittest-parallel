package com.example.parallel.guarddemo;

import com.example.parallel.it.AbstractJpaTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** NEGATIVE CONTROL (expected: PT12). TRUNCATE through JdbcTemplate (not a forbidden method, not Hibernate). */
class JdbcTemplateGuardDemo extends AbstractJpaTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void truncates() {
        jdbc.execute("truncate table account");
    }
}
