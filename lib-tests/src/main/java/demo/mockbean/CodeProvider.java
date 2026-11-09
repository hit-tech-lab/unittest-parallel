package demo.mockbean;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bean {@code @ConfigurationProperties} chỉ có getter, không có setter: giá trị vào field bằng {@code @Value}. Với bean thật, binder thấy
 * getter trả đúng giá trị đã bind nên không báo lỗi; với mock, getter trả null nên binder ném "No setter found for property" NẾU mock
 * bị đưa qua {@code ConfigurationPropertiesBindingPostProcessor}.
 */
@ConfigurationProperties(prefix = "mbx")
public class CodeProvider {

    @Value("${mbx.code}")
    private String code;

    public String getCode() {
        return code;
    }
}
