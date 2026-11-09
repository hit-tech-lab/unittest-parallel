package com.hit.ptest.context;

import static org.assertj.core.api.Assertions.assertThat;

import com.hit.ptest.config.PtestKeys;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.core.SpringProperties;
import org.springframework.mock.env.MockEnvironment;

/** Cảnh báo khi key TOÀN CỤC bị đặt vào Environment của context (application.yml) thay vì spring.properties / -D. */
class MisplacedGlobalKeysTest {

    private ListAppender<ILoggingEvent> appender;
    private Logger logger;
    private List<String> captured;

    @BeforeEach
    void capture() {
        MisplacedGlobalKeys.resetForTest();
        logger = (Logger) LoggerFactory.getLogger(MisplacedGlobalKeys.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        logger.setLevel(Level.WARN);
        captured = new java.util.AbstractList<>() {
            @Override
            public String get(int i) {
                return appender.list.get(i).getFormattedMessage();
            }

            @Override
            public int size() {
                return appender.list.size();
            }
        };
    }

    @AfterEach
    void cleanUp() {
        logger.detachAppender(appender);
        appender.stop();
        System.clearProperty(PtestKeys.MAX_CONTEXTS);
        MisplacedGlobalKeys.resetForTest();
    }

    @Test
    void globalKeyInEnvironmentWarnsOnceEvenAcrossCalls() {
        MockEnvironment env = new MockEnvironment().withProperty(PtestKeys.MAX_CONTEXTS, "3")
                .withProperty(PtestKeys.guardRule("PT07"), "off");
        MisplacedGlobalKeys.warnOnce(env, k -> null);
        assertThat(captured).hasSize(2)
                .anyMatch(m -> m.startsWith(PtestKeys.MAX_CONTEXTS + " là cấu hình TOÀN CỤC") && m.contains("-Dptest.max-contexts=..."))
                .anyMatch(m -> m.startsWith("ptest.guard.PT07 "));
        MisplacedGlobalKeys.warnOnce(env, k -> null);
        assertThat(captured).as("không lặp lần hai").hasSize(2);
    }

    @Test
    void globalKeyEffectiveViaSpringPropertiesDoesNotWarn() {
        System.setProperty(PtestKeys.MAX_CONTEXTS, "3");
        MockEnvironment env = new MockEnvironment().withProperty(PtestKeys.MAX_CONTEXTS, "3");
        MisplacedGlobalKeys.warnOnce(env, SpringProperties::getProperty);
        assertThat(captured).isEmpty();
    }

    @Test
    void contextKeysNeverWarn() {
        MockEnvironment env = new MockEnvironment().withProperty(PtestKeys.DB_MODE, "h2")
                .withProperty(PtestKeys.UNBOUND_POLICY, "fail").withProperty(PtestKeys.DB_POOL_SIZE, "5");
        MisplacedGlobalKeys.warnOnce(env, k -> null);
        assertThat(captured).isEmpty();
    }
}
