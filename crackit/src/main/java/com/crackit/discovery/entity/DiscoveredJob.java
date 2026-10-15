package com.crackit.discovery.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "discovered_jobs")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DiscoveredJob {
    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "external_id", length = 200)
    private String externalId;

    @Column(name = "source", length = 50)
    private String source; // ADZUNA or JSEARCH

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "company", length = 200)
    private String company;

    @Column(name = "location", length = 200)
    private String location;

    @Column(name = "description", columnDefinition = "LONGTEXT")
    private String description;

    @Column(name = "url", columnDefinition = "TEXT")
    private String url;

    @Column(name = "salary_min")
    private Double salaryMin;

    @Column(name = "salary_max")
    private Double salaryMax;

    @Column(name = "salary_currency", length = 10)
    private String salaryCurrency;

    @Column(name = "job_type", length = 50)
    private String jobType;

    @Column(name = "remote")
    private Boolean remote;

    @Column(name = "posted_at")
    private LocalDateTime postedAt;

    @CreationTimestamp
    @Column(name = "fetched_at", updatable = false)
    private LocalDateTime fetchedAt;

    @Column(name = "canonical_url", length = 1000)
    private String canonicalUrl;

    @Column(name = "url_hash", length = 64)
    private String urlHash;

    @Column(name = "semantic_hash", length = 64)
    private String semanticHash;

    @Column(name = "source_type", length = 50)
    @Builder.Default
    private String sourceType = "API_AGGREGATOR";

    @Column(name = "ats_platform", length = 50)
    private String atsPlatform;

    @Column(name = "company_domain", length = 150)
    private String companyDomain;

    @Column(name = "is_active")
    @Builder.Default
    private Boolean isActive = true;

    @Column(name = "verification_status", length = 30)
    @Builder.Default
    private String verificationStatus = "ACTIVE";

    @Column(name = "last_verified_at")
    private LocalDateTime lastVerifiedAt;

    @Column(name = "extracted_skills", columnDefinition = "LONGTEXT")
    private String extractedSkills;
}