package com.xiaofuzi.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGroup {
    private Long id;
    private String name;
    private Long latestDocumentId;
    private String department;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
