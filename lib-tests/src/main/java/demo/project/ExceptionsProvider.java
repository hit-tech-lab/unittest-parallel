package demo.project;

import org.springframework.stereotype.Component;

/** Bean thật dùng chung mà một số test mock còn test khác dùng bản thật (như một bean cấu hình thông điệp lỗi của ứng dụng). */
@Component
public class ExceptionsProvider {

    public String messageFor(String code) {
        return "real:" + code;
    }
}
