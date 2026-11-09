package demo.project;

import org.springframework.stereotype.Service;

/** Đọc {@link LimitProperties} mỗi lần gọi (không cache) để test thấy giá trị mock riêng của mình. */
@Service
public class LimitService {

    private final LimitProperties properties;

    public LimitService(LimitProperties properties) {
        this.properties = properties;
    }

    public int limit() {
        return properties.getMaxItems();
    }
}
