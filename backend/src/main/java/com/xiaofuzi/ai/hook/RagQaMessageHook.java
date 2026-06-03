package com.xiaofuzi.ai.hook;

import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.xiaofuzi.ai.dto.FaqMatchResult;
import com.xiaofuzi.ai.rag.FaqService;
import com.xiaofuzi.ai.rag.IntentType;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@HookPositions({HookPosition.BEFORE_MODEL})
public class RagQaMessageHook extends MessagesModelHook {

    private static final Logger logger = LoggerFactory.getLogger(RagQaMessageHook.class);

    private static final Map<IntentType, String> ANSWER_TEMPLATES = Map.ofEntries(
            Map.entry(IntentType.POLICY_QA, """

                    【回答策略：制度问答】
                    请按以下方式组织回答：
                    1. 先概述相关制度的核心要点（1-2句）
                    2. 逐条列出具体条款，每条格式：【条款】内容... → 【出处】文档名 > 章节路径
                    3. 如有补充说明，放在最后"""),
            Map.entry(IntentType.PROCESS_GUIDE, """

                    【回答策略：流程指引】
                    请按以下方式组织回答：
                    1. 先说明该流程的适用场景
                    2. 按步骤编号分点说明：第1步...、第2步...，每步标注依据出处
                    3. 如涉及时间/材料等要求，单独列出"""),
            Map.entry(IntentType.DEFINITION, """

                    【回答策略：定义查询】
                    请按以下方式组织回答：
                    1. 先给出简洁定义（1句话）
                    2. 再展开说明定义中的关键要素
                    3. 如有相关概念，补充说明区分"""),
            Map.entry(IntentType.COMPARISON, """

                    【回答策略：对比查询】
                    请按以下方式组织回答：
                    1. 先列出待对比的各方
                    2. 用对比表格或分项结构呈现差异
                    3. 如有优劣分析，基于原文客观说明"""),
            Map.entry(IntentType.SCOPE_CHECK, """

                    【回答策略：范围确认】
                    请按以下方式组织回答：
                    1. 明确给出"是/否/部分适用"的结论
                    2. 引用原文条款说明适用范围
                    3. 如有例外情况，单独列出"""),
            Map.entry(IntentType.VIOLATION, """

                    【回答策略：违规后果】
                    请按以下方式组织回答：
                    1. 列出具体的违规情形
                    2. 每种情形对应说明后果/处罚
                    3. 如涉及申诉/减免途径，一并说明"""),
            Map.entry(IntentType.FALLBACK, "")
    );

    private final KnowledgeBaseService knowledgeBaseService;
    private final FaqService faqService;

    /** 存储最近一次检索的元信息，供 SSE 控制器读取并推送给前端展示 */
    private final ThreadLocal<Map<String, Object>> lastSearchInfo = new ThreadLocal<>();

    public RagQaMessageHook(KnowledgeBaseService knowledgeBaseService, FaqService faqService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.faqService = faqService;
    }

    /** SSE 控制器在 ask() 完成后调用，获取本次检索的元信息推送给前端 */
    public Map<String, Object> getLastSearchInfo() {
        Map<String, Object> info = lastSearchInfo.get();
        lastSearchInfo.remove();
        return info;
    }

    @Override
    public String getName() {
        return "ragQaMessageHook";
    }

    @Override
    public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
        String userQuery = extractUserQuery(previousMessages);
        if (userQuery == null || userQuery.isEmpty()) {
            return new AgentCommand(previousMessages);
        }

        //FAQ匹配（优先，零延迟）
        FaqMatchResult faqResult = faqService.match(userQuery);
        if (faqResult.matched()) {
            //命中FAQ
            return handleFaqHit(previousMessages, faqResult);
        }

        //意图识别
        IntentType intent = IntentType.classify(userQuery);
        logger.info("意图识别(规则): query='{}' → {}", userQuery, intent.getDisplayName());

        if (intent.isFallback()) {
            return handleFallback(previousMessages, userQuery);
        }

