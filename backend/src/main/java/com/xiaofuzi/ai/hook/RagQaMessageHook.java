package com.xiaofuzi.ai.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.xiaofuzi.ai.dto.FaqMatchResult;
import com.xiaofuzi.ai.enums.IntentType;
import com.xiaofuzi.ai.service.ChitchatDetector;
import com.xiaofuzi.ai.service.FaqService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 对话前置 Hook：FAQ 快速匹配 + 闲聊检测 + 意图路由（注入对应策略指令）。
 *
 * <p>基础 Prompt 仅包含公共规则，策略由本 Hook 根据 IntentType 动态选择并注入，
 * 实现制度问答与流程指引的指令级分离。
 *
 * @author Chao C Qian
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class RagQaMessageHook extends MessagesModelHook {

    private static final Logger logger = LoggerFactory.getLogger(RagQaMessageHook.class);

    private static final ThreadLocal<String> currentUserQuery = new ThreadLocal<>();

    // ========== 策略指令常量（从基础 Prompt 中分离，按意图动态注入） ==========

    /**
     * 流程分步引导 —— 多轮交互式流程指引。
     *
     * <p>首条规则即防编造：LLM 必须先读这条约束，再读分步引导指令，
     * 避免因"列出完整步骤""逐项列出材料"等完整性要求触发训练数据补充。
     */
    private static final String PROCESS_GUIDE_STRATEGY = """
            ## 回答策略：流程分步引导

            **【硬约束】以下所有内容必须严格来自 searchKnowledge 返回的文档，检索结果里没有的一律不写。**
            步骤、材料、时限、角色、条件，文档提了几个就写几个，不准用自己的常识补全。

            **第1轮：给出流程概览**
            1. 调用 searchKnowledge 检索相关制度文档
            2. 基于检索结果，列出文档中实际提到的步骤（有几个就写几个），用序号标注
            3. 格式示例（实际步骤名称和数量必须来自检索到的文档内容）：
            "关于[流程名称]，根据公司制度，主要分为以下步骤：
            ① [步骤一名称] — [一句话说明]
            ② [步骤二名称] — [一句话说明]
            ...
            【出处】文档名 > 章节路径
            请问您准备好了吗？我们从第①步开始？"

            **后续轮次：逐步详细引导**
            用户确认后，只输出检索结果中对该步骤的说明：
            - 资格条件 / 操作方式 / 注意事项（文档中有才写，没有就说"文档未详述"）
            - 材料清单（文档中有才逐项列出，没有就说"材料清单未提及"）
            - 时限要求（文档中有才写，没有就说"时限未说明"）
            - 回答末尾必须标注：【出处】文档名 > 章节路径
            - 完成后询问："这一步清楚了吗？需要我继续讲解第N步吗？"

            **步骤间灵活跳转**：用户可以说「跳过这一步」「重新讲第X步」「直接讲最后一步」，灵活响应。
            第3轮用户发言后必须给出最终答案，禁止再追问。""";

    /**
     * 精准回答 —— 制度条款 / 定义 / 对比 / 范围 / 违规后果的单轮精准问答。
     *
     * <p>首条规则即防编造：LLM 必须先读这条约束，再读格式指令，
     * 避免因"逐条列出""展开说明"等要求触发训练数据补充。
     */
    private static final String PRECISE_ANSWER_STRATEGY = """
            ## 回答策略：精准回答

            **【硬约束】以下所有内容必须严格来自 searchKnowledge 返回的文档，检索结果里没有的一律不写。**
            每条回答必须标注出处，格式为：【出处】文档名 > 章节路径。

            - 调用 searchKnowledge 检索并给出精准答案
            - 制度问答：列出检索结果中实际提到的相关条款，有几条写几条
            - 定义查询：基于检索结果给出定义，不扩展
            - 对比查询：基于检索结果呈现对比（如表格），分别标注
            - 范围确认：引用检索结果中的适用范围原文
            - 违规后果：列出检索结果中实际提到的违规情形及措施
            - 回答末尾可提供 ≤2 条基于检索结果的关联建议，不要超出检索范围""";

    /** 兜底检索 —— FALLBACK 时先检索再回答，结果为空时如实告知 */
    private static final String FALLBACK_STRATEGY = """
            ## 回答策略：兜底检索

            **【硬约束】以下所有内容必须严格来自 searchKnowledge 返回的文档，检索结果里没有的一律不写。**

            1. 先调用 searchKnowledge 检索相关制度文档
            2. 基于检索结果给出精准回答，标注【出处】
            3. 如果检索结果为空或质量不足，如实告知用户，建议更换关键词或联系 HR
            4. 仅当问题本身极模糊（如单个词、无上下文）时，才可反问用户最多 1 个关键问题，但仍应先尝试检索""";

    public static void setCurrentUserQuery(String query) {
        currentUserQuery.set(query);
    }

    public static void clearCurrentUserQuery() {
        currentUserQuery.remove();
    }

    private final FaqService faqService;
    private final ChitchatDetector chitchatDetector;

    public RagQaMessageHook(FaqService faqService,
                            ChitchatDetector chitchatDetector) {
        this.faqService = faqService;
        this.chitchatDetector = chitchatDetector;
    }

    @Override
    public String getName() {
        return "ragQaMessageHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        // 仅在用户新发言时触发 FAQ 匹配，工具调用链中的循环不重复触发
        if (!isNewUserTurn(previousMessages)) {
            return new AgentCommand(previousMessages);
        }

        String userQuery = extractUserQuery(previousMessages);
        if (userQuery == null || userQuery.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        // FAQ 快速匹配：命中则直接返回标准答案，绕过 Agent 交互流程
        FaqMatchResult faqResult = faqService.match(userQuery);
        if (faqResult.matched()) {
            return handleFaqHit(previousMessages, faqResult);
        }

        // 流程指引续接：多轮流程引导中，用户简短回复视为流程续接，跳过闲聊检测
        if (isInProcessGuidanceFlow(previousMessages) && isFlowContinuation(userQuery)) {
            List<Message> enriched = new ArrayList<>(previousMessages);
            enriched.add(buildProcessContinuationMessage(userQuery));
            logger.info("RAG QA Hook: 流程指引续接 | query='{}'", userQuery);
            return new AgentCommand(enriched);
        }

        // 闲聊意图检测：命中则注入友好回复指令，不调用 searchKnowledge
        ChitchatDetector.ChitchatResult chitchatResult = chitchatDetector.detect(userQuery);
        if (chitchatResult.isChitchat()) {
            return handleChitchat(previousMessages, chitchatResult.type());
        }

        // 意图路由：识别提问类型，注入对应的完整策略指令
        IntentType intent = IntentType.classify(userQuery);
        List<Message> enriched = new ArrayList<>(previousMessages);
        enriched.add(buildStrategyMessage(intent, userQuery));
        logger.info("RAG QA Hook: 意图路由 intent={}, 注入策略指令 | query='{}'",
                intent.getDisplayName(), userQuery);
        return new AgentCommand(enriched);
    }

    /** 闲聊意图：注入友好回复指令，不调用 searchKnowledge */
    private AgentCommand handleChitchat(List<Message> previousMessages,
                                        ChitchatDetector.ChitchatType type) {
        List<Message> enriched = new ArrayList<>(previousMessages);
        String prompt = "【闲聊模式】\n\n"
                + "用户正在闲聊，请以友好、轻松的语气进行回应，"
                + "无需调用知识库检索工具。";
        enriched.add(new SystemMessage(prompt));
        logger.info("RAG QA Hook: 闲聊检测 type={}", type);
        return new AgentCommand(enriched);
    }

    /** FAQ 命中时直接注入标准答案，不走 Agent 交互流程 */
    private AgentCommand handleFaqHit(List<Message> previousMessages,
                                      FaqMatchResult faqResult) {
        List<Message> enriched = new ArrayList<>(previousMessages);
        String prompt = "【FAQ 标准答案 - 命中类型：" + faqResult.matchType() + "】\n\n"
                + "以下为该问题的标准答案，请直接输出此内容，无需额外检索或修改：\n\n"
                + "问题：" + faqResult.entry().getQuestion() + "\n\n"
                + "答案：\n" + faqResult.entry().getAnswer() + "\n\n"
                + "【出处】FAQ 标准答案库";
        enriched.add(new SystemMessage(prompt));
        logger.info("RAG QA Hook: FAQ命中 type={} question='{}'",
                faqResult.matchType(), faqResult.entry().getQuestion());
        return new AgentCommand(enriched);
    }

    /** 最后一条消息是 UserMessage → 新的用户轮次 */
    private boolean isNewUserTurn(List<Message> messages) {
        if (messages == null || messages.isEmpty()) {
            return false;
        }
        return messages.get(messages.size() - 1) instanceof UserMessage;
    }

    /** 兼容旧 SSE 接口的检索元信息查询（当前已无检索，固定返回 null） */
    public Map<String, Object> getLastSearchInfo() {
        return null;
    }

    /**
     * 检测当前是否处于流程指引的多轮交互中。
     * 检查最近的助手消息是否包含流程引导标记。
     */
    private boolean isInProcessGuidanceFlow(List<Message> messages) {
        if (messages == null || messages.isEmpty()) return false;
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof AssistantMessage) {
                String text = msg.getText();
                return text.contains("第①步") || text.contains("流程概览")
                        || text.contains("这一步清楚了吗") || text.contains("需要我继续讲解");
            }
            if (msg instanceof UserMessage) break;
        }
        return false;
    }

    /**
     * 判断用户消息是否为流程引导中的续接信号（确认/跳过/回退等），
     * 而非新的独立提问。
     */
    private boolean isFlowContinuation(String query) {
        if (query == null || query.isBlank()) return false;
        String normalized = query.trim().toLowerCase();
        for (String kw : new String[]{"继续", "下一步", "跳过", "重新", "回退", "好的", "清楚了", "明白", "知道了"}) {
            if (normalized.contains(kw)) return true;
        }
        return normalized.length() <= 6;
    }

    /** 流程指引续接指令：告知 LLM 用户正在跟进流程，继续引导下一步 */
    private SystemMessage buildProcessContinuationMessage(String userQuery) {
        String prompt = """
                【系统指令 - 流程指引续接】

                用户正在跟进流程指引的上一步，请根据历史对话中的流程上下文继续引导。

                用户说：「%s」

                如果用户表示确认或准备好了，则推进到下一步。
                如果用户要求跳过或回退，则按用户要求跳转步骤。

                【硬约束】所有内容必须严格来自 searchKnowledge 返回的文档。"""
                .formatted(userQuery);
        return new SystemMessage(prompt);
    }

    private String extractUserQuery(List<Message> messages) {
        String query = currentUserQuery.get();
        if (query != null && !query.isBlank()) {
            return query;
        }
        if (messages == null || messages.isEmpty()) {
            return null;
        }
        for (int i = messages.size() - 1; i >= 0; i--) {
            Message msg = messages.get(i);
            if (msg instanceof UserMessage) {
                return msg.getText();
            }
        }
        return null;
    }

    /**
     * 根据意图类型选择对应的完整策略指令，构造 SystemMessage。
     *
     * <p>制度问答（6 种具体意图）注入精准回答策略，
     * 流程指引注入分步引导策略，
     * FALLBACK 注入兜底检索策略。
     * 每种策略都是完整的、可直接执行的指令，不需要 Agent 再到基础 Prompt 中查找。
     */
    private SystemMessage buildStrategyMessage(IntentType intent, String userQuery) {
        String strategy = selectStrategy(intent);
        StringBuilder sb = new StringBuilder();
        sb.append("【系统指令 - 意图类型：").append(intent.getDisplayName()).append("】\n\n");
        sb.append(strategy).append("\n\n");
        sb.append("用户问题：").append(userQuery);
        return new SystemMessage(sb.toString());
    }

    /**
     * 策略选择器：意图到策略的一对多映射。
     * PROCESS_GUIDE → 流程分步引导，六种事实查询 → 精准回答，FALLBACK → 兜底检索。
     */
    private String selectStrategy(IntentType intent) {
        return switch (intent) {
            case PROCESS_GUIDE -> PROCESS_GUIDE_STRATEGY;
            case POLICY_QA, DEFINITION, COMPARISON, SCOPE_CHECK, VIOLATION -> PRECISE_ANSWER_STRATEGY;
            case FALLBACK -> FALLBACK_STRATEGY;
        };
    }
}
