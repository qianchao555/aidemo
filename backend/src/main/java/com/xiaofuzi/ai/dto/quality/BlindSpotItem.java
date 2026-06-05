package com.xiaofuzi.ai.dto.quality;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BlindSpotItem {
    private String sourceDoc;
    private String headingPath;
    private long negativeCount;
    private String lastOccurrence;
}
