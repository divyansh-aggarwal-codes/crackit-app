package com.crackit.tracker.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrackApplyRequest {
    private String jobId;          // either Job.id or DiscoveredJob.id
    private String companyName;
    private String title;
    private String location;
    private String applyUrl;
    private String jdText;
    private String source;
    private String notes;
    private String contactPerson;
    private LocalDate appliedDate;
}
