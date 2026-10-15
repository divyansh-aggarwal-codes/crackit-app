package com.crackit.discovery.service;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.common.util.AuthUtil;
import com.crackit.discovery.client.AdzunaClient;
import com.crackit.discovery.client.JSearchClient;
import com.crackit.discovery.dto.DiscoveredJobDto;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.provider.CompanyAtsDiscoveryProvider;
import com.crackit.discovery.provider.SearchEngineJobDiscoveryProvider;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import com.crackit.discovery.util.JobUrlNormalizer;
import com.crackit.resume.entity.Experience;
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.ExperienceRepository;
import com.crackit.resume.repository.SkillRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
@Service
@RequiredArgsConstructor
public class JobDiscoveryService {

    private final AdzunaClient adzunaClient;
    private final JSearchClient jSearchClient;
    private final CompanyAtsDiscoveryProvider companyAtsDiscoveryProvider;
    private final SearchEngineJobDiscoveryProvider searchEngineJobDiscoveryProvider;
    private final JobUrlNormalizer jobUrlNormalizer;
    private final ResumeMatchService resumeMatchService;
    private final JobVerificationService jobVerificationService;
    private final DiscoveredJobRepository discoveredJobRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;
    private final UserRepository userRepository;
    private final SkillRepository skillRepository;
    private final ExperienceRepository experienceRepository;

    @Autowired
    @Lazy
    private JobDiscoveryService self;

    // ─── Dynamic Scheduled Fetch across User Target Roles ────────────────────
    @CacheEvict(value = "discoveredJobs", allEntries = true)
    @Scheduled(fixedDelay = 3600000)
    public void scheduledFetch() {
        log.info("[JOB DISCOVERY ENGINE] Running scheduled multi-provider job discovery fetch...");

        // Dynamically collect distinct target roles from active users
        List<User> activeUsers = userRepository.findAll();
        Set<String> rolesToFetch = new LinkedHashSet<>();
        Set<String> locationsToFetch = new LinkedHashSet<>();

        for (User u : activeUsers) {
            if (u.getTargetRole() != null && !u.getTargetRole().isBlank()) {
                rolesToFetch.add(u.getTargetRole().trim());
            } else if (u.getCurrentRole() != null && !u.getCurrentRole().isBlank()) {
                rolesToFetch.add(u.getCurrentRole().trim());
            }
            if (u.getPreferredLocations() != null && !u.getPreferredLocations().isBlank()) {
                String loc = u.getPreferredLocations().split("[,;/]")[0].trim();
                if (!loc.isBlank()) locationsToFetch.add(loc);
            }
        }

        // Add standard tech roles as baseline fallbacks
        rolesToFetch.addAll(List.of(
                "Java Backend Engineer",
                "Software Engineer",
                "Backend Developer",
                "Full Stack Developer",
                "Python Developer"
        ));

        if (locationsToFetch.isEmpty()) {
            locationsToFetch.add("India");
            locationsToFetch.add("Remote");
        }

        // Limit scheduled discovery batches to top 5 roles
        List<String> prioritizedRoles = rolesToFetch.stream().limit(5).toList();
        for (String role : prioritizedRoles) {
            for (String location : locationsToFetch.stream().limit(2).toList()) {
                fetchAndStore(role, location);
            }
        }

        log.info("[JOB DISCOVERY ENGINE] Scheduled fetch complete. Pre-scoring against candidate resumes...");
        preScoreForActiveUsers(activeUsers);
    }

    private void preScoreForActiveUsers(List<User> users) {
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<DiscoveredJob> recentJobs = discoveredJobRepository.findActiveJobsSince(since);

        for (User user : users) {
            for (DiscoveredJob job : recentJobs) {
                try {
                    ResumeMatchService.MatchResult match = resumeMatchService.calculateTier1Score(user, job);
                    resumeMatchService.recordInteraction(user, job, match, "NEW");
                } catch (Exception ignored) {}
            }
        }
    }

