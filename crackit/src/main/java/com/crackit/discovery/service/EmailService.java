package com.crackit.discovery.service;

import com.crackit.auth.entity.User;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.UserJobInteraction;
import jakarta.mail.Authenticator;
import jakarta.mail.Message;
import jakarta.mail.PasswordAuthentication;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Properties;
import java.util.UUID;

import lombok.Setter;

@Slf4j
@Service
@Setter
@RequiredArgsConstructor
public class EmailService {

    private final ResumeMatchService resumeMatchService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.crackit.auth.repository.UserRepository userRepository;

    @Value("${mail.enabled:true}")
    private boolean mailEnabled = true;

    @Value("${mail.smtp.host:smtp.gmail.com}")
    private String host = "smtp.gmail.com";

    @Value("${mail.smtp.port:587}")
    private int port = 587;

    @Value("${mail.smtp.username:crackit.notifications@gmail.com}")
    private String username = "crackit.notifications@gmail.com";

    @Value("${mail.smtp.password:xsfsnrbukazpxnas}")
    private String password = "xsfsnrbukazpxnas";

    @Value("${mail.smtp.from:crackit.notifications@gmail.com}")
    private String fromEmail = "crackit.notifications@gmail.com";

    @Value("${mail.smtp.from-name:CrackIt Job Alerts}")
    private String fromName = "CrackIt Job Alerts";

    @Value("${mail.unsubscribe-url:https://crackit-backend-4bpr.onrender.com/api/public/unsubscribe}")
    private String unsubscribeBaseUrl = "https://crackit-backend-4bpr.onrender.com/api/public/unsubscribe";

    @Value("${mail.track-apply-url:https://crackit-backend-4bpr.onrender.com/api/public/jobs/track-applied}")
    private String trackApplyBaseUrl = "https://crackit-backend-4bpr.onrender.com/api/public/jobs/track-applied";

    @Value("${mail.resend.api-key:${RESEND_API_KEY:}}")
    private String resendApiKey = "";

    @Value("${mail.brevo.api-key:${BREVO_API_KEY:}}")
    private String brevoApiKey = "";

