package com.hittech.paralleltest.guard;

import java.util.Map;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.resource.jdbc.spi.StatementInspector;
import org.springframework.boot.hibernate.autoconfigure.HibernatePropertiesCustomizer;

/** Installs {@link SqlGuard} as Hibernate StatementInspector (only loaded when Hibernate is present). */
class HibernateSqlGuard implements HibernatePropertiesCustomizer {

    @Override
    public void customize(Map<String, Object> hibernateProperties) {
        Object existing = hibernateProperties.get(AvailableSettings.STATEMENT_INSPECTOR);
        StatementInspector delegate = existing instanceof StatementInspector si ? si : null;
        if (existing != null && delegate == null) {
            return; // configured by class name: leave the project's own inspector alone
        }
        hibernateProperties.put(AvailableSettings.STATEMENT_INSPECTOR, (StatementInspector) sql -> {
            SqlGuard.check(sql);
            return delegate == null ? sql : delegate.inspect(sql);
        });
    }
}
