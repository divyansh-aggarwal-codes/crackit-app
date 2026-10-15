package com.crackit.discovery.service;

import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobVerificationService {

    private final DiscoveredJobRepository discoveredJobRepository;
    private final RestTemplate restTemplate;

    /**
     * Synchronously or asynchronously verifies a single job opening.
     */
    public boolean verifyJob(DiscoveredJob job) {
        if (job == null || job.getUrl() == null || job.getUrl().isBlank()) {
            return false;
        }

        try {
            // Attempt lightweight HEAD request
            ResponseEntity<Void> response = restTemplate.exchange(job.getUrl(), HttpMethod.HEAD, null, Void.class);
            if (response.getStatusCode().is2xxSuccessful() || response.getStatusCode().is3xxRedirection()) {
                job.setIsActive(true);
                job.setVerificationStatus("ACTIVE");
                job.setLastVerifiedAt(LocalDateTime.now());
                discoveredJobRepository.save(job);
                return true;
            }
        } catch (HttpStatusCodeException ex) {
            if (ex.getStatusCode().value() == 404 || ex.getStatusCode().value() == 410) {
                log.info("[JOB VERIFIER] Job link expired ({}) for job: {} - {}", ex.getStatusCode(), job.getCompany(), job.getTitle());
                job.setIsActive(false);
                job.setVerificationStatus("EXPIRED");
                job.setLastVerifiedAt(LocalDateTime.now());
                discoveredJobRepository.save(job);
                return false;
            }
        } catch (Exception e) {
            log.debug("[JOB VERIFIER] Status check exception for {}: {}", job.getUrl(), e.getMessage());
        }

        // Fallback: keep current active status, update lastVerifiedAt
        job.setLastVerifiedAt(LocalDateTime.now());
        discoveredJobRepository.save(job);
        return Boolean.TRUE.equals(job.getIsActive());
    }

    /**
     * Nightly verification scan (runs at 2 AM) for stored jobs that haven't been verified in 48 hours.
     */
    @Scheduled(cron = "0 0 2 * * *")
    public void scheduledVerificationScan() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(2);
        List<DiscoveredJob> pending = discoveredJobRepository.findJobsNeedingVerification(cutoff);
        log.info("[JOB VERIFIER] Starting nightly freshness check for {} stored jobs...", pending.size());

        int activeCount = 0;
        int expiredCount = 0;

        for (DiscoveredJob job : pending) {
            boolean active = verifyJob(job);
            if (active) activeCount++;
            else expiredCount++;

            try {
                Thread.sleep(150); // Gentle throttle to respect target career server rate limits
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        log.info("[JOB VERIFIER] Verification complete: {} active, {} marked expired.", activeCount, expiredCount);
    }
}
