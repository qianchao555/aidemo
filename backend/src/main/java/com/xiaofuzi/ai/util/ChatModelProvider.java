package com.xiaofuzi.ai.util;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.util.StringUtils;

/**
 * ChatModel 多厂商提供工具类
 * 支持：通义千问（DashScope）、火山引擎（OpenAI兼容API）、OpenAI等
 */
public class ChatModelProvider {

    public enum ModelProvider {
        DASHSCOPE,      // 阿里云通义千问
        VOLCANO_ENGINE, // 火山引擎豆包
        OPENAI          // OpenAI GPT
    }

    private ChatModelProvider() {
    }

    /**
     * 根据厂商类型创建 ChatModel
     */
    public static ChatModel createChatModel(ModelProvider provider, String apiKey, String baseUrl, String modelName) {
        return switch (provider) {
            case DASHSCOPE -> createDashScopeModel(apiKey, baseUrl, modelName);
            case VOLCANO_ENGINE -> createVolcanoEngineModel(apiKey, baseUrl, modelName);
            case OPENAI -> createOpenAIModel(apiKey, baseUrl, modelName);
        };
    }

    /**
     * 创建通义千问（DashScope）ChatModel
     */
    public static ChatModel createDashScopeModel(String apiKey, String baseUrl, String modelName) {
        DashScopeApi dashScopeApi = DashScopeApi.builder()
                .apiKey(StringUtils.hasText(apiKey) ? apiKey : System.getenv("AI_DASHSCOPE_API_KEY"))
                .baseUrl(StringUtils.hasText(baseUrl) ? baseUrl : "https://dashscope.aliyuncs.com")
                .build();

        return DashScopeChatModel.builder()
                .dashScopeApi(dashScopeApi)
                .build();
    }

    /**
     * 创建火山引擎（豆包）ChatModel
     * 火山引擎支持 OpenAI 兼容 API
     */
    public static ChatModel createVolcanoEngineModel(String apiKey, String baseUrl, String modelName) {
        String effectiveApiKey = StringUtils.hasText(apiKey) ? apiKey : System.getenv("VolcanoEngine_API");
        String effectiveBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : "https://ark.cn-beijing.volces.com/api/v3";
        String effectiveModel = StringUtils.hasText(modelName) ? modelName : "doubao-pro-32k";

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(effectiveBaseUrl)
                .apiKey(effectiveApiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(effectiveModel)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }

    /**
     * 创建 OpenAI ChatModel
     */
    public static ChatModel createOpenAIModel(String apiKey, String baseUrl, String modelName) {
        String effectiveApiKey = StringUtils.hasText(apiKey) ? apiKey : System.getenv("OPENAI_API_KEY");
        String effectiveBaseUrl = StringUtils.hasText(baseUrl) ? baseUrl : "https://api.openai.com/v1";
        String effectiveModel = StringUtils.hasText(modelName) ? modelName : "gpt-4";

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(effectiveBaseUrl)
                .apiKey(effectiveApiKey)
                .build();

        OpenAiChatOptions options = OpenAiChatOptions.builder()
                .model(effectiveModel)
                .build();

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(options)
                .build();
    }
}
