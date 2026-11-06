package com.hittech.paralleltest.spring;

import com.hittech.paralleltest.annotation.SharedContextBase;
import com.hittech.paralleltest.fixtures.Fixtures.Collaborator;
import com.hittech.paralleltest.mock.PerTestMock;
import com.hittech.paralleltest.mock.RoutedMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/** The library dogfoods its own rules: its Spring tests share this base and context. */
@SharedContextBase
@SpringJUnitConfig(LibraryTestConfig.class)
@RoutedMocks(Collaborator.class)
public abstract class AbstractLibrarySpringTest {

    @PerTestMock protected Collaborator collaborator;
    @Autowired protected JdbcTemplate jdbc;
    @Autowired protected LibraryTestConfig.GreetingService greetingService;
}
