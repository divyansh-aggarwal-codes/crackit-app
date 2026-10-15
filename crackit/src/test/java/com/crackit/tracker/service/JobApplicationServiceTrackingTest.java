package com.crackit.tracker.service;

import com.crackit.ai.repository.TailoredResumeRepository;
import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import com.crackit.jobs.entity.Job;
import com.crackit.jobs.repository.JobRepository;
import com.crackit.tracker.dto.JobApplicationResponse;
import com.crackit.tracker.dto.TrackApplyRequest;
import com.crackit.tracker.entity.JobApplication;
import com.crackit.tracker.enums.ApplicationStatus;
import com.crackit.tracker.repository.JobApplicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JobApplicationServiceTrackingTest {

    @Mock
    private JobApplicationRepository jobApplicationRepository;

    @Mock
    private JobRepository jobRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private TailoredResumeRepository tailoredResumeRepository;

    @Mock
    private DiscoveredJobRepository discoveredJobRepository;

    @Mock
    private UserJobInteractionRepository userJobInteractionRepository;

    private JobApplicationService service;

    private User testUser;

    @BeforeEach
    void setUp() {
        service = new JobApplicationService(
                jobApplicationRepository,
                jobRepository,
                userRepository,
                tailoredResumeRepository,
                discoveredJobRepository,
                userJobInteractionRepository
        );

        testUser = User.builder()
                .id("user-100")
                .email("divyanshu@example.com")
                .build();
    }

    @Test
    @DisplayName("trackApplyForUser upgrades existing application to APPLIED and updates appliedDate")
    void testTrackApplyUpdatesExistingApplication() {
        Job job = Job.builder()
                .id("job-existing-1")
                .companyName("Amazon")
                .title("SDE II")
                .applyUrl("https://amazon.jobs/123")
                .user(testUser)
                .build();

        JobApplication existingApp = JobApplication.builder()
                .id("app-1")
                .user(testUser)
                .job(job)
                .status(ApplicationStatus.SAVED)
                .build();

        when(jobApplicationRepository.findByUserId(testUser.getId())).thenReturn(List.of(existingApp));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        TrackApplyRequest request = TrackApplyRequest.builder()
                .jobId("job-existing-1")
                .companyName("Amazon")
                .title("SDE II")
                .applyUrl("https://amazon.jobs/123")
                .notes("Applied through referral")
                .build();

        JobApplicationResponse response = service.trackApplyForUser(testUser, request);

        assertNotNull(response);
        assertEquals(ApplicationStatus.APPLIED, response.getStatus());
        assertEquals("Applied through referral", response.getNotes());
        assertEquals(LocalDate.now(), response.getAppliedDate());
        verify(jobApplicationRepository, times(1)).save(existingApp);
    }

    @Test
    @DisplayName("trackApplyForUser with DiscoveredJob creates Job, JobApplication with APPLIED, and syncs interaction")
    void testTrackApplyCreatesFromDiscoveredJob() {
        String discJobId = "disc-job-999";
        DiscoveredJob dj = DiscoveredJob.builder()
                .id(discJobId)
                .title("Backend Lead")
                .company("Stripe")
                .location("Remote")
                .url("https://stripe.com/jobs/999")
                .description("Build payment systems")
                .source("ADZUNA")
                .build();

        when(jobApplicationRepository.findByUserId(testUser.getId())).thenReturn(List.of());
        when(jobRepository.findById(discJobId)).thenReturn(Optional.empty());
        when(discoveredJobRepository.findById(discJobId)).thenReturn(Optional.of(dj));
        when(jobRepository.save(any(Job.class))).thenAnswer(inv -> inv.getArgument(0));
        when(jobApplicationRepository.save(any(JobApplication.class))).thenAnswer(inv -> inv.getArgument(0));

        UserJobInteraction interaction = UserJobInteraction.builder()
                .id("uji-1")
                .user(testUser)
                .discoveredJob(dj)
                .status("NEW")
                .build();
        when(userJobInteractionRepository.findByUserIdAndDiscoveredJobId(testUser.getId(), discJobId))
                .thenReturn(Optional.of(interaction));

        TrackApplyRequest request = TrackApplyRequest.builder()
                .jobId(discJobId)
                .companyName("Stripe")
                .title("Backend Lead")
                .applyUrl("https://stripe.com/jobs/999")
                .build();

        JobApplicationResponse response = service.trackApplyForUser(testUser, request);

        assertNotNull(response);
        assertEquals(ApplicationStatus.APPLIED, response.getStatus());
        assertEquals("Stripe", response.getCompanyName());
        assertEquals("Backend Lead", response.getJobTitle());

        // Verify interaction status updated to APPLIED
        ArgumentCaptor<UserJobInteraction> captor = ArgumentCaptor.forClass(UserJobInteraction.class);
        verify(userJobInteractionRepository).save(captor.capture());
        assertEquals("APPLIED", captor.getValue().getStatus());
    }
}
