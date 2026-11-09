package demo.mockbean;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;

/**
 * Bean có đủ ba thứ Spring xử lý qua post-processor: field {@code @Autowired}, {@code @PostConstruct} (private, nên không bị Mockito chặn)
 * và method {@code @Async} (bean bị bọc proxy AOP). Bản thật: field có giá trị, {@code initialized} = true, là proxy AOP. Mock của thư
 * viện không được đi qua các bước đó nên cả ba phải ở trạng thái "chưa chạm".
 */
public class Wired {

    @Autowired
    Helper helper;

    boolean initialized;

    @PostConstruct
    private void init() {
        initialized = true;
    }

    @Async
    public String work() {
        return "real";
    }
}
