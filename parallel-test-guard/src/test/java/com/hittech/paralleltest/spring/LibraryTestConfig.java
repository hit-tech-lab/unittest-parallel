package com.hittech.paralleltest.spring;

import com.hittech.paralleltest.fixtures.Fixtures.Collaborator;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;

/** Minimal plain-Spring context (no Boot) used by the library's own end-to-end tests. */
@Configuration(proxyBeanMethods = false)
public class LibraryTestConfig {

    public static class RealCollaborator implements Collaborator {
        @Override
        public String hello() {
            return "REAL";
        }
    }

    public record GreetingService(Collaborator collaborator) {
        public String greet() {
            return "greeting:" + collaborator.hello();
        }
    }

    /** Runs a whole-table DELETE while the context starts: must NOT be blocked (seed/reset scripts, migrations). */
    public static class StartupReset {
        public final boolean ran;

        public StartupReset(JdbcTemplate jdbc) {
            jdbc.execute("create table if not exists item (id int primary key, name varchar(50))");
            jdbc.update("delete from item");
            ran = true;
        }
    }

    @Bean
    DataSource dataSource() {
        return new EmbeddedDatabaseBuilder().setType(EmbeddedDatabaseType.H2).generateUniqueName(true).build();
    }

    @Bean
    JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    @Bean
    StartupReset startupReset(JdbcTemplate jdbc) {
        return new StartupReset(jdbc);
    }

    @Bean
    RealCollaborator realCollaborator() {
        return new RealCollaborator();
    }

    @Bean
    GreetingService greetingService(Collaborator collaborator) {
        return new GreetingService(collaborator);
    }
}
