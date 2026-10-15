package com.crackit.tracker.service;

import com.crackit.ai.entity.TailoredResume;
import com.crackit.ai.repository.TailoredResumeRepository;
import com.crackit.auth.entity.User;
import com.crackit.auth.repository.UserRepository;
import com.crackit.common.util.AuthUtil;
import com.crackit.discovery.entity.DiscoveredJob;
import com.crackit.discovery.entity.UserJobInteraction;
import com.crackit.discovery.repository.DiscoveredJobRepository;
import com.crackit.discovery.repository.UserJobInteractionRepository;
import com.crackit.jobs.entity.Job;
import com.crackit.jobs.repository.JobRepository;
import com.crackit.tracker.dto.*;
import com.crackit.tracker.entity.JobApplication;
import com.crackit.tracker.enums.ApplicationStatus;
import com.crackit.tracker.repository.JobApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class JobApplicationService {

    private final JobApplicationRepository jobApplicationRepository;
    private final JobRepository jobRepository;
    private final UserRepository userRepository;
    private final TailoredResumeRepository tailoredResumeRepository;
    private final DiscoveredJobRepository discoveredJobRepository;
    private final UserJobInteractionRepository userJobInteractionRepository;

    // Save a job (initial status = SAVED)
    public JobApplicationResponse saveJob(JobApplicationRequest request) {
        User user = getLoggedInUser();

        jobApplicationRepository.findByUserIdAndJobId(user.getId(), request.getJobId())
                .ifPresent(a -> { throw new RuntimeException("You have already saved this job"); });

        Job job = jobRepository.findById(request.getJobId())
                .orElseThrow(() -> new RuntimeException("Job not found"));

        JobApplication application = JobApplication.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .job(job)
                .status(ApplicationStatus.SAVED)
                .notes(request.getNotes())
                .contactPerson(request.getContactPerson())
                .appliedDate(request.getAppliedDate())
                .build();

        return mapToResponse(jobApplicationRepository.save(application));
    }

    // Track a job application directly with status APPLIED
    public JobApplicationResponse trackApply(TrackApplyRequest request) {
        User user = getLoggedInUser();
        return trackApplyForUser(user, request);
    }

    public JobApplicationResponse trackApplyForUser(User user, TrackApplyRequest request) {
        String jobId = request.getJobId() != null ? request.getJobId().trim() : null;
        String applyUrl = request.getApplyUrl() != null ? request.getApplyUrl().trim() : null;
        String companyName = request.getCompanyName() != null ? request.getCompanyName().trim() : null;
        String title = request.getTitle() != null ? request.getTitle().trim() : null;

        // 1. Check if user already has an application for this job
        List<JobApplication> userApps = jobApplicationRepository.findByUserId(user.getId());
        JobApplication existingApp = userApps.stream()
                .filter(a -> {
                    if (jobId != null && a.getJob() != null && jobId.equals(a.getJob().getId())) return true;
                    if (applyUrl != null && a.getJob() != null && a.getJob().getApplyUrl() != null && applyUrl.equalsIgnoreCase(a.getJob().getApplyUrl())) return true;
                    if (companyName != null && title != null && a.getJob() != null
                            && companyName.equalsIgnoreCase(a.getJob().getCompanyName())
                            && title.equalsIgnoreCase(a.getJob().getTitle())) return true;
                    return false;
                })
                .findFirst()
                .orElse(null);

        if (existingApp != null) {
            existingApp.setStatus(ApplicationStatus.APPLIED);
            if (existingApp.getAppliedDate() == null) {
                existingApp.setAppliedDate(request.getAppliedDate() != null ? request.getAppliedDate() : LocalDate.now());
            }
            if (request.getNotes() != null && !request.getNotes().isBlank()) {
                existingApp.setNotes(request.getNotes().trim());
            }
            if (request.getContactPerson() != null && !request.getContactPerson().isBlank()) {
                existingApp.setContactPerson(request.getContactPerson().trim());
            }

            syncUserJobInteraction(user.getId(), jobId);

            return mapToResponse(jobApplicationRepository.save(existingApp));
        }

        // 2. Find or create the Job entity for this user
        Job job = null;
        if (jobId != null) {
            job = jobRepository.findById(jobId)
                    .filter(j -> j.getUser() != null && j.getUser().getId().equals(user.getId()))
                    .orElse(null);
        }

        if (job == null && jobId != null && discoveredJobRepository != null) {
            Optional<DiscoveredJob> djOpt = discoveredJobRepository.findById(jobId);
            if (djOpt.isPresent()) {
                DiscoveredJob dj = djOpt.get();
                job = Job.builder()
                        .id(UUID.randomUUID().toString())
                        .user(user)
                        .companyName(dj.getCompany() != null ? dj.getCompany() : (companyName != null ? companyName : "Hiring Company"))
                        .title(dj.getTitle() != null ? dj.getTitle() : (title != null ? title : "Software Engineer"))
                        .location(dj.getLocation() != null ? dj.getLocation() : request.getLocation())
                        .applyUrl(dj.getUrl() != null ? dj.getUrl() : applyUrl)
                        .jdText(dj.getDescription() != null ? dj.getDescription() : request.getJdText())
                        .source(dj.getSource() != null ? dj.getSource() : (request.getSource() != null ? request.getSource() : "DISCOVERED"))
                        .salaryRange(dj.getSalaryMin() != null ? String.valueOf(dj.getSalaryMin()) : null)
                        .build();
                job = jobRepository.save(job);
            }
        }

        if (job == null) {
            job = Job.builder()
                    .id(UUID.randomUUID().toString())
                    .user(user)
                    .companyName(companyName != null && !companyName.isBlank() ? companyName : "Hiring Company")
                    .title(title != null && !title.isBlank() ? title : "Software Engineer")
                    .location(request.getLocation())
                    .applyUrl(applyUrl)
                    .jdText(request.getJdText())
                    .source(request.getSource() != null ? request.getSource() : "DIRECT_APPLY")
                    .build();
            job = jobRepository.save(job);
        }

        // 3. Create new JobApplication with status APPLIED
        JobApplication application = JobApplication.builder()
                .id(UUID.randomUUID().toString())
                .user(user)
                .job(job)
                .status(ApplicationStatus.APPLIED)
                .appliedDate(request.getAppliedDate() != null ? request.getAppliedDate() : LocalDate.now())
                .notes(request.getNotes())
                .contactPerson(request.getContactPerson())
                .build();

        JobApplication savedApp = jobApplicationRepository.save(application);

        // 4. Sync UserJobInteraction if applicable
        syncUserJobInteraction(user.getId(), jobId);

        return mapToResponse(savedApp);
    }

    private void syncUserJobInteraction(String userId, String jobId) {
        if (userJobInteractionRepository == null || jobId == null) return;
        try {
            userJobInteractionRepository.findByUserIdAndDiscoveredJobId(userId, jobId)
                    .ifPresent(interaction -> {
                        interaction.setStatus("APPLIED");
                        userJobInteractionRepository.save(interaction);
                    });
        } catch (Exception ignored) {
            // Non-critical background sync
        }
    }

    // Update status (APPLIED, CONTACTED, INTERVIEW, OFFER, REJECTED)
    public JobApplicationResponse updateStatus(String applicationId, UpdateStatusRequest request) {
        User user = getLoggedInUser();

        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!application.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        application.setStatus(request.getStatus());

        if (request.getNotes() != null) application.setNotes(request.getNotes());
        if (request.getContactPerson() != null) application.setContactPerson(request.getContactPerson());
        if (request.getAppliedDate() != null) application.setAppliedDate(request.getAppliedDate());

        return mapToResponse(jobApplicationRepository.save(application));
    }

    // Get all applications for logged-in user
    public List<JobApplicationResponse> getAllApplications() {
        User user = getLoggedInUser();
        return jobApplicationRepository.findByUserId(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // Get single application by id
    public JobApplicationResponse getApplication(String applicationId) {
        User user = getLoggedInUser();
        JobApplication application = jobApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new RuntimeException("Application not found"));

        if (!application.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Unauthorized");
        }

        return mapToResponse(application);
    }

    // Dashboard — counts per status + full list
    public DashboardResponse getDashboard() {
        User user = getLoggedInUser();
        List<JobApplication> applications = jobApplicationRepository.findByUserId(user.getId());

        Map<String, Long> statusCounts = applications.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getStatus().name(),
                        Collectors.counting()
                ));

        // ensure all statuses appear even if count is 0
        for (ApplicationStatus status : ApplicationStatus.values()) {
            statusCounts.putIfAbsent(status.name(), 0L);
        }

        List<JobApplicationResponse> responses = applications.stream()
                .map(this::mapToResponse)
                .toList();

        return DashboardResponse.builder()
                .statusCounts(statusCounts)
                .applications(responses)
                .build();
    }

    private JobApplicationResponse mapToResponse(JobApplication application) {
        Integer matchScore = tailoredResumeRepository
                .findTopByJobIdOrderByCreatedAtDesc(application.getJob().getId())
                .map(TailoredResume::getMatchScore)
                .orElse(null);

        return JobApplicationResponse.builder()
                .id(application.getId())
                .jobId(application.getJob().getId())
                .companyName(application.getJob().getCompanyName())
                .jobTitle(application.getJob().getTitle())
                .status(application.getStatus())
                .appliedDate(application.getAppliedDate())
                .notes(application.getNotes())
                .contactPerson(application.getContactPerson())
                .matchScore(matchScore)
                .createdAt(application.getCreatedAt())
                .updatedAt(application.getUpdatedAt())
                .build();
    }
    private User getLoggedInUser() {
        String email = AuthUtil.getLoggedInUserEmail();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
}