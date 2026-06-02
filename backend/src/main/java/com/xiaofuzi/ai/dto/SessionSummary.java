package com.xiaofuzi.ai.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionSummary {

    private String threadId;
    private String title;
    private int messageCount;
    private LocalDateTime lastUpdateTime;
}
