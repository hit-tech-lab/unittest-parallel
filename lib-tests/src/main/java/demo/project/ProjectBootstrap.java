package demo.project;

import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/** Gọi hai bean mock-only ngay lúc khởi động (chưa có test, chưa có binding): phải nhận mặc định của Mockito, không NPE. */
@Component
public class ProjectBootstrap {

    private final Ports.Mailer mailer;
    private final Ports.FeatureFlags flags;
    private String startupMail = "unset";
    private boolean startupFlag = true;

    public ProjectBootstrap(Ports.Mailer mailer, Ports.FeatureFlags flags) {
        this.mailer = mailer;
        this.flags = flags;
    }

    @PostConstruct
    void init() {
        startupMail = mailer.send("boot");
        startupFlag = flags.enabled("boot");
    }

    public String startupMail() {
        return startupMail;
    }

    public boolean startupFlag() {
        return startupFlag;
    }
}
