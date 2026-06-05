package com.xiaofuzi.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 请求实体类
 *
 */
@Data
public class ContentChatRequest {
    @NotBlank
    private String userMessage;

    private String topic;

    private String style;

    //默认false
    private Boolean skipResearch=false;

    //内容
    private String content;

    //内容类型
    private String contentType;


    private String threadId;

    private Long userId;

    private String department;

    private List<Map<String, Object>> versionOverrides;
}
