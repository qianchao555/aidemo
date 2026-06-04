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
public class ChatHistory {

    private Long id;

    private String threadId;

    private String role;

    private String content;

    private String sourceDoc;

    private String headingPath;

    private LocalDateTime createTime;

    private Integer rating;
}
