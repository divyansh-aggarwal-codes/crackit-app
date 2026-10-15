package com.crackit.discovery.service;

import com.crackit.ai.client.AiServiceClient;
import com.crackit.ai.dto.JDAnalysisRequest;
import com.crackit.ai.dto.JDAnalysisResponse;
import com.crackit.auth.entity.User;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import com.crackit.resume.entity.Skill;
import com.crackit.resume.repository.SkillRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ResumeMatchServiceTest {

    @Mock
    private SkillRepository skillRepository;

    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;

    private ResumeMatchService matchService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        matchService = new ResumeMatchService(skillRepository, userJobInteractionRepository, objectMapper);
    }

    @Test
    @DisplayName("Candidate with matching target role, skills, and remote preference should score high (>=80%)")
    void testHighFitCandidateMatching() {
        User user = User.builder()
                .id("user-101")
                .targetRole("Backend Engineer")
                .preferredWorkMode("Remote")
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-201")
                .title("Senior Backend Engineer (Java / Spring Boot)")
                .description("We are looking for a backend engineer proficient in Java, Spring Boot, MySQL, and Docker.")
                .location("Remote")
                .remote(true)
                .build();

        List<Skill> userSkills = List.of(
                Skill.builder().skillName("Java").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Spring Boot").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("MySQL").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("Docker").proficiencyLevel("Intermediate").build()
        );

        when(skillRepository.findByUserId("user-101")).thenReturn(userSkills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, job);

        assertNotNull(result);
        assertTrue(result.getMatchScore() >= 80, "Expected match score >= 80, got: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().contains("Java"));
        assertTrue(result.getMatchedSkills().contains("Spring Boot"));
    }

    @Test
    @DisplayName("Candidate with unrelated skills should receive a low match score (<50%)")
    void testLowFitCandidateMatching() {
        User user = User.builder()
                .id("user-102")
                .targetRole("Graphic Designer")
                .preferredWorkMode("Onsite")
                .preferredLocations("Delhi")
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-202")
                .title("C++ Embedded Firmware Engineer")
                .description("Requires deep kernel programming, assembly, and C++ experience in Munich.")
                .location("Munich, Germany")
                .remote(false)
                .build();

        List<Skill> userSkills = List.of(
                Skill.builder().skillName("Photoshop").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Figma").proficiencyLevel("Expert").build()
        );

        when(skillRepository.findByUserId("user-102")).thenReturn(userSkills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, job);

        assertNotNull(result);
        assertTrue(result.getMatchScore() < 50, "Expected match score < 50 for mismatch, got: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().isEmpty());
    }

    @Test
    @DisplayName("Frontend profile (React/TypeScript) should match frontend roles and be penalized on backend-only stacks")
    void testFrontendProfileMatching() {
        User user = User.builder()
                .id("user-frontend")
                .targetRole("Frontend Engineer")
                .preferredWorkMode("Remote")
                .yearsExperience(4)
                .build();

        DiscoveredJob reactJob = DiscoveredJob.builder()
                .id("job-fe-1")
                .title("Senior React / Next.js Frontend Developer")
                .description("Build modern web interfaces using React, TypeScript, Next.js, and Tailwind CSS.")
                .location("Remote")
                .remote(true)
                .build();

        List<Skill> feSkills = List.of(
                Skill.builder().skillName("React").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("TypeScript").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("Next.js").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("Tailwind").proficiencyLevel("Intermediate").build()
        );

        when(skillRepository.findByUserId("user-frontend")).thenReturn(feSkills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, reactJob);
        assertNotNull(result);
        assertTrue(result.getMatchScore() >= 80, "Expected score >= 80 for strong frontend match, got: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().contains("React"));
        assertTrue(result.getMatchedSkills().contains("Typescript"));
    }

    @Test
    @DisplayName("Java candidate should receive negative penalty when evaluated against PHP / WordPress roles")
    void testDisjointEcosystemPenalty() {
        User user = User.builder()
                .id("user-java")
                .targetRole("Java Backend Engineer")
                .preferredWorkMode("Hybrid")
                .preferredLocations("Bengaluru")
                .yearsExperience(4)
                .build();

        DiscoveredJob phpJob = DiscoveredJob.builder()
                .id("job-php")
                .title("Full Stack Web Developer (PHP / Laravel / WordPress)")
                .description("Looking for PHP, Laravel, WordPress, and Drupal plugin developer.")
                .location("Bengaluru, India")
                .remote(false)
                .build();

        List<Skill> javaSkills = List.of(
                Skill.builder().skillName("Java").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Spring Boot").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Kafka").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("MySQL").proficiencyLevel("Advanced").build()
        );

        when(skillRepository.findByUserId("user-java")).thenReturn(javaSkills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, phpJob);
        assertNotNull(result);
        assertTrue(result.getMatchScore() < 55, "Expected disjoint penalty for PHP/WordPress JD on Java profile, got: " + result.getMatchScore());
    }

    @Test
    @DisplayName("DevOps / Cloud Engineer profile should match SRE and Infrastructure roles")
    void testDevOpsProfileMatching() {
        User user = User.builder()
                .id("user-devops")
                .targetRole("DevOps Engineer")
                .preferredWorkMode("Remote")
                .yearsExperience(5)
                .build();

        DiscoveredJob devopsJob = DiscoveredJob.builder()
                .id("job-devops-1")
                .title("Senior Site Reliability Engineer (SRE / Cloud)")
                .description("Manage infrastructure with Kubernetes, Docker, Terraform, AWS, and CI/CD pipelines.")
                .location("Remote India")
                .remote(true)
                .build();

        List<Skill> devopsSkills = List.of(
                Skill.builder().skillName("Kubernetes").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Docker").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Terraform").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("AWS").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("CI/CD").proficiencyLevel("Advanced").build()
        );

        when(skillRepository.findByUserId("user-devops")).thenReturn(devopsSkills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, devopsJob);
        assertNotNull(result);
        assertTrue(result.getMatchScore() >= 80, "Expected score >= 80 for DevOps SRE match, got: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().contains("Kubernetes"));
        assertTrue(result.getMatchedSkills().contains("Aws"));
    }

    @Test
    @DisplayName("Candidate wanting India should have score capped when job is strictly on-site in a foreign city")
    void testOverseasOnsiteMismatchCapping() {
        User user = User.builder()
                .id("user-india")
                .targetRole("Software Engineer")
                .preferredLocations("Bengaluru, India")
                .preferredWorkMode("Onsite")
                .yearsExperience(4)
                .build();

        DiscoveredJob foreignJob = DiscoveredJob.builder()
                .id("job-foreign")
                .title("Software Engineer (Java / Backend)")
                .description("In-person office role in London UK requiring full on-site presence. Java and Spring Boot.")
                .location("London, UK")
                .remote(false)
                .build();

        List<Skill> skills = List.of(
                Skill.builder().skillName("Java").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Spring Boot").proficiencyLevel("Expert").build()
        );

        when(skillRepository.findByUserId("user-india")).thenReturn(skills);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, foreignJob);
        assertNotNull(result);
        assertTrue(result.getMatchScore() <= 40, "Expected score capped <= 40 for on-site foreign mismatch, got: " + result.getMatchScore());
    }

    @Test
    @DisplayName("Stage B Gemini AI evaluation should validate request schema, blend score, and merge keywords")
    void testStageBInvokedAndBlendsScoreForTopFitCandidate() {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        matchService.setAiServiceClient(aiServiceClient);

        User user = User.builder()
                .id("user-stageb")
                .targetRole("Backend Engineer")
                .preferredWorkMode("Remote")
                .yearsExperience(4)
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-stageb")
                .title("Senior Backend Engineer (Java / Spring Boot)")
                .description("We are looking for a backend engineer proficient in Java, Spring Boot, MySQL, and Docker.")
                .location("Remote")
                .remote(true)
                .build();

        List<Skill> userSkills = List.of(
                Skill.builder().skillName("Java").category("Language").proficiencyLevel("Expert").yearsUsed(4).build(),
                Skill.builder().skillName("Spring Boot").category("Framework").proficiencyLevel("Expert").yearsUsed(4).build(),
                Skill.builder().skillName("MySQL").category("Database").proficiencyLevel("Advanced").yearsUsed(3).build(),
                Skill.builder().skillName("Docker").category("DevOps").proficiencyLevel("Intermediate").yearsUsed(2).build()
        );

        when(skillRepository.findByUserId("user-stageb")).thenReturn(userSkills);

        JDAnalysisResponse aiResponse = new JDAnalysisResponse();
        aiResponse.setMatchScore(90);
        aiResponse.setMatchedKeywords(List.of("Java", "Spring Boot", "Microservices"));
        aiResponse.setMissingKeywords(List.of("Kubernetes"));
        aiResponse.setExperienceLevel("Senior");

        when(aiServiceClient.analyzeJd(any(JDAnalysisRequest.class))).thenReturn(aiResponse);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, job);

        assertNotNull(result);
        ArgumentCaptor<JDAnalysisRequest> captor = ArgumentCaptor.forClass(JDAnalysisRequest.class);
        verify(aiServiceClient, times(1)).analyzeJd(captor.capture());

        JDAnalysisRequest sentRequest = captor.getValue();
        assertNotNull(sentRequest.getSkills(), "Skills list must not be null");
        assertEquals(4, sentRequest.getSkills().size());

        // Validate each skill has all 4 required FastAPI Pydantic fields
        for (Map<String, Object> skillMap : sentRequest.getSkills()) {
            assertTrue(skillMap.containsKey("skillName"), "Must contain skillName");
            assertTrue(skillMap.containsKey("category"), "Must contain category");
            assertTrue(skillMap.containsKey("proficiencyLevel"), "Must contain proficiencyLevel");
            assertTrue(skillMap.containsKey("yearsUsed"), "Must contain yearsUsed");
            assertTrue(skillMap.get("yearsUsed") instanceof Integer, "yearsUsed must be an Integer");
            assertNotNull(skillMap.get("category"), "category must not be null");
        }

        // Verify score blending: 35% stageA + 65% aiResponse (90)
        assertTrue(result.getMatchScore() >= 80, "Blended score should be >= 80, was: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().contains("Microservices"));
        assertTrue(result.getMissingSkills().contains("Kubernetes"));
    }

    @Test
    @DisplayName("Stage B should gracefully fall back to Stage A score when AI service throws exception")
    void testStageBFallbackToStageAOnAiServiceException() {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        matchService.setAiServiceClient(aiServiceClient);

        User user = User.builder()
                .id("user-stageb-fallback")
                .targetRole("Backend Engineer")
                .preferredWorkMode("Remote")
                .yearsExperience(4)
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-stageb-fallback")
                .title("Senior Backend Engineer (Java / Spring Boot)")
                .description("We are looking for a backend engineer proficient in Java, Spring Boot, MySQL, and Docker.")
                .location("Remote")
                .remote(true)
                .build();

        List<Skill> userSkills = List.of(
                Skill.builder().skillName("Java").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("Spring Boot").proficiencyLevel("Expert").build(),
                Skill.builder().skillName("MySQL").proficiencyLevel("Advanced").build(),
                Skill.builder().skillName("Docker").proficiencyLevel("Intermediate").build()
        );

        when(skillRepository.findByUserId("user-stageb-fallback")).thenReturn(userSkills);
        when(aiServiceClient.analyzeJd(any(JDAnalysisRequest.class)))
                .thenThrow(new RuntimeException("Simulated AI service connection timeout (504 Gateway Timeout)"));

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, job);

        assertNotNull(result);
        assertTrue(result.getMatchScore() >= 80, "Fallback to Stage A should still produce high score, was: " + result.getMatchScore());
        assertTrue(result.getMatchedSkills().contains("Java"));
        verify(aiServiceClient, times(1)).analyzeJd(any(JDAnalysisRequest.class));
    }

    @Test
    @DisplayName("Request serialization for candidate without explicit skills table entries should pass targetRole fallback")
    void testFallbackSkillSerializationWhenSkillsTableEmpty() {
        AiServiceClient aiServiceClient = mock(AiServiceClient.class);
        matchService.setAiServiceClient(aiServiceClient);

        User user = User.builder()
                .id("user-no-skills")
                .targetRole("Senior Java Backend Engineer")
                .preferredWorkMode("Remote")
                .yearsExperience(5)
                .build();

        DiscoveredJob job = DiscoveredJob.builder()
                .id("job-target-role")
                .title("Senior Java Backend Engineer")
                .description("Looking for Senior Java Backend Engineer with Spring Boot and Kafka.")
                .location("Remote")
                .remote(true)
                .build();

        when(skillRepository.findByUserId("user-no-skills")).thenReturn(List.of());

        JDAnalysisResponse aiResponse = new JDAnalysisResponse();
        aiResponse.setMatchScore(85);
        when(aiServiceClient.analyzeJd(any(JDAnalysisRequest.class))).thenReturn(aiResponse);

        ResumeMatchService.MatchResult result = matchService.calculateTier1Score(user, job);

        ArgumentCaptor<JDAnalysisRequest> captor = ArgumentCaptor.forClass(JDAnalysisRequest.class);
        verify(aiServiceClient, times(1)).analyzeJd(captor.capture());

        JDAnalysisRequest req = captor.getValue();
        assertEquals(1, req.getSkills().size());
        Map<String, Object> fallbackSkill = req.getSkills().get(0);
        assertEquals("Senior Java Backend Engineer", fallbackSkill.get("skillName"));
        assertEquals("Technical", fallbackSkill.get("category"));
        assertEquals("Advanced", fallbackSkill.get("proficiencyLevel"));
        assertEquals(5, fallbackSkill.get("yearsUsed"));
    }
}
