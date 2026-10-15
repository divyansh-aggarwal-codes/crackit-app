package com.crackit.discovery.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JobUrlNormalizerTest {

    private JobUrlNormalizer normalizer;

    @BeforeEach
    void setUp() {
        normalizer = new JobUrlNormalizer();
    }

    @Test
    @DisplayName("Should strip tracking parameters (UTM, ref, gh_src) and preserve canonical URL")
    void testCanonicalizeUrlStripsTracking() {
        String raw = "https://boards.greenhouse.io/stripe/jobs/456123?utm_source=linkedin&utm_medium=job_post&ref=partner&gh_src=custom#apply";
        String expected = "https://boards.greenhouse.io/stripe/jobs/456123";

        String canonical = normalizer.canonicalizeUrl(raw);
        assertEquals(expected, canonical);
    }

    @Test
    @DisplayName("Should produce identical urlHash for different tracking links pointing to same job")
    void testUrlHashConsistency() {
        String url1 = "https://boards.greenhouse.io/stripe/jobs/101?utm_source=twitter";
        String url2 = "https://boards.greenhouse.io/stripe/jobs/101?gh_src=feed&utm_campaign=hiring";

        String hash1 = normalizer.computeUrlHash(normalizer.canonicalizeUrl(url1));
        String hash2 = normalizer.computeUrlHash(normalizer.canonicalizeUrl(url2));

        assertNotNull(hash1);
        assertEquals(64, hash1.length());
        assertEquals(hash1, hash2, "URL hashes must be identical after canonicalization");
    }

    @Test
    @DisplayName("Should produce consistent semantic hash across company noise variants")
    void testSemanticHashNormalization() {
        String hash1 = normalizer.computeSemanticHash("Stripe Inc.", "Senior Java Engineer", "Bangalore, India");
        String hash2 = normalizer.computeSemanticHash("Stripe Technologies LLC", "Senior Java Engineer", "Bangalore");

        assertNotNull(hash1);
        assertNotNull(hash2);
        // Normalized companies should both be "stripe"
        assertEquals("stripe", normalizer.normalizeCompany("Stripe Inc."));
        assertEquals("stripe", normalizer.normalizeCompany("Stripe Technologies LLC"));
    }
}
