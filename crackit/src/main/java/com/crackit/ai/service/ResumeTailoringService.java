package com.crackit.ai.service;

import com.crackit.ai.client.AiServiceClient;
import com.crackit.ai.dto.ResumeTailoringRequest;
import com.crackit.ai.dto.ResumeTailoringResponse;
import com.crackit.ai.dto.SavedTailoredResumeResponse;
import com.crackit.ai.entity.JdAnalysis;
import com.crackit.ai.entity.TailoredResume;
import com.crackit.ai.repository.JdAnalysisRepository;
import com.crackit.ai.repository.TailoredResumeRepository;
import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.common.util.AuthUtil;
import com.crackit.jobs.entity.Job;
import com.crackit.jobs.repository.JobRepository;
import com.crackit.resume.entity.Experience;
import com.crackit.resume.entity.ExperienceBullet;
import com.crackit.resume.entity.MasterResume;
import com.crackit.resume.entity.Project;
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ResumeTailoringService {

    private final JobRepository jobRepository;
    private final JdAnalysisRepository jdAnalysisRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final MasterResumeRepository masterResumeRepository;
    private final SkillRepository skillRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceBulletRepository experienceBulletRepository;
    private final ProjectRepository projectRepository;
    private final AiServiceClient aiServiceClient;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final com.crackit.payment.service.SubscriptionService subscriptionService;

    public SavedTailoredResumeResponse tailorResume(String jobId) {
        User user = getLoggedInUser();
        subscriptionService.checkAndIncrementAiQuota(user);

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        JdAnalysis jdAnalysis = jdAnalysisRepository.findByJobId(jobId)
                .stream()
                .reduce((first, second) -> second)
                .orElseThrow(() -> new RuntimeException("No JD analysis found — analyse the job first"));

        MasterResume masterResume = masterResumeRepository.findByUserId(user.getId())
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No master resume found — please create your resume first"));

        List<Skill> skills = skillRepository.findByUserId(user.getId());
        List<Experience> experiences = experienceRepository.findByUserId(user.getId());
        List<Project> projects = projectRepository.findByUserId(user.getId());

        // Build request to Python
        Map<String, Object> jdAnalysisMap = new LinkedHashMap<>();
        jdAnalysisMap.put("requiredSkills", fromJson(jdAnalysis.getRequiredSkills()));
        jdAnalysisMap.put("preferredSkills", fromJson(jdAnalysis.getPreferredSkills()));
        jdAnalysisMap.put("importantTopics", fromJson(jdAnalysis.getImportantTopics()));
        jdAnalysisMap.put("atsKeywords", fromJson(jdAnalysis.getAtsKeywords()));
        jdAnalysisMap.put("matchedKeywords", fromJson(jdAnalysis.getMatchedKeywords()));
        jdAnalysisMap.put("missingKeywords", fromJson(jdAnalysis.getMissingKeywords()));
        jdAnalysisMap.put("experienceLevel", jdAnalysis.getExperienceLevel() != null ? jdAnalysis.getExperienceLevel() : "");
        jdAnalysisMap.put("summary", jdAnalysis.getAiSummary() != null ? jdAnalysis.getAiSummary() : "");

        ResumeTailoringRequest request = ResumeTailoringRequest.builder()
                .jdAnalysis(jdAnalysisMap)
                .summary(masterResume.getSummary())
                .skills(skills.stream().map(s -> Map.<String, Object>of(
                        "skillName", s.getSkillName(),
                        "category", s.getCategory() != null ? s.getCategory() : "",
                        "proficiencyLevel", s.getProficiencyLevel() != null ? s.getProficiencyLevel() : "",
                        "yearsUsed", s.getYearsUsed() != null ? s.getYearsUsed() : 0
                )).toList())
                .experiences(experiences.stream().map(e -> {
                    List<ExperienceBullet> bullets = experienceBulletRepository.findByExperienceId(e.getId());
                    return Map.<String, Object>of(
                            "companyName", e.getCompanyName(),
                            "role", e.getRole(),
                            "description", e.getDescription() != null ? e.getDescription() : "",
                            "bullets", bullets.stream().map(b -> Map.<String, Object>of(
                                    "bulletText", b.getBulletText(),
                                    "technologies", b.getTechnologies() != null ? b.getTechnologies() : ""
                            )).toList()
                    );
                }).toList())
                .projects(projects.stream().map(p -> Map.<String, Object>of(
                        "title", p.getTitle(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "techStack", p.getTechStack() != null ? p.getTechStack() : "",
                        "impactMetrics", p.getImpactMetrics() != null ? p.getImpactMetrics() : ""
                )).toList())
                .build();

        ResumeTailoringResponse aiResponse = aiServiceClient.tailorResume(request);

        TailoredResume tailored = TailoredResume.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .job(job)
                .jdAnalysis(jdAnalysis)
                .tailoredSummary(aiResponse.getTailoredSummary())
                .tailoredSkills(toJson(aiResponse.getTailoredSkills()))
                .tailoredExperience(toJson(aiResponse.getTailoredExperiences()))
                .tailoredProjects(toJson(aiResponse.getTailoredProjects()))
                .atsKeywordsUsed(toJson(aiResponse.getAtsKeywordsUsed()))
                .matchScore(aiResponse.getMatchScore())
                .build();

        return mapToResponse(tailoredResumeRepository.save(tailored));
    }

    public SavedTailoredResumeResponse getLatestTailoredResume(String jobId) {
        return tailoredResumeRepository.findTopByJobIdOrderByCreatedAtDesc(jobId)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("No tailored resume found for this job"));
    }

    public SavedTailoredResumeResponse updateTailoredResume(String jobId, Map<String, Object> payload) {
        TailoredResume tailored = tailoredResumeRepository.findTopByJobIdOrderByCreatedAtDesc(jobId)
                .orElseThrow(() -> new RuntimeException("No tailored resume found for this job"));

        if (payload.containsKey("tailoredSummary")) {
            tailored.setTailoredSummary((String) payload.get("tailoredSummary"));
        }
        if (payload.containsKey("tailoredSkills")) {
            tailored.setTailoredSkills(toJson(payload.get("tailoredSkills")));
        }
        if (payload.containsKey("tailoredExperiences")) {
            tailored.setTailoredExperience(toJson(payload.get("tailoredExperiences")));
        }
        if (payload.containsKey("tailoredProjects")) {
            tailored.setTailoredProjects(toJson(payload.get("tailoredProjects")));
        }

        return mapToResponse(tailoredResumeRepository.save(tailored));
    }

    private SavedTailoredResumeResponse mapToResponse(TailoredResume tailored) {
        return SavedTailoredResumeResponse.builder()
                .id(tailored.getId())
                .jobId(tailored.getJob().getId())
                .jdAnalysisId(tailored.getJdAnalysis().getId())
                .tailoredSummary(tailored.getTailoredSummary())
                .tailoredSkills(fromJson(tailored.getTailoredSkills()))
                .tailoredExperiences(fromJsonMap(tailored.getTailoredExperience()))
                .tailoredProjects(fromJsonMap(tailored.getTailoredProjects()))
                .atsKeywordsUsed(fromJson(tailored.getAtsKeywordsUsed()))
                .matchScore(tailored.getMatchScore())
                .createdAt(tailored.getCreatedAt())
                .build();
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value != null ? value : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private List<Map<String, Object>> fromJsonMap(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    private User getLoggedInUser() {
        String email = AuthUtil.getLoggedInUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}