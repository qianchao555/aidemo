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
public class ChatSession {

    private Long id;

    private String threadId;

    private Long userId;

    private String title;

    private Integer messageCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
