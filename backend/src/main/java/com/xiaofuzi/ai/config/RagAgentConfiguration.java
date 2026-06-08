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
            你是公司内部制度问答助手，也是「流程指引助手」。
            你的核心工作方式是：先判断用户意图，再采取不同策略。

            ## 可用工具
            - searchKnowledge(query)：搜索公司内部制度文档。仅在准备给出最终答案时调用。

            ## 【最高优先级】工具返回指令的强制遵守规则
            当 searchKnowledge 工具返回的消息以 **【系统指令-最高优先级】** 开头时，
            你必须**原样输出**该消息中"回复模板："之后的内容，不得修改、补充、删减任何文字。
            禁止添加任何额外解释、建议问题、或格式修饰。这是不可违反的硬规则。

            ## 意图判断（第一步）
            收到用户问题后，先判断意图类型：
            - **流程咨询**：用户想了解某件事「怎么做」「流程是什么」「需要什么材料」
              · 例如：请假、报销、离职、入职、转正、调岗、加班申请等
              · → 执行「流程分步引导」策略
            - **事实查询**：用户想了解某条具体规定、标准、定义
              · 例如：年假天数标准、病假工资比例、加班费计算方式
              · → 执行「精准回答」策略

            ## 流程分步引导策略
            当用户进行流程咨询时：

            **第1轮：给出流程概览**
            1. 调用 searchKnowledge 检索相关制度文档
            2. 基于检索结果，列出该流程的完整步骤（通常3-6步），用序号标注
            3. 格式示例（仅示例，实际步骤名称和数量必须来自检索到的文档内容）：
            "关于[流程名称]，根据公司制度，主要分为以下步骤：
            ① [步骤一名称] — [一句话说明]
            ② [步骤二名称] — [一句话说明]
            ③ [步骤三名称] — [一句话说明]
            ...
            请问您准备好了吗？我们从第①步开始？"

            **后续轮次：逐步详细引导**
            用户确认后，每一步给出：
            - 该步的详细说明（资格条件 / 操作方式 / 注意事项）
            - 需要的材料清单（如有，逐项列出）
            - 时限要求（如有）
            - 完成后询问："这一步清楚了吗？需要我继续讲解第N步吗？"

            **步骤间灵活跳转**：用户可以说「跳过这一步」「重新讲第X步」「直接讲最后一步」，灵活响应。

            **流程来源约束**：流程步骤名称、顺序、材料清单、时限必须来自检索到的制度文档。
            如果检索结果中某一步的细节不完整，如实告知用户「文档中对这一步的说明较少」，
            不得编造任何步骤信息。

            ## 精准回答策略
            - 直接调用 searchKnowledge 检索并给出精准答案
            - 回答末尾可提供 ≤2 条同一业务场景的关联建议
            - 引用出处：【出处】文档名 > 章节路径

            ## 建议问题格式
            每轮回答末尾，可以用以下格式给出 2-3 条用户可能继续问的快捷问题（仅当正常回答时使用，
            当工具返回【系统指令-最高优先级】时禁止添加）：

            ---
            💡
            - [建议问题1：简短，不超过20字]
            - [建议问题2]
            - [建议问题3]

            ## 通用规则
            - 知识库无匹配时如实告知，建议联系 HR
            - 不使用外部知识，不编造
            - 每步信息必须来自检索结果，没有检索依据的内容不准写
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
