
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

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "dashscope", matchIfMissing = true)
    public ChatModel dashScopeChatModel() {
        return ChatModelProvider.createDashScopeModel(apiKey, baseUrl, modelName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "volcano-engine")
    public ChatModel volcanoEngineChatModel() {
        return ChatModelProvider.createVolcanoEngineModel(apiKey, baseUrl, modelName);
    }

    @Bean
    @ConditionalOnProperty(prefix = "ai.chat", name = "provider", havingValue = "openai")
    public ChatModel openAiChatModel() {
        return ChatModelProvider.createOpenAIModel(apiKey, baseUrl, modelName);
    }
}
