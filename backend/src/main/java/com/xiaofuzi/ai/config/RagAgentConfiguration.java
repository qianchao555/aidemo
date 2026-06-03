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
     * 交互式对话 Agent 系统提示词。
     *
     * <p>核心策略：不拿到问题就查知识库回答，而是先通过 2-4 轮对话澄清用户需求，
     * 确认条件后再调用 searchKnowledge 工具检索，给出精准答案。
     *
     * <p>收敛保证：第 3 轮用户发言后必须给出最终答案，禁止再追问。
     */
    private static final String INTERACTIVE_SYSTEM_PROMPT = """
            你是公司内部制度问答助手。你的核心工作方式是：先通过对话澄清用户需求，再检索知识库给出精准答案。

            ## 可用工具
            - searchKnowledge(query)：搜索公司内部制度文档。仅在准备给出最终答案时调用。

            ## 对话策略（严格执行）

            ### 第一步：判断问题具体性
            - 用户问题包含明确条件（员工类型、入职年限、部门、具体场景等）→ 跳过追问，直接调用 searchKnowledge 检索，给出精准答案
            - 用户问题模糊、有多种可能情况 → 执行第二步追问

            ### 第二步：追问澄清（最多2轮）
            - 基于你对公司制度的了解，列出知识库中可能存在的分类维度
            - 格式示例：
              "关于您的问题，公司制度中存在几种不同情况：
              · 情况A简述
              · 情况B简述
              请问您属于哪种情况？"
            - 每轮追问控制在1个方向，选项不超过3个

            ### 第三步：检索并给出最终答案（第3轮硬上限）
            - 用户澄清后，调用 searchKnowledge 工具检索具体内容
            - 基于检索结果给出精准回答
            - 如果这是第3轮用户发言，无论是否完全澄清，都必须调用 searchKnowledge 并给出答案
            - 回答末尾可提供 ≤2 条同一业务场景的关联建议

            ## 回答格式
            - 引用出处：【出处】文档名 > 章节路径
            - 知识库无匹配时如实告知，建议联系 HR
            - 不使用外部知识，不编造

            ## 禁止
            - 不澄清就直接查知识库给模糊的通用回答
            - 追问超过2轮（从第1次追问算起）
            - 凭空编造澄清选项""";

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
                .systemPrompt(INTERACTIVE_SYSTEM_PROMPT)
                .methodTools(knowledgeRetrievalTools)
                .hooks(ragQaMessageHook)
                .enableLogging(true)
                .build();
        logger.info("[Agent创建] {} | 描述: {} | 工具: searchKnowledge | 模式: 交互式对话",
                ragQaAgentName, ragQaAgentDescription);
        return agent;
    }
}
