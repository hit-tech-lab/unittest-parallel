package com.hit.ptest.db;

import com.hit.ptest.ParallelTestException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collection;

/**
 * H2 in-memory: URL duy nhất cho mỗi context (fork + uuid + tên), nên không dùng chung giữa context/fork.
 *
 * <p>{@code options}: phần mở rộng của URL H2 do người dùng chọn qua {@code ptest.db.h2.options}, vd
 * {@code MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE} để H2 chấp nhận cú pháp/đặt tên của Postgres (cú pháp
 * {@code key=value} cách nhau {@code ;}). Mọi kết nối của thư viện tới DB này dùng cùng URL nên cùng cài đặt.
 */
public final class H2Provisioner implements DatabaseProvisioner {

    private final String options;

    public H2Provisioner() {
        this("");
    }

    public H2Provisioner(String options) {
        String trimmed = options == null ? "" : options.strip().replaceAll("^;+|;+$", "");
        this.options = trimmed.isEmpty() ? "" : ";" + trimmed;
    }

    @Override
    public String mode() {
        return "h2";
    }

    @Override
    public DbResource provision(String uniqueName, Collection<String> schemas) {
        String url = "jdbc:h2:mem:" + uniqueName + ";DB_CLOSE_DELAY=-1" + options;
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            for (String schema : schemas) {
                st.execute("CREATE SCHEMA IF NOT EXISTS " + schema);
            }
        } catch (SQLException e) {
            shutdown(url); // DB_CLOSE_DELAY=-1 giữ DB sống đến khi SHUTDOWN: dọn phần đã tạo
            throw new ParallelTestException.Config("DB_PROVISION_FAILED: H2 " + url + ": " + e);
        }
        return new DbResource("h2:" + url, url, "sa", "", () -> shutdown(url), () -> exists(url));
    }

    /** IFEXISTS=TRUE: chỉ nối vào DB đang tồn tại, không vô tình tạo mới. */
    private static boolean exists(String url) {
        try (Connection c = DriverManager.getConnection(url + ";IFEXISTS=TRUE", "sa", "")) {
            return true;
        } catch (SQLException e) {
            return false;
        }
    }

    private static void shutdown(String url) {
        try (Connection c = DriverManager.getConnection(url, "sa", ""); Statement st = c.createStatement()) {
            st.execute("SHUTDOWN");
        } catch (SQLException ignored) {
            // đã đóng
        }
    }
}
