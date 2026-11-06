package com.hittech.paralleltest.e2e;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;

import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.testkit.engine.EngineExecutionResults;
import org.junit.platform.testkit.engine.EngineTestKit;
import org.junit.platform.testkit.engine.Events;
import org.junit.jupiter.api.Test;

/** Runs fixture classes through the real JUnit engine + Spring + the guard listener, and inspects the outcome. */
class GuardEndToEndTest {

    private static EngineExecutionResults run(Class<?> fixture) {
        return EngineTestKit.engine("junit-jupiter").selectors(selectClass(fixture)).execute();
    }

    private static String failureMessages(Events events) {
        return String.join("\n", events.failed().stream()
                .map(e -> e.getPayload(TestExecutionResult.class).flatMap(TestExecutionResult::getThrowable)
                        .map(t -> t.getMessage() == null ? t.toString() : t.getMessage()).orElse(""))
                .toList());
    }

    @Test
    void violationSwallowedOnAnotherThreadStillFailsTheTest() {
        EngineExecutionResults r = run(E2eFixtures.SwallowedOnOtherThreadCase.class);
        assertThat(r.testEvents().failed().count()).isEqualTo(1);
        assertThat(failureMessages(r.allEvents())).contains("[PT12]", "other threads", "delete from item");
    }

    @Test
    void nestedTestsSeeTheEnclosingMocks() {
        EngineExecutionResults r = run(E2eFixtures.NestedCase.class);
        assertThat(failureMessages(r.allEvents())).isEmpty();
        assertThat(r.testEvents().succeeded().count()).isEqualTo(1);
    }

    @Test
    void realBeanInjectedByConcreteTypeIsReported() {
        EngineExecutionResults r = run(E2eFixtures.RoutedBeanBypassCase.class);
        assertThat(failureMessages(r.allEvents())).contains("[PT14]", "realCollaborator", "legacyService");
    }
}
