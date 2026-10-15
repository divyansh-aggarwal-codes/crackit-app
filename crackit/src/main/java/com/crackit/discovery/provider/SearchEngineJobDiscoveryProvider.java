package com.crackit.discovery.provider;

import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.util.JobUrlNormalizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class SearchEngineJobDiscoveryProvider implements JobDiscoveryProvider {

    private final RestTemplate restTemplate;
    private final JobUrlNormalizer jobUrlNormalizer;

    @Value("${search.engine.google.key:}")
    private String googleApiKey;

    @Value("${search.engine.google.cx:}")
    private String googleSearchCx;

    private static final String GOOGLE_SEARCH_BASE = "https://www.googleapis.com/customsearch/v1";

    @Override
    public String getProviderName() {
        return "GOOGLE_SEARCH";
    }

    @Override
    public String getSourceType() {
        return "SEARCH_ENGINE";
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<DiscoveredJob> discoverJobs(String keyword, String location) {
        if (googleApiKey == null || googleApiKey.isBlank() || googleSearchCx == null || googleSearchCx.isBlank()) {
            log.debug("[SEARCH ENGINE PROVIDER] Google Custom Search API key or CX not configured. Skipping search-engine ingestion.");
            return List.of();
        }

        try {
            String targetQuery = String.format(
                    "site:boards.greenhouse.io OR site:jobs.lever.co OR site:jobs.ashbyhq.com \"%s\" %s",
                    keyword,
                    (location != null && !location.isBlank() && !"India".equalsIgnoreCase(location)) ? "\"" + location + "\"" : ""
            ).trim();

            String url = UriComponentsBuilder.fromHttpUrl(GOOGLE_SEARCH_BASE)
                    .queryParam("key", googleApiKey)
                    .queryParam("cx", googleSearchCx)
                    .queryParam("q", targetQuery)
                    .queryParam("num", 10)
                    .build()
                    .toUriString();

            log.info("[SEARCH ENGINE PROVIDER] Querying Google Programmable Search for keyword: '{}'", keyword);
            ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
            if (response.getBody() == null || !response.getBody().containsKey("items")) {
                return List.of();
            }

            List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("items");
            List<DiscoveredJob> discovered = new ArrayList<>();

            for (Map<String, Object> item : items) {
                String title = (String) item.getOrDefault("title", "");
                String link = (String) item.getOrDefault("link", "");
                String snippet = (String) item.getOrDefault("snippet", "");

                if (link.isBlank() || title.isBlank()) continue;

                String company = extractCompanyFromUrlOrTitle(link, title);
                String canonicalUrl = jobUrlNormalizer.canonicalizeUrl(link);
                String urlHash = jobUrlNormalizer.computeUrlHash(canonicalUrl);
                String semanticHash = jobUrlNormalizer.computeSemanticHash(company, title, location != null ? location : "");

                String atsPlatform = "CUSTOM";
                if (link.contains("greenhouse.io")) atsPlatform = "GREENHOUSE";
                else if (link.contains("lever.co")) atsPlatform = "LEVER";
                else if (link.contains("ashbyhq.com")) atsPlatform = "ASHBY";

                DiscoveredJob dj = DiscoveredJob.builder()
                        .id(UUID.randomUUID().toString())
                        .externalId("google_search_" + Math.abs(link.hashCode()))
                        .source("GOOGLE_SEARCH")
                        .sourceType(getSourceType())
                        .atsPlatform(atsPlatform)
                        .company(company)
                        .title(cleanTitle(title))
                        .location(location != null ? location : "")
                        .description(snippet)
                        .url(link)
                        .canonicalUrl(canonicalUrl)
                        .urlHash(urlHash)
                        .semanticHash(semanticHash)
                        .remote(title.toLowerCase().contains("remote") || snippet.toLowerCase().contains("remote"))
                        .isActive(true)
                        .verificationStatus("ACTIVE")
                        .postedAt(LocalDateTime.now())
                        .lastVerifiedAt(LocalDateTime.now())
                        .build();

                discovered.add(dj);
            }

            log.info("[SEARCH ENGINE PROVIDER] Discovered {} jobs via Google Programmable Search.", discovered.size());
            return discovered;

        } catch (Exception e) {
            log.warn("[SEARCH ENGINE PROVIDER] Google Search query failed: {}", e.getMessage());
            return List.of();
        }
    }

    private String extractCompanyFromUrlOrTitle(String link, String title) {
        try {
            URI uri = URI.create(link);
            String path = uri.getPath();
            if (path != null) {
                String[] segments = path.split("/");
                for (String s : segments) {
                    if (!s.isBlank() && !s.equalsIgnoreCase("jobs") && !s.equalsIgnoreCase("posting") && !s.matches("\\d+")) {
                        return capitalize(s.replace("-", " "));
                    }
                }
            }
        } catch (Exception ignored) {}

        if (title.contains(" at ")) {
            String[] parts = title.split(" at ");
            return parts[parts.length - 1].split("[|\\-]")[0].trim();
        }
        if (title.contains(" - ")) {
            String[] parts = title.split(" - ");
            return parts[parts.length - 1].trim();
        }
        return "Company";
    }

    private String cleanTitle(String title) {
        return title.replaceAll("(?i)\\s*[-|]\\s*(greenhouse|lever|ashby|careers).*$", "").trim();
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
}
