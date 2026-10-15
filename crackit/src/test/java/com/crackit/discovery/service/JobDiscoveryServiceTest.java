package com.crackit.discovery.service;

import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
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
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.ExperienceRepository;
import com.crackit.resume.repository.SkillRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobDiscoveryServiceTest {

    @Mock private AdzunaClient adzunaClient;
    @Mock private JSearchClient jSearchClient;
    @Mock private CompanyAtsDiscoveryProvider companyAtsDiscoveryProvider;
    @Mock private SearchEngineJobDiscoveryProvider searchEngineJobDiscoveryProvider;
    @Mock private JobUrlNormalizer jobUrlNormalizer;
    @Mock private ResumeMatchService resumeMatchService;
    @Mock private JobVerificationService jobVerificationService;
    @Mock private DiscoveredJobRepository discoveredJobRepository;
    @Mock private UserJobInteractionRepository userJobInteractionRepository;
    @Mock private UserRepository userRepository;
    @Mock private SkillRepository skillRepository;
    @Mock private ExperienceRepository experienceRepository;

    private JobDiscoveryService jobDiscoveryService;

    private User testUser;

    @BeforeEach
    void setUp() {
        jobDiscoveryService = new JobDiscoveryService(
                adzunaClient,
                jSearchClient,
                companyAtsDiscoveryProvider,
                searchEngineJobDiscoveryProvider,
                jobUrlNormalizer,
                resumeMatchService,
                jobVerificationService,
                discoveredJobRepository,
                userJobInteractionRepository,
                userRepository,
                skillRepository,
                experienceRepository
        );
        ReflectionTestUtils.setField(jobDiscoveryService, "self", jobDiscoveryService);

        testUser = User.builder()
                .id("user-1")
                .email("test.developer@example.com")
                .targetRole("Senior Java Backend Engineer")
                .currentRole("Java backend engineer")
                .preferredLocations("Bangalore, India")
                .preferredWorkMode("Hybrid")
                .build();

        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(testUser.getEmail(), null, List.of())
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("getRecommended should retrieve candidates via token search and return scored jobs without remote scraping")
    void testGetRecommendedNonBlockingAndTokenAware() {
        when(userRepository.findByEmail("test.developer@example.com")).thenReturn(Optional.of(testUser));
        when(userJobInteractionRepository.findDismissedJobIdsByUserId("user-1")).thenReturn(Collections.emptyList());

        Skill javaSkill = Skill.builder().id("s-1").skillName("Java").proficiencyLevel("Expert").build();
        when(skillRepository.findByUserId("user-1")).thenReturn(List.of(javaSkill));

        DiscoveredJob job1 = DiscoveredJob.builder()
                .id("job-uber-1")
                .title("Senior Backend Engineer - Platform")
                .company("Uber")
                .description("Build microservices in Java and Go for global platform.")
                .location("Bangalore, India")
                .sourceType("CAREER_PAGE_ATS")
                .isActive(true)
                .fetchedAt(LocalDateTime.now().minusHours(2))
                .build();

        DiscoveredJob job2 = DiscoveredJob.builder()
                .id("job-databricks-2")
                .title("Software Development Engineer II - Backend")
                .company("Databricks")
                .description("Core distributed systems development in Java.")
                .location("Bangalore, India")
                .sourceType("CAREER_PAGE_ATS")
                .isActive(true)
                .fetchedAt(LocalDateTime.now().minusHours(5))
                .build();

        // When searching by keyword or tokens (e.g. "backend", "java")
        when(discoveredJobRepository.searchActiveByKeyword(anyString())).thenAnswer(invocation -> {
            String kw = invocation.getArgument(0, String.class).toLowerCase();
            if (kw.contains("backend") || kw.contains("java")) {
                return List.of(job1, job2);
            }
            return List.of();
        });

        when(resumeMatchService.calculateTier1Score(eq(testUser), eq(job1), anyBoolean())).thenReturn(
                ResumeMatchService.MatchResult.builder()
                        .matchScore(92)
                        .matchedSkills(List.of("Java", "Microservices"))
                        .missingSkills(List.of())
                        .build()
        );
        when(resumeMatchService.calculateTier1Score(eq(testUser), eq(job2), anyBoolean())).thenReturn(
                ResumeMatchService.MatchResult.builder()
                        .matchScore(88)
                        .matchedSkills(List.of("Java", "Distributed Systems"))
                        .missingSkills(List.of())
                        .build()
        );

        when(resumeMatchService.recordInteraction(any(), any(), any(), any())).thenAnswer(invocation ->
                UserJobInteraction.builder()
                        .id("uji-1")
                        .status("NEW")
                        .build()
        );

        List<DiscoveredJobDto> recommendations = jobDiscoveryService.getRecommended();

        assertNotNull(recommendations);
        assertEquals(2, recommendations.size());
        assertEquals("Uber", recommendations.get(0).getCompany());
        assertEquals(92, recommendations.get(0).getMatchScore());
        assertEquals("Databricks", recommendations.get(1).getCompany());
        assertEquals(88, recommendations.get(1).getMatchScore());

        // Verify that NO external remote scraping was performed synchronously!
        verifyNoInteractions(companyAtsDiscoveryProvider);
        verifyNoInteractions(searchEngineJobDiscoveryProvider);
        verifyNoInteractions(adzunaClient);
        verifyNoInteractions(jSearchClient);
    }

    @Test
    @DisplayName("getRecommended should exclude dismissed jobs")
    void testGetRecommendedExcludesDismissedJobs() {
        when(userRepository.findByEmail("test.developer@example.com")).thenReturn(Optional.of(testUser));
        when(userJobInteractionRepository.findDismissedJobIdsByUserId("user-1")).thenReturn(List.of("job-dismissed-1"));
        when(skillRepository.findByUserId("user-1")).thenReturn(Collections.emptyList());

        DiscoveredJob dismissedJob = DiscoveredJob.builder()
                .id("job-dismissed-1")
                .title("Backend Engineer")
                .company("Dismissed Corp")
                .isActive(true)
                .build();

        DiscoveredJob validJob = DiscoveredJob.builder()
                .id("job-valid-2")
                .title("Java Engineer")
                .company("Valid Corp")
                .isActive(true)
                .build();

        when(discoveredJobRepository.searchActiveByKeyword(anyString())).thenReturn(List.of(dismissedJob, validJob));

        when(resumeMatchService.calculateTier1Score(eq(testUser), eq(validJob), anyBoolean())).thenReturn(
                ResumeMatchService.MatchResult.builder()
                        .matchScore(85)
                        .matchedSkills(List.of("Java"))
                        .missingSkills(List.of())
                        .build()
        );
        when(resumeMatchService.recordInteraction(any(), any(), any(), any())).thenAnswer(invocation ->
                UserJobInteraction.builder().id("uji-2").status("NEW").build()
        );

        List<DiscoveredJobDto> recommendations = jobDiscoveryService.getRecommended();

        assertEquals(1, recommendations.size());
        assertEquals("Valid Corp", recommendations.get(0).getCompany());
    }

    @Test
    @DisplayName("getCached should return top 6 recommendations for the user")
    void testGetCachedReturnsTopRecommendations() {
        when(userRepository.findByEmail("test.developer@example.com")).thenReturn(Optional.of(testUser));
        when(userJobInteractionRepository.findDismissedJobIdsByUserId("user-1")).thenReturn(Collections.emptyList());
        when(skillRepository.findByUserId("user-1")).thenReturn(Collections.emptyList());

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-razorpay-1")
                .title("Backend Engineer")
                .company("Razorpay")
                .sourceType("CAREER_PAGE_ATS")
                .isActive(true)
                .build();

        when(discoveredJobRepository.searchActiveByKeyword(anyString())).thenReturn(List.of(job));
        when(resumeMatchService.calculateTier1Score(eq(testUser), eq(job), anyBoolean())).thenReturn(
                ResumeMatchService.MatchResult.builder()
                        .matchScore(90)
                        .matchedSkills(List.of("Java", "MySQL"))
                        .missingSkills(List.of())
                        .build()
        );
        when(resumeMatchService.recordInteraction(any(), any(), any(), any())).thenAnswer(invocation ->
                UserJobInteraction.builder().id("uji-3").status("NEW").build()
        );

        List<DiscoveredJobDto> cachedJobs = jobDiscoveryService.getCached();

        assertEquals(1, cachedJobs.size());
        assertEquals("Razorpay", cachedJobs.get(0).getCompany());
        assertEquals(90, cachedJobs.get(0).getMatchScore());
    }

    @Test
    @DisplayName("search should query active jobs and return results immediately without calling fetchAndStore")
    void testSearchFastAndNonBlocking() {
        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-swiggy-1")
                .title("Software Development Engineer")
                .company("Swiggy")
                .location("Bangalore, India")
                .isActive(true)
                .sourceType("CAREER_PAGE_ATS")
                .build();

        when(discoveredJobRepository.searchActiveByKeyword("Java")).thenReturn(List.of(job));
        when(userRepository.findByEmail(anyString())).thenReturn(Optional.of(testUser));
        when(resumeMatchService.calculateTier1Score(eq(testUser), eq(job), anyBoolean())).thenReturn(
                ResumeMatchService.MatchResult.builder()
                        .matchScore(80)
                        .matchedSkills(List.of("Java"))
                        .missingSkills(List.of())
                        .build()
        );
        when(resumeMatchService.recordInteraction(any(), any(), any(), any())).thenAnswer(invocation ->
                UserJobInteraction.builder().id("uji-4").status("NEW").build()
        );

        List<DiscoveredJobDto> searchResults = jobDiscoveryService.search("Java", "India", 50, "ALL");

        assertEquals(1, searchResults.size());
        assertEquals("Swiggy", searchResults.get(0).getCompany());
        verifyNoInteractions(companyAtsDiscoveryProvider);
    }
}
