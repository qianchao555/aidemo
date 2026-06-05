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
public class FaqEntry {

    private Long id;

    private String question;

    private String answer;

    private String keywords;

    private String category;

    private String sourceDoc;

    private String headingPath;

    private Integer hitCount;

    private String status;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;

    private LocalDateTime lastHitTime;
}
