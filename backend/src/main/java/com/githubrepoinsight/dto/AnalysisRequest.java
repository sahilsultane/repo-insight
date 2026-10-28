package com.githubrepoinsight.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class AnalysisRequest {

    @NotBlank(message = "Repository URL is required")
    @Pattern(
        regexp = "^https://github\\.com/[a-zA-Z0-9._-]+/[a-zA-Z0-9._-]+(/.*)?$",
        message = "Must be a valid GitHub repository URL (https://github.com/owner/repo)"
    )
    private String repositoryUrl;
}