        return handleAnswerable(previousMessages, userQuery, intent);
    }

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

    //带阈值的RAG检索，适用于明确了意图但需要控制相关性要求的场景
    private static final double DEFAULT_SIMILARITY_THRESHOLD = 0.5;

    @SuppressWarnings("unchecked")
    private AgentCommand handleFallback(List<Message> previousMessages, String userQuery) {
        Map<String, Object> hybridResult = knowledgeBaseService.hybridSearch(
                userQuery, 5, DEFAULT_SIMILARITY_THRESHOLD);
        List<Document> relevantDocs = (List<Document>) hybridResult.get("documents");

        // 存储检索元信息，供 SSE 控制器推送给前端
        Map<String, Object> info = new java.util.HashMap<>(hybridResult);
        info.remove("documents");
        info.put("searchMode", "hybrid");
        lastSearchInfo.set(info);

        List<Message> enriched = new ArrayList<>(previousMessages);

        if (relevantDocs.isEmpty()) {
            String prompt = "【重要】已检索内部知识库（混合检索模式），未找到相关文档。"
                    + "请告知用户：'抱歉，在内部知识库中未搜索到与您问题相关的信息。"
                    + "建议联系 HR 或行政部门获取帮助。'"
                    + "严禁使用训练数据或外部知识编造回答。";
            enriched.add(new SystemMessage(prompt));
            logger.info("RAG QA Hook: 兜底混合检索未命中");
        } else {
            String knowledgeContext = knowledgeBaseService.formatAsContext(relevantDocs, 3000);
            String prompt = knowledgeContext
                    + "\n\n【铁律 - 必须严格遵守】\n"
                    + "1. 你的回答必须完全基于以上知识库参考资料，严禁使用任何外部知识或训练数据\n"
                    + "2. 每个观点必须注明出处：格式为 【出处】文档名 > 章节路径\n"
                    + "3. 如果参考资料不足以回答用户问题，请如实说明\n"
                    + "4. 不要添加知识库中没有的信息，不要编造，不要猜测";
            enriched.add(new SystemMessage(prompt));
            logger.info("RAG QA Hook: 兜底混合检索 | 向量{} + 关键词{} → 融合后{}",
                    hybridResult.get("vectorCount"), hybridResult.get("keywordCount"),
                    hybridResult.get("mergedCount"));
        }

        return new AgentCommand(enriched);
    }

    @SuppressWarnings("unchecked")
    private AgentCommand handleAnswerable(List<Message> previousMessages,
                                          String userQuery, IntentType intent) {
        double threshold = intent.getThreshold() > 0 ? intent.getThreshold() : DEFAULT_SIMILARITY_THRESHOLD;
        Map<String, Object> hybridResult = knowledgeBaseService.hybridSearch(
                userQuery, intent.getTopK(), threshold);
        List<Document> relevantDocs = (List<Document>) hybridResult.get("documents");

        // 存储检索元信息，供 SSE 控制器推送给前端
        Map<String, Object> info = new java.util.HashMap<>(hybridResult);
        info.remove("documents");
        info.put("searchMode", "hybrid");
        info.put("intent", intent.getDisplayName());
        lastSearchInfo.set(info);

        List<Message> enriched = new ArrayList<>(previousMessages);

        if (relevantDocs.isEmpty()) {
            String prompt = "【重要】已检索内部知识库（意图：" + intent.getDisplayName()
                    + "，混合检索模式），未找到相关文档。"
                    + "请直接告知用户：'抱歉，在内部知识库中未搜索到与您问题相关的文档，无法提供答案。'"
                    + "严禁使用训练数据或外部知识编造回答。";
            enriched.add(new SystemMessage(prompt));
            logger.info("RAG QA Hook: 意图={} 混合检索未命中", intent.getDisplayName());
        } else {
            String knowledgeContext = knowledgeBaseService.formatAsContext(relevantDocs, 3000);
            String template = ANSWER_TEMPLATES.getOrDefault(intent, "");
            String prompt = knowledgeContext
                    + "\n\n【铁律 - 必须严格遵守】\n"
                    + "1. 你的回答必须完全基于以上知识库参考资料，严禁使用任何外部知识或训练数据\n"
                    + "2. 每个观点必须注明出处：格式为 【出处】文档名 > 章节路径\n"
                    + "3. 如果参考资料不足以回答用户问题，请如实说明\n"
                    + "4. 不要添加知识库中没有的信息，不要编造，不要猜测"
                    + template;
            enriched.add(new SystemMessage(prompt));
            logger.info("RAG QA Hook: 意图={} 混合检索 | 向量{} + 关键词{} → 融合后{}",
                    intent.getDisplayName(), hybridResult.get("vectorCount"),
                    hybridResult.get("keywordCount"), hybridResult.get("mergedCount"));
        }

        return new AgentCommand(enriched);
    }

    private String extractUserQuery(List<Message> messages) {
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
}
