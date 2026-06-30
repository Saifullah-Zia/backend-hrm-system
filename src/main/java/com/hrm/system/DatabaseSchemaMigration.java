package com.hrm.system;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Idempotent schema fixes for production PostgreSQL where ddl-auto=update
 * does not always relax NOT NULL constraints on existing columns.
 */
@Slf4j
@Component
@Order(0)
public class DatabaseSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;

    public DatabaseSchemaMigration(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        relaxAuditLogPerformedByNotNull();
        backfillAuditLogPerformerNames();
    }

    private void relaxAuditLogPerformedByNotNull() {
        try {
            jdbcTemplate.execute("ALTER TABLE audit_logs ALTER COLUMN performed_by DROP NOT NULL");
            log.info("audit_logs.performed_by is nullable (user delete can preserve audit history)");
        } catch (Exception ex) {
            log.debug("audit_logs.performed_by nullable migration skipped: {}", ex.getMessage());
        }
    }

    private void backfillAuditLogPerformerNames() {
        try {
            int updated = jdbcTemplate.update("""
                    UPDATE audit_logs al
                    SET performed_by_name = u.name
                    FROM users u
                    WHERE al.performed_by = u.id
                      AND (al.performed_by_name IS NULL OR al.performed_by_name = '')
                    """);
            if (updated > 0) {
                log.info("Backfilled performed_by_name on {} audit log row(s)", updated);
            }
        } catch (Exception ex) {
            log.debug("audit_logs.performed_by_name backfill skipped: {}", ex.getMessage());
        }
    }
}
