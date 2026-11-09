package demo.core;

import org.springframework.stereotype.Service;

/** Bean can thiệp được nhưng KHÔNG test nào khai báo chế độ: dùng để kiểm tra thread không binding gọi bean "chưa ai can thiệp". */
@Service
public class UntouchedService {
    public String hello() {
        return "hello";
    }
}
