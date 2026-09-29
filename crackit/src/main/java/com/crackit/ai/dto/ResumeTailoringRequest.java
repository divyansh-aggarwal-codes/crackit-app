package com.crackit.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

@Getter
@Builder
public class ResumeTailoringRequest {

    private Map<String, Object> jdAnalysis;
    private String summary;
    private List<Map<String, Object>> skills;
    private List<Map<String, Object>> experiences;
    private List<Map<String, Object>> projects;
    private String mode;
}