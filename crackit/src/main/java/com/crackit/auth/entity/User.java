package com.crackit.auth.entity;

import com.crackit.payment.enums.SubscriptionTier;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(nullable = false, unique = true, length = 150)
    private String email;

    @Column(length = 20)
    private String phone;

    @Column(length = 150)
    private String location;

    @Column(name = "password_hash", columnDefinition = "TEXT")
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "`role`", length = 20)
    @Builder.Default
    private com.crackit.auth.enums.Role role = com.crackit.auth.enums.Role.ROLE_USER;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    @Column(name = "auth_provider", length = 30)
    @Builder.Default
    private String authProvider = "LOCAL";

    @Column(name = "google_id", length = 100)
    private String googleId;

    @Column(name = "years_experience")
    private Integer yearsExperience;

    @Column(name = "current_company", length = 150)
    private String currentCompany;

    @Column(name = "`current_role`", length = 150)
    private String currentRole;

    @Column(name = "target_role", length = 150)
    private String targetRole;

    @Column(name = "current_ctc", length = 50)
    private String currentCtc;

    @Column(name = "expected_ctc", length = 50)
    private String expectedCtc;

    @Column(name = "notice_period", length = 50)
    private String noticePeriod;

    @Column(name = "serving_notice")
    @Builder.Default
    private Boolean servingNotice = false;

    @Column(name = "last_working_day", length = 30)
    private String lastWorkingDay;

    @Column(name = "preferred_work_mode", length = 50)
    private String preferredWorkMode;

    @Column(name = "preferred_locations", length = 200)
    private String preferredLocations;

    @Column(name = "education", length = 500)
    private String education;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "linkedin_url", length = 200)
    private String linkedinUrl;

    @Column(name = "github_url", length = 200)
    private String githubUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_tier", length = 20)
    @Builder.Default
    private SubscriptionTier subscriptionTier = SubscriptionTier.FREE;

    @Column(name = "subscription_status", length = 20)
    @Builder.Default
    private String subscriptionStatus = "ACTIVE";

    @Column(name = "subscription_expires_at")
    private LocalDateTime subscriptionExpiresAt;

    @Column(name = "ai_usage_count")
    @Builder.Default
    private Integer aiUsageCount = 0;

    @Column(name = "job_alerts_enabled")
    @Builder.Default
    private Boolean jobAlertsEnabled = true;

    @Column(name = "unsubscribe_token", length = 64)
    private String unsubscribeToken;
}