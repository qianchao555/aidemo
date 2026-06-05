package com.xiaofuzi.ai.dto.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LowRatedMessage {
    private Long id;
    private String threadId;
    private String userQuestion;
    private String assistantAnswerExcerpt;
    private String assistantAnswerFull;
    private String sourceDoc;
    private String headingPath;
    private String createTime;
    private Integer rating;
}
