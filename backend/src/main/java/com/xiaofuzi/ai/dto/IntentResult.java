package com.xiaofuzi.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class IntentResult {

    private String intent;

    private double confidence;

    private String subIntent;

    private String topic;

    private List<String> keywords;

    private String style;

    private String explanation;
}
