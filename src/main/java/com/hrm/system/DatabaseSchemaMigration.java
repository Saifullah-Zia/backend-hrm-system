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
        detachRemovedChatTablesFromUsers();
        createIndexes();
        createUniqueIndexes();
    }

    /**
     * The chat module is gone, but its tables may still hold rows pointing at users. Those foreign
     * keys would block deleting a user, so they are dropped. Chat rows themselves are left untouched.
     */
    private void detachRemovedChatTablesFromUsers() {
        try {
            jdbcTemplate.execute("""
                    DO $$
                    DECLARE r RECORD;
                    BEGIN
                      FOR r IN
                        SELECT c.conrelid::regclass AS tbl, c.conname
                        FROM pg_constraint c
                        WHERE c.contype = 'f'
                          AND c.confrelid = 'users'::regclass
                          AND c.conrelid::regclass::text IN ('chat_messages', 'conversation_members', 'conversations')
                      LOOP
                        EXECUTE format('ALTER TABLE %s DROP CONSTRAINT %I', r.tbl, r.conname);
                      END LOOP;
                    END $$;
                    """);
        } catch (Exception ex) {
            log.warn("Could not detach old chat tables from users: {}", ex.getMessage());
        }
    }

    private void createIndexes() {
        String[] statements = {
                "CREATE INDEX IF NOT EXISTS idx_attendance_date ON attendance (date)",
                "CREATE INDEX IF NOT EXISTS idx_attendance_user_date ON attendance (user_id, date)",
                "CREATE INDEX IF NOT EXISTS idx_payroll_user ON payroll (user_id)",
                "CREATE INDEX IF NOT EXISTS idx_payroll_period ON payroll (payroll_period_id)",
                "CREATE INDEX IF NOT EXISTS idx_leaves_user ON leaves (user_id)",
                "CREATE INDEX IF NOT EXISTS idx_leaves_status ON leaves (status)",
                "CREATE INDEX IF NOT EXISTS idx_leaves_attachment ON leaves (attachment_url)",
                "CREATE INDEX IF NOT EXISTS idx_notification_user_status ON notification (user_id, status)",
                "CREATE INDEX IF NOT EXISTS idx_notice_user ON notice (user_id)",
                "CREATE INDEX IF NOT EXISTS idx_correction_status ON attendance_correction_requests (status)",
                "CREATE INDEX IF NOT EXISTS idx_correction_user ON attendance_correction_requests (user_id)",
                "CREATE INDEX IF NOT EXISTS idx_users_last_seen ON users (last_seen_at)"
        };
        for (String statement : statements) {
            try {
                jdbcTemplate.execute(statement);
            } catch (Exception ex) {
                log.warn("Index not created [{}]: {}", statement, ex.getMessage());
            }
        }
    }

    /**
     * Database-level protection against double clicks and races. Each one fails harmlessly while
     * duplicate rows exist and is retried at every startup, so it applies once the data is cleaned up.
     */
    private void createUniqueIndexes() {
        String[] statements = {
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_attendance_user_date ON attendance (user_id, date)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_payroll_user_period ON payroll (user_id, payroll_period_id)",
                "CREATE UNIQUE INDEX IF NOT EXISTS uq_payroll_period ON payroll_periods (lower(month), year, coalesce(department, ''))"
        };
        for (String statement : statements) {
            try {
                jdbcTemplate.execute(statement);
            } catch (Exception ex) {
                log.warn("Unique index not created because duplicate rows exist [{}]", statement);
            }
        }
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
