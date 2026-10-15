package com.crackit.tracker.controller;

import com.crackit.tracker.dto.*;
import com.crackit.tracker.service.JobApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/tracker")
@RequiredArgsConstructor
public class JobApplicationController {

    private final JobApplicationService jobApplicationService;

    @PostMapping("/save")
    public JobApplicationResponse saveJob(@RequestBody JobApplicationRequest request) {
        return jobApplicationService.saveJob(request);
    }

    @PostMapping("/track-apply")
    public JobApplicationResponse trackApply(@RequestBody TrackApplyRequest request) {
        return jobApplicationService.trackApply(request);
    }

    @PatchMapping("/{applicationId}/status")
    public JobApplicationResponse updateStatus(
            @PathVariable String applicationId,
            @RequestBody UpdateStatusRequest request) {
        return jobApplicationService.updateStatus(applicationId, request);
    }

    @GetMapping
    public List<JobApplicationResponse> getAllApplications() {
        return jobApplicationService.getAllApplications();
    }

    @GetMapping("/{applicationId}")
    public JobApplicationResponse getApplication(@PathVariable String applicationId) {
        return jobApplicationService.getApplication(applicationId);
    }

    @GetMapping("/dashboard")
    public DashboardResponse getDashboard() {
        return jobApplicationService.getDashboard();
    }
}