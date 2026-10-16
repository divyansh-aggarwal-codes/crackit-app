package com.crackit.discovery.provider;

import com.crackit.discovery.entity.CuratedCompany;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.repository.CuratedCompanyRepository;
import com.crackit.discovery.util.JobUrlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyAtsDiscoveryProvider implements JobDiscoveryProvider {

    private final CuratedCompanyRepository curatedCompanyRepository;
    private final RestTemplate restTemplate;
    private final JobUrlNormalizer jobUrlNormalizer;

    @Override
    public String getProviderName() {
        return "COMPANY_ATS";
    }

    @Override
    public String getSourceType() {
        return "CAREER_PAGE_ATS";
    }

    @Override
    public List<DiscoveredJob> discoverJobs(String keyword, String location) {
        List<CuratedCompany> companies = curatedCompanyRepository.findByIsActiveTrue();
        log.info("[COMPANY ATS PROVIDER] Scanning career portals for {} active curated companies...", companies.size());

        List<DiscoveredJob> discovered = new ArrayList<>();
        String kwLower = keyword != null ? keyword.trim().toLowerCase() : "";
        String locLower = location != null ? location.trim().toLowerCase() : "";

        for (CuratedCompany company : companies) {
            try {
                String platform = company.getAtsPlatform() != null ? company.getAtsPlatform().toUpperCase() : "GREENHOUSE";
                List<DiscoveredJob> jobs = switch (platform) {
                    case "GREENHOUSE" -> fetchGreenhouseJobs(company, kwLower, locLower);
                    case "LEVER" -> fetchLeverJobs(company, kwLower, locLower);
                    case "ASHBY" -> fetchAshbyJobs(company, kwLower, locLower);
                    default -> fetchGreenhouseJobs(company, kwLower, locLower);
                };
                company.setLastDiscoveredAt(LocalDateTime.now());
                company.setLastJobCount(jobs.size());
                company.setHealthStatus("HEALTHY");
                curatedCompanyRepository.save(company);
                discovered.addAll(jobs);
            } catch (Exception e) {
                log.warn("[COMPANY ATS PROVIDER] Failed scanning {} ({}): {}", company.getName(), company.getAtsPlatform(), e.getMessage());
                try {
                    company.setLastDiscoveredAt(LocalDateTime.now());
                    company.setHealthStatus("DEGRADED: " + (e.getMessage() != null && e.getMessage().length() > 200 ? e.getMessage().substring(0, 200) : e.getMessage()));
                    curatedCompanyRepository.save(company);
                } catch (Exception ignored) {}
            }
        }

        log.info("[COMPANY ATS PROVIDER] Discovered {} matching openings directly from official career portals.", discovered.size());
        return discovered;
    }

    @SuppressWarnings("unchecked")
    private List<DiscoveredJob> fetchGreenhouseJobs(CuratedCompany company, String keyword, String location) {
        String url = "https://boards-api.greenhouse.io/v1/boards/" + company.getAtsBoardToken() + "/jobs?content=true";
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null || !response.getBody().containsKey("jobs")) {
                return List.of();
            }

            List<Map<String, Object>> jobList = (List<Map<String, Object>>) response.getBody().get("jobs");
            List<DiscoveredJob> matched = new ArrayList<>();

            for (Map<String, Object> jobMap : jobList) {
                String title = getString(jobMap, "title");
                String rawContent = getString(jobMap, "content");
                String cleanDescription = cleanHtml(rawContent);

                Map<String, Object> locMap = (Map<String, Object>) jobMap.get("location");
                String loc = locMap != null ? getString(locMap, "name") : "";

                if (!matchesFilter(title, cleanDescription, loc, keyword, location)) {
                    continue;
                }

                String applyUrl = getString(jobMap, "absolute_url");
                String jobId = String.valueOf(jobMap.get("id"));
                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(applyUrl);

                LocalDateTime postedAt = parseDateTime(getString(jobMap, "updated_at"));

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId("greenhouse_" + company.getAtsBoardToken() + "_" + jobId)
                        .source("CAREER_PAGE")
                        .sourceType(getSourceType())
                        .atsPlatform("GREENHOUSE")
                        .company(company.getName())
                        .companyDomain(company.getDomain())
                        .title(title)
                        .location(loc)
                        .description(cleanDescription)
                        .url(applyUrl)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(jobUrlNormalizer.computeUrlHash(canonicalUrl))
                        .semanticHash(jobUrlNormalizer.computeSemanticHash(company.getName(), title, loc))
                        .remote(isRemote(title, loc, cleanDescription))
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .postedAt(postedAt)
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                matched.add(dj);
            }
            return matched;
        } catch (Exception e) {
            log.debug("Greenhouse board query failed for {}: {}", company.getAtsBoardToken(), e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<DiscoveredJob> fetchLeverJobs(CuratedCompany company, String keyword, String location) {
        String url = "https://api.lever.co/v0/postings/" + company.getAtsBoardToken() + "?mode=json";
        try {
            ResponseEntity<List> response = restTemplate.getForEntity(url, List.class);
            if (response.getBody() == null) {
                return List.of();
            }

            List<Map<String, Object>> postings = (List<Map<String, Object>>) response.getBody();
            List<DiscoveredJob> matched = new ArrayList<>();

            for (Map<String, Object> p : postings) {
                String title = getString(p, "text");
                String cleanDescription = getString(p, "descriptionPlain");
                if (cleanDescription.isBlank()) {
                    cleanDescription = cleanHtml(getString(p, "description"));
                }

                Map<String, Object> cats = (Map<String, Object>) p.get("categories");
                String loc = cats != null ? getString(cats, "location") : "";
                String commitment = cats != null ? getString(cats, "commitment") : "";

                if (!matchesFilter(title, cleanDescription, loc, keyword, location)) {
                    continue;
                }

                String applyUrl = getString(p, "hostedUrl");
                String jobId = getString(p, "id");
                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(applyUrl);

                LocalDateTime postedAt = null;
                Object createdObj = p.get("createdAt");
                if (createdObj instanceof Number num) {
                    postedAt = LocalDateTime.ofInstant(Instant.ofEpochMilli(num.longValue()), ZoneId.systemDefault());
                }

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId("lever_" + company.getAtsBoardToken() + "_" + jobId)
                        .source("CAREER_PAGE")
                        .sourceType(getSourceType())
                        .atsPlatform("LEVER")
                        .company(company.getName())
                        .companyDomain(company.getDomain())
                        .title(title)
                        .location(loc)
                        .description(cleanDescription)
                        .url(applyUrl)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(jobUrlNormalizer.computeUrlHash(canonicalUrl))
                        .semanticHash(jobUrlNormalizer.computeSemanticHash(company.getName(), title, loc))
                        .jobType(commitment)
                        .remote(isRemote(title, loc, cleanDescription))
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .postedAt(postedAt)
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                matched.add(dj);
            }
            return matched;
        } catch (Exception e) {
            log.debug("Lever board query failed for {}: {}", company.getAtsBoardToken(), e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<DiscoveredJob> fetchAshbyJobs(CuratedCompany company, String keyword, String location) {
        String url = "https://api.ashbyhq.com/posting-api/job-board/" + company.getAtsBoardToken();
        try {
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null || !response.getBody().containsKey("jobs")) {
                return List.of();
            }

            List<Map<String, Object>> jobList = (List<Map<String, Object>>) response.getBody().get("jobs");
            List<DiscoveredJob> matched = new ArrayList<>();

            for (Map<String, Object> j : jobList) {
                String title = getString(j, "title");
                String cleanDescription = getString(j, "descriptionPlain");
                if (cleanDescription.isBlank()) {
                    cleanDescription = cleanHtml(getString(j, "descriptionHtml"));
                }

                String loc = getString(j, "location");
                String applyUrl = getString(j, "jobUrl");
                String jobId = getString(j, "id");

                if (!matchesFilter(title, cleanDescription, loc, keyword, location)) {
                    continue;
                }

                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(applyUrl);
                LocalDateTime postedAt = parseDateTime(getString(j, "publishedAt"));

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId("ashby_" + company.getAtsBoardToken() + "_" + jobId)
                        .source("CAREER_PAGE")
                        .sourceType(getSourceType())
                        .atsPlatform("ASHBY")
                        .company(company.getName())
                        .companyDomain(company.getDomain())
                        .title(title)
                        .location(loc)
                        .description(cleanDescription)
                        .url(applyUrl)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(jobUrlNormalizer.computeUrlHash(canonicalUrl))
                        .semanticHash(jobUrlNormalizer.computeSemanticHash(company.getName(), title, loc))
                        .remote(isRemote(title, loc, cleanDescription))
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .postedAt(postedAt)
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                matched.add(dj);
            }
            return matched;
        } catch (Exception e) {
            log.debug("Ashby board query failed for {}: {}", company.getAtsBoardToken(), e.getMessage());
            return List.of();
        }
    }

    private boolean matchesFilter(String title, String desc, String location, String keyword, String filterLoc) {
        if (!JobUrlNormalizer.isTechnicalJob(title)) {
            return false;
        }

        if (keyword != null && !keyword.isBlank()) {
            String[] tokens = keyword.toLowerCase().split("\\s+");
            boolean matchesToken = false;
            String lowerTitle = (title != null ? title : "").toLowerCase();
            for (String t : tokens) {
                if (t.length() >= 3 && lowerTitle.contains(t)) {
                    matchesToken = true;
                    break;
                }
            }
            if (!matchesToken) {
                String lowerDesc = (desc != null ? desc : "").toLowerCase();
                for (String t : tokens) {
                    if (t.length() >= 3 && lowerDesc.contains(t)) {
                        matchesToken = true;
                        break;
                    }
                }
            }
            if (!matchesToken) return false;
        }

        if (filterLoc != null && !filterLoc.isBlank() && !filterLoc.equalsIgnoreCase("All")) {
            boolean remote = isRemote(title, location, desc);
            boolean remotePref = "remote".equalsIgnoreCase(filterLoc.trim());
            return jobUrlNormalizer.isLocationEligible(filterLoc, remotePref, location, remote);
        }

        return true;
    }

    private boolean isRemote(String title, String loc, String desc) {
        String combined = (title + " " + loc + " " + (desc.length() > 300 ? desc.substring(0, 300) : desc)).toLowerCase();
        return combined.contains("remote") || combined.contains("work from anywhere") || combined.contains("distributed");
    }

    private String cleanHtml(String html) {
        if (html == null || html.isBlank()) return "";
        try {
            return Jsoup.parse(html).text();
        } catch (Exception e) {
            return html.replaceAll("<[^>]*>", " ").replaceAll("\\s+", " ").trim();
        }
    }

    private LocalDateTime parseDateTime(String isoStr) {
        if (isoStr == null || isoStr.isBlank()) return null;
        try {
            return LocalDateTime.ofInstant(Instant.parse(isoStr), ZoneId.systemDefault());
        } catch (Exception ignored) {
            try {
                return LocalDateTime.parse(isoStr.replace("Z", ""));
            } catch (Exception e) {
                return null;
            }
        }
    }

    private String getString(Map<String, Object> map, String key) {
        Object val = map.get(key);
        return val != null ? val.toString().trim() : "";
    }
}
