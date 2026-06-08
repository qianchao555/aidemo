package com.xiaofuzi.ai;

import com.alibaba.cloud.ai.autoconfigure.dashscope.DashScopeChatAutoConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
//import org.springframework.ai.mcp.client.common.autoconfigure.McpClientAutoConfiguration;
//import org.springframework.ai.mcp.client.common.autoconfigure.McpToolCallbackAutoConfiguration;
//import org.springframework.ai.mcp.client.common.autoconfigure.StdioTransportAutoConfiguration;
//import org.springframework.ai.mcp.client.webflux.autoconfigure.SseWebFluxTransportAutoConfiguration;
//import org.springframework.ai.mcp.server.common.autoconfigure.McpServerAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.xiaofuzi.ai")
@MapperScan(basePackages = "com.xiaofuzi.ai.mapper")
@EnableAutoConfiguration(exclude = {

        DashScopeChatAutoConfiguration.class,

//        SseWebFluxTransportAutoConfiguration.class,

        OpenAiChatAutoConfiguration.class,
//        StdioTransportAutoConfiguration.class,
//        McpToolCallbackAutoConfiguration.class,
//        McpClientAutoConfiguration.class,
//        McpServerAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,

})
public class AiBackendApplication {
    public static void main(String[] args) {
        SpringApplication.run(AiBackendApplication.class, args);
    }

}
