package com.hittech.paralleltest.spring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNoException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hittech.paralleltest.guard.ParallelGuardViolation;
import java.sql.Connection;
import java.sql.Statement;
import java.util.concurrent.ThreadLocalRandom;
import javax.sql.DataSource;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

/** End-to-end in a real Spring context: routing, and the JDBC-level SQL guard on every access path. */
class LibrarySpringTest extends AbstractLibrarySpringTest {

    @Autowired DataSource dataSource;
    @Autowired LibraryTestConfig.StartupReset startupReset;

    @RepeatedTest(20)
    void routerSendsEachTestToItsOwnMock() {
        String mine = "mock-" + ThreadLocalRandom.current().nextLong();
        when(collaborator.hello()).thenReturn(mine);

        assertThat(greetingService.greet()).isEqualTo("greeting:" + mine);
        verify(collaborator).hello();
    }

    @Test
    void wholeTableDeleteAtStartupIsAllowed() {
        assertThat(startupReset.ran).isTrue();
    }

    @Test
    void jdbcTemplateWholeTableDeleteIsRejected() {
        assertThatThrownBy(() -> jdbc.update("delete from item"))
                .isInstanceOf(ParallelGuardViolation.class).hasMessageContaining("[PT12]");
    }

    @Test
    void targetedStatementsPass() {
        int id = ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);
        assertThatNoException().isThrownBy(() -> {
            jdbc.update("insert into item (id, name) values (?, ?)", id, "x");
            jdbc.update("delete from item where id = ?", id);
        });
    }

    @Test
    void plainJdbcStatementsAreRejected() throws Exception {
        try (Connection c = dataSource.getConnection(); Statement st = c.createStatement()) {
            assertThatThrownBy(() -> st.execute("truncate table item")).isInstanceOf(ParallelGuardViolation.class);
            assertThatThrownBy(() -> st.addBatch("delete from item")).isInstanceOf(ParallelGuardViolation.class);
            assertThatThrownBy(() -> c.prepareStatement("DELETE FROM item")).isInstanceOf(ParallelGuardViolation.class);
            assertThat(st.getConnection()).isSameAs(c);
            assertThat(c.isWrapperFor(Connection.class)).isTrue();
        }
    }

    /** The path used by @Sql scripts and ResourceDatabasePopulator. */
    @Test
    void sqlScriptsAreRejected() throws Exception {
        try (Connection c = dataSource.getConnection()) {
            assertThatThrownBy(() -> ScriptUtils.executeSqlScript(c, new ByteArrayResource("delete from item;".getBytes())))
                    .isInstanceOf(ParallelGuardViolation.class);
        }
    }
}
