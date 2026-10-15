package com.crackit.common.scheduler;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class DistributedSchedulerLockService {

    private final JdbcTemplate jdbcTemplate;
    private final String instanceId = UUID.randomUUID().toString().substring(0, 8);

    /**
     * Attempts to acquire an atomic database lease for a scheduled job.
     * Prevents duplicate executions across multi-replica deployments.
     */
    public boolean acquireLock(String jobName, int leaseMinutes) {
        try {
            // MySQL / TiDB atomic upsert lease
            String sql = """
                INSERT INTO scheduled_job_locks (job_name, locked_at, locked_by, expires_at)
                VALUES (?, NOW(), ?, DATE_ADD(NOW(), INTERVAL ? MINUTE))
                ON DUPLICATE KEY UPDATE
                    locked_at = IF(expires_at < NOW(), NOW(), locked_at),
                    locked_by = IF(expires_at < NOW(), VALUES(locked_by), locked_by),
                    expires_at = IF(expires_at < NOW(), DATE_ADD(NOW(), INTERVAL ? MINUTE), expires_at)
            """;
            jdbcTemplate.update(sql, jobName, instanceId, leaseMinutes, leaseMinutes);

            // Check if this instance currently holds the active lease
            String checkSql = "SELECT COUNT(*) FROM scheduled_job_locks WHERE job_name = ? AND locked_by = ? AND expires_at > NOW()";
            Integer count = jdbcTemplate.queryForObject(checkSql, Integer.class, jobName, instanceId);
            boolean acquired = count != null && count > 0;
            if (acquired) {
                log.info("[DISTRIBUTED LOCK] Acquired lease for job '{}' (instance: {})", jobName, instanceId);
            } else {
                log.info("[DISTRIBUTED LOCK] Job '{}' is currently locked by another active instance. Skipping this run.", jobName);
            }
            return acquired;
        } catch (Exception e) {
            log.warn("[DISTRIBUTED LOCK] Failed to check database lock for '{}': {}. Proceeding with run.", jobName, e.getMessage());
            return true; // Fallback so local dev or environments without the table still execute
        }
    }

    public void releaseLock(String jobName) {
        try {
            String sql = "DELETE FROM scheduled_job_locks WHERE job_name = ? AND locked_by = ?";
            jdbcTemplate.update(sql, jobName, instanceId);
        } catch (Exception ignored) {}
    }
}
