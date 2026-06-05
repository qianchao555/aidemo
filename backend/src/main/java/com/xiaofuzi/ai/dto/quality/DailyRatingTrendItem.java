package com.xiaofuzi.ai.dto.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DailyRatingTrendItem {
    private String day;
    private long thumbsUp;
    private long thumbsDown;
    private long unrated;
    private Double satisfactionRate;
}
