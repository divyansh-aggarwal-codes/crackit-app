package com.crackit.discovery.controller;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.service.NotificationService;
import com.crackit.tracker.dto.TrackApplyRequest;
import com.crackit.tracker.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/public/jobs")
@RequiredArgsConstructor
public class PublicJobTrackingController {

    private final UserRepository userRepository;
    private final DiscoveredJobRepository discoveredJobRepository;
    private final JobApplicationService jobApplicationService;
    private final NotificationService notificationService;

    @Value("${app.frontend-url:https://crackit-app-five.vercel.app}")
    private String frontendUrl = "https://crackit-app-five.vercel.app";

    /**
     * Public webhook endpoint to trigger daily job match alerts.
     * Can be invoked by external schedulers (e.g. cron-job.org) or manually to wake Render and dispatch alerts.
     */
    @RequestMapping(value = "/dispatch-alerts", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> dispatchAlertsWebhook(
            @RequestParam(value = "email", required = false) String email
    ) {
        log.info("[WEBHOOK] Received dispatch-alerts trigger request. Target email: {}", email);
        if (email != null && !email.isBlank()) {
            Optional<User> userOpt = userRepository.findByEmail(email.trim());
            if (userOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of(
                        "success", false,
                        "message", "User not found with email: " + email
                ));
            }
            User user = userOpt.get();
            boolean dispatched = notificationService.sendDailyStrongMatchNotificationForUser(user);
            return ResponseEntity.ok(Map.of(
                    "success", dispatched,
                    "target", user.getEmail(),
                    "message", dispatched ? "Daily strong match alert dispatched successfully." : "No new qualifying matches found or alerts disabled."
            ));
        }

        int count = notificationService.sendDailyStrongMatchNotifications();
        return ResponseEntity.ok(Map.of(
                "success", true,
                "dispatchedCount", count,
                "message", String.format("Daily alert job executed. Dispatched alerts to %d users.", count)
        ));
    }

    @GetMapping(value = "/track-applied", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> trackAppliedFromEmail(
            @RequestParam(value = "jobId", required = false) String jobId,
            @RequestParam(value = "token", required = false) String token
    ) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(renderErrorPage(
                    "Invalid Request",
                    "No security token was provided in the tracking link. Please log in directly to CrackIt."
            ));
        }

        Optional<User> userOpt = userRepository.findByUnsubscribeToken(token.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(renderErrorPage(
                    "Link Expired",
                    "This tracking link is invalid or has expired. Please log in to CrackIt to track your applications."
            ));
        }

        User user = userOpt.get();

        if (jobId == null || jobId.isBlank()) {
            return ResponseEntity.badRequest().body(renderErrorPage(
                    "Missing Job Identifier",
                    "No job was specified to track. Please check your alert email."
            ));
        }

        Optional<DiscoveredJob> jobOpt = discoveredJobRepository.findById(jobId.trim());
        if (jobOpt.isEmpty()) {
            return ResponseEntity.ok(renderErrorPage(
                    "Job Not Found",
                    "This job opening could not be located or may have been archived. You can manually add it in CrackIt."
            ));
        }

        DiscoveredJob dj = jobOpt.get();

        TrackApplyRequest req = TrackApplyRequest.builder()
                .jobId(dj.getId())
                .companyName(dj.getCompany() != null ? dj.getCompany() : "Hiring Company")
                .title(dj.getTitle() != null ? dj.getTitle() : "Software Engineer")
                .location(dj.getLocation())
                .applyUrl(dj.getUrl())
                .jdText(dj.getDescription())
                .source(dj.getSource() != null ? dj.getSource() : "EMAIL_ALERT")
                .notes("Added via 1-click email alert link")
                .build();

        jobApplicationService.trackApplyForUser(user, req);

        log.info("[EMAIL TRACK] User {} tracked application for '{}' at '{}'",
                user.getEmail(), dj.getTitle(), dj.getCompany());