    // ─── Recommended Jobs — Personalized & AI Scored for Current User ────────
    public List<DiscoveredJobDto> getRecommended() {
        String email = AuthUtil.getLoggedInUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String location = "India";
        if ("Remote".equalsIgnoreCase(user.getPreferredWorkMode())) {
            location = "Remote";
        } else if (user.getPreferredLocations() != null && !user.getPreferredLocations().isBlank()) {
            location = user.getPreferredLocations().split("[,;/]")[0].trim();
        } else if (user.getLocation() != null && !user.getLocation().isBlank()) {
            location = user.getLocation().trim();
        }

        List<String> keywords = buildKeywords(user);
        log.info("Fetching recommended jobs for keywords: {}, location: {}", keywords, location);

        for (String keyword : keywords) {
            fetchAndStore(keyword, location);
        }

        LocalDateTime since = LocalDateTime.now().minusHours(48);
        List<DiscoveredJob> candidates = new ArrayList<>();
        for (String keyword : keywords) {
            List<DiscoveredJob> found = discoveredJobRepository.searchFreshByKeyword(keyword, since);
            for (DiscoveredJob job : found) {
                if (Boolean.TRUE.equals(job.getIsActive()) && candidates.stream().noneMatch(c -> c.getId().equals(job.getId()))) {
                    candidates.add(job);
                }
            }
        }

        // Filter out dismissed jobs
        List<String> dismissedJobIds = userJobInteractionRepository.findDismissedJobIdsByUserId(user.getId());
        Set<String> dismissedSet = new HashSet<>(dismissedJobIds);

        List<DiscoveredJobDto> dtos = new ArrayList<>();
        for (DiscoveredJob job : candidates) {
            if (dismissedSet.contains(job.getId())) continue;

            ResumeMatchService.MatchResult match = resumeMatchService.calculateTier1Score(user, job);
            UserJobInteraction interaction = resumeMatchService.recordInteraction(user, job, match, null);

            dtos.add(toDto(job, match.getMatchScore(), match.getMatchedSkills(), match.getMissingSkills(), interaction.getStatus()));
        }

        // Sort by match score DESC, with official career pages prioritized
        return dtos.stream()
                .sorted(Comparator.comparing(DiscoveredJobDto::getMatchScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(j -> "CAREER_PAGE_ATS".equals(j.getSourceType()) ? 0 : 1))
                .limit(25)
                .collect(Collectors.toList());
    }

    // ─── Manual Search with Filter Controls ──────────────────────────────────
    public List<DiscoveredJobDto> search(String keyword, String location, Integer minScore, String sourceType) {
        fetchAndStore(keyword, location);

        User user = null;
        try {
            String email = AuthUtil.getLoggedInUserEmail();
            if (email != null) {
                user = userRepository.findByEmail(email).orElse(null);
            }
        } catch (Exception ignored) {}

        LocalDateTime since = LocalDateTime.now().minusHours(48);
        List<DiscoveredJob> matchedJobs = discoveredJobRepository.searchFreshByKeyword(keyword, since);

        Set<String> dismissedSet = (user != null)
                ? new HashSet<>(userJobInteractionRepository.findDismissedJobIdsByUserId(user.getId()))
                : Collections.emptySet();

        List<DiscoveredJobDto> dtos = new ArrayList<>();
        for (DiscoveredJob job : matchedJobs) {
            if (!Boolean.TRUE.equals(job.getIsActive())) continue;
            if (dismissedSet.contains(job.getId())) continue;

            // Filter by sourceType if specified
            if (sourceType != null && !sourceType.isBlank() && !"ALL".equalsIgnoreCase(sourceType)) {
                if (!sourceType.equalsIgnoreCase(job.getSourceType()) && !sourceType.equalsIgnoreCase(job.getSource())) {
                    continue;
                }
            }

            int score = 65;
            List<String> matchedSkills = List.of();
            List<String> missingSkills = List.of();
            String status = "NEW";

            if (user != null) {
                ResumeMatchService.MatchResult match = resumeMatchService.calculateTier1Score(user, job);
                score = match.getMatchScore();
                matchedSkills = match.getMatchedSkills();
                missingSkills = match.getMissingSkills();
                UserJobInteraction interaction = resumeMatchService.recordInteraction(user, job, match, null);
                status = interaction.getStatus();
            }

            // Filter by minScore if specified
            if (minScore != null && score < minScore) {
                continue;
            }

            dtos.add(toDto(job, score, matchedSkills, missingSkills, status));
        }

        return dtos.stream()
                .sorted(Comparator.comparing(DiscoveredJobDto::getMatchScore, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(j -> "CAREER_PAGE_ATS".equals(j.getSourceType()) ? 0 : 1))
                .collect(Collectors.toList());
    }

    // ─── Dismiss Job for Current User ─────────────────────────────────────────
    public void dismissJob(String jobId) {
        String email = AuthUtil.getLoggedInUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        DiscoveredJob job = discoveredJobRepository.findById(jobId)
                .orElseThrow(() -> new RuntimeException("Job not found"));

        UserJobInteraction interaction = userJobInteractionRepository
                .findByUserIdAndDiscoveredJobId(user.getId(), job.getId())
                .orElseGet(() -> UserJobInteraction.builder()
                        .id(UUID.randomUUID().toString())
                        .user(user)
                        .discoveredJob(job)
                        .build());

        interaction.setStatus("DISMISSED");
        userJobInteractionRepository.save(interaction);
        log.info("User {} dismissed job {} ({})", user.getEmail(), job.getId(), job.getTitle());
    }

    // ─── Build Keywords from Profile ─────────────────────────────────────────
    private List<String> buildKeywords(User user) {
        List<String> keywords = new ArrayList<>();

        if (user.getTargetRole() != null && !user.getTargetRole().isBlank()) {
            keywords.add(user.getTargetRole().trim());
        }

        if (user.getCurrentRole() != null && !user.getCurrentRole().isBlank()
                && !keywords.contains(user.getCurrentRole().trim())) {
            keywords.add(user.getCurrentRole().trim());
        }

        List<Experience> experiences = experienceRepository.findByUserId(user.getId());
        experiences.stream()
                .filter(e -> Boolean.TRUE.equals(e.getCurrentCompany()))
                .findFirst()
                .ifPresent(e -> {
                    if (e.getRole() != null && !e.getRole().isBlank() && !keywords.contains(e.getRole().trim())) {
                        keywords.add(e.getRole().trim());
                    }
                });

        List<Skill> skills = skillRepository.findByUserId(user.getId());
        skills.stream()
                .filter(s -> s.getProficiencyLevel() != null &&
                        (s.getProficiencyLevel().equalsIgnoreCase("Expert") ||
                         s.getProficiencyLevel().equalsIgnoreCase("Advanced")))
                .limit(2)
                .forEach(s -> {
                    String skillKeyword = s.getSkillName() + " Developer";
                    if (!keywords.contains(skillKeyword)) {
                        keywords.add(skillKeyword);
                    }
                });

        if (keywords.isEmpty()) {
            keywords.add("Software Engineer");
        }

        return keywords.stream().distinct().limit(4).collect(Collectors.toList());
    }

    // ─── Core Multi-Provider Fetch + Deduplicate + Save ───────────────────────
    public void fetchAndStore(String keyword, String location) {
        int saved = 0;

        // 1. Ingest from Official Company Career Portals (Greenhouse, Lever, Ashby)
        try {
            List<DiscoveredJob> atsJobs = companyAtsDiscoveryProvider.discoverJobs(keyword, location);
            for (DiscoveredJob job : atsJobs) {
                if (saveIfUnique(job)) saved++;
            }
        } catch (Exception e) {
            log.warn("Company ATS discovery failed for keyword {}: {}", keyword, e.getMessage());
        }

        // 2. Ingest from Search Engine Provider (Google Programmable Search)
        try {
            List<DiscoveredJob> searchJobs = searchEngineJobDiscoveryProvider.discoverJobs(keyword, location);
            for (DiscoveredJob job : searchJobs) {
                if (saveIfUnique(job)) saved++;
            }
        } catch (Exception e) {
            log.warn("Search engine discovery failed for keyword {}: {}", keyword, e.getMessage());
        }

        // 3. Ingest from Adzuna Aggregator
        try {
            List<Map<String, Object>> adzunaResults = adzunaClient.search(keyword, location, 1);
            for (Map<String, Object> job : adzunaResults) {
                String externalId = "adzuna_" + job.get("id");
                String applyUrl = getString(job, "redirect_url");
                String company = getCompanyName(job);
                String title = getString(job, "title");
                String jobLoc = getAdzunaLocation(job);
                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(applyUrl);

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId(externalId)
                        .source("ADZUNA")
                        .sourceType("API_AGGREGATOR")
                        .title(title)
                        .company(company)
                        .location(jobLoc)
                        .description(getString(job, "description"))
                        .url(applyUrl)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(jobUrlNormalizer.computeUrlHash(canonicalUrl))
                        .semanticHash(jobUrlNormalizer.computeSemanticHash(company, title, jobLoc))
                        .salaryMin(getDouble(job, "salary_min"))
                        .salaryMax(getDouble(job, "salary_max"))
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                if (saveIfUnique(dj)) saved++;
            }
        } catch (Exception e) {
            log.warn("Adzuna ingestion failed: {}", e.getMessage());
        }

        // 4. Ingest from JSearch RapidAPI
        try {
            List<Map<String, Object>> jsearchResults = jSearchClient.search(keyword, location);
            for (Map<String, Object> job : jsearchResults) {
                String externalId = "jsearch_" + job.get("job_id");
                String city = getString(job, "job_city");
                String state = getString(job, "job_state");
                String country = getString(job, "job_country");
                String jobLocation = Stream.of(city, state, country).filter(s -> !s.isBlank()).collect(Collectors.joining(", "));
                String applyUrl = getString(job, "job_apply_link");
                String company = getString(job, "employer_name");
                String title = getString(job, "job_title");
                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(applyUrl);

                LocalDateTime postedAt = null;
                String postedStr = getString(job, "job_posted_at_datetime_utc");
                if (!postedStr.isBlank()) {
                    try { postedAt = LocalDateTime.parse(postedStr.replace("Z", "")); } catch (Exception ignored) {}
                }

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId(externalId)
                        .source("JSEARCH")
                        .sourceType("API_AGGREGATOR")
                        .title(title)
                        .company(company)
                        .location(jobLocation)
                        .description(getString(job, "job_description"))
                        .url(applyUrl)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(jobUrlNormalizer.computeUrlHash(canonicalUrl))
                        .semanticHash(jobUrlNormalizer.computeSemanticHash(company, title, jobLocation))
                        .remote(Boolean.TRUE.equals(job.get("job_is_remote")))
                        .jobType(getString(job, "job_employment_type"))
                        .salaryMin(getDouble(job, "job_min_salary"))
                        .salaryMax(getDouble(job, "job_max_salary"))
                        .postedAt(postedAt)
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                if (saveIfUnique(dj)) saved++;
            }
        } catch (Exception e) {
            log.warn("JSearch ingestion failed: {}", e.getMessage());
        }

        log.info("[JOB DISCOVERY ENGINE] Saved {} new unique jobs across all providers for keyword: '{}'", saved, keyword);
    }

    /**
     * Strict multi-layer deduplication guard:
     * 1. URL Hash check (exact canonical link match)
     * 2. Semantic Hash check (normCompany + normTitle + normLocation match)
     * 3. External ID check
     */
    private boolean saveIfUnique(DiscoveredJob job) {
        if (job.getUrlHash() != null && !job.getUrlHash().isBlank()) {
            if (discoveredJobRepository.findByUrlHash(job.getUrlHash()).isPresent()) {
                return false;
            }
        }
        if (job.getSemanticHash() != null && !job.getSemanticHash().isBlank()) {
            if (discoveredJobRepository.findBySemanticHash(job.getSemanticHash()).isPresent()) {
                return false;
            }
        }
        if (job.getExternalId() != null && !job.getExternalId().isBlank()) {
            if (discoveredJobRepository.findByExternalId(job.getExternalId()).isPresent()) {
                return false;
            }
        }

        discoveredJobRepository.save(job);
        return true;
    }

    // ─── Cleanup Old Jobs (Daily Midnight) ───────────────────────────────────
    @Scheduled(cron = "0 0 0 * * *")
    public void cleanupOldJobs() {
        LocalDateTime cutoff = LocalDateTime.now().minusDays(14);
        List<DiscoveredJob> old = discoveredJobRepository.findByFetchedAtBefore(cutoff);
        discoveredJobRepository.deleteAll(old);
        log.info("[JOB DISCOVERY ENGINE] Cleaned up {} stale discovered jobs older than 14 days", old.size());
    }

    // ─── Cached Jobs for Dashboard ───────────────────────────────────────────
    public List<DiscoveredJobDto> getCached() {
        String email = AuthUtil.getLoggedInUserEmail();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String role = user.getCurrentRole() != null && !user.getCurrentRole().isBlank()
                ? user.getCurrentRole().trim().toLowerCase()
                : "software engineer";

        return self.getCachedJobsForRole(role);
    }

    @Cacheable(value = "discoveredJobs", key = "#role")
    public List<DiscoveredJobDto> getCachedJobsForRole(String role) {
        log.info("[CACHE MISS] Querying database for role: '{}'", role);
        LocalDateTime since = LocalDateTime.now().minusHours(48);

        List<DiscoveredJob> freshJobs = discoveredJobRepository.searchFreshByKeyword(role, since);
        List<DiscoveredJob> finalJobs = freshJobs.stream()
                .filter(j -> Boolean.TRUE.equals(j.getIsActive()))
                .collect(Collectors.toList());

        if (finalJobs.size() < 6) {
            List<DiscoveredJob> olderJobs = discoveredJobRepository.cacheFallback(role);
            Set<String> existingIds = finalJobs.stream().map(DiscoveredJob::getId).collect(Collectors.toSet());
            olderJobs.stream()
                    .filter(j -> Boolean.TRUE.equals(j.getIsActive()) && !existingIds.contains(j.getId()))
                    .forEach(finalJobs::add);
        }

        return finalJobs.stream()
                .sorted(Comparator.comparing(
                        (DiscoveredJob j) -> j.getPostedAt() != null ? j.getPostedAt() : j.getFetchedAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ).thenComparing(j -> "CAREER_PAGE_ATS".equals(j.getSourceType()) ? 0 : 1))
                .limit(6)
                .map(j -> toDto(j, 85, List.of("Java", "Spring Boot"), List.of(), "NEW"))
                .collect(Collectors.toList());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────
    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : "";
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val == null) return null;
        try { return Double.parseDouble(val.toString()); }
        catch (Exception e) { return null; }
    }

    @SuppressWarnings("unchecked")
    private String getCompanyName(Map<String, Object> job) {
        Object company = job.get("company");
        if (company instanceof Map)
            return ((Map<String, Object>) company).getOrDefault("display_name", "").toString();
        return company != null ? company.toString() : "";
    }

    @SuppressWarnings("unchecked")
    private String getAdzunaLocation(Map<String, Object> job) {
        Object location = job.get("location");
        if (location instanceof Map)
            return ((Map<String, Object>) location).getOrDefault("display_name", "").toString();
        return "";
    }

    private DiscoveredJobDto toDto(DiscoveredJob job, Integer matchScore, List<String> matchedSkills, List<String> missingSkills, String status) {
        return DiscoveredJobDto.builder()
                .id(job.getId())
                .externalId(job.getExternalId())
                .source(job.getSource())
                .sourceType(job.getSourceType())
                .atsPlatform(job.getAtsPlatform())
                .companyDomain(job.getCompanyDomain())
                .title(job.getTitle())
                .company(job.getCompany())
                .location(job.getLocation())
                .description(job.getDescription())
                .url(job.getUrl())
                .canonicalUrl(job.getCanonicalUrl())
                .salaryMin(job.getSalaryMin())
                .salaryMax(job.getSalaryMax())
                .remote(job.getRemote())
                .jobType(job.getJobType())
                .fetchedAt(job.getFetchedAt())
                .postedAt(job.getPostedAt())
                .isActive(job.getIsActive())
                .verificationStatus(job.getVerificationStatus())
                .matchScore(matchScore != null ? matchScore : 60)
                .matchedSkills(matchedSkills != null ? matchedSkills : List.of())
                .missingSkills(missingSkills != null ? missingSkills : List.of())
                .status(status != null ? status : "NEW")
                .build();
    }
}