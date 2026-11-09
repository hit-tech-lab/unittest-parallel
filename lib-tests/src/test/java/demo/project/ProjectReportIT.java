package demo.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.hit.ptest.PerTestMock;
import org.junit.jupiter.api.Test;

/** Cổng mock-only không được stub trả mặc định Mockito (null/0/false), không NPE; cổng đã stub chỉ ảnh hưởng test này. */
class ProjectReportIT extends ProjectBaseIT {

    @PerTestMock Ports.Cache cache;
    @PerTestMock Ports.FeatureFlags flags;
    @PerTestMock Ports.MetricsSink metrics;
    @PerTestMock Ports.MessageQueue queue;
    @PerTestMock Ports.SmsSender sms;
    @PerTestMock Ports.AuditSink audit;

    @Test
    void stubbedPortsOnlyAffectThisTest() {
        when(cache.get("r1")).thenReturn("from-cache");
        when(flags.enabled("beta")).thenReturn(true);
        meetInSameContext("report", 2);

        assertThat(report.report("r1")).isEqualTo("from-cache:beta");

        verify(metrics).count("report");
        verify(queue).publish("reports", "r1");
        verify(sms).send("ops", "r1");
    }

    @Test
    void unstubbedPortsAnswerMockitoDefaultsWithoutNpe() {
        meetInSameContext("report", 2);

        assertThat(report.report("r2")).isEqualTo("null:0:std");

        verify(audit).write("r2");
    }
}
