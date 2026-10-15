package com.crackit.discovery.controller;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.common.util.AuthUtil;
import com.crackit.discovery.entity.Notification;
import com.crackit.discovery.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final UserRepository userRepository;

    @GetMapping
    public List<Notification> getNotifications() {
        User user = getLoggedInUser();
        return notificationService.getNotificationsForUser(user.getId());
    }

    @GetMapping("/unread-count")
    public Map<String, Long> getUnreadCount() {
        User user = getLoggedInUser();
        long count = notificationService.getUnreadCount(user.getId());
        return Map.of("unreadCount", count);
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<Void> markAsRead(@PathVariable String id) {
        User user = getLoggedInUser();
        notificationService.markAsRead(id, user.getId());
        return ResponseEntity.ok().build();
    }

    @PutMapping("/read-all")
    public ResponseEntity<Void> markAllAsRead() {
        User user = getLoggedInUser();
        notificationService.markAllAsRead(user.getId());
        return ResponseEntity.ok().build();
    }

    @PostMapping("/test-email")
    public ResponseEntity<Map<String, Object>> sendTestEmail(@RequestParam(required = false) String email) {
        String targetEmail;
        String targetName = "Candidate";
        try {
            User user = getLoggedInUser();
            targetEmail = (email != null && !email.isBlank()) ? email.trim() : user.getEmail();
            if (user.getFullName() != null && !user.getFullName().isBlank()) {
                targetName = user.getFullName();
            }
        } catch (Exception e) {
            targetEmail = (email != null && !email.isBlank()) ? email.trim() : "divyanshu5981.iimt@gmail.com";
        }

        boolean sent = notificationService.sendTestEmail(targetEmail, targetName);
        return ResponseEntity.ok(Map.of(
                "success", sent,
                "recipient", targetEmail,
                "message", sent ? "Test email dispatched successfully via crackit.notifications@gmail.com!" : "Failed to deliver test email. Check server logs."
        ));
    }

    @GetMapping("/preferences")
    public ResponseEntity<Map<String, Object>> getPreferences() {
        User user = getLoggedInUser();
        return ResponseEntity.ok(Map.of(
                "jobAlertsEnabled", user.getJobAlertsEnabled() != null ? user.getJobAlertsEnabled() : true,
                "email", user.getEmail()
        ));
    }

    @PutMapping("/preferences")
    public ResponseEntity<Map<String, Object>> updatePreferences(@RequestBody Map<String, Boolean> body) {
        User user = getLoggedInUser();
        Boolean enabled = body.get("jobAlertsEnabled");
        if (enabled != null) {
            user.setJobAlertsEnabled(enabled);
            userRepository.save(user);
        }
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "jobAlertsEnabled", user.getJobAlertsEnabled() != null ? user.getJobAlertsEnabled() : true
        ));
    }

    private User getLoggedInUser() {
        String email = AuthUtil.getLoggedInUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}
