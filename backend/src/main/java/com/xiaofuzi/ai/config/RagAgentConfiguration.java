package com.xiaofuzi.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RAG Agent配置
 *
 * @author Chao C Qian
 * @date 2026/5/29
 */
@Configuration
public class RagAgentConfiguration {
    private static final Logger logger = LoggerFactory.getLogger(RagAgentConfiguration.class);


    @Value("${ai.agent.rag-qa.name:rag-qa-agent}")
    private String ragQaAgentName;

    @Value("${ai.agent.rag-qa.description:内部知识库问答助手，严格基于已上传的文档回答用户问题，注明出处，不编造信息}")
    private String ragQaAgentDescription;


    private final ChatModel chatModel;
    private final RagQaMessageHook ragQaMessageHook;

    public RagAgentConfiguration(ChatModel chatModel,
                                 RagQaMessageHook ragQaMessageHook) {
        this.chatModel = chatModel;
        this.ragQaMessageHook = ragQaMessageHook;
    }

    @Bean
    public ReactAgent ragQaAgent() {
        ReactAgent agent = ReactAgent.builder()
                .name(ragQaAgentName)
                .description(ragQaAgentDescription)
                .model(chatModel)
                .hooks(ragQaMessageHook)
                .enableLogging(true)
                .build();
        logger.info("[Agent创建] {} | 描述: {} | SkillHook: ragQaMessageHook", ragQaAgentName, ragQaAgentDescription);
        return agent;
    }
}
