package com.xiaofuzi.ai.config;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.rag.KnowledgeRetrievalTools;
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

    /**
     * Agent 基础系统提示词，仅包含公共规则。
     *
     * <p>意图对应的策略指令由 RagQaMessageHook 根据 IntentType 动态注入，
     * 避免单体 Prompt 中所有策略混在一起。
     */
    private static final String BASE_SYSTEM_PROMPT = """
            你是公司内部制度问答助手，也是「流程指引助手」。
            具体的回答策略和格式要求已通过系统消息标注，请严格遵守标注中的策略指令。

            ## 可用工具
            - searchKnowledge(query)：搜索公司内部制度文档。仅在准备给出最终答案时调用。

            ## 【最高优先级】工具返回指令的强制遵守规则
            当 searchKnowledge 工具返回的消息以 **【系统指令-最高优先级】** 开头时，
            你必须**原样输出**该消息中"回复模板："之后的内容，不得修改、补充、删减任何文字。
            禁止添加任何额外解释、建议问题、或格式修饰。这是不可违反的硬规则。

            ## 【核心约束】禁止编造
            检索结果里有就写，没有就说没有。不准用自己的常识或训练数据补充任何信息。
            违反此约束会导致错误的制度指引，对用户造成实际损害，这是不可接受的。

            ## 建议问题格式
            每轮回答末尾，可以用以下格式给出 2-3 条用户可能继续问的快捷问题（仅当正常回答时使用，
            当工具返回【系统指令-最高优先级】或处于引导澄清时禁止添加）：

            ---
            💡 您可以继续问：
            - [建议问题1：简短，不超过20字]
            - [建议问题2]
            - [建议问题3]

            ## 通用规则
            - 知识库无匹配时如实告知，建议联系 HR
            - 回答中引用的材料、时限、条件等必须有检索依据""";

    @Value("${ai.agent.rag-qa.name:rag-qa-agent}")
    private String ragQaAgentName;

    @Value("${ai.agent.rag-qa.description:内部知识库问答助手，严格基于已上传的文档回答用户问题，注明出处，不编造信息}")
    private String ragQaAgentDescription;

    private final ChatModel chatModel;
    private final RagQaMessageHook ragQaMessageHook;
    private final KnowledgeRetrievalTools knowledgeRetrievalTools;

    public RagAgentConfiguration(ChatModel chatModel,
                                 RagQaMessageHook ragQaMessageHook,
                                 KnowledgeRetrievalTools knowledgeRetrievalTools) {
        this.chatModel = chatModel;
        this.ragQaMessageHook = ragQaMessageHook;
        this.knowledgeRetrievalTools = knowledgeRetrievalTools;
    }

    @Bean
    public ReactAgent ragQaAgent() {
        ReactAgent agent = ReactAgent.builder()
                .name(ragQaAgentName)
                .description(ragQaAgentDescription)
                .model(chatModel)
                .systemPrompt(BASE_SYSTEM_PROMPT)
                .methodTools(knowledgeRetrievalTools)
                .hooks(ragQaMessageHook)
                .enableLogging(true)
                .build();
        logger.info("[Agent创建] {} | 描述: {} | 工具: searchKnowledge | 模式: 交互式对话",
                ragQaAgentName, ragQaAgentDescription);
        return agent;
    }
}
