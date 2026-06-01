package com.xiaofuzi.ai.dto;

import lombok.Data;

@Data
public class IntentAwareResponse {

    private IntentResult intent;

    private String knowledgeContext;

    private String agentResponse;

    public static IntentAwareResponse of(IntentResult intent, String knowledgeContext, String agentResponse) {
        IntentAwareResponse resp = new IntentAwareResponse();
        resp.setIntent(intent);
        resp.setKnowledgeContext(knowledgeContext);
        resp.setAgentResponse(agentResponse);
        return resp;
    }
}
