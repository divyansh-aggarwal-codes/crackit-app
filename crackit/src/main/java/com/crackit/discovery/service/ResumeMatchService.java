package com.crackit.discovery.service;

import com.crackit.ai.client.AiServiceClient;
import com.crackit.ai.dto.JDAnalysisRequest;
import com.crackit.ai.dto.JDAnalysisResponse;
import com.crackit.auth.entity.User;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import com.crackit.discovery.util.JobUrlNormalizer;
import com.crackit.resume.entity.Experience;
import com.crackit.resume.entity.MasterResume;
import com.crackit.resume.entity.Project;
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.ExperienceRepository;
import com.crackit.resume.repository.MasterResumeRepository;
import com.crackit.resume.repository.ProjectRepository;
import com.crackit.resume.repository.SkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResumeMatchService {

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private UserJobInteractionRepository userJobInteractionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired(required = false)
    private ExperienceRepository experienceRepository;

    @Autowired(required = false)
    private ProjectRepository projectRepository;

    @Autowired(required = false)
    private MasterResumeRepository masterResumeRepository;

    @Autowired(required = false)
    private JobUrlNormalizer jobUrlNormalizer;

    @Autowired(required = false)
    private AiServiceClient aiServiceClient;

    private final Map<String, MatchResult> aiScoreCache = new ConcurrentHashMap<>();

    // Standard constructor for unit tests
    public ResumeMatchService(SkillRepository skillRepository,
                              UserJobInteractionRepository userJobInteractionRepository,
                              ObjectMapper objectMapper) {
        this.skillRepository = skillRepository;
        this.userJobInteractionRepository = userJobInteractionRepository;
        this.objectMapper = objectMapper;
    }

    public enum TechDomain {
        BACKEND, FRONTEND, FULLSTACK, DEVOPS, DATA, MOBILE, QA, GENERAL_TECH
    }

    // Technology ecosystem groupings for generalized multi-profile matching
    private static final Map<TechDomain, Set<String>> DOMAIN_KEYWORDS = Map.of(
            TechDomain.BACKEND, Set.of("backend", "back-end", "java", "spring", "spring boot", "microservices",
                    "golang", "go", "node.js", "nodejs", "express", "fastapi", "django", "flask", "c#", ".net",
                    "distributed systems", "kafka", "redis", "mysql", "postgresql", "sql", "rest api", "grpc"),
            TechDomain.FRONTEND, Set.of("frontend", "front-end", "react", "react.js", "angular", "vue", "vue.js",
                    "typescript", "javascript", "next.js", "nextjs", "tailwind", "html", "css", "redux", "ui/ux", "web developer"),
            TechDomain.FULLSTACK, Set.of("full stack", "fullstack", "full-stack", "mern", "mean"),
            TechDomain.DEVOPS, Set.of("devops", "sre", "cloud", "infrastructure", "kubernetes", "k8s", "docker",
                    "terraform", "aws", "gcp", "azure", "ci/cd", "jenkins", "linux", "ansible", "platform engineer"),
            TechDomain.DATA, Set.of("data engineer", "data scientist", "data analyst", "machine learning", "ml",
                    "ai", "spark", "hadoop", "airflow", "snowflake", "bigquery", "databricks", "etl", "deep learning", "nlp"),
            TechDomain.MOBILE, Set.of("mobile", "android", "ios", "swift", "kotlin", "flutter", "react native"),
            TechDomain.QA, Set.of("qa", "sdet", "quality assurance", "test automation", "selenium", "cypress", "playwright", "testng", "junit")
    );

    // Negative disjoint stacks (e.g. if a Java backend engineer sees WordPress or PHP)
    private static final Map<String, Set<String>> DISJOINT_ECOSYSTEMS = Map.of(
            "java", Set.of("php", "laravel", "wordpress", "drupal", "ruby on rails", "magento"),
            "python", Set.of("wordpress", "drupal", "magento", "php"),
            "react", Set.of("angularjs", "wordpress", "drupal"),
            "golang", Set.of("wordpress", "php", "drupal")
    );

    private static final Pattern YEARS_PATTERN = Pattern.compile("(\\d+)\\+?\\s*(?:to|-)?\\s*(\\d+)?\\s*(?:years?|yrs?)", Pattern.CASE_INSENSITIVE);

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchResult {
        private int matchScore; // 0 - 100
        private List<String> matchedSkills;
        private List<String> missingSkills;
    }

    /**
     * Generalized Two-Stage Matcher:
     * Stage A: Deterministic calibration across all tech domains (Role, Tech Stack, Seniority, Location, Negative Penalties)
     * Stage B: AI Gemini deep evaluation for top candidates (>=75%) with SHA-256 caching and provisional fallback.
     */
    public MatchResult calculateTier1Score(User user, DiscoveredJob job) {
        return calculateTier1Score(user, job, true);
    }

    public MatchResult calculateTier1Score(User user, DiscoveredJob job, boolean enableStageB) {
        if (user == null || job == null) {
            return MatchResult.builder().matchScore(50).matchedSkills(List.of()).missingSkills(List.of()).build();
        }
        List<Skill> userSkills = skillRepository != null ? skillRepository.findByUserId(user.getId()) : List.of();
        int candidateExp = resolveCandidateExperience(user);
        return calculateTier1Score(user, job, userSkills, candidateExp, enableStageB);
    }

    public MatchResult calculateTier1Score(User user, DiscoveredJob job, List<Skill> userSkills, int candidateExp, boolean enableStageB) {
        if (user == null || job == null) {
            return MatchResult.builder().matchScore(50).matchedSkills(List.of()).missingSkills(List.of()).build();
        }

        String jobTitle = (job.getTitle() != null ? job.getTitle() : "").toLowerCase();
        String jobDesc = (job.getDescription() != null ? job.getDescription() : "").toLowerCase();
        String fullJobCorpus = jobTitle + " " + jobDesc;

        // 1. Ingest Candidate Profile
        List<Skill> safeSkills = userSkills != null ? userSkills : List.of();
        Set<String> userSkillSet = safeSkills.stream()
                .map(s -> s.getSkillName().toLowerCase().trim())
                .filter(s -> !s.isBlank())
                .collect(Collectors.toSet());

        String targetRole = user.getTargetRole() != null && !user.getTargetRole().isBlank()
                ? user.getTargetRole().toLowerCase().trim()
                : (user.getCurrentRole() != null ? user.getCurrentRole().toLowerCase().trim() : "software engineer");

        // 2. Classify Candidate Domain and Job Domain
        TechDomain candidateDomain = detectDomain(targetRole, userSkillSet);
        TechDomain jobDomain = detectDomain(jobTitle, extractTechTokens(fullJobCorpus));

        // 3. Stage A - Dimension 1: Role / Title Alignment & Domain Compatibility (35 pts max)
        int roleScore = calculateRoleAlignment(targetRole, candidateDomain, jobTitle, jobDomain);

        // 4. Stage A - Dimension 2: Tech Skill Overlap & Core Stack Alignment (40 pts max)
        List<String> matchedSkills = new ArrayList<>();
        List<String> missingSkills = new ArrayList<>();
        int skillScore = calculateSkillOverlap(userSkills, userSkillSet, fullJobCorpus, candidateDomain, matchedSkills, missingSkills);

        // Apply Disjoint Ecosystem Penalty (e.g. Java candidate vs PHP/WordPress JD)
        int disjointPenalty = calculateDisjointPenalty(userSkillSet, fullJobCorpus);
        skillScore = Math.max(0, skillScore - disjointPenalty);

        // 5. Stage A - Dimension 3: Seniority & Experience Calibration (15 pts max)
        int seniorityScore = calculateSeniorityScore(candidateExp, jobTitle, jobDesc);

        // 6. Stage A - Dimension 4: Location & Work Mode Fit (10 pts max)
        int locationScore = calculateLocationScore(user, job, jobTitle, jobDesc);

        // Hard cap if job is strictly on-site in an incompatible foreign country
        boolean isOverseasOnsite = isOverseasOnsiteMismatch(user, job);

        int rawStageAScore = roleScore + skillScore + seniorityScore + locationScore;
        int stageAScore = isOverseasOnsite ? Math.min(38, rawStageAScore) : Math.min(99, Math.max(25, rawStageAScore));

        MatchResult stageAResult = MatchResult.builder()
                .matchScore(stageAScore)
                .matchedSkills(matchedSkills.stream().distinct().limit(10).toList())
                .missingSkills(missingSkills.stream().distinct().limit(6).toList())
                .build();

        // 7. Check Stage B Cache first
        String cacheKey = JobUrlNormalizer.sha256(user.getId() + ":" + (job.getUrlHash() != null ? job.getUrlHash() : job.getId()));
        if (aiScoreCache.containsKey(cacheKey)) {
            return aiScoreCache.get(cacheKey);
        }

        // 8. Stage B: Gemini AI Deep Evaluation Hook (only when explicitly requested, never in fast bulk ranking)
        if (enableStageB && stageAScore >= 75 && aiServiceClient != null) {
            return evaluateStageBWithAi(user, job, stageAResult, fullJobCorpus);
        }

        return stageAResult;
    }

    private TechDomain detectDomain(String title, Set<String> skillsOrTokens) {
        String titleLower = title.toLowerCase();

        for (Map.Entry<TechDomain, Set<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
            for (String kw : entry.getValue()) {
                if (titleLower.contains(kw)) {
                    return entry.getKey();
                }
            }
        }

        // Secondary detection based on candidate skills
        for (Map.Entry<TechDomain, Set<String>> entry : DOMAIN_KEYWORDS.entrySet()) {
            long matches = entry.getValue().stream().filter(skillsOrTokens::contains).count();
            if (matches >= 2) {
                return entry.getKey();
            }
        }

        return TechDomain.GENERAL_TECH;
    }

    private int calculateRoleAlignment(String targetRole, TechDomain candidateDomain, String jobTitle, TechDomain jobDomain) {
        // Severe domain clash check
        if (isDomainClash(candidateDomain, jobDomain, jobTitle)) {
            return 5; // Incompatible domain
        }

        String[] targetTokens = targetRole.split("\\s+");
        int matchedTokens = 0;
        for (String token : targetTokens) {
            if (token.length() > 2 && jobTitle.contains(token)) {
                matchedTokens++;
            }
        }

        if (matchedTokens > 0) {
            double ratio = (double) matchedTokens / targetTokens.length;
            return Math.min(35, (int) Math.round(ratio * 35));
        }

        // Equivalent tech domain match (e.g. Candidate is Backend, Job is "Software Engineer" or "Systems Engineer")
        if (candidateDomain == jobDomain && candidateDomain != TechDomain.GENERAL_TECH) {
            return 28;
        }

        if (candidateDomain == TechDomain.FULLSTACK && (jobDomain == TechDomain.BACKEND || jobDomain == TechDomain.FRONTEND)) {
            return 26;
        }

        return 18; // Generic engineering baseline
    }

    private boolean isDomainClash(TechDomain candidateDomain, TechDomain jobDomain, String jobTitle) {
        if (candidateDomain == TechDomain.BACKEND) {
            if (jobDomain == TechDomain.FRONTEND || jobDomain == TechDomain.QA || jobDomain == TechDomain.MOBILE) {
                return true;
            }
            if (jobTitle.contains("ui designer") || jobTitle.contains("graphic") || jobTitle.contains("product manager")) {
                return true;
            }
        } else if (candidateDomain == TechDomain.FRONTEND) {
            if (jobDomain == TechDomain.DEVOPS || jobDomain == TechDomain.DATA || jobTitle.contains("firmware") || jobTitle.contains("kernel")) {
                return true;
            }
        } else if (candidateDomain == TechDomain.QA) {
            if (jobDomain == TechDomain.DEVOPS || jobTitle.contains("architect")) {
                return true;
            }
        }
        return false;
    }

    private int calculateSkillOverlap(List<Skill> userSkills, Set<String> userSkillSet, String fullJobCorpus,
                                      TechDomain domain, List<String> matchedSkills, List<String> missingSkills) {
        int weightedMatches = 0;
        int totalWeightPossible = 0;

        for (Skill s : userSkills) {
            String skillLower = s.getSkillName().toLowerCase().trim();
            int weight = 1;
            if (s.getProficiencyLevel() != null &&
                    (s.getProficiencyLevel().equalsIgnoreCase("Expert") || s.getProficiencyLevel().equalsIgnoreCase("Advanced"))) {
                weight = 2;
            }
            totalWeightPossible += weight;

            if (containsWord(fullJobCorpus, skillLower)) {
                matchedSkills.add(capitalize(s.getSkillName()));
                weightedMatches += weight;
            }
        }

        // Identify key missing domain skills mentioned in the job description
        Set<String> domainSkills = DOMAIN_KEYWORDS.getOrDefault(domain, DOMAIN_KEYWORDS.get(TechDomain.BACKEND));
        for (String keyTech : domainSkills) {
            if (containsWord(fullJobCorpus, keyTech) && !userSkillSet.contains(keyTech)) {
                missingSkills.add(capitalize(keyTech));
            }
        }

        if (userSkillSet.isEmpty()) {
            return 20; // Default when candidate has not yet saved skills
        }

        double ratio = (double) weightedMatches / Math.max(3, Math.min(totalWeightPossible, 8));
        return Math.min(40, (int) Math.round(ratio * 40));
    }

    private int calculateDisjointPenalty(Set<String> userSkillSet, String fullJobCorpus) {
        int penalty = 0;
        for (Map.Entry<String, Set<String>> entry : DISJOINT_ECOSYSTEMS.entrySet()) {
            if (userSkillSet.contains(entry.getKey())) {
                for (String conflicting : entry.getValue()) {
                    if (containsWord(fullJobCorpus, conflicting)) {
                        penalty += 15;
                    }
                }
            }
        }
        return Math.min(25, penalty);
    }

    private int calculateSeniorityScore(int candidateExp, String jobTitle, String jobDesc) {
        boolean isLeadOrStaff = jobTitle.contains("staff") || jobTitle.contains("principal") || jobTitle.contains("lead") || jobTitle.contains("architect");
        boolean isSenior = isLeadOrStaff || jobTitle.contains("senior") || jobTitle.contains("sr.") || jobTitle.contains("sr ");
        boolean isJunior = jobTitle.contains("junior") || jobTitle.contains("associate") || jobTitle.contains("entry") || jobTitle.contains("intern") || jobTitle.contains("graduate");

        // Parse explicit years from job text
        int minYearsReq = -1;
        Matcher m = YEARS_PATTERN.matcher(jobTitle + " " + (jobDesc.length() > 500 ? jobDesc.substring(0, 500) : jobDesc));
        if (m.find()) {
            try {
                minYearsReq = Integer.parseInt(m.group(1));
            } catch (Exception ignored) {}
        }

        if (minYearsReq > 0) {
            int diff = Math.abs(candidateExp - minYearsReq);
            if (diff <= 1) return 15; // Spot-on seniority
            if (diff <= 3) return 12;
            if (candidateExp < minYearsReq) return Math.max(4, 15 - (minYearsReq - candidateExp) * 3);
            return 10; // Slightly overqualified
        }

        if (candidateExp >= 4) {
            if (isSenior) return 15;
            if (isJunior) return 4; // Overqualified for intern/junior
            return 13; // Mid-level / standard Software Engineer
        } else if (candidateExp >= 2) {
            if (isSenior) return 10;
            if (isJunior) return 12;
            return 14;
        } else {
            if (isJunior) return 15;
            if (isSenior) return 4;
            return 10;
        }
    }

    private int calculateLocationScore(User user, DiscoveredJob job, String jobTitle, String jobDesc) {
        boolean userPrefersRemote = "Remote".equalsIgnoreCase(user.getPreferredWorkMode());
        boolean jobIsRemote = Boolean.TRUE.equals(job.getRemote()) || jobTitle.contains("remote") || jobDesc.contains("remote");

        if (jobUrlNormalizer != null) {
            boolean eligible = jobUrlNormalizer.isLocationEligible(
                    user.getPreferredLocations(),
                    userPrefersRemote,
                    job.getLocation(),
                    jobIsRemote
            );
            if (eligible) return 10;
        }

        if (userPrefersRemote && jobIsRemote) return 10;
        if (jobIsRemote) return 9;

        if (user.getPreferredLocations() != null && !user.getPreferredLocations().isBlank()) {
            String[] preferred = user.getPreferredLocations().split("[,;/]");
            for (String p : preferred) {
                String pClean = p.trim().toLowerCase();
                if (!pClean.isBlank() && job.getLocation() != null && job.getLocation().toLowerCase().contains(pClean)) {
                    return 10;
                }
            }
        }

        return 6;
    }

    private boolean isOverseasOnsiteMismatch(User user, DiscoveredJob job) {
        if (Boolean.TRUE.equals(job.getRemote())) return false;
        String loc = job.getLocation() != null ? job.getLocation().toLowerCase() : "";
        if (loc.isBlank() || loc.contains("flexible") || loc.contains("remote")) return false;

        if (jobUrlNormalizer != null && jobUrlNormalizer.isIndianTechHub(jobUrlNormalizer.normalizeLocation(loc))) {
            return false;
        }

        // If candidate preferred India / Indian cities and job is explicitly non-Indian on-site
        String userPref = user.getPreferredLocations() != null ? user.getPreferredLocations().toLowerCase() : "india";
        if (userPref.contains("india") || userPref.contains("bengaluru") || userPref.contains("bangalore") || userPref.contains("delhi")) {
            boolean isForeignCity = loc.contains("munich") || loc.contains("berlin") || loc.contains("london")
                    || loc.contains("san francisco") || loc.contains("new york") || loc.contains("singapore")
                    || loc.contains("tokyo") || loc.contains("toronto") || loc.contains("sydney");
            return isForeignCity;
        }

        return false;
    }

    public int resolveCandidateExperience(User user) {
        if (user.getYearsExperience() != null && user.getYearsExperience() > 0) {
            return user.getYearsExperience();
        }

        if (experienceRepository != null) {
            try {
                List<Experience> exps = experienceRepository.findByUserId(user.getId());
                if (!exps.isEmpty()) {
                    return Math.max(1, exps.size() * 2);
                }
            } catch (Exception ignored) {}
        }

        return 4; // Sensible default for working professional
    }

    private Set<String> extractTechTokens(String corpus) {
        return Arrays.stream(corpus.toLowerCase().split("[^a-zA-Z0-9+#.]+"))
                .filter(t -> t.length() >= 2)
                .collect(Collectors.toSet());
    }

    /**
     * Stage B: AI Gemini Deep Evaluation for Top Matches (>=75%)
     */
    private MatchResult evaluateStageBWithAi(User user, DiscoveredJob job, MatchResult stageAResult, String fullJobCorpus) {
        String cacheKey = JobUrlNormalizer.sha256(user.getId() + ":" + (job.getUrlHash() != null ? job.getUrlHash() : job.getId()));
        if (aiScoreCache.containsKey(cacheKey)) {
            return aiScoreCache.get(cacheKey);
        }

        try {
            String summary = "";
            if (masterResumeRepository != null) {
                List<MasterResume> resumes = masterResumeRepository.findByUserId(user.getId());
                if (resumes != null && !resumes.isEmpty() && resumes.get(0).getSummary() != null) {
                    summary = resumes.get(0).getSummary();
                }
            }

            List<Skill> skills = skillRepository != null ? skillRepository.findByUserId(user.getId()) : List.of();
            List<Map<String, Object>> skillMaps = new ArrayList<>();
            for (Skill s : skills) {
                Map<String, Object> map = new HashMap<>();
                map.put("skillName", s.getSkillName() != null ? s.getSkillName() : "");
                map.put("category", s.getCategory() != null && !s.getCategory().isBlank() ? s.getCategory() : "Technical");
                map.put("proficiencyLevel", s.getProficiencyLevel() != null && !s.getProficiencyLevel().isBlank() ? s.getProficiencyLevel() : "Intermediate");
                map.put("yearsUsed", s.getYearsUsed() != null ? s.getYearsUsed() : 1);
                skillMaps.add(map);
            }

            if (skillMaps.isEmpty() && user.getTargetRole() != null && !user.getTargetRole().isBlank()) {
                Map<String, Object> fallbackSkill = new HashMap<>();
                fallbackSkill.put("skillName", user.getTargetRole());
                fallbackSkill.put("category", "Technical");
                fallbackSkill.put("proficiencyLevel", "Advanced");
                fallbackSkill.put("yearsUsed", Math.max(1, resolveCandidateExperience(user)));
                skillMaps.add(fallbackSkill);
            }

            List<Map<String, Object>> expMaps = new ArrayList<>();
            if (experienceRepository != null) {
                List<Experience> exps = experienceRepository.findByUserId(user.getId());
                if (exps != null) {
                    for (Experience exp : exps) {
                        Map<String, Object> expMap = new HashMap<>();
                        expMap.put("companyName", exp.getCompanyName() != null ? exp.getCompanyName() : "Company");
                        expMap.put("role", exp.getRole() != null ? exp.getRole() : "Engineer");
                        expMap.put("description", exp.getDescription() != null ? exp.getDescription() : "");
                        expMap.put("bullets", List.of());
                        expMaps.add(expMap);
                    }
                }
            }

            List<Map<String, Object>> projectMaps = new ArrayList<>();
            if (projectRepository != null) {
                List<Project> projs = projectRepository.findByUserId(user.getId());
                if (projs != null) {
                    for (Project proj : projs) {
                        Map<String, Object> projMap = new HashMap<>();
                        projMap.put("title", proj.getTitle() != null ? proj.getTitle() : "Project");
                        projMap.put("description", proj.getDescription() != null ? proj.getDescription() : "");
                        projMap.put("techStack", proj.getTechStack() != null ? proj.getTechStack() : "");
                        projMap.put("impactMetrics", proj.getImpactMetrics() != null ? proj.getImpactMetrics() : "");
                        projectMaps.add(projMap);
                    }
                }
            }

            JDAnalysisRequest request = new JDAnalysisRequest(
                    job.getDescription() != null && !job.getDescription().isBlank() ? job.getDescription() : fullJobCorpus,
                    summary,
                    skillMaps,
                    expMaps,
                    projectMaps
            );

            JDAnalysisResponse response = aiServiceClient.analyzeJd(request);
            if (response != null && response.getMatchScore() != null) {
                // Calibrated blend: 35% deterministic Stage A + 65% Gemini Deep Evaluation
                int blendedScore = (int) Math.round(0.35 * stageAResult.getMatchScore() + 0.65 * response.getMatchScore());
                blendedScore = Math.min(99, Math.max(30, blendedScore));

                List<String> combinedMatched = new ArrayList<>(stageAResult.getMatchedSkills());
                if (response.getMatchedKeywords() != null) {
                    combinedMatched.addAll(response.getMatchedKeywords().stream().map(this::capitalize).toList());
                }

                List<String> combinedMissing = new ArrayList<>(stageAResult.getMissingSkills());
                if (response.getMissingKeywords() != null) {
                    combinedMissing.addAll(response.getMissingKeywords().stream().map(this::capitalize).toList());
                }

                MatchResult aiResult = MatchResult.builder()
                        .matchScore(blendedScore)
                        .matchedSkills(combinedMatched.stream().distinct().limit(10).toList())
                        .missingSkills(combinedMissing.stream().distinct().limit(6).toList())
                        .build();

                aiScoreCache.put(cacheKey, aiResult);
                return aiResult;
            }
        } catch (Exception e) {
            log.debug("[RESUME MATCH SERVICE] Stage B AI evaluation skipped: {}. Falling back to Stage A.", e.getMessage());
        }

        return stageAResult;
    }

    /**
     * Records or updates a user-job interaction row with the calculated match score.
     */
    public UserJobInteraction recordInteraction(User user, DiscoveredJob job, MatchResult match, String status) {
        UserJobInteraction interaction = userJobInteractionRepository
                .findByUserIdAndDiscoveredJobId(user.getId(), job.getId())
                .orElseGet(() -> UserJobInteraction.builder()
                        .id(UUID.randomUUID().toString())
                        .user(user)
                        .discoveredJob(job)
                        .status(status != null ? status : "NEW")
                        .build());

        interaction.setMatchScore(match.getMatchScore());
        interaction.setMatchedSkills(toJson(match.getMatchedSkills()));
        interaction.setMissingSkills(toJson(match.getMissingSkills()));
        if (status != null && !status.isBlank()) {
            interaction.setStatus(status);
        }

        return userJobInteractionRepository.save(interaction);
    }

    private boolean containsWord(String text, String word) {
        if (word == null || word.isBlank()) return false;
        if (word.length() <= 3) {
            Pattern p = Pattern.compile("\\b" + Pattern.quote(word) + "\\b", Pattern.CASE_INSENSITIVE);
            return p.matcher(text).find();
        }
        return text.contains(word.toLowerCase());
    }

    private String capitalize(String text) {
        if (text == null || text.isBlank()) return text;
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase()).append(" ");
            }
        }
        return sb.toString().trim();
    }

    private String toJson(List<String> list) {
        try {
            return objectMapper.writeValueAsString(list != null ? list : List.of());
        } catch (JsonProcessingException e) {
            return "[]";
        }
    }

    public List<String> fromJson(String json) {
        try {
            if (json == null || json.isBlank()) return List.of();
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
