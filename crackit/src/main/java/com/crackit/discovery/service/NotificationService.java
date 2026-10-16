package com.crackit.discovery.service;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.Notification;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.repository.NotificationRepository;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@lombok.Setter
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.crackit.common.scheduler.DistributedSchedulerLockService distributedSchedulerLockService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private JobDiscoveryService jobDiscoveryService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private DiscoveredJobRepository discoveredJobRepository;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    @org.springframework.context.annotation.Lazy
    private ResumeMatchService resumeMatchService;

    public List<Notification> getNotificationsForUser(String userId) {
        return notificationRepository.findTop20ByUserIdOrderByCreatedAtDesc(userId);
    }

    public long getUnreadCount(String userId) {
        return notificationRepository.countUnreadByUserId(userId);
    }

    @Transactional
    public void markAsRead(String notificationId, String userId) {
        notificationRepository.findById(notificationId).ifPresent(n -> {
            if (n.getUser().getId().equals(userId)) {
                n.setIsRead(true);
                notificationRepository.save(n);
            }
        });
    }

    @Transactional
    public void markAllAsRead(String userId) {
        notificationRepository.markAllAsReadForUser(userId);
    }

    public Notification createNotification(User user, String title, String message, String type, String referenceId) {
        Notification notification = Notification.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .title(title)
                .message(message)
                .type(type != null ? type : "STRONG_JOB_MATCH")
                .referenceId(referenceId)
                .isRead(false)
                .build();
        return notificationRepository.save(notification);
    }

    /**
     * Daily morning notification runner (runs at 9:00 AM Asia/Kolkata every day).
     * Proactively pre-scores candidates, scans for strong matches (>=75%), and dispatches emails.
     */
    @Scheduled(cron = "0 0 9 * * *", zone = "Asia/Kolkata")
    @Transactional
    public int sendDailyStrongMatchNotifications() {
        if (distributedSchedulerLockService != null && !distributedSchedulerLockService.acquireLock("daily_job_alerts", 30)) {
            log.info("[NOTIFICATION SERVICE] Distributed lock 'daily_job_alerts' is held by another instance. Skipping.");
            return 0;
        }

        try {
            // Proactively ensure active candidates have fresh pre-scored matches
            if (jobDiscoveryService != null) {
                try {
                    List<User> activeUsers = userRepository.findAll().stream()
                            .filter(u -> !Boolean.FALSE.equals(u.getJobAlertsEnabled()))
                            .collect(Collectors.toList());
                    jobDiscoveryService.preScoreForActiveUsers(activeUsers);
                } catch (Exception e) {
                    log.warn("[NOTIFICATION SERVICE] Pre-scoring pass before daily alerts had a non-fatal error: {}", e.getMessage());
                }
            }

            // Lower threshold to 75% for resilient candidate coverage and remove 24h cutoff
            List<UserJobInteraction> unnotified = userJobInteractionRepository.findUnnotifiedStrongMatches(75);
            log.info("[NOTIFICATION SERVICE] Running daily match notification scan. Found {} unnotified strong matches.", unnotified.size());

            // Group by user
            Map<User, List<UserJobInteraction>> byUser = unnotified.stream()
                    .collect(Collectors.groupingBy(UserJobInteraction::getUser));

            // Candidate Fallback: For any active candidate with alerts enabled who has NOT received
            // an alert email in the last 20 hours, include their top active matches so candidates
            // reliably receive their morning alert even if no new jobs were scraped since yesterday.
            try {
                List<User> activeCandidates = userRepository.findAll().stream()
                        .filter(u -> !Boolean.FALSE.equals(u.getJobAlertsEnabled()))
                        .collect(Collectors.toList());

                LocalDateTime last20Hours = LocalDateTime.now().minusHours(20);
                for (User candidate : activeCandidates) {
                    if (!byUser.containsKey(candidate)) {
                        long recentAlerts = notificationRepository.countRecentNotificationsByUserAndType(
                                candidate.getId(), "STRONG_JOB_MATCH", last20Hours);
                        if (recentAlerts == 0) {
                            List<UserJobInteraction> userMatches = userJobInteractionRepository.findByUserIdAndMinMatchScore(candidate.getId(), 50);
                            if (userMatches.isEmpty()) {
                                userMatches = userJobInteractionRepository.findTopActiveInteractionsForUser(candidate.getId());
                            }
                            if (!userMatches.isEmpty()) {
                                byUser.put(candidate, userMatches);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("[NOTIFICATION SERVICE] Fallback daily match grouping encountered error: {}", e.getMessage());
            }

            if (byUser.isEmpty()) {
                log.info("[NOTIFICATION SERVICE] No candidates require daily notifications at this time.");
                return 0;
            }

            int alertCount = 0;
            for (Map.Entry<User, List<UserJobInteraction>> entry : byUser.entrySet()) {
                User user = entry.getKey();
                List<UserJobInteraction> matches = entry.getValue();

                if (Boolean.FALSE.equals(user.getJobAlertsEnabled())) {
                    log.debug("[NOTIFICATION SERVICE] User {} has job alerts disabled. Skipping.", user.getEmail());
                    continue;
                }

                // Ensure user has a persistent unsubscribe token saved
                if (user.getUnsubscribeToken() == null || user.getUnsubscribeToken().isBlank()) {
                    String token = com.crackit.discovery.util.JobUrlNormalizer.sha256(user.getId() + ":" + UUID.randomUUID());
                    user.setUnsubscribeToken(token);
                    User saved = userRepository.save(user);
                    if (saved != null) {
                        user = saved;
                    }
                }

                // Find top match
                matches.sort(Comparator.comparing(UserJobInteraction::getMatchScore).reversed());
                UserJobInteraction topMatch = matches.get(0);
                String topCompany = topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getCompany() : "Top Company";
                String topTitle = topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getTitle() : "Engineer";

                String roleTarget = user.getTargetRole() != null && !user.getTargetRole().isBlank()
                        ? user.getTargetRole()
                        : "your profile";

                String title = String.format("🎯 %d New Strong Job Matches Found", matches.size());
                String message = String.format(
                        "We found %d new openings matching for %s! Top match: %s at %s (%d%% fit). Check them out in Discover.",
                        matches.size(),
                        roleTarget,
                        topTitle,
                        topCompany,
                        topMatch.getMatchScore()
                );

                // Dispatch external Email Alert to candidate via Gmail SMTP
                boolean sent = false;
                try {
                    sent = emailService.sendStrongJobMatchAlert(user, matches);
                } catch (Exception e) {
                    log.warn("[NOTIFICATION SERVICE] Failed to dispatch match email to {}: {}", user.getEmail(), e.getMessage());
                }

                // Mark interactions as notified AND create in-app notification ONLY IF dispatch succeeds
                if (sent) {
                    createNotification(user, title, message, "STRONG_JOB_MATCH", topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getId() : null);
                    alertCount++;
                    for (UserJobInteraction uji : matches) {
                        uji.setNotified(true);
                        userJobInteractionRepository.save(uji);
                    }
                } else {
                    log.warn("[NOTIFICATION SERVICE] Retaining unnotified status for {} matches for user {} due to email delivery failure. Will retry on next run.",
                            matches.size(), user.getEmail());
                }
            }

            log.info("[NOTIFICATION SERVICE] Dispatched daily strong match notifications to {} users.", alertCount);
            return alertCount;
        } finally {
            if (distributedSchedulerLockService != null) {
                distributedSchedulerLockService.releaseLock("daily_job_alerts");
            }
        }
    }

    /**
     * Dispatches daily strong match alert email on-demand for a single user.
     */
    @Transactional
    public boolean sendDailyStrongMatchNotificationForUser(User user) {
        return sendDailyStrongMatchNotificationForUser(user, false);
    }

    /**
     * Dispatches daily strong match alert email on-demand for a single user with option to force-enable alerts.
     */
    @Transactional
    public boolean sendDailyStrongMatchNotificationForUser(User user, boolean forceAlerts) {
        if (user == null) {
            return false;
        }

        if (forceAlerts && Boolean.FALSE.equals(user.getJobAlertsEnabled())) {
            log.info("[NOTIFICATION SERVICE] Force-enabling job alerts for user {}", user.getEmail());
            user.setJobAlertsEnabled(true);
            User saved = userRepository.save(user);
            if (saved != null) user = saved;
        } else if (Boolean.FALSE.equals(user.getJobAlertsEnabled())) {
            log.info("[NOTIFICATION SERVICE] User {} has job alerts explicitly disabled. Skipping.", user.getEmail());
            return false;
        }

        // Proactively pre-score for this specific user
        if (jobDiscoveryService != null) {
            try {
                jobDiscoveryService.preScoreForUser(user);
            } catch (Exception e) {
                log.warn("[NOTIFICATION SERVICE] Single user pre-scoring error for {}: {}", user.getEmail(), e.getMessage());
            }
        }

        List<UserJobInteraction> matches = userJobInteractionRepository.findUnnotifiedStrongMatchesForUser(user.getId(), 70);
        if (matches.isEmpty()) {
            matches = userJobInteractionRepository.findByUserIdAndMinMatchScore(user.getId(), 50);
        }
        if (matches.isEmpty()) {
            matches = userJobInteractionRepository.findTopActiveInteractionsForUser(user.getId());
        }

        if (matches.isEmpty() && discoveredJobRepository != null) {
            log.info("[NOTIFICATION SERVICE] Generating baseline matches for user {} directly from active catalog.", user.getEmail());
            List<DiscoveredJob> baseline = discoveredJobRepository.findTop10ActiveJobs();
            matches = new ArrayList<>();
            for (DiscoveredJob dj : baseline) {
                if (resumeMatchService != null) {
                    ResumeMatchService.MatchResult m = resumeMatchService.calculateTier1Score(user, dj, false);
                    matches.add(resumeMatchService.recordInteraction(user, dj, m, "NEW"));
                }
            }
        }

        if (matches.isEmpty()) {
            log.warn("[NOTIFICATION SERVICE] No strong matches found to notify for user {}", user.getEmail());
            return false;
        }

        // Ensure user has a persistent unsubscribe token saved
        if (user.getUnsubscribeToken() == null || user.getUnsubscribeToken().isBlank()) {
            String token = com.crackit.discovery.util.JobUrlNormalizer.sha256(user.getId() + ":" + UUID.randomUUID());
            user.setUnsubscribeToken(token);
            User saved = userRepository.save(user);
            if (saved != null) user = saved;
        }

        List<UserJobInteraction> topMatches = matches.stream()
                .sorted(Comparator.comparing(UserJobInteraction::getMatchScore).reversed())
                .limit(5)
                .collect(Collectors.toList());
        UserJobInteraction topMatch = topMatches.get(0);
        String topCompany = topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getCompany() : "Top Company";
        String topTitle = topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getTitle() : "Engineer";
        String roleTarget = user.getTargetRole() != null && !user.getTargetRole().isBlank() ? user.getTargetRole() : "your profile";

        String title = String.format("🎯 %d New Strong Job Matches Found", topMatches.size());
        String message = String.format(
                "We found %d new openings matching for %s! Top match: %s at %s (%d%% fit). Check them out in Discover.",
                topMatches.size(),
                roleTarget,
                topTitle,
                topCompany,
                topMatch.getMatchScore()
        );

        boolean sent = false;
        try {
            sent = emailService.sendStrongJobMatchAlert(user, topMatches);
        } catch (Exception e) {
            log.error("[NOTIFICATION SERVICE] Failed to dispatch match email to {}: {}", user.getEmail(), e.getMessage());
        }

        if (sent) {
            createNotification(user, title, message, "STRONG_JOB_MATCH", topMatch.getDiscoveredJob() != null ? topMatch.getDiscoveredJob().getId() : null);
            for (UserJobInteraction uji : topMatches) {
                uji.setNotified(true);
                userJobInteractionRepository.save(uji);
            }
            log.info("[NOTIFICATION SERVICE] Successfully dispatched on-demand daily match alert to {}", user.getEmail());
        }

        return sent;
    }

    public boolean sendTestEmail(User user) {
        String name = user.getFullName() != null && !user.getFullName().isBlank() ? user.getFullName() : user.getEmail();
        return sendTestEmail(user.getEmail(), name, user.getUnsubscribeToken());
    }

    public boolean sendTestEmail(String recipientEmail, String recipientName) {
        return sendTestEmail(recipientEmail, recipientName, null);
    }

    public boolean sendTestEmail(String recipientEmail, String recipientName, String unsubscribeToken) {
        String name = recipientName != null && !recipientName.isBlank() ? recipientName : recipientEmail;
        String subject = "🔔 CrackIt Job Alerts — Test Email Notification";
        String html = String.format("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <meta name='color-scheme' content='light dark'>
                <meta name='supported-color-schemes' content='light dark'>
                <title>CrackIt Email Verification</title>
                <style>
                    :root { color-scheme: light dark; supported-color-schemes: light dark; }
                    body { margin: 0; padding: 0; width: 100%% !important; -webkit-text-size-adjust: 100%%; }
                    @media (prefers-color-scheme: dark) {
                        body, .email-body { background-color: #0b0f19 !important; }
                        .email-container { background-color: #111827 !important; border-color: #1f2937 !important; }
                        .info-box { background-color: #1f2937 !important; border-color: #374151 !important; }
                        .text-title { color: #f9fafb !important; }
                        .text-body { color: #e5e7eb !important; }
                        .footer-box { background-color: #0f172a !important; color: #64748b !important; border-color: #1e293b !important; }
                    }
                </style>
            </head>
            <body class='email-body' style='margin:0;padding:16px 8px;background-color:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;'>
                <div style='max-width:540px;margin:0 auto;'>
                    <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' class='email-container' style='background-color:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;border-collapse:separate;'>
                        <tr>
                            <td style='background:linear-gradient(135deg, #6d28d9 0%%, #8b5cf6 100%%);padding:26px 20px;text-align:center;'>
                                <div style='font-size:22px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;'>CrackIt Job Alerts</div>
                                <div style='font-size:13px;color:#ede9fe;font-weight:500;margin-top:4px;'>Email Delivery Verification</div>
                            </td>
                        </tr>
                        <tr>
                            <td style='padding:22px 18px;'>
                                <p class='text-title' style='font-size:16px;font-weight:700;color:#0f172a;margin:0 0 10px 0;'>Hello %s,</p>
                                <p class='text-body' style='font-size:14px;color:#475569;line-height:1.6;margin:0 0 16px 0;'>
                                    Your CrackIt email notification channel is successfully connected and verified! 
                                    Our AI-powered job discovery engine scans official company career portals (Greenhouse, Lever, Ashby) and verified search listings every morning.
                                </p>
                                <div class='info-box' style='background-color:#f8fafc;border:1px solid #e2e8f0;border-left:4px solid #7c3aed;padding:14px;border-radius:8px;margin:16px 0;font-size:13px;line-height:1.6;'>
                                    <div class='text-body' style='margin-bottom:4px;color:#1e293b;'><strong>✓ Delivery Status:</strong> Connected & Active</div>
                                    <div class='text-body' style='margin-bottom:4px;color:#1e293b;'><strong>✓ Sender Address:</strong> crackit.notifications@gmail.com</div>
                                    <div class='text-body' style='margin-bottom:4px;color:#1e293b;'><strong>✓ Verified Recipient:</strong> %s</div>
                                    <div class='text-body' style='color:#1e293b;'><strong>✓ Notification Trigger:</strong> ≥ 80%% Resume Match Digest</div>
                                </div>
                                <p class='text-body' style='font-size:14px;color:#475569;line-height:1.6;margin:0 0 16px 0;'>
                                    When relevant roles match your skills and target role, you'll receive a curated digest right here with direct application links.
                                </p>
                                <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' style='margin-top:20px;'>
                                    <tr>
                                        <td align='center'>
                                            <a href='https://crackit-app.onrender.com/discover' target='_blank' style='display:inline-block;background-color:#7c3aed;color:#ffffff !important;text-decoration:none;padding:11px 26px;border-radius:8px;font-size:13px;font-weight:700;text-align:center;'>
                                                View Discovered Jobs ↗
                                            </a>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <tr>
                            <td class='footer-box' style='background-color:#f8fafc;padding:16px;text-align:center;font-size:11px;color:#94a3b8;border-top:1px solid #e2e8f0;'>
                                CrackIt AI Career Platform • Sent from crackit.notifications@gmail.com
                            </td>
                        </tr>
                    </table>
                </div>
            </body>
            </html>
        """, name, recipientEmail);

        return emailService.sendEmail(recipientEmail, subject, html, unsubscribeToken);
    }
}