    /**
     * Sends an HTML email via Gmail SMTP using STARTTLS.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlBody) {
        return sendEmail(toEmail, subject, htmlBody, null);
    }

    /**
     * Sends an HTML email with RFC 8058 one-click unsubscribe headers.
     */
    public boolean sendEmail(String toEmail, String subject, String htmlBody, String unsubscribeToken) {
        if (!mailEnabled) {
            log.info("[EMAIL SERVICE] Mail delivery disabled via configuration.");
            return false;
        }

        if (toEmail == null || toEmail.isBlank() || !toEmail.contains("@")) {
            log.warn("[EMAIL SERVICE] Invalid recipient email address: '{}'", toEmail);
            return false;
        }

        // 1. Check for Resend HTTPS API (runs over port 443 - bypasses Render SMTP block)
        if (resendApiKey != null && !resendApiKey.isBlank()) {
            boolean httpSent = sendViaResend(toEmail, subject, htmlBody);
            if (httpSent) return true;
            log.warn("[EMAIL SERVICE] Resend HTTPS delivery failed. Falling back to next channel.");
        }

        // 2. Check for Brevo HTTPS API (runs over port 443 - bypasses Render SMTP block)
        if (brevoApiKey != null && !brevoApiKey.isBlank()) {
            boolean httpSent = sendViaBrevo(toEmail, subject, htmlBody);
            if (httpSent) return true;
            log.warn("[EMAIL SERVICE] Brevo HTTPS delivery failed. Falling back to next channel.");
        }

        String cleanPassword = password != null ? password.replace(" ", "").trim() : "";
        if (cleanPassword.isBlank()) {
            log.warn("[EMAIL SERVICE] SMTP password is empty. Cannot deliver email.");
            return false;
        }

        try {
            Properties props = new Properties();
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            props.put("mail.smtp.starttls.required", "true");
            props.put("mail.smtp.host", host);
            props.put("mail.smtp.port", String.valueOf(port));
            props.put("mail.smtp.ssl.protocols", "TLSv1.2 TLSv1.3");
            props.put("mail.smtp.connectiontimeout", "10000");
            props.put("mail.smtp.timeout", "10000");

            Session session = Session.getInstance(props, new Authenticator() {
                @Override
                protected PasswordAuthentication getPasswordAuthentication() {
                    return new PasswordAuthentication(username.trim(), cleanPassword);
                }
            });

            MimeMessage message = new MimeMessage(session);
            message.setFrom(new InternetAddress(fromEmail, fromName, "UTF-8"));
            message.setRecipient(Message.RecipientType.TO, new InternetAddress(toEmail.trim()));
            message.setSubject(subject, "UTF-8");

            // RFC 8058 One-Click List-Unsubscribe headers
            if (unsubscribeToken != null && !unsubscribeToken.isBlank()) {
                String unsubUrl = unsubscribeBaseUrl + "?token=" + unsubscribeToken.trim();
                message.setHeader("List-Unsubscribe", "<" + unsubUrl + ">, <mailto:" + fromEmail + "?subject=Unsubscribe>");
                message.setHeader("List-Unsubscribe-Post", "List-Unsubscribe=One-Click");
            }

            message.setContent(htmlBody, "text/html; charset=UTF-8");

            log.info("[EMAIL SERVICE] Dispatching email to '{}' with subject: '{}'", toEmail, subject);
            Transport.send(message);
            log.info("[EMAIL SERVICE] Successfully delivered email to '{}'", toEmail);
            return true;

        } catch (Exception e) {
            log.error("[EMAIL SERVICE] Failed to deliver email to '{}': {}", toEmail, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Builds and sends the daily strong matches digest to the candidate.
     */
    public boolean sendStrongJobMatchAlert(User user, List<UserJobInteraction> matches) {
        if (user == null || user.getEmail() == null || matches == null || matches.isEmpty()) {
            return false;
        }

        String candidateName = user.getFullName() != null && !user.getFullName().isBlank()
                ? user.getFullName()
                : "Candidate";

        String targetRole = user.getTargetRole() != null && !user.getTargetRole().isBlank()
                ? user.getTargetRole()
                : "your career profile";

        String token = user.getUnsubscribeToken();
        if (token == null || token.isBlank()) {
            token = com.crackit.discovery.util.JobUrlNormalizer.sha256(user.getId() + ":" + UUID.randomUUID());
            user.setUnsubscribeToken(token);
            if (userRepository != null) {
                user = userRepository.save(user);
            }
        }

        String subject = String.format("🎯 %d New Strong Job Matches Found (%s) - CrackIt", matches.size(), targetRole);
        String htmlBody = buildMatchAlertHtml(candidateName, targetRole, matches, token);

        return sendEmail(user.getEmail(), subject, htmlBody, token);
    }

    public String buildMatchAlertHtml(String candidateName, String targetRole, List<UserJobInteraction> matches) {
        return buildMatchAlertHtml(candidateName, targetRole, matches, null);
    }

    public String buildMatchAlertHtml(String candidateName, String targetRole, List<UserJobInteraction> matches, String unsubscribeToken) {
        StringBuilder cards = new StringBuilder();

        int count = 0;
        for (UserJobInteraction m : matches) {
            if (count++ >= 5) break; // Top 5 in email

            DiscoveredJob job = m.getDiscoveredJob();
            if (job == null) continue;

            String title = job.getTitle() != null ? job.getTitle() : "Software Engineer";
            String company = job.getCompany() != null ? job.getCompany() : "Hiring Company";
            String location = job.getLocation() != null && !job.getLocation().isBlank() ? job.getLocation() : "Remote / Flexible";
            int score = m.getMatchScore() != null ? m.getMatchScore() : 85;
            String applyUrl = job.getUrl() != null ? job.getUrl() : "https://crackit-app.onrender.com/discover";
            String trackAppliedUrl = (unsubscribeToken != null && !unsubscribeToken.isBlank() && job.getId() != null)
                    ? trackApplyBaseUrl + "?jobId=" + java.net.URLEncoder.encode(job.getId(), java.nio.charset.StandardCharsets.UTF_8)
                    + "&token=" + java.net.URLEncoder.encode(unsubscribeToken.trim(), java.nio.charset.StandardCharsets.UTF_8)
                    : "https://crackit-app-five.vercel.app/tracker";

            List<String> skills = resumeMatchService != null ? resumeMatchService.fromJson(m.getMatchedSkills()) : List.of();
            StringBuilder skillChips = new StringBuilder();
            for (String s : skills.stream().limit(5).toList()) {
                skillChips.append(String.format(
                        "<span class='chip-skill' style='display:inline-block;white-space:nowrap;background-color:#f1f5f9;color:#0f172a;border:1px solid #cbd5e1;padding:3px 8px;border-radius:6px;font-size:11px;font-weight:600;margin-right:6px;margin-bottom:6px;'>✓ %s</span>",
                        s
                ));
            }

            String sourceBadge = "CAREER_PAGE_ATS".equalsIgnoreCase(job.getSourceType()) || "CAREER_PAGE".equalsIgnoreCase(job.getSource())
                    ? "<span class='portal-badge' style='display:inline-block;white-space:nowrap;background-color:#ede9fe;color:#5b21b6;border:1px solid #ddd6fe;padding:3px 8px;border-radius:6px;font-size:10px;font-weight:700;letter-spacing:0.4px;'>🏢 OFFICIAL CAREER PORTAL</span>"
                    : "<span class='portal-badge' style='display:inline-block;white-space:nowrap;background-color:#e0f2fe;color:#0369a1;border:1px solid #bae6fd;padding:3px 8px;border-radius:6px;font-size:10px;font-weight:700;letter-spacing:0.4px;'>VERIFIED OPENING</span>";

            cards.append(String.format("""
                <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' class='card-box' style='background-color:#ffffff;border:1px solid #e2e8f0;border-radius:12px;margin-bottom:14px;border-collapse:separate;'>
                    <tr>
                        <td style='padding:16px;'>
                            <!-- Badge row -->
                            <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' style='margin-bottom:10px;'>
                                <tr>
                                    <td align='left' style='vertical-align:middle;'>
                                        %s
                                    </td>
                                    <td align='right' style='vertical-align:middle;'>
                                        <span style='display:inline-block;white-space:nowrap;background-color:#059669;color:#ffffff !important;padding:4px 10px;border-radius:99px;font-size:11px;font-weight:800;letter-spacing:0.3px;'>
                                            🎯 %d%%%% Fit
                                        </span>
                                    </td>
                                </tr>
                            </table>

                            <!-- Title -->
                            <div class='text-title' style='font-size:16px;font-weight:800;color:#0f172a;line-height:1.35;margin-bottom:6px;'>
                                %s
                            </div>

                            <!-- Company & Location -->
                            <div style='font-size:13px;line-height:1.4;margin-bottom:12px;'>
                                <span class='text-company' style='color:#6d28d9;font-weight:700;'>%s</span>
                                <span style='color:#94a3b8;margin:0 4px;'>•</span>
                                <span class='text-location' style='color:#64748b;font-weight:500;'>%s</span>
                            </div>

                            <!-- Skills -->
                            <div style='margin-bottom:14px;line-height:1.8;'>
                                %s
                            </div>

                            <!-- Action Buttons -->
                            <table role='presentation' cellpadding='0' cellspacing='0' style='margin-top:4px;'>
                                <tr>
                                    <td style='padding-right:8px;vertical-align:middle;'>
                                        <a href='%s' target='_blank' style='display:inline-block;background-color:#7c3aed;color:#ffffff !important;text-decoration:none;padding:9px 16px;border-radius:8px;font-size:12px;font-weight:700;text-align:center;'>
                                            View & Apply ↗
                                        </a>
                                    </td>
                                    <td style='vertical-align:middle;'>
                                        <a href='%s' target='_blank' style='display:inline-block;background-color:#059669;color:#ffffff !important;text-decoration:none;padding:9px 16px;border-radius:8px;font-size:12px;font-weight:700;text-align:center;'>
                                            ✓ Mark as Applied
                                        </a>
                                    </td>
                                </tr>
                            </table>
                        </td>
                    </tr>
                </table>
            """, sourceBadge, score, title, company, location, skillChips.toString(), applyUrl, trackAppliedUrl));
        }

        String unsubLink = (unsubscribeToken != null && !unsubscribeToken.isBlank())
                ? unsubscribeBaseUrl + "?token=" + unsubscribeToken.trim()
                : unsubscribeBaseUrl;

        return String.format("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <meta name='color-scheme' content='light dark'>
                <meta name='supported-color-schemes' content='light dark'>
                <title>CrackIt Job Discovery</title>
                <style>
                    :root {
                        color-scheme: light dark;
                        supported-color-schemes: light dark;
                    }
                    body {
                        margin: 0;
                        padding: 0;
                        width: 100%% !important;
                        -webkit-text-size-adjust: 100%%;
                        -ms-text-size-adjust: 100%%;
                    }
                    @media (prefers-color-scheme: dark) {
                        body, .email-body { background-color: #0b0f19 !important; }
                        .email-container { background-color: #111827 !important; border-color: #1f2937 !important; }
                        .card-box { background-color: #1f2937 !important; border-color: #374151 !important; }
                        .text-title { color: #f9fafb !important; }
                        .text-company { color: #c4b5fd !important; }
                        .text-location { color: #9ca3af !important; }
                        .text-body { color: #e5e7eb !important; }
                        .chip-skill { background-color: #374151 !important; color: #f3f4f6 !important; border-color: #4b5563 !important; }
                        .portal-badge { background-color: #312e81 !important; color: #c7d2fe !important; border-color: #4338ca !important; }
                        .footer-box { background-color: #0f172a !important; color: #64748b !important; border-color: #1e293b !important; }
                    }
                </style>
            </head>
            <body class='email-body' style='margin:0;padding:16px 8px;background-color:#f1f5f9;font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,Helvetica,Arial,sans-serif;'>
                <div style='max-width:580px;margin:0 auto;'>
                    <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' class='email-container' style='background-color:#ffffff;border-radius:16px;overflow:hidden;border:1px solid #e2e8f0;border-collapse:separate;'>
                        <!-- Header -->
                        <tr>
                            <td style='background:linear-gradient(135deg, #6d28d9 0%%, #8b5cf6 100%%);padding:26px 20px;text-align:center;'>
                                <div style='font-size:24px;font-weight:800;color:#ffffff;letter-spacing:-0.5px;margin:0;'>CrackIt</div>
                                <div style='font-size:13px;color:#ede9fe;font-weight:500;margin-top:4px;letter-spacing:0.2px;'>AI-Powered Job Discovery Digest</div>
                            </td>
                        </tr>
                        <!-- Content -->
                        <tr>
                            <td style='padding:22px 18px;'>
                                <p class='text-body' style='font-size:15px;font-weight:700;color:#0f172a;margin:0 0 8px 0;'>Hi %s,</p>
                                <p class='text-body' style='font-size:14px;line-height:1.5;color:#475569;margin:0 0 20px 0;'>
                                     We discovered <strong style='color:#6d28d9;'>%d verified openings</strong> matching <strong>80%%+</strong> with your resume profile and target role (<strong>%s</strong>).
                                </p>

                                <!-- Job Cards -->
                                %s

                                <!-- View Dashboard CTA -->
                                <table role='presentation' width='100%%' cellpadding='0' cellspacing='0' style='margin-top:18px;padding-top:16px;border-top:1px solid #e2e8f0;'>
                                    <tr>
                                        <td align='center'>
                                            <a href='https://crackit-app.onrender.com/discover' target='_blank' style='display:inline-block;background-color:#0f172a;color:#ffffff !important;text-decoration:none;padding:12px 28px;border-radius:10px;font-size:14px;font-weight:700;text-align:center;'>
                                                Open Discover Dashboard →
                                            </a>
                                        </td>
                                    </tr>
                                </table>
                            </td>
                        </tr>
                        <!-- Footer -->
                        <tr>
                            <td class='footer-box' style='background-color:#f8fafc;padding:18px 16px;text-align:center;font-size:11px;line-height:1.6;color:#94a3b8;border-top:1px solid #e2e8f0;'>
                                Sent by CrackIt AI Job Discovery Engine • Sender: <span style='color:#64748b;'>crackit.notifications@gmail.com</span><br/>
                                You are receiving this digest based on your daily job alert preferences.<br/>
                                <a href='%s' target='_blank' style='color:#7c3aed;text-decoration:underline;margin-top:6px;display:inline-block;'>Unsubscribe from daily job alerts</a>
                            </td>
                        </tr>
                    </table>
                </div>
            </body>
            </html>
        """, candidateName, matches.size(), targetRole, cards.toString(), unsubLink);
    }

    private boolean sendViaResend(String toEmail, String subject, String htmlBody) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "from", fromName + " <onboarding@resend.dev>",
                    "to", java.util.List.of(toEmail.trim()),
                    "subject", subject,
                    "html", htmlBody
            );

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.resend.com/emails"))
                    .header("Authorization", "Bearer " + resendApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("[EMAIL SERVICE] Delivered email via Resend HTTPS API to '{}'", toEmail);
                return true;
            } else {
                log.warn("[EMAIL SERVICE] Resend API returned status {}: {}", res.statusCode(), res.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[EMAIL SERVICE] Resend HTTPS delivery error: {}", e.getMessage());
            return false;
        }
    }

    private boolean sendViaBrevo(String toEmail, String subject, String htmlBody) {
        try {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(java.time.Duration.ofSeconds(10))
                    .build();

            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> sender = java.util.Map.of("name", fromName, "email", "crackit.notifications@gmail.com");
            java.util.Map<String, Object> recipient = java.util.Map.of("email", toEmail.trim());
            java.util.Map<String, Object> payload = java.util.Map.of(
                    "sender", sender,
                    "to", java.util.List.of(recipient),
                    "subject", subject,
                    "htmlContent", htmlBody
            );

            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create("https://api.brevo.com/v3/smtp/email"))
                    .header("api-key", brevoApiKey.trim())
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(payload)))
                    .timeout(java.time.Duration.ofSeconds(10))
                    .build();

            java.net.http.HttpResponse<String> res = client.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() >= 200 && res.statusCode() < 300) {
                log.info("[EMAIL SERVICE] Delivered email via Brevo HTTPS API to '{}'", toEmail);
                return true;
            } else {
                log.warn("[EMAIL SERVICE] Brevo API returned status {}: {}", res.statusCode(), res.body());
                return false;
            }
        } catch (Exception e) {
            log.error("[EMAIL SERVICE] Brevo HTTPS delivery error: {}", e.getMessage());
            return false;
        }
    }
}
