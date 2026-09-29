package com.crackit.ai.entity;

import com.crackit.jobs.entity.Job;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "jd_analysis")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JdAnalysis {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_id", nullable = false)
    private Job job;

    @Column(name = "required_skills", columnDefinition = "JSON")
    private String requiredSkills;

    @Column(name = "preferred_skills", columnDefinition = "JSON")
    private String preferredSkills;

    @Column(name = "important_topics", columnDefinition = "JSON")
    private String importantTopics;

    @Column(name = "ats_keywords", columnDefinition = "JSON")
    private String atsKeywords;

    @Column(name = "matched_keywords", columnDefinition = "JSON")
    private String matchedKeywords;

    @Column(name = "missing_keywords", columnDefinition = "JSON")
    private String missingKeywords;

    @Column(name = "experience_level", length = 50)
    private String experienceLevel;

    @Column(name = "match_score")
    private Integer matchScore;

    @Column(name = "ai_summary", columnDefinition = "LONGTEXT")
    private String aiSummary;

    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
}