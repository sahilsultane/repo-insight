package com.githubrepoinsight.dto;

import com.githubrepoinsight.model.Analysis.AnalysisStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStatusResponse {
    private String analysisId;
    private AnalysisStatus status;
    private String statusMessage;
    private String errorMessage;
    private LocalDateTime createdAt;
    private LocalDateTime completedAt;
}
