package io.hit.ptest.context;

import io.hit.ptest.ParallelTestException;
import io.hit.ptest.db.PostgresProvisioner;

import io.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Nguồn DB của thư viện: ai sở hữu url thì sở hữu cả bộ kết nối; sau khi đóng không cấp thêm; lỗi khởi động Postgres cache. */
class DbPropertySourceTest {

    private static final String[] NONE = {};

    @AfterEach
    void cleanup() {
        PostgresProvisioner.resetForTest();
        Fixtures.closeAll();
    }

    @Test
    void userUrlOnlyMeansNoExtraDbAndNoLibraryUsernamePasswordOrPool() {
        int dbsBefore = ContextRegistry.INSTANCE.snapshot().get("databases").length();
        AnnotationConfigApplicationContext ctx = Fixtures.started("user-url-only", NONE,
                Map.of("ptest.db.mode", "h2", "spring.datasource.url", "jdbc:h2:mem:mine"), Fixtures.AskForCredentialsConfig.class);

        assertThat(ctx.getBean("credentials", String.class)).as("password asked BEFORE url: still not ours").isEqualTo("null|null|null|jdbc:h2:mem:mine");
        assertThat(Fixtures.managed(ctx).hasDb()).isFalse();
        assertThat(ContextRegistry.INSTANCE.snapshot().get("databases").length()).isEqualTo(dbsBefore);
    }

    @Test
    void urlFromADynamicPropertySourceAddedLaterIsAlsoRespected() {
        AnnotationConfigApplicationContext ctx = Fixtures.context("dynamic-url", NONE, NONE, Map.of("ptest.db.mode", "h2"),
                Fixtures.AskForCredentialsConfig.class);
        // như @DynamicPropertySource: thêm nguồn ưu tiên cao sau customizer của thư viện
        ctx.getEnvironment().getPropertySources().addFirst(new MapPropertySource("dyn", Map.of("spring.datasource.url", "jdbc:h2:mem:dyn")));
        ctx.refresh();

        assertThat(ctx.getBean("credentials", String.class)).isEqualTo("null|null|null|jdbc:h2:mem:dyn");
        assertThat(Fixtures.managed(ctx).hasDb()).isFalse();
    }

    @Test
    void controlWithoutUserUrlTheLibraryOwnsAllConnectionKeysWhicheverIsAskedFirst() {
        AnnotationConfigApplicationContext ctx = Fixtures.started("lib-owns", NONE, Map.of("ptest.db.mode", "h2"),
                Fixtures.AskForCredentialsConfig.class);

        String[] parts = ctx.getBean("credentials", String.class).split("\\|");
        assertThat(parts[0]).isEqualTo("sa");
        assertThat(parts[2]).isEqualTo("4");
        assertThat(parts[3]).startsWith("jdbc:h2:mem:ptest_f");
        assertThat(Fixtures.managed(ctx).hasDb()).isTrue();
    }

    @Test
    void afterTheContextIsClosedAskingAgainProvisionsNothing() {
        AnnotationConfigApplicationContext ctx = Fixtures.started("closed-asks", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);
        ctx.close();
        String before = ContextRegistry.INSTANCE.snapshot().get("databases");

        assertThat(ctx.getEnvironment().getProperty("spring.datasource.url")).isNull();
        assertThat(ctx.getEnvironment().getProperty("spring.datasource.username")).isNull();
        assertThat(ContextRegistry.INSTANCE.snapshot().get("databases")).isEqualTo(before);
    }

    @Test
    void libraryProvidedDbDefaultsHikariMinimumIdleToOneButUserValueWins() {
        AnnotationConfigApplicationContext lib = Fixtures.started("min-idle", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForCredentialsConfig.class);
        AnnotationConfigApplicationContext user = Fixtures.started("min-idle-user", NONE,
                Map.of("ptest.db.mode", "h2", "spring.datasource.hikari.minimum-idle", "3"), Fixtures.AskForCredentialsConfig.class);
        AnnotationConfigApplicationContext ownUrl = Fixtures.started("min-idle-own-url", NONE,
                Map.of("ptest.db.mode", "h2", "spring.datasource.url", "jdbc:h2:mem:mine2"), Fixtures.AskForCredentialsConfig.class);

        assertThat(lib.getEnvironment().getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("1");
        assertThat(user.getEnvironment().getProperty("spring.datasource.hikari.minimum-idle")).isEqualTo("3");
        assertThat(ownUrl.getEnvironment().getProperty("spring.datasource.hikari.minimum-idle")).as("DB của người dùng: không áp").isNull();
    }

    @Test
    void postgresContainerStartFailureCarriesTheCodeAndIsNotRetriedForEveryContext() {
        PostgresProvisioner.resetForTest();
        PostgresProvisioner.setStarterForTest((image, maxConnections) -> {
            throw new IllegalStateException("docker unavailable (simulated)");
        });
        PostgresProvisioner provisioner = new PostgresProvisioner("no-such-registry.invalid/team/pg-mirror:0", false, 300);

        assertThatThrownBy(() -> provisioner.provision("a", java.util.List.of()))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("DB_PROVISION_FAILED");
        long start = System.nanoTime();
        assertThatThrownBy(() -> provisioner.provision("b", java.util.List.of()))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("DB_PROVISION_FAILED").hasMessageContaining("not retried");

        assertThat(PostgresProvisioner.startAttempts()).as("container start tried once").isEqualTo(1);
        assertThat((System.nanoTime() - start) / 1_000_000).as("second context fails immediately, no new start").isLessThan(500);
    }
}
