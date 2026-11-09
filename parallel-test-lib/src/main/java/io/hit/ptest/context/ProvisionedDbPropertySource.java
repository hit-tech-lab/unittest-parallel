package io.hit.ptest.context;

import io.hit.ptest.db.DatabaseProvisioner;
import io.hit.ptest.db.DbResource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.PropertySource;
import org.springframework.util.ClassUtils;
import java.util.Collection;

/**
 * Cung cấp {@code spring.datasource.url/username/password} (và cỡ pool) từ DB riêng của context, nhưng chỉ
 * KHI ĐƯỢC HỎI và ở độ ưu tiên THẤP NHẤT trong Environment:
 * <ul>
 *   <li>người dùng tự đặt url (properties, {@code @DynamicPropertySource}) là sở hữu CẢ bộ kết nối: thư viện không cấp DB
 *   và không trả username/password/pool (nếu chỉ đặt username/password mà không đặt url thì giá trị của họ vẫn thắng
 *   của thư viện vì độ ưu tiên cao hơn);</li>
 *   <li>context không cần DataSource không bao giờ hỏi => không cấp DB.</li>
 * </ul>
 * Việc cấp DB xảy ra trong lúc refresh (không phải lúc customize), nên mọi đường lỗi đều đi qua hủy bean của Spring
 * và {@link ContextLifecycle#destroy()} dọn dẹp.
 */
public final class ProvisionedDbPropertySource extends PropertySource<ManagedContext> {

    /** Nguồn "nhìn" của Boot bọc các nguồn còn lại (relaxed binding); bỏ qua để không đệ quy vào chính nó. */
    private static final String ATTACHED_BOOT_VIEW = "configurationProperties";
    /** Package chứa JdbcConnectionDetails: Boot 4 (spring-boot-jdbc) và Boot 3.x (spring-boot-autoconfigure). */
    private static final String[] DETAILS_PACKAGES = {"org.springframework.boot.jdbc.autoconfigure", "org.springframework.boot.autoconfigure.jdbc"};
    static final String NAME = "ptestProvisionedDb";
    private static final Logger LOG = LoggerFactory.getLogger(ProvisionedDbPropertySource.class);
    private static final String P_URL = "spring.datasource.url";
    private static final String P_USER = "spring.datasource.username";
    private static final String P_PASSWORD = "spring.datasource.password";
    private static final String P_POOL = "spring.datasource.hikari.maximum-pool-size";
    /** Hikari mặc định minimumIdle = maximumPoolSize: mỗi context giữ trọn pool kết nối; đặt 1 để chỉ giữ khi cần (ghi đè được bằng property). */
    private static final String P_MIN_IDLE = "spring.datasource.hikari.minimum-idle";
    private static final String MIN_IDLE_VALUE = "1";

    private final DatabaseProvisioner provisioner;
    private final Collection<String> schemas;
    private final String poolSize;
    private volatile DbResource resource;
    private volatile String userOwner;
    private final java.util.concurrent.atomic.AtomicBoolean userOwnedLogged = new java.util.concurrent.atomic.AtomicBoolean();

    ProvisionedDbPropertySource(ManagedContext owner, DatabaseProvisioner provisioner, Collection<String> schemas, String poolSize) {
        super(NAME, owner);
        this.provisioner = provisioner;
        this.schemas = schemas;
        this.poolSize = poolSize;
    }

    @Override
    public Object getProperty(String name) {
        if (!P_URL.equals(name) && !P_USER.equals(name) && !P_PASSWORD.equals(name) && !P_POOL.equals(name)
                && !P_MIN_IDLE.equals(name)) {
            return null;
        }
        if (resource == null && urlProvidedElsewhere()) {
            logUserOwnedOnce();
            return null; // người dùng/nguồn khác sở hữu kết nối: thư viện không cấp DB và không trả username/password/pool
        }
        if (P_POOL.equals(name)) {
            return resource == null ? null : poolSize; // chỉ áp pool lên DB do thư viện cấp
        }
        if (P_MIN_IDLE.equals(name)) {
            return resource == null ? null : MIN_IDLE_VALUE;
        }
        DbResource r = ensure();
        if (r == null) {
            return null; // context đã đóng: không cấp thêm DB
        }
        return switch (name) {
            case P_URL -> r.url();
            case P_USER -> r.username();
            default -> r.password();
        };
    }

