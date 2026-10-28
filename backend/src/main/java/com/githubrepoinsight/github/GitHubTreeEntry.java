package com.githubrepoinsight.github;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GitHubTreeEntry {
    private String path;
    private String type; // "blob" (file) or "tree" (directory)
    private Long size;
    private String sha;
}
