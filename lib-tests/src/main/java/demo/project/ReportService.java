package demo.project;

import org.springframework.stereotype.Service;

/** Dùng bảy cổng ra ngoài còn lại: mọi cổng mặc định của mock (null/0/false) phải không gây NPE. */
@Service
public class ReportService {

    private final Ports.SmsSender sms;
    private final Ports.Cache cache;
    private final Ports.SearchIndex search;
    private final Ports.FileStore files;
    private final Ports.FeatureFlags flags;
    private final Ports.MetricsSink metrics;
    private final Ports.AuditSink audit;
    private final Ports.MessageQueue queue;

    public ReportService(Ports.SmsSender sms, Ports.Cache cache, Ports.SearchIndex search, Ports.FileStore files,
                         Ports.FeatureFlags flags, Ports.MetricsSink metrics, Ports.AuditSink audit, Ports.MessageQueue queue) {
        this.sms = sms;
        this.cache = cache;
        this.search = search;
        this.files = files;
        this.flags = flags;
        this.metrics = metrics;
        this.audit = audit;
        this.queue = queue;
    }

    public String report(String id) {
        metrics.count("report");
        audit.write(id);
        String cached = cache.get(id);
        String body = cached != null ? cached : files.read(id) + ":" + search.count(id);
        queue.publish("reports", id);
        sms.send("ops", id);
        return body + ":" + (flags.enabled("beta") ? "beta" : "std");
    }
}
