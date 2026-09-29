package com.crackit.ai.service;

import com.crackit.ai.client.AiServiceClient;
import com.crackit.ai.dto.JDAnalysisRequest;
import com.crackit.ai.dto.JDAnalysisResponse;
import com.crackit.ai.dto.ResumeTailoringRequest;
import com.crackit.ai.dto.ResumeTailoringResponse;
import com.crackit.ai.dto.SavedJdAnalysisResponse;
import com.crackit.ai.entity.JdAnalysis;
import com.crackit.ai.entity.TailoredResume;
import com.crackit.ai.repository.JdAnalysisRepository;
import com.crackit.ai.repository.TailoredResumeRepository;
import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.common.util.AuthUtil;
import com.crackit.jobs.entity.Job;
import com.crackit.jobs.repository.JobRepository;
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.SkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.crackit.resume.entity.*;
import com.crackit.resume.repository.*;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AiIntegrationService {

    private final JobRepository jobRepository;
    private final AiServiceClient aiServiceClient;
    private final JdAnalysisRepository jdAnalysisRepository;
    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;
    private final MasterResumeRepository masterResumeRepository;
    private final ExperienceRepository experienceRepository;
    private final ExperienceBulletRepository experienceBulletRepository;
    private final ProjectRepository projectRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final UserRepository userRepository;
    private final com.crackit.payment.service.SubscriptionService subscriptionService;

    public SavedJdAnalysisResponse analyzeJob(String jobId) {

        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        if (job.getJdText() == null || job.getJdText().isBlank()) {
            throw new RuntimeException("Job description is empty");
        }

        User user = job.getUser();
        subscriptionService.checkAndIncrementAiQuota(user);

        MasterResume masterResume = masterResumeRepository.findByUserId(user.getId())
                .stream().findFirst().orElse(null);

        List<Skill> skills = skillRepository.findByUserId(user.getId());
        List<Experience> experiences = experienceRepository.findByUserId(user.getId());
        List<Project> projects = projectRepository.findByUserId(user.getId());

        JDAnalysisRequest request = new JDAnalysisRequest(
                job.getJdText(),
                masterResume != null ? masterResume.getSummary() : "",
                skills.stream().map(s -> Map.<String, Object>of(
                        "skillName", s.getSkillName(),
                        "category", s.getCategory() != null ? s.getCategory() : "",
                        "proficiencyLevel", s.getProficiencyLevel() != null ? s.getProficiencyLevel() : "",
                        "yearsUsed", s.getYearsUsed() != null ? s.getYearsUsed() : 0
                )).toList(),
                experiences.stream().map(e -> {
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
                }).toList(),
                projects.stream().map(p -> Map.<String, Object>of(
                        "title", p.getTitle(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "techStack", p.getTechStack() != null ? p.getTechStack() : "",
                        "impactMetrics", p.getImpactMetrics() != null ? p.getImpactMetrics() : ""
                )).toList()
        );

        JDAnalysisResponse aiResponse = aiServiceClient.analyzeJd(request);

        JdAnalysis analysis = JdAnalysis.builder()
                .id(UUID.randomUUID().toString())
                .job(job)
                .requiredSkills(toJson(aiResponse.getRequiredSkills()))
                .preferredSkills(toJson(aiResponse.getPreferredSkills()))
                .importantTopics(toJson(aiResponse.getImportantTopics()))
                .atsKeywords(toJson(aiResponse.getAtsKeywords()))
                .matchedKeywords(toJson(aiResponse.getMatchedKeywords()))
                .missingKeywords(toJson(aiResponse.getMissingKeywords()))
                .experienceLevel(aiResponse.getExperienceLevel())
                .matchScore(aiResponse.getMatchScore())
                .aiSummary(aiResponse.getSummary())
                .build();

        return mapToResponse(jdAnalysisRepository.save(analysis));
    }

    public SavedJdAnalysisResponse getLatestAnalysisForJob(String jobId) {
        return jdAnalysisRepository.findByJobId(jobId)
                .stream()
                .reduce((first, second) -> second)
                .map(this::mapToResponse)
                .orElseThrow(() -> new RuntimeException("No analysis found for this job"));
    }

    private SavedJdAnalysisResponse mapToResponse(JdAnalysis analysis) {
        return SavedJdAnalysisResponse.builder()
                .id(analysis.getId())
                .jobId(analysis.getJob().getId())
                .requiredSkills(fromJson(analysis.getRequiredSkills()))
                .preferredSkills(fromJson(analysis.getPreferredSkills()))
                .importantTopics(fromJson(analysis.getImportantTopics()))
                .atsKeywords(fromJson(analysis.getAtsKeywords()))
                .matchedKeywords(fromJson(analysis.getMatchedKeywords()))
                .missingKeywords(fromJson(analysis.getMissingKeywords()))
                .experienceLevel(analysis.getExperienceLevel())
                .matchScore(analysis.getMatchScore())
                .aiSummary(analysis.getAiSummary())
                .createdAt(analysis.getCreatedAt())
                .build();
    }

    private String toJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values != null ? values : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    private List<String> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }

    public byte[] generateTailoredResumePdf(String jobId) {
        return generateTailoredResumePdf(jobId, "compact");
    }

    public byte[] generateTailoredResumePdf(String jobId, String template) {
        User user = getLoggedInUser();

        TailoredResume tailored = tailoredResumeRepository
                .findTopByJobIdOrderByCreatedAtDesc(jobId)
                .orElseThrow(() -> new RuntimeException("No tailored resume found"));

        List<Map<String, Object>> experiences = fromJsonMap(tailored.getTailoredExperience());

        // cross-reference dates from original experiences
        List<Experience> originalExperiences = experienceRepository.findByUserId(user.getId());
        Map<String, Experience> expByCompany = originalExperiences.stream()
                .collect(Collectors.toMap(
                        e -> e.getCompanyName().toLowerCase(),
                        e -> e,
                        (a, b) -> a
                ));

        List<Map<String, Object>> experiencesWithDates = experiences.stream().map(exp -> {
            String company = ((String) exp.getOrDefault("companyName", "")).toLowerCase();
            Experience original = expByCompany.get(company);
            if (original != null) {
                Map<String, Object> merged = new java.util.HashMap<>(exp);
                merged.put("startDate", original.getStartDate() != null ? original.getStartDate().toString() : "");
                merged.put("endDate", original.getEndDate() != null ? original.getEndDate().toString() : "");
                merged.put("currentCompany", original.getCurrentCompany() != null ? original.getCurrentCompany() : false);
                merged.put("location", original.getLocation() != null ? original.getLocation() : "");
                return merged;
            }
            return exp;
        }).toList();
        List<Map<String, Object>> projects = fromJsonMap(tailored.getTailoredProjects());
        List<String> skills = fromJson(tailored.getTailoredSkills());
        // cross-reference user's skills to get categories
        List<Skill> userSkills = skillRepository.findByUserId(user.getId());
        Map<String, String> skillCategoryMap = userSkills.stream()
                .collect(Collectors.toMap(
                        s -> s.getSkillName().toLowerCase(),
                        Skill::getCategory,
                        (a, b) -> a
                ));

        MasterResume mr = masterResumeRepository.findByUserId(user.getId()).stream().findFirst().orElse(null);
        String eduRaw = mr != null && mr.getEducation() != null && !mr.getEducation().isBlank()
                ? mr.getEducation()
                : user.getEducation();
        Object eduObj = null;
        if (eduRaw != null && !eduRaw.isBlank()) {
            try {
                eduObj = objectMapper.readValue(eduRaw, Object.class);
            } catch (Exception ignored) {
                eduObj = eduRaw;
            }
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("fullName", user.getFullName() != null ? user.getFullName() : "guest");
        payload.put("email", user.getEmail());
        payload.put("phone", user.getPhone() != null ? user.getPhone() : "");
        payload.put("location", user.getLocation() != null ? user.getLocation() : "");
        payload.put("linkedinUrl", user.getLinkedinUrl() != null ? user.getLinkedinUrl() : "");
        payload.put("githubUrl", user.getGithubUrl() != null ? user.getGithubUrl() : "");
        payload.put("summary", tailored.getTailoredSummary() != null ? tailored.getTailoredSummary() : "");
        payload.put("template", template != null && !template.isBlank() ? template : "compact");
        Set<String> seenSkills = new LinkedHashSet<>();
        List<Map<String, Object>> finalSkills = new ArrayList<>();
        for (String s : skills) {
            if (s != null && !s.isBlank() && seenSkills.add(s.toLowerCase())) {
                finalSkills.add(Map.of(
                        "skillName", s,
                        "category", skillCategoryMap.getOrDefault(s.toLowerCase(), "Other")
                ));
            }
        }
        for (Skill s : userSkills) {
            if (s.getSkillName() != null && !s.getSkillName().isBlank() && seenSkills.add(s.getSkillName().toLowerCase())) {
                finalSkills.add(Map.of(
                        "skillName", s.getSkillName(),
                        "category", s.getCategory() != null && !s.getCategory().isBlank() ? s.getCategory() : "Other"
                ));
            }
        }
        payload.put("skills", finalSkills);
        payload.put("experiences", experiencesWithDates);
        payload.put("projects", projects);
        if (eduObj != null) {
            payload.put("education", eduObj);
        }

        return aiServiceClient.generateResumePdf(payload);
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

    public JDAnalysisResponse quickScan(String jdText) {
        User user = getLoggedInUser();
        subscriptionService.checkAndIncrementAiQuota(user);

        MasterResume masterResume = masterResumeRepository.findByUserId(user.getId())
                .stream().findFirst().orElse(null);

        List<Skill> skills = skillRepository.findByUserId(user.getId());
        List<Experience> experiences = experienceRepository.findByUserId(user.getId());
        List<Project> projects = projectRepository.findByUserId(user.getId());

        JDAnalysisRequest request = new JDAnalysisRequest(
                jdText,
                masterResume != null ? masterResume.getSummary() : "",
                skills.stream().map(s -> Map.<String, Object>of(
                        "skillName", s.getSkillName(),
                        "category", s.getCategory() != null ? s.getCategory() : "",
                        "proficiencyLevel", s.getProficiencyLevel() != null ? s.getProficiencyLevel() : "",
                        "yearsUsed", s.getYearsUsed() != null ? s.getYearsUsed() : 0
                )).toList(),
                experiences.stream().map(e -> {
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
                }).toList(),
                projects.stream().map(p -> Map.<String, Object>of(
                        "title", p.getTitle(),
                        "description", p.getDescription() != null ? p.getDescription() : "",
                        "techStack", p.getTechStack() != null ? p.getTechStack() : "",
                        "impactMetrics", p.getImpactMetrics() != null ? p.getImpactMetrics() : ""
                )).toList()
        );

        return aiServiceClient.analyzeJd(request);
    }

    public Map<String, Object> enhanceBullet(Map<String, Object> payload) {
        String email = AuthUtil.getLoggedInUserEmail();
        if (email != null && !email.isBlank()) {
            User user = userRepository.findByEmail(email).orElse(null);
            if (user != null) {
                subscriptionService.checkAndIncrementAiQuota(user);
            }
        }
        return aiServiceClient.enhanceBullet(payload);
    }

    public Map<String, Object> quickTailor(String jdText) {
        return quickTailor(jdText, "strict");
    }

    public Map<String, Object> quickTailor(String jdText, String mode) {
        User user = getLoggedInUser();
        subscriptionService.checkAndIncrementAiQuota(user);

        // 1. Run Quick Scan
        JDAnalysisResponse scan = quickScan(jdText);

        // 2. Build Tailoring Request from Master Resume
        MasterResume masterResume = masterResumeRepository.findByUserId(user.getId())
                .stream().findFirst()
                .orElseThrow(() -> new RuntimeException("No master resume found — please create your resume first"));

        List<Skill> skills = skillRepository.findByUserId(user.getId());
        List<Experience> experiences = experienceRepository.findByUserId(user.getId());
        List<Project> projects = projectRepository.findByUserId(user.getId());

        ResumeTailoringRequest request = ResumeTailoringRequest.builder()
                .jdAnalysis(Map.of(
                        "requiredSkills", scan.getRequiredSkills() != null ? scan.getRequiredSkills() : List.of(),
                        "preferredSkills", scan.getPreferredSkills() != null ? scan.getPreferredSkills() : List.of(),
                        "importantTopics", scan.getImportantTopics() != null ? scan.getImportantTopics() : List.of(),
                        "atsKeywords", scan.getAtsKeywords() != null ? scan.getAtsKeywords() : List.of(),
                        "matchedKeywords", scan.getMatchedKeywords() != null ? scan.getMatchedKeywords() : List.of(),
                        "missingKeywords", scan.getMissingKeywords() != null ? scan.getMissingKeywords() : List.of(),
                        "experienceLevel", scan.getExperienceLevel() != null ? scan.getExperienceLevel() : "",
                        "summary", scan.getSummary() != null ? scan.getSummary() : ""
                ))
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
                .mode(mode)
                .build();

        ResumeTailoringResponse tailored = aiServiceClient.tailorResume(request);

        // Cross-reference dates and locations
        Map<String, Experience> expByCompany = experiences.stream()
                .collect(Collectors.toMap(e -> e.getCompanyName().toLowerCase(), e -> e, (a, b) -> a));

        List<Map<String, Object>> expsWithDates = tailored.getTailoredExperiences() != null
                ? tailored.getTailoredExperiences().stream().map(exp -> {
                    String comp = String.valueOf(exp.getOrDefault("companyName", "")).toLowerCase();
                    Experience original = expByCompany.get(comp);
                    Map<String, Object> merged = new LinkedHashMap<>(exp);
                    if (original != null) {
                        merged.put("startDate", original.getStartDate() != null ? original.getStartDate().toString() : "");
                        merged.put("endDate", original.getEndDate() != null ? original.getEndDate().toString() : "");
                        merged.put("currentCompany", Boolean.TRUE.equals(original.getCurrentCompany()));
                        merged.put("location", original.getLocation() != null ? original.getLocation() : "");
                    }
                    return merged;
                }).toList()
                : List.of();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("scan", scan);
        result.put("fullName", user.getFullName() != null ? user.getFullName() : "");
        result.put("tailoredSummary", tailored.getTailoredSummary());
        result.put("tailoredSkills", tailored.getTailoredSkills());
        result.put("tailoredExperiences", expsWithDates);
        result.put("tailoredProjects", tailored.getTailoredProjects());
        result.put("atsKeywordsUsed", tailored.getAtsKeywordsUsed());
        result.put("injectedSkills", tailored.getInjectedSkills() != null ? tailored.getInjectedSkills() : List.of());
        result.put("learningNotes", tailored.getLearningNotes() != null ? tailored.getLearningNotes() : List.of());
        result.put("mode", mode != null ? mode : "strict");
        result.put("matchScore", tailored.getMatchScore());
        result.put("matchedKeywords", scan.getMatchedKeywords());
        result.put("missingKeywords", scan.getMissingKeywords());
        return result;
    }

    public byte[] generateDirectResumePdf(Map<String, Object> payload) {
        User user = getLoggedInUser();

        String template = (String) payload.getOrDefault("template", "compact");
        Object summaryObj = payload.get("tailoredSummary") != null ? payload.get("tailoredSummary") : payload.get("summary");
        String summary = summaryObj != null ? String.valueOf(summaryObj) : "";

        Object rawSkills = payload.get("tailoredSkills") != null ? payload.get("tailoredSkills") : payload.get("skills");
        List<String> skillList = new ArrayList<>();
        if (rawSkills instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    skillList.add(s);
                } else if (item instanceof Map<?, ?> m && m.get("skillName") != null) {
                    skillList.add(String.valueOf(m.get("skillName")));
                }
            }
        }

        List<Skill> userSkills = skillRepository.findByUserId(user.getId());
        Map<String, String> skillCategoryMap = userSkills.stream()
                .collect(Collectors.toMap(s -> s.getSkillName().toLowerCase(), Skill::getCategory, (a, b) -> a));

        Object expsRaw = payload.get("tailoredExperiences") != null ? payload.get("tailoredExperiences") : payload.get("experiences");
        List<?> experiences = expsRaw instanceof List<?> list ? list : List.of();

        Object projsRaw = payload.get("tailoredProjects") != null ? payload.get("tailoredProjects") : payload.get("projects");
        List<?> projects = projsRaw instanceof List<?> list ? list : List.of();

        MasterResume mr = masterResumeRepository.findByUserId(user.getId()).stream().findFirst().orElse(null);
        String eduRaw = mr != null && mr.getEducation() != null && !mr.getEducation().isBlank()
                ? mr.getEducation()
                : user.getEducation();
        Object eduObj = null;
        if (eduRaw != null && !eduRaw.isBlank()) {
            try {
                eduObj = objectMapper.readValue(eduRaw, Object.class);
            } catch (Exception ignored) {
                eduObj = eduRaw;
            }
        }

        Map<String, Object> pdfPayload = new LinkedHashMap<>();
        pdfPayload.put("fullName", user.getFullName() != null ? user.getFullName() : "Candidate");
        pdfPayload.put("email", user.getEmail());
        pdfPayload.put("phone", user.getPhone() != null ? user.getPhone() : "");
        pdfPayload.put("location", user.getLocation() != null ? user.getLocation() : "");
        pdfPayload.put("linkedinUrl", user.getLinkedinUrl() != null ? user.getLinkedinUrl() : "");
        pdfPayload.put("githubUrl", user.getGithubUrl() != null ? user.getGithubUrl() : "");
        pdfPayload.put("summary", summary);
        pdfPayload.put("template", template);
        Set<String> seenSkills = new LinkedHashSet<>();
        List<Map<String, Object>> finalSkills = new ArrayList<>();
        for (String s : skillList) {
            if (s != null && !s.isBlank() && seenSkills.add(s.toLowerCase())) {
                finalSkills.add(Map.of(
                        "skillName", s,
                        "category", skillCategoryMap.getOrDefault(s.toLowerCase(), "Other")
                ));
            }
        }
        for (Skill s : userSkills) {
            if (s.getSkillName() != null && !s.getSkillName().isBlank() && seenSkills.add(s.getSkillName().toLowerCase())) {
                finalSkills.add(Map.of(
                        "skillName", s.getSkillName(),
                        "category", s.getCategory() != null && !s.getCategory().isBlank() ? s.getCategory() : "Other"
                ));
            }
        }
        pdfPayload.put("skills", finalSkills);
        pdfPayload.put("experiences", experiences);
        pdfPayload.put("projects", projects);
        if (eduObj != null) {
            pdfPayload.put("education", eduObj);
        }

        return aiServiceClient.generateResumePdf(pdfPayload);
    }
}