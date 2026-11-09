package demo.mockbean;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Bean {@code @ConfigurationProperties} kiểu JavaBean có setter. */
@ConfigurationProperties(prefix = "mbs")
public class SettableLimits {

    private int limit = 3;

    public int getLimit() {
        return limit;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }
}
