package com.xiaofuzi.ai.dto.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DepartmentQualityItem {
    private String department;
    private long totalRated;
    private long thumbsUp;
    private long thumbsDown;
    private Double satisfactionRate;
}
