package demo.support;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Kiểm tra một DB/schema còn tồn tại không, từ bên ngoài ứng dụng; chỉ coi "không tồn tại" khi đúng mã lỗi, lỗi khác ném ra. */
public final class DbProbe {

    private static final int H2_DATABASE_NOT_FOUND_IFEXISTS = 90146;

    private DbProbe() {
    }

    public static boolean exists(String url, String user, String password) {
        String probeUrl = url.startsWith("jdbc:h2:") ? url.replace(";DB_CLOSE_DELAY=-1", "") + ";IFEXISTS=TRUE" : url;
        try (Connection c = DriverManager.getConnection(probeUrl, user, password)) {
            int at = url.indexOf("currentSchema=");
            if (at < 0) {
                return true;
            }
            String schema = url.substring(at + "currentSchema=".length()).split("[,&]")[0]; // currentSchema=<tên>,public
            try (Statement st = c.createStatement();
                 ResultSet rs = st.executeQuery("select 1 from information_schema.schemata where schema_name = '" + schema + "'")) {
                return rs.next();
            }
        } catch (SQLException e) {
            if ("3D000".equals(e.getSQLState()) || e.getErrorCode() == H2_DATABASE_NOT_FOUND_IFEXISTS) {
                return false;
            }
            throw new AssertionError("probe failed for another reason than 'does not exist': " + url, e);
        }
    }

    /** Số database Postgres có tên khớp mẫu (dùng kết nối tới bất kỳ DB nào của cùng server). */
    public static int postgresDatabasesLike(String anyUrl, String user, String password, String pattern) throws SQLException {
        try (Connection c = DriverManager.getConnection(anyUrl, user, password);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("select count(*) from pg_database where datname like '" + pattern + "'")) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
