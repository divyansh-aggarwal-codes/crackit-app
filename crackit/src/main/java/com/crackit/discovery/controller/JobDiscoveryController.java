package com.crackit.discovery.controller;

import com.crackit.discovery.dto.DiscoveredJobDto;
import com.crackit.discovery.service.JobDiscoveryService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/jobs/discover")
@RequiredArgsConstructor
public class JobDiscoveryController {

    private final JobDiscoveryService jobDiscoveryService;

    @GetMapping("/recommended")
    public List<DiscoveredJobDto> getRecommended() {
        return jobDiscoveryService.getRecommended();
    }

    @GetMapping("/search")
    public List<DiscoveredJobDto> search(
            @RequestParam String keyword,
            @RequestParam(defaultValue = "India") String location,
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) String sourceType
    ) {
        return jobDiscoveryService.search(keyword, location, minScore, sourceType);
    }

    @GetMapping("/cached")
    public List<DiscoveredJobDto> getCached() {
        return jobDiscoveryService.getCached();
    }

    @PostMapping("/{id}/dismiss")
    public ResponseEntity<Map<String, Object>> dismissJob(@PathVariable String id) {
        jobDiscoveryService.dismissJob(id);
        return ResponseEntity.ok(Map.of("message", "Job dismissed successfully", "jobId", id));
    }

    @PostMapping("/sync")
    public ResponseEntity<Map<String, String>> triggerSync() {
        java.util.concurrent.CompletableFuture.runAsync(jobDiscoveryService::scheduledFetch);
        return ResponseEntity.ok(Map.of("message", "Multi-provider job discovery synchronization triggered in background"));
    }
}