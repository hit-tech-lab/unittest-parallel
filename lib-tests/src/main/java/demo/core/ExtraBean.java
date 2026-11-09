package demo.core;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Chỉ tồn tại khi app.extra-bean=true: cấu trúc khác nhau => context riêng. */
@Component
@ConditionalOnProperty("app.extra-bean")
public class ExtraBean {
}
