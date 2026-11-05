package com.example.parallel.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.parallel.fx.ExchangeRateClient;
import com.example.parallel.support.Rendezvous;
import java.sql.Connection;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;

/** Verifies the isolation guarantees the other tests rely on. */
class IsolationIT extends AbstractIntegrationTest {

    @Autowired DataSource dataSource;
    @Autowired ExchangeRateClient injectedClient;

    @Test
    void databaseIsPrivateToThisForkAndContext() throws Exception {
        String url;
        try (Connection c = dataSource.getConnection()) {
            url = c.getMetaData().getURL();
        }
        assertThat(url).startsWith("jdbc:h2:mem:fork" + System.getProperty("fork.number", "0") + "-");
    }

    @Test
    void serverRunsOnRandomPort() {
        assertThat(port).isPositive().isNotEqualTo(8080);
    }

    @Test
    void theRealExternalClientIsReplacedByTheRouter() {
        assertThat(Mockito.mockingDetails(injectedClient).getMockCreationSettings().getMockName().toString())
                .startsWith("perTestMockRouter");
    }

    /** Positive proof that ITs really run in parallel (times out and fails otherwise). */
    @Test
    void parallelSanityA() {
        Rendezvous.await("it-sanity", 2);
    }

    @Test
    void parallelSanityB() {
        Rendezvous.await("it-sanity", 2);
    }
}
