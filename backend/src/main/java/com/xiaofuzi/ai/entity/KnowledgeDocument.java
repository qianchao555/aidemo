package com.xiaofuzi.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeDocument {

    private Long id;
    private String documentName;
    private String documentType;
    private String filePath;
    private Long fileSize;
    private String category;
    private String department;
    private String version;
    private Long groupId;
    private Boolean isLatest;
    private LocalDate effectiveDate;
    private String description;
    private Integer chunkCount;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
