package com.crackit.discovery.entity;

import com.crackit.auth.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_job_interactions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "discovered_job_id"}))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserJobInteraction {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovered_job_id", nullable = false, foreignKey = @ForeignKey(ConstraintMode.NO_CONSTRAINT))
    private DiscoveredJob discoveredJob;

    @Column(name = "match_score")
    @Builder.Default
    private Integer matchScore = 0;

    @Column(name = "matched_skills", columnDefinition = "LONGTEXT")
    private String matchedSkills;

    @Column(name = "missing_skills", columnDefinition = "LONGTEXT")
    private String missingSkills;

    @Column(name = "status", length = 20)
    @Builder.Default
    private String status = "NEW"; // NEW, VIEWED, SAVED, DISMISSED, APPLIED

    @Column(name = "notified")
    @Builder.Default
    private Boolean notified = false;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
