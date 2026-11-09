package io.hit.ptest.db;

import io.hit.ptest.ParallelTestException;

import java.util.Collection;
import java.util.Locale;
import java.util.UUID;

/** Tạo tài nguyên DB riêng cho một context; chạy lúc context cần DataSource (xem {@code ProvisionedDbPropertySource}). */
public interface DatabaseProvisioner {

    /** Độ dài tối đa của tên: Postgres cắt identifier ở 63 ký tự, nên giữ dưới mức đó. */
    int MAX_NAME_LENGTH = 60;

    /** Tên chế độ để báo cáo: h2 | postgres. */
    String mode();

    /**
     * Tạo DB/schema tên {@code uniqueName}. Nếu lỗi giữa chừng phải tự dọn phần đã tạo rồi ném
     * {@link ParallelTestException.Config} (DB_PROVISION_FAILED).
     */
    DbResource provision(String uniqueName, Collection<String> schemas);

    /**
     * Tên duy nhất, an toàn cho identifier SQL. Phần DUY NHẤT (fork + uuid) đứng trước, tên context sau cùng và bị cắt
     * nếu quá dài, nên tên dài không làm hai context trùng nhau.
     */
    static String uniqueName(String contextKey, String forkNumber) {
        String name = "ptest_f" + forkNumber + "_" + UUID.randomUUID().toString().substring(0, 8)
                + "_" + contextKey.replaceAll("[^A-Za-z0-9]", "_");
        name = name.toLowerCase(Locale.ROOT);
        return name.length() <= MAX_NAME_LENGTH ? name : name.substring(0, MAX_NAME_LENGTH);
    }
}
