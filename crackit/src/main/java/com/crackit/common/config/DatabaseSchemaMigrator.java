package com.crackit.common.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

@Slf4j
@Component("databaseSchemaMigrator")
@RequiredArgsConstructor
public class DatabaseSchemaMigrator implements ApplicationRunner {

    private final DataSource dataSource;

    @PostConstruct
    public void migrateOnInit() {
        runMigrations("PostConstruct");
    }

    @Override
    public void run(ApplicationArguments args) {
        runMigrations("ApplicationRunner");
    }

    private synchronized void runMigrations(String source) {
        log.info("DatabaseSchemaMigrator [{}]: Checking and synchronizing schema columns in TiDB/MySQL...", source);

        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // master_resume table
            ensureColumn(stmt, "master_resume", "education", "TEXT");
            ensureColumn(stmt, "master_resume", "raw_resume_text", "LONGTEXT");

            // users table
            ensureColumn(stmt, "users", "education", "VARCHAR(500)");
            ensureColumn(stmt, "users", "current_company", "VARCHAR(150)");
            ensureColumn(stmt, "users", "current_role", "VARCHAR(150)");
            ensureColumn(stmt, "users", "target_role", "VARCHAR(150)");
            ensureColumn(stmt, "users", "current_ctc", "VARCHAR(50)");
            ensureColumn(stmt, "users", "expected_ctc", "VARCHAR(50)");
            ensureColumn(stmt, "users", "notice_period", "VARCHAR(50)");
            ensureColumn(stmt, "users", "serving_notice", "BOOLEAN DEFAULT FALSE");
            ensureColumn(stmt, "users", "last_working_day", "VARCHAR(30)");
            ensureColumn(stmt, "users", "preferred_work_mode", "VARCHAR(50)");
            ensureColumn(stmt, "users", "preferred_locations", "VARCHAR(200)");
            ensureColumn(stmt, "users", "linkedin_url", "VARCHAR(200)");
            ensureColumn(stmt, "users", "github_url", "VARCHAR(200)");
            ensureColumn(stmt, "users", "years_experience", "INT");

            // projects table
            ensureColumn(stmt, "projects", "impact_metrics", "TEXT");
            ensureColumn(stmt, "projects", "github_url", "TEXT");

            // career_roadmaps table
            ensureColumn(stmt, "career_roadmaps", "target_compensation", "VARCHAR(100)");

            // jd_analysis table
            ensureColumn(stmt, "jd_analysis", "matched_keywords", "JSON");
            ensureColumn(stmt, "jd_analysis", "missing_keywords", "JSON");

            log.info("DatabaseSchemaMigrator [{}]: Schema synchronization completed successfully.", source);
        } catch (Exception e) {
            log.error("DatabaseSchemaMigrator [{}]: Failed to execute schema migration: {}", source, e.getMessage(), e);
        }
    }

    private void ensureColumn(Statement stmt, String tableName, String columnName, String columnType) {
        try {
            String checkSql = String.format(
                "SELECT COUNT(*) FROM information_schema.COLUMNS " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s' AND COLUMN_NAME = '%s'",
                tableName, columnName
            );
            boolean exists = false;
            try (ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }

            if (!exists) {
                log.info("Adding missing column '{}.{}' ({})", tableName, columnName, columnType);
                String alterSql = String.format("ALTER TABLE `%s` ADD COLUMN `%s` %s", tableName, columnName, columnType);
                stmt.executeUpdate(alterSql);
                log.info("Successfully added column '{}.{}'", tableName, columnName);
            } else {
                log.debug("Column '{}.{}' already exists.", tableName, columnName);
            }
        } catch (Exception e) {
            try {
                String fallbackSql = String.format("ALTER TABLE `%s` ADD COLUMN IF NOT EXISTS `%s` %s", tableName, columnName, columnType);
                stmt.executeUpdate(fallbackSql);
                log.info("Added column '{}.{}' via IF NOT EXISTS fallback.", tableName, columnName);
            } catch (Exception ex) {
                if ("JSON".equalsIgnoreCase(columnType)) {
                    try {
                        String textSql = String.format("ALTER TABLE `%s` ADD COLUMN `%s` LONGTEXT", tableName, columnName);
                        stmt.executeUpdate(textSql);
                        log.info("Added column '{}.{}' via LONGTEXT fallback.", tableName, columnName);
                        return;
                    } catch (Exception ignored) {}
                }
                log.debug("Column '{}.{}' already exists or could not be added: {}", tableName, columnName, ex.getMessage());
            }
        }
    }
}
