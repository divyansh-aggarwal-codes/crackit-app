package com.crackit.discovery.provider;

import com.crackit.discovery.entity.DiscoveredJob;

import java.util.List;

public interface JobDiscoveryProvider {

    /**
     * Unique identifier for this provider (e.g. "COMPANY_ATS", "GOOGLE_SEARCH", "ADZUNA", "JSEARCH").
     */
    String getProviderName();

    /**
     * High-level category: CAREER_PAGE_ATS, SEARCH_ENGINE, API_AGGREGATOR.
     */
    String getSourceType();

    /**
     * Discovers jobs matching the keyword and location.
     */
    List<DiscoveredJob> discoverJobs(String keyword, String location);
}