        String destinationUrl = frontendUrl + "/tracker?tracked=applied";
        return ResponseEntity.ok(renderSuccessPage(dj.getTitle(), dj.getCompany(), destinationUrl));
    }

    private String renderSuccessPage(String title, String company, String redirectUrl) {
        String safeTitle = title != null ? escapeHtml(title) : "Software Engineer";
        String safeCompany = company != null ? escapeHtml(company) : "Hiring Company";

        return String.format("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <meta http-equiv='refresh' content='3;url=%s'>
                <title>Application Tracked — CrackIt</title>
                <style>
                    body {
                        margin: 0;
                        padding: 40px 16px;
                        background-color: #0f172a;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        color: #f8fafc;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 85vh;
                    }
                    .card {
                        background-color: #1e293b;
                        border: 1px solid #334155;
                        border-radius: 20px;
                        max-width: 490px;
                        width: 100%%;
                        padding: 36px 28px;
                        text-align: center;
                        box-shadow: 0 15px 35px -5px rgba(0, 0, 0, 0.45);
                    }
                    .icon-check {
                        width: 64px;
                        height: 64px;
                        border-radius: 50%%;
                        background: rgba(16, 185, 129, 0.15);
                        border: 2px solid #10b981;
                        color: #10b981;
                        display: inline-flex;
                        align-items: center;
                        justify-content: center;
                        font-size: 32px;
                        margin-bottom: 18px;
                    }
                    .logo {
                        font-size: 13px;
                        font-weight: 800;
                        letter-spacing: 1.2px;
                        color: #a78bfa;
                        text-transform: uppercase;
                        margin-bottom: 6px;
                    }
                    .headline {
                        font-size: 22px;
                        font-weight: 800;
                        margin-bottom: 14px;
                        color: #ffffff;
                    }
                    .job-box {
                        background-color: #0f172a;
                        border: 1px solid #334155;
                        border-radius: 12px;
                        padding: 16px;
                        margin-bottom: 22px;
                        text-align: left;
                    }
                    .job-title {
                        font-size: 15px;
                        font-weight: 700;
                        color: #f1f5f9;
                        margin-bottom: 4px;
                    }
                    .job-company {
                        font-size: 13px;
                        color: #a78bfa;
                        font-weight: 600;
                    }
                    .badge-applied {
                        display: inline-block;
                        background-color: #065f46;
                        color: #a7f3d0;
                        border: 1px solid #059669;
                        padding: 3px 9px;
                        border-radius: 99px;
                        font-size: 11px;
                        font-weight: 700;
                        margin-top: 8px;
                    }
                    .desc {
                        font-size: 13.5px;
                        line-height: 1.6;
                        color: #94a3b8;
                        margin-bottom: 24px;
                    }
                    .btn-tracker {
                        display: block;
                        background-color: #7c3aed;
                        color: #ffffff !important;
                        text-decoration: none;
                        padding: 13px 24px;
                        font-size: 14px;
                        font-weight: 700;
                        border-radius: 10px;
                        transition: background-color 0.2s;
                        box-shadow: 0 4px 14px rgba(124, 58, 237, 0.35);
                    }
                    .btn-tracker:hover {
                        background-color: #6d28d9;
                    }
                    .redirect-note {
                        font-size: 12px;
                        color: #64748b;
                        margin-top: 16px;
                    }
                </style>
            </head>
            <body>
                <div class='card'>
                    <div class='icon-check'>✓</div>
                    <div class='logo'>CrackIt Application Tracker</div>
                    <div class='headline'>Application Added to Pipeline!</div>
                    <div class='job-box'>
                        <div class='job-title'>%s</div>
                        <div class='job-company'>%s</div>
                        <div class='badge-applied'>STATUS: APPLIED</div>
                    </div>
                    <div class='desc'>
                        This opening has been linked to your candidate dashboard and marked as applied today.
                    </div>
                    <a href='%s' class='btn-tracker'>View Application Pipeline →</a>
                    <div class='redirect-note'>Redirecting you automatically in 3 seconds...</div>
                </div>
            </body>
            </html>
        """, redirectUrl, safeTitle, safeCompany, redirectUrl);
    }

    private String renderErrorPage(String title, String message) {
        String safeTitle = escapeHtml(title);
        String safeMessage = escapeHtml(message);

        return String.format("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <title>%s — CrackIt</title>
                <style>
                    body {
                        margin: 0;
                        padding: 40px 16px;
                        background-color: #0f172a;
                        font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                        color: #f8fafc;
                        display: flex;
                        justify-content: center;
                        align-items: center;
                        min-height: 80vh;
                    }
                    .card {
                        background-color: #1e293b;
                        border: 1px solid #334155;
                        border-radius: 16px;
                        max-width: 480px;
                        width: 100%%;
                        padding: 32px 24px;
                        text-align: center;
                    }
                    .logo {
                        font-size: 24px;
                        font-weight: 800;
                        color: #a78bfa;
                        margin-bottom: 8px;
                    }
                    .title {
                        font-size: 19px;
                        font-weight: 700;
                        margin-bottom: 12px;
                        color: #f87171;
                    }
                    .desc {
                        font-size: 14px;
                        line-height: 1.6;
                        color: #94a3b8;
                        margin-bottom: 24px;
                    }
                    .btn-home {
                        display: inline-block;
                        background-color: #7c3aed;
                        color: #ffffff;
                        text-decoration: none;
                        padding: 10px 22px;
                        font-size: 13px;
                        font-weight: 600;
                        border-radius: 8px;
                    }
                </style>
            </head>
            <body>
                <div class='card'>
                    <div class='logo'>CrackIt</div>
                    <div class='title'>%s</div>
                    <div class='desc'>%s</div>
                    <a href='%s' class='btn-home'>Go to CrackIt Dashboard</a>
                </div>
            </body>
            </html>
        """, safeTitle, safeTitle, safeMessage, frontendUrl);
    }

    private String escapeHtml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }
}
