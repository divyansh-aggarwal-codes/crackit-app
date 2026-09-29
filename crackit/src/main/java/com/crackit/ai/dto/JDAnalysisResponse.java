package com.crackit.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class JDAnalysisResponse {

    private List<String> requiredSkills;
    private List<String> preferredSkills;
    private String experienceLevel;
    private List<String> importantTopics;
    private List<String> atsKeywords;
    private List<String> matchedKeywords;
    private List<String> missingKeywords;
    private String summary;
    private Integer matchScore;
}