package com.crackit.ai.dto;

import lombok.Getter;
import lombok.Setter;

import java.util.List;
import java.util.Map;

@Getter
@Setter
public class ResumeTailoringResponse {

    private String tailoredSummary;
    private List<String> tailoredSkills;
    private List<Map<String, Object>> tailoredExperiences;
    private List<Map<String, Object>> tailoredProjects;
    private List<String> atsKeywordsUsed;
    private List<String> injectedSkills;
    private List<String> learningNotes;
    private Integer matchScore;
}