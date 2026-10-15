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
import java.util.HashSet;
import java.util.Set;

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
            ensureColumn(stmt, "users", "job_alerts_enabled", "BOOLEAN DEFAULT TRUE");
            ensureColumn(stmt, "users", "unsubscribe_token", "VARCHAR(64)");

            // projects table
            ensureColumn(stmt, "projects", "impact_metrics", "TEXT");
            ensureColumn(stmt, "projects", "github_url", "TEXT");

            // career_roadmaps table
            ensureColumn(stmt, "career_roadmaps", "target_compensation", "VARCHAR(100)");

            // jd_analysis table
            ensureColumn(stmt, "jd_analysis", "matched_keywords", "JSON");
            ensureColumn(stmt, "jd_analysis", "missing_keywords", "JSON");

            // discovered_jobs table extensions
            ensureColumn(stmt, "discovered_jobs", "canonical_url", "VARCHAR(1000)");
            ensureColumn(stmt, "discovered_jobs", "url_hash", "VARCHAR(64)");
            ensureColumn(stmt, "discovered_jobs", "semantic_hash", "VARCHAR(64)");
            ensureColumn(stmt, "discovered_jobs", "source_type", "VARCHAR(50) DEFAULT 'API_AGGREGATOR'");
            ensureColumn(stmt, "discovered_jobs", "ats_platform", "VARCHAR(50)");
            ensureColumn(stmt, "discovered_jobs", "company_domain", "VARCHAR(150)");
            ensureColumn(stmt, "discovered_jobs", "is_active", "BOOLEAN DEFAULT TRUE");
            ensureColumn(stmt, "discovered_jobs", "verification_status", "VARCHAR(30) DEFAULT 'ACTIVE'");
            ensureColumn(stmt, "discovered_jobs", "last_verified_at", "DATETIME");
            ensureColumn(stmt, "discovered_jobs", "extracted_skills", "LONGTEXT");

            // curated_companies table extensions
            ensureColumn(stmt, "curated_companies", "last_discovered_at", "DATETIME");
            ensureColumn(stmt, "curated_companies", "last_job_count", "INT DEFAULT 0");
            ensureColumn(stmt, "curated_companies", "health_status", "VARCHAR(30) DEFAULT 'ACTIVE'");

            // user_job_interactions table
            ensureTable(stmt, "user_job_interactions",
                "CREATE TABLE IF NOT EXISTS `user_job_interactions` (" +
                "`id` VARCHAR(36) PRIMARY KEY, " +
                "`user_id` VARCHAR(36) NOT NULL, " +
                "`discovered_job_id` VARCHAR(36) NOT NULL, " +
                "`match_score` INT DEFAULT 0, " +
                "`matched_skills` LONGTEXT, " +
                "`missing_skills` LONGTEXT, " +
                "`status` VARCHAR(20) DEFAULT 'NEW', " +
                "`notified` BOOLEAN DEFAULT FALSE, " +
                "`created_at` DATETIME NOT NULL, " +
                "`updated_at` DATETIME NOT NULL, " +
                "UNIQUE KEY `uk_user_job` (`user_id`, `discovered_job_id`)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci"
            );

            try {
                stmt.executeUpdate("ALTER TABLE `user_job_interactions` CONVERT TO CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
            } catch (Exception ignored) {}

            // notifications table
            ensureTable(stmt, "notifications",
                "CREATE TABLE IF NOT EXISTS `notifications` (" +
                "`id` VARCHAR(36) PRIMARY KEY, " +
                "`user_id` VARCHAR(36) NOT NULL, " +
                "`title` VARCHAR(200) NOT NULL, " +
                "`message` TEXT NOT NULL, " +
                "`type` VARCHAR(50) NOT NULL, " +
                "`reference_id` VARCHAR(36), " +
                "`is_read` BOOLEAN DEFAULT FALSE, " +
                "`created_at` DATETIME NOT NULL" +
                ")"
            );

            // curated_companies table
            ensureTable(stmt, "curated_companies",
                "CREATE TABLE IF NOT EXISTS `curated_companies` (" +
                "`id` VARCHAR(36) PRIMARY KEY, " +
                "`name` VARCHAR(150) NOT NULL, " +
                "`domain` VARCHAR(150), " +
                "`ats_platform` VARCHAR(50) NOT NULL, " +
                "`ats_board_token` VARCHAR(150) NOT NULL, " +
                "`is_active` BOOLEAN DEFAULT TRUE, " +
                "`created_at` DATETIME NOT NULL" +
                ")"
            );

            // scheduled_job_locks table (for single-instance scheduled execution)
            ensureTable(stmt, "scheduled_job_locks",
                "CREATE TABLE IF NOT EXISTS `scheduled_job_locks` (" +
                "`job_name` VARCHAR(64) PRIMARY KEY, " +
                "`locked_at` DATETIME NOT NULL, " +
                "`locked_by` VARCHAR(64) NOT NULL, " +
                "`expires_at` DATETIME NOT NULL" +
                ")"
            );

            // Seed default curated companies
            seedCuratedCompanies(stmt);

            log.info("DatabaseSchemaMigrator [{}]: Schema synchronization completed successfully.", source);
        } catch (Exception e) {
            log.error("DatabaseSchemaMigrator [{}]: Failed to execute schema migration: {}", source, e.getMessage(), e);
        }
    }

    private void ensureTable(Statement stmt, String tableName, String createSql) {
        try {
            String checkSql = String.format(
                "SELECT COUNT(*) FROM information_schema.TABLES " +
                "WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = '%s'",
                tableName
            );
            boolean exists = false;
            try (ResultSet rs = stmt.executeQuery(checkSql)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    exists = true;
                }
            }
            if (!exists) {
                log.info("Creating table '{}'...", tableName);
                stmt.executeUpdate(createSql);
                log.info("Successfully created table '{}'.", tableName);
            }
        } catch (Exception e) {
            try {
                stmt.executeUpdate(createSql);
            } catch (Exception ex) {
                log.debug("Table '{}' creation handled: {}", tableName, ex.getMessage());
            }
        }
    }

    private void seedCuratedCompanies(Statement stmt) {
        try {
            // Find existing board tokens to avoid duplicates
            Set<String> existingTokens = new HashSet<>();
            try (ResultSet rs = stmt.executeQuery("SELECT `ats_board_token` FROM `curated_companies`")) {
                while (rs.next()) {
                    existingTokens.add(rs.getString(1).toLowerCase().trim());
                }
            }

            // Expanded catalog of Indian tech unicorns, product companies, and top engineering employers
            String[][] catalog = {
                // Name, Domain, Platform, Token
                {"Razorpay", "razorpay.com", "GREENHOUSE", "razorpay"},
                {"Postman", "postman.com", "GREENHOUSE", "postman"},
                {"BrowserStack", "browserstack.com", "GREENHOUSE", "browserstack"},
                {"CRED", "cred.club", "GREENHOUSE", "cred"},
                {"Meesho", "meesho.com", "GREENHOUSE", "meesho"},
                {"InMobi", "inmobi.com", "GREENHOUSE", "inmobi"},
                {"Groww", "groww.in", "GREENHOUSE", "groww"},
                {"Urban Company", "urbancompany.com", "GREENHOUSE", "urbancompany"},
                {"Cars24", "cars24.com", "GREENHOUSE", "cars24"},
                {"Delhivery", "delhivery.com", "GREENHOUSE", "delhivery"},
                {"Nykaa", "nykaa.com", "GREENHOUSE", "nykaa"},
                {"Databricks", "databricks.com", "GREENHOUSE", "databricks"},
                {"Uber", "uber.com", "GREENHOUSE", "uber"},
                {"Atlassian", "atlassian.com", "GREENHOUSE", "atlassian"},
                {"Rubrik", "rubrik.com", "GREENHOUSE", "rubrik"},
                {"Nutanix", "nutanix.com", "GREENHOUSE", "nutanix"},
                {"Elastic", "elastic.co", "GREENHOUSE", "elastic"},
                {"CleverTap", "clevertap.com", "GREENHOUSE", "clevertap"},
                {"Dream11", "dream11.com", "GREENHOUSE", "dream11"},
                {"Branch", "branch.io", "GREENHOUSE", "branch"},
                {"Stripe", "stripe.com", "GREENHOUSE", "stripe"},
                {"Airbnb", "airbnb.com", "GREENHOUSE", "airbnb"},
                {"Swiggy", "swiggy.com", "GREENHOUSE", "swiggy"},
                {"Zeta", "zeta.tech", "GREENHOUSE", "zeta"},

                // Lever boards
                {"Zepto", "zeptonow.com", "LEVER", "zepto"},
                {"Canva", "canva.com", "LEVER", "canva"},
                {"Spotify", "spotify.com", "LEVER", "spotify"},
                {"Glance", "glance.com", "LEVER", "glance"},
                {"Khatabook", "khatabook.com", "LEVER", "khatabook"},
                {"MPL", "mpl.live", "LEVER", "mpl"},
                {"Hotstar", "hotstar.com", "LEVER", "hotstar"},
                {"Loco", "loco.gg", "LEVER", "loco"},

                // Ashby boards
                {"Figma", "figma.com", "ASHBY", "figma"},
                {"Notion", "notion.so", "ASHBY", "notion"},
                {"Linear", "linear.app", "ASHBY", "linear"},
                {"Quora", "quora.com", "ASHBY", "quora"},
                {"Ramp", "ramp.com", "ASHBY", "ramp"},
                {"Airtable", "airtable.com", "ASHBY", "airtable"},
                {"Snyk", "snyk.io", "ASHBY", "snyk"},
                {"Vanta", "vanta.com", "ASHBY", "vanta"}
            };

            int inserted = 0;
            for (String[] c : catalog) {
                String token = c[3].toLowerCase().trim();
                if (!existingTokens.contains(token)) {
                    String sql = String.format(
                        "INSERT INTO `curated_companies` (`id`, `name`, `domain`, `ats_platform`, `ats_board_token`, `is_active`, `created_at`) VALUES " +
                        "(UUID(), '%s', '%s', '%s', '%s', true, NOW())",
                        c[0], c[1], c[2], c[3]
                    );
                    stmt.executeUpdate(sql);
                    existingTokens.add(token);
                    inserted++;
                }
            }
            if (inserted > 0) {
                log.info("DatabaseSchemaMigrator: Seeded {} new curated tech employers for discovery.", inserted);
            }
        } catch (Exception e) {
            log.debug("Curated companies seeding handled: {}", e.getMessage());
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
