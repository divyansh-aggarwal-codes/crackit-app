package com.crackit.discovery.controller;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@Slf4j
@RestController
@RequestMapping("/api/public/unsubscribe")
@RequiredArgsConstructor
public class UnsubscribeController {

    private final UserRepository userRepository;

    /**
     * RFC 8058 One-Click POST Unsubscribe Endpoint:
     * Dispatched by email clients (e.g. Gmail, Yahoo Mail) when clicking their native Unsubscribe button,
     * or submitted by our web confirmation page.
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> unsubscribePost(@RequestParam(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Missing or invalid unsubscribe token."
            ));
        }

        Optional<User> userOpt = userRepository.findByUnsubscribeToken(token.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "ERROR",
                    "message", "Invalid or expired unsubscribe token."
            ));
        }

        User user = userOpt.get();
        user.setJobAlertsEnabled(false);
        userRepository.save(user);

        log.info("[UNSUBSCRIBE] User {} successfully unsubscribed from daily job alert emails.", user.getEmail());

        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "You have successfully unsubscribed from CrackIt daily job alert emails.",
                "email", maskEmail(user.getEmail())
        ));
    }

    /**
     * Scanner-Safe GET Landing Page:
     * Automated security crawlers/antivirus link scanners often follow GET links in emails.
     * Rendering an interactive confirmation page prevents accidental unsubscriptions.
     */
    @GetMapping(produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribeLandingPage(@RequestParam(value = "token", required = false) String token) {
        if (token == null || token.isBlank()) {
            return ResponseEntity.ok(renderMessagePage(
                    "Invalid Request",
                    "No unsubscribe token was provided. If you need help with your notification preferences, please log in to CrackIt.",
                    false,
                    null
            ));
        }

        Optional<User> userOpt = userRepository.findByUnsubscribeToken(token.trim());
        if (userOpt.isEmpty()) {
            return ResponseEntity.ok(renderMessagePage(
                    "Link Expired",
                    "This unsubscribe link is invalid or has expired. You may already be unsubscribed or your profile was updated.",
                    false,
                    null
            ));
        }

        User user = userOpt.get();
        String masked = maskEmail(user.getEmail());

        if (Boolean.FALSE.equals(user.getJobAlertsEnabled())) {
            return ResponseEntity.ok(renderMessagePage(
                    "Already Unsubscribed",
                    String.format("The account associated with <strong>%s</strong> is already unsubscribed from daily job alerts.", masked),
                    false,
                    null
            ));
        }

        return ResponseEntity.ok(renderConfirmPage(masked, token.trim()));
    }

    private String renderConfirmPage(String maskedEmail, String token) {
        return String.format("""
            <!DOCTYPE html>
            <html lang='en'>
            <head>
                <meta charset='utf-8'>
                <meta name='viewport' content='width=device-width, initial-scale=1.0'>
                <title>Manage Subscription — CrackIt</title>
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
                        box-shadow: 0 10px 25px -5px rgba(0, 0, 0, 0.4);
                    }
                    .logo {
                        font-size: 26px;
                        font-weight: 800;
                        color: #a78bfa;
                        margin-bottom: 8px;
                        letter-spacing: -0.5px;
                    }
                    .title {
                        font-size: 19px;
                        font-weight: 700;
                        margin-bottom: 12px;
                        color: #ffffff;
                    }
                    .desc {
                        font-size: 14px;
                        line-height: 1.6;
                        color: #94a3b8;
                        margin-bottom: 24px;
                    }
                    .btn-unsub {
                        background-color: #dc2626;
                        color: #ffffff;
                        border: none;
                        padding: 12px 24px;
                        font-size: 14px;
                        font-weight: 600;
                        border-radius: 8px;
                        cursor: pointer;
                        transition: background-color 0.2s;
                        width: 100%%;
                    }
                    .btn-unsub:hover {
                        background-color: #b91c1c;
                    }
                    .footer-note {
                        font-size: 12px;
                        color: #64748b;
                        margin-top: 20px;
                    }
                </style>
            </head>
            <body>
                <div class='card'>
                    <div class='logo'>CrackIt</div>
                    <div class='title'>Unsubscribe from Daily Job Alerts?</div>
                    <div class='desc'>
                        You are managing email preferences for <strong>%s</strong>.<br/><br/>
                        If you confirm, you will no longer receive 9:00 AM daily job matches matching your resume profile.
                    </div>
                    <form method='POST' action='/api/public/unsubscribe'>
                        <input type='hidden' name='token' value='%s' />
                        <button type='submit' class='btn-unsub'>Yes, Unsubscribe Me</button>
                    </form>
                    <div class='footer-note'>
                        You can always re-enable job alerts in your CrackIt profile settings at any time.
                    </div>
                </div>
            </body>
            </html>
        """, maskedEmail, token);
    }

    private String renderMessagePage(String title, String message, boolean isSuccess, String extra) {
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
                        font-size: 26px;
                        font-weight: 800;
                        color: #a78bfa;
                        margin-bottom: 8px;
                    }
                    .title {
                        font-size: 19px;
                        font-weight: 700;
                        margin-bottom: 12px;
                        color: #ffffff;
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
                    <a href='https://crackit-app.onrender.com' class='btn-home'>Go to CrackIt Home</a>
                </div>
            </body>
            </html>
        """, title, title, message);
    }

    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIdx = email.indexOf('@');
        if (atIdx <= 2) return email;
        return email.charAt(0) + "***" + email.substring(atIdx - 1);
    }
}
