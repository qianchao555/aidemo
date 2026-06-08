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
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * RAG 对话前置 Hook：仅负责 FAQ 快速匹配。
 *
 * <p>非 FAQ 查询不在此注入知识库内容，交由 Agent 通过 searchKnowledge 工具自行决定检索时机，
 * 实现交互式多轮对话（先澄清 → 再检索 → 最终答案）。
 *
 * @author Chao C Qian
 */
@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class RagQaMessageHook extends MessagesModelHook {

    private static final Logger logger = LoggerFactory.getLogger(RagQaMessageHook.class);

    private static final ThreadLocal<String> currentUserQuery = new ThreadLocal<>();

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

        // 闲聊意图检测：命中则注入友好回复指令，不调用 searchKnowledge
        ChitchatDetector.ChitchatResult chitchatResult = chitchatDetector.detect(userQuery);
        if (chitchatResult.isChitchat()) {
            return handleChitchat(previousMessages, chitchatResult.type());
        }

        // 意图标注：识别提问类型，注入对应策略，交给 Agent 处理
        IntentType intent = IntentType.classify(userQuery);
        List<Message> enriched = new ArrayList<>(previousMessages);
        enriched.add(new SystemMessage(buildIntentAnnotation(intent, userQuery)));
        logger.info("RAG QA Hook: 意图标注 intent={}, 交由 Agent 交互式处理 | query='{}'",
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

    /** 根据意图识别结果构造标注 SystemMessage */
    private String buildIntentAnnotation(IntentType intent, String userQuery) {
        StringBuilder sb = new StringBuilder();
        sb.append("【意图识别结果 - 已由系统自动标注】\n");
        sb.append("意图类型：").append(intent.getDisplayName()).append("\n");

        if (intent.isFallback()) {
            sb.append("回答策略：").append(intent.getAnswerStrategy()).append("\n\n");
            sb.append("用户问题：").append(userQuery).append("\n\n");
            sb.append("严格按以上策略执行，禁止调用 searchKnowledge，禁止编造答案。");
        } else {
            sb.append("回答策略：").append(intent.getAnswerStrategy()).append("\n\n");
            sb.append("用户问题：").append(userQuery).append("\n\n");
            sb.append("请遵守以上策略回答，不必重新判断意图类型。");
        }
        return sb.toString();
    }
}
