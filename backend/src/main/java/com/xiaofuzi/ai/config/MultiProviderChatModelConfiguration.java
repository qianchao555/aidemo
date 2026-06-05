
package com.xiaofuzi.ai.config;

import com.xiaofuzi.ai.util.ChatModelProvider;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * 多厂商 ChatModel 配置
 * 使用手动配置的 ChatModel，覆盖 Spring AI Alibaba 自动配置
 */
@Configuration
public class MultiProviderChatModelConfiguration {

    @Value("${ai.chat.provider:dashscope}")
    private String provider;

    @Value("${ai.chat.api-key:}")
    private String apiKey;

    @Value("${ai.chat.base-url:}")
    private String baseUrl;

    @Value("${ai.chat.model:}")
    private String modelName;

    @Value("${ai.chat.dashscope.base-url:https://dashscope.aliyuncs.com}")
    private String dashscopeBaseUrl;

    @Value("${ai.chat.volcano-engine.base-url:https://ark.cn-beijing.volces.com/api/v3}")
    private String volcanoBaseUrl;

    @Value("${ai.chat.volcano-engine.model:doubao-pro-32k}")
    private String volcanoModel;

    @Value("${ai.chat.openai.base-url:https://api.openai.com/v1}")
    private String openaiBaseUrl;

    @Value("${ai.chat.openai.model:gpt-4}")
    private String openaiModel;

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "dashscope", matchIfMissing = true)
    public ChatModel dashScopeChatModel() {
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : dashscopeBaseUrl;
        return ChatModelProvider.createDashScopeModel(apiKey, effectiveBaseUrl, modelName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "volcano-engine")
    public ChatModel volcanoEngineChatModel() {
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : volcanoBaseUrl;
        String effectiveModel = modelName != null && !modelName.isBlank() ? modelName : volcanoModel;
        return ChatModelProvider.createVolcanoEngineModel(apiKey, effectiveBaseUrl, effectiveModel);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "openai")
    public ChatModel openAiChatModel() {
        String effectiveBaseUrl = baseUrl != null && !baseUrl.isBlank() ? baseUrl : openaiBaseUrl;
        String effectiveModel = modelName != null && !modelName.isBlank() ? modelName : openaiModel;
        return ChatModelProvider.createOpenAIModel(apiKey, effectiveBaseUrl, effectiveModel);
    }
}