    /**
     * True nếu một nguồn khác (properties, {@code @DynamicPropertySource}, biến môi trường...) đã có
     * {@code spring.datasource.url}. Kiểm tra theo nguồn (không gọi {@code env.getProperty}) để không đệ quy vào chính nó,
     * và để mọi khóa (username/password được Boot đọc trước url) đều cho cùng kết luận. {@code @ServiceConnection} là bean
     * {@code JdbcConnectionDetails} (không nằm trong Environment) nên được nhận ra riêng.
     */
    private boolean urlProvidedElsewhere() {
        if (connectionDetailsBeanDeclared()) {
            userOwner = "a JdbcConnectionDetails bean (@ServiceConnection / Docker Compose)";
            return true;
        }
        for (PropertySource<?> ps : source.context.getEnvironment().getPropertySources()) {
            if (ps != this && !ATTACHED_BOOT_VIEW.equals(ps.getName())
                    && ps.containsProperty(P_URL)) {
                userOwner = "property source '" + ps.getName() + "'";
                return true;
            }
        }
        return false;
    }

    /**
     * Ghi INFO (một lần mỗi context) rằng thư viện KHÔNG cấp DB vì url do nguồn khác sở hữu: nếu nguồn đó là file cấu hình của ứng dụng
     * thì mọi context dùng chung MỘT database (mất cô lập dữ liệu giữa context), nên phải nhìn thấy được ở mức INFO.
     */
    private void logUserOwnedOnce() {
        if (userOwnedLogged.compareAndSet(false, true)) {
            LOG.info("Context {}: spring.datasource.url is provided by {}; ptest does NOT provision a database for this context "
                    + "(contexts with the same url share one database; ptest only manages the default spring.datasource.*)",
                    source.key, userOwner);
        }
    }

    /**
     * True nếu có bean {@code JdbcConnectionDetails} không phải loại "đọc từ spring.datasource.*" của Boot (vd do
     * {@code @ServiceConnection} hoặc Docker Compose đăng ký): kết nối đó nằm trong tay người dùng, DataSourceProperties vẫn
     * được bind nhưng không được dùng, nên cấp DB ở đây sẽ là DB thừa. Chỉ dùng tên lớp (dò cả package của Boot 3 và Boot 4) để không phụ thuộc spring-boot-jdbc.
     */
    private boolean connectionDetailsBeanDeclared() {
        ClassLoader cl = source.context.getClassLoader();
        var bf = source.context.getBeanFactory();
        for (String pkg : DETAILS_PACKAGES) {
            String detailsName = pkg + ".JdbcConnectionDetails";
            if (!ClassUtils.isPresent(detailsName, cl)) {
                continue;
            }
            String propertiesBased = pkg + ".PropertiesJdbcConnectionDetails";
            for (String bean : bf.getBeanNamesForType(ClassUtils.resolveClassName(detailsName, cl), true, false)) {
                Class<?> type = bf.getType(bean, false);
                if (type == null || !propertiesBased.equals(type.getName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Cấp DB một lần. Lỗi là BỀN VỮNG: ném lại ở mỗi lần hỏi (không thử lại, không tạo thêm DB) và ghi vào context vì Boot có thể
     * nuốt ngoại lệ từ nguồn thuộc tính trong lúc đánh giá điều kiện/binding; {@link ContextLifecycle} sẽ làm context thất bại
     * khi refresh xong, để không bao giờ chạy âm thầm trên DB khác (vd embedded mặc định của Boot).
     */
    private synchronized DbResource ensure() {
        if (source.isDestroyed()) {
            return null;
        }
        if (source.provisionFailure != null) {
            throw source.provisionFailure;
        }
        if (resource == null) {
            DbResource r;
            try {
                r = provisioner.provision(DatabaseProvisioner.uniqueName(source.key, PtestSettings.forkNumber()), schemas);
            } catch (RuntimeException e) {
                source.provisionFailure = e;
                throw e;
            }
            ContextRegistry.INSTANCE.attachDatabase(source, r);
            LOG.debug("Provisioned {} for context {}", r.description(), source.key);
            resource = r;
        }
        return resource;
    }
}
