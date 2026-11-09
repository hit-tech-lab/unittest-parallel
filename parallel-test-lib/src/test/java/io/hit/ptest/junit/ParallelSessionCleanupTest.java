package io.hit.ptest.junit;

import static org.assertj.core.api.Assertions.assertThat;

import io.hit.ptest.testsupport.Fixtures;

import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

/** Phiên JUnit lồng bên trong (vd {@code LauncherFactory.create()} trong một test) không được đóng context của các test đang chạy. */
class ParallelSessionCleanupTest {

    @AfterEach
    void cleanup() {
        Fixtures.closeAll();
    }

    @Test
    void nestedSessionClosingWhileTheOuterSessionIsOpenKeepsContextsActive() {
        AnnotationConfigApplicationContext ctx = Fixtures.started("nested-session", new String[0], Map.of("ptest.db.mode", "h2"), Fixtures.AskForUrlConfig.class);
        ParallelSessionCleanup listener = new ParallelSessionCleanup();

        // phiên của chính lần chạy test này còn mở nên phiên lồng đóng không phải phiên cuối
        listener.launcherSessionOpened(null);
        listener.launcherSessionClosed(null);

        assertThat(ctx.isActive()).isTrue();
    }
}
