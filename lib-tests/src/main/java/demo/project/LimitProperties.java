package demo.project;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Bean cấu hình kiểu {@code @ConfigurationProperties} của ứng dụng: mặc định không được bọc; lớp nào cần mock theo test thì bật bằng {@code wrap}. */
@Component
@ConfigurationProperties("project.limits")
public class LimitProperties {

    private int maxItems = 10;

    public int getMaxItems() {
        return maxItems;
    }

    public void setMaxItems(int maxItems) {
        this.maxItems = maxItems;
    }
}
