package com.crackit.discovery.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Component
public class JobUrlNormalizer {

    private static final Set<String> TRACKING_PARAMS = Set.of(
            "utm_source", "utm_medium", "utm_campaign", "utm_term", "utm_content",
            "ref", "source", "lever-source", "gh_src",
            "fbclid", "gclid", "msclkid", "trk", "trkinfo", "mkt_tok", "sp_rid",
            "sp_mid", "sid", "aff_id", "affiliate", "from", "original_referer"
    );

    private static final Pattern COMPANY_NOISE = Pattern.compile(
            "\\b(inc|corp|corporation|llc|ltd|limited|technologies|tech|solutions|pvt|private|group|co)\\b",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern TITLE_NOISE = Pattern.compile(
            "\\b(urgent|immediate|hiring|fresher|experienced|apply now|remote|hybrid|onsite|immediate joiner)\\b",
            Pattern.CASE_INSENSITIVE
    );

    /**
     * Canonicalizes a job URL by removing tracking query parameters, normalising scheme/host,
     * while preserving critical job identifiers (e.g. gh_jid, ashby_jid, id).
     */
    public String canonicalizeUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return "";
        }
        try {
            String trimmed = rawUrl.trim();
            URI uri = URI.create(trimmed);

            String scheme = uri.getScheme() != null ? uri.getScheme().toLowerCase() : "https";
            String host = uri.getHost() != null ? uri.getHost().toLowerCase() : "";
            if (host.startsWith("www.")) {
                host = host.substring(4);
            }

            String path = uri.getPath() != null ? uri.getPath() : "";
            // Remove trailing slash if path is longer than 1
            if (path.length() > 1 && path.endsWith("/")) {
                path = path.substring(0, path.length() - 1);
            }

            // Filter out tracking parameters
            String query = uri.getQuery();
            String cleanQuery = "";
            if (query != null && !query.isBlank()) {
                String[] pairs = query.split("&");
                List<String> retained = new ArrayList<>();
                for (String pair : pairs) {
                    int eqIdx = pair.indexOf('=');
                    String key = eqIdx > 0 ? pair.substring(0, eqIdx).toLowerCase() : pair.toLowerCase();
                    if (!TRACKING_PARAMS.contains(key)) {
                        retained.add(pair);
                    }
                }
                if (!retained.isEmpty()) {
                    Collections.sort(retained);
                    cleanQuery = "?" + String.join("&", retained);
                }
            }

            int port = uri.getPort();
            String portPart = (port != -1 && port != 80 && port != 443) ? ":" + port : "";

            return scheme + "://" + host + portPart + path + cleanQuery;
        } catch (Exception e) {
            log.debug("Failed to canonicalize URL '{}': {}", rawUrl, e.getMessage());
            return rawUrl.trim();
        }
    }

    /**
     * Generates a 64-char SHA-256 hash of the canonical URL for fast O(1) deduplication.
     */
    public String computeUrlHash(String canonicalUrl) {
        if (canonicalUrl == null || canonicalUrl.isBlank()) {
            return "";
        }
        return sha256(canonicalUrl.toLowerCase().trim());
    }

    /**
     * Generates a semantic hash: SHA-256(normCompany + ":" + normTitle + ":" + normLocation)
     * to deduplicate identical job postings across different aggregators or board variants.
     */
    public String computeSemanticHash(String company, String title, String location) {
        String c = normalizeCompany(company);
        String t = normalizeTitle(title);
        String l = normalizeLocation(location);
        return sha256(c + ":" + t + ":" + l);
    }

    public String normalizeCompany(String company) {
        if (company == null) return "";
        String s = company.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();
        s = COMPANY_NOISE.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    public String normalizeTitle(String title) {
        if (title == null) return "";
        String s = title.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .trim();
        s = TITLE_NOISE.matcher(s).replaceAll(" ");
        return s.replaceAll("\\s+", " ").trim();
    }

    /**
     * Normalizes location strings with canonical Indian city and remote mappings:
     * - Bangalore / Bengaluru -> bengaluru
     * - Gurgaon / Gurugram -> gurugram
     * - Delhi / New Delhi / Noida / NCR -> delhi ncr
     * - Bombay / Navi Mumbai / Thane / Mumbai -> mumbai
     * - Hyderabad / Secunderabad -> hyderabad
     */
    public String normalizeLocation(String location) {
        if (location == null || location.isBlank()) return "flexible";
        String s = location.toLowerCase()
                .replaceAll("[^a-z0-9\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (s.contains("remote") && s.contains("india")) return "remote india";
        if (s.contains("remote") || s.contains("anywhere") || s.contains("distributed")) return "remote";

        if (s.contains("bengaluru") || s.contains("bangalore")) return "bengaluru";
        if (s.contains("gurgaon") || s.contains("gurugram")) return "gurugram";
        if (s.contains("delhi") || s.contains("ncr") || s.contains("noida") || s.contains("faridabad") || s.contains("ghaziabad")) return "delhi ncr";
        if (s.contains("mumbai") || s.contains("bombay") || s.contains("thane")) return "mumbai";
        if (s.contains("hyderabad") || s.contains("secunderabad") || s.contains("cyberabad")) return "hyderabad";
        if (s.contains("pune")) return "pune";
        if (s.contains("chennai") || s.contains("madras")) return "chennai";
        if (s.contains("kolkata") || s.contains("calcutta")) return "kolkata";
        if (s.contains("ahmedabad")) return "ahmedabad";
        if (s.contains("india") || s.contains("in")) return "india";

        return s;
    }

    /**
     * Checks if job location is compatible with candidate's location preferences.
     */
    public boolean isLocationEligible(String candidatePreferredLocations, boolean candidatePrefersRemote, String jobLocation, Boolean jobIsRemote) {
        boolean remote = Boolean.TRUE.equals(jobIsRemote) || (jobLocation != null && jobLocation.toLowerCase().contains("remote"));
        if (candidatePrefersRemote && remote) return true;

        String normJobLoc = normalizeLocation(jobLocation);
        if (normJobLoc.equals("remote") || normJobLoc.equals("remote india")) return true;

        // If candidate specified locations
        if (candidatePreferredLocations != null && !candidatePreferredLocations.isBlank()) {
            String[] candidateLocs = candidatePreferredLocations.split("[,;/]");
            for (String cl : candidateLocs) {
                String normCl = normalizeLocation(cl);
                if (normCl.equals(normJobLoc)) return true;
                if (normCl.equals("india") && (isIndianTechHub(normJobLoc) || normJobLoc.equals("india"))) return true;
            }
        } else {
            // Default: if job is in India or remote
            if (isIndianTechHub(normJobLoc) || normJobLoc.equals("india")) return true;
        }

        return false;
    }

    public boolean isIndianTechHub(String normLocation) {
        return normLocation.equals("bengaluru") ||
               normLocation.equals("gurugram") ||
               normLocation.equals("delhi ncr") ||
               normLocation.equals("hyderabad") ||
               normLocation.equals("pune") ||
               normLocation.equals("mumbai") ||
               normLocation.equals("chennai") ||
               normLocation.equals("kolkata") ||
               normLocation.equals("ahmedabad") ||
               normLocation.equals("india") ||
               normLocation.equals("remote india");
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            throw new RuntimeException("SHA-256 algorithm unavailable", e);
        }
    }
}
