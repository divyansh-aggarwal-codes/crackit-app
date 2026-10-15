package com.crackit.discovery.service;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Disabled("Manual live SMTP verification test")
public class SendManualTestEmailTest {

    @Test
    void sendTestEmailToUser() {
        EmailService emailService = new EmailService(null);
        emailService.setHost("smtp.gmail.com");
        emailService.setPort(587);
        emailService.setUsername("crackit.notifications@gmail.com");
        emailService.setPassword("xsfsnrbukazpxnas");
        emailService.setFromEmail("crackit.notifications@gmail.com");
        emailService.setFromName("CrackIt Job Alerts");
        emailService.setMailEnabled(true);

        NotificationService notificationService = new NotificationService(
                null, null, null, emailService
        );

        boolean sent = notificationService.sendTestEmail("divyanshu5981.iimt@gmail.com", "Divyanshu Aggarwal");
        System.out.println("=================================================");
        System.out.println("[EMAIL TEST] Dispatched to divyanshu5981.iimt@gmail.com: " + sent);
        System.out.println("=================================================");
        assertTrue(sent, "Email dispatch should succeed via Gmail SMTP");
    }

    @Test
    void sendSampleJobDigestEmail() {
        ResumeMatchService resumeMatchService = new ResumeMatchService(
                null, null, new com.fasterxml.jackson.databind.ObjectMapper()
        );

        EmailService emailService = new EmailService(resumeMatchService);
        emailService.setHost("smtp.gmail.com");
        emailService.setPort(587);
        emailService.setUsername("crackit.notifications@gmail.com");
        emailService.setPassword("xsfsnrbukazpxnas");
        emailService.setFromEmail("crackit.notifications@gmail.com");
        emailService.setFromName("CrackIt Job Alerts");
        emailService.setMailEnabled(true);

        com.crackit.auth.entity.User testUser = new com.crackit.auth.entity.User();
        testUser.setEmail("divyanshu5981.iimt@gmail.com");
        testUser.setFullName("Divyanshu Aggarwal");
        testUser.setTargetRole("Full Stack Software Engineer");

        com.crackit.discovery.entity.DiscoveredJob job1 = new com.crackit.discovery.entity.DiscoveredJob();
        job1.setTitle("Senior Full Stack Engineer (Java / React)");
        job1.setCompany("Stripe");
        job1.setLocation("Remote / Global");
        job1.setSourceType("CAREER_PAGE_ATS");
        job1.setUrl("https://stripe.com/jobs");

        com.crackit.discovery.entity.UserJobInteraction match1 = new com.crackit.discovery.entity.UserJobInteraction();
        match1.setUser(testUser);
        match1.setDiscoveredJob(job1);
        match1.setMatchScore(94);
        match1.setMatchedSkills("[\"Java\", \"Spring Boot\", \"React\", \"TypeScript\"]");

        com.crackit.discovery.entity.DiscoveredJob job2 = new com.crackit.discovery.entity.DiscoveredJob();
        job2.setTitle("Staff Backend Engineer");
        job2.setCompany("Databricks");
        job2.setLocation("San Francisco, CA / Remote");
        job2.setSourceType("CAREER_PAGE_ATS");
        job2.setUrl("https://databricks.com/company/careers");

        com.crackit.discovery.entity.UserJobInteraction match2 = new com.crackit.discovery.entity.UserJobInteraction();
        match2.setUser(testUser);
        match2.setDiscoveredJob(job2);
        match2.setMatchScore(89);
        match2.setMatchedSkills("[\"Java\", \"Kafka\", \"Distributed Systems\", \"MySQL\"]");

        boolean digestSent = emailService.sendStrongJobMatchAlert(testUser, java.util.List.of(match1, match2));
        System.out.println("=================================================");
        System.out.println("[JOB DIGEST TEST] Dispatched to divyanshu5981.iimt@gmail.com: " + digestSent);
        System.out.println("=================================================");
        assertTrue(digestSent, "Job digest email dispatch should succeed via Gmail SMTP");
    }
}
