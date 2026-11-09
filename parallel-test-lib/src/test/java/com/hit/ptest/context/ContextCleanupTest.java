package com.hit.ptest.context;

import com.hit.ptest.ParallelTestException;
import com.hit.ptest.db.H2Provisioner;

import com.hit.ptest.testsupport.Fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Điểm 1: DB và slot không rò rỉ trên mọi đường kết thúc của context (lỗi lúc khởi động, lỗi provision, đóng bình thường). */
class ContextCleanupTest {

    private static final String[] NONE = {};

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    static boolean h2Exists(String url) {
        String probe = url.replace(";DB_CLOSE_DELAY=-1", "") + ";IFEXISTS=TRUE";
        try (Connection c = DriverManager.getConnection(probe, "sa", "")) {
            return c.isValid(1);
        } catch (SQLException e) {
            return false;
        }
    }

    @Test
    void contextFailingDuringBeanCreationDropsItsDatabaseAndReleasesTheSlot() {
        int before = ContextRegistry.INSTANCE.activeCount();
        Fixtures.FailingConfig.URL.set(null);
        AnnotationConfigApplicationContext ctx = Fixtures.context("fail-bean", NONE, NONE, Map.of("ptest.db.mode", "h2"), Fixtures.FailingConfig.class);

        assertThatThrownBy(ctx::refresh).hasMessageContaining("boom during startup");

        String url = Fixtures.FailingConfig.URL.get();
        assertThat(url).as("the failing bean asked for a datasource url, so a DB was provisioned").startsWith("jdbc:h2:mem:");
        assertThat(h2Exists(url)).as("DB dropped although the lifecycle bean was never part of the bean definitions").isFalse();
        assertThat(ContextRegistry.INSTANCE.activeCount()).as("slot released").isEqualTo(before);
    }

    @Test
    void contextFailingInBeanFactoryPostProcessorReleasesTheSlot() {
        int before = ContextRegistry.INSTANCE.activeCount();
        AnnotationConfigApplicationContext ctx = Fixtures.context("fail-bfpp", NONE, NONE, Map.of(), Fixtures.FailingBfppConfig.class);

        assertThatThrownBy(ctx::refresh).hasMessageContaining("boom in BFPP");

        assertThat(ContextRegistry.INSTANCE.activeCount()).isEqualTo(before);
    }

    @Test
    void controlDatabaseExistsWhileContextIsOpenAndIsDroppedWhenItCloses() {
        AnnotationConfigApplicationContext ctx = Fixtures.started("ok-close", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);
        String url = ctx.getBean("url", String.class);

        assertThat(h2Exists(url)).as("negative control: the probe sees a live DB").isTrue();
        ctx.close();
        assertThat(h2Exists(url)).isFalse();
    }

    @Test
    void closeAllActiveClosesEveryContextDropsTheirDatabasesAndMeasuresLeftoversWhileTheServerIsAlive() {
        AnnotationConfigApplicationContext first = Fixtures.started("sess-1", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);
        AnnotationConfigApplicationContext second = Fixtures.started("sess-2", NONE, Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);
        String urlFirst = first.getBean("url", String.class);
        String urlSecond = second.getBean("url", String.class);
        assertThat(h2Exists(urlFirst) && h2Exists(urlSecond)).as("negative control: both DBs are live").isTrue();

        ContextRegistry.INSTANCE.closeAllActive();

        assertThat(first.isActive() || second.isActive()).isFalse();
        assertThat(ContextRegistry.INSTANCE.activeCount()).isZero();
        assertThat(h2Exists(urlFirst) || h2Exists(urlSecond)).isFalse();
        assertThat(ContextRegistry.INSTANCE.exitSnapshot(0)).containsEntry("exitContextsActive", "0").containsEntry("exitLeftoverDbs", "0");
    }

    @Test
    void h2ProvisioningFailureHalfwayRollsBackWhatWasCreated() {
        String name = "rollback_" + System.nanoTime();
        assertThatThrownBy(() -> new H2Provisioner().provision(name, List.of("good_schema", "bad;name")))
                .isInstanceOf(ParallelTestException.Config.class).hasMessageContaining("DB_PROVISION_FAILED");

        assertThat(h2Exists("jdbc:h2:mem:" + name)).as("in-memory DB shut down after the half-done provision").isFalse();
    }

    /** Boot nuốt ngoại lệ từ nguồn thuộc tính trong lúc đánh giá điều kiện: lỗi cấp DB vẫn phải làm context thất bại. */
    @Test
    void provisioningFailureSwallowedByTheCallerStillFailsTheContextAndIsNotRetried() {
        AnnotationConfigApplicationContext ctx = Fixtures.context("swallow", NONE, new String[] {"bad;name"}, Map.of("ptest.db.mode", "h2"),
                Fixtures.SwallowingConfig.class);

        assertThatThrownBy(ctx::refresh).hasMessageContaining("DB_PROVISION_FAILED");

        assertThat(Fixtures.SwallowingConfig.ATTEMPTS.get()).as("asked twice (once swallowed), one provisioning attempt only").isEqualTo(2);
        assertThat(ContextRegistry.INSTANCE.snapshot().get("databases")).doesNotContain("swallow@");
    }
}
