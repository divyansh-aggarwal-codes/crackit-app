package com.crackit.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class SavedJdAnalysisResponse {

    private String id;
    private String jobId;

    private List<String> requiredSkills;
    private List<String> preferredSkills;
    private List<String> importantTopics;
    private List<String> atsKeywords;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private String experienceLevel;
    private Integer matchScore;
    private String aiSummary;

    private LocalDateTime createdAt;
}