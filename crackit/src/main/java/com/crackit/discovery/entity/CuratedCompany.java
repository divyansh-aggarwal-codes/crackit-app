package com.crackit.discovery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "curated_companies")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CuratedCompany {

    @Id
    @Column(length = 36)
    private String id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 150)
    private String domain;

    @Column(name = "ats_platform", nullable = false, length = 50)
    private String atsPlatform; // GREENHOUSE, LEVER, ASHBY, WORKABLE, SMARTRECRUITERS

    @Column(name = "ats_board_token", nullable = false, length = 150)
    private String atsBoardToken;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_discovered_at")
    private LocalDateTime lastDiscoveredAt;

    @Column(name = "last_job_count")
    @Builder.Default
    private Integer lastJobCount = 0;

    @Column(name = "health_status", length = 30)
    @Builder.Default
    private String healthStatus = "ACTIVE";
}
