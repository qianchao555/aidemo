package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.context.DepartmentContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Agent RAG 知识检索工具类。
 *
 * <p>提供 searchKnowledge 工具供 ReAct Agent 调用。
 * 检索质量不足时通过返回值中的系统指令告知 Agent，由 Agent 自行处理兜底。
 */
@Component
public class KnowledgeRetrievalTools {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalTools.class);

    /** ★ 检索质量状态枚举 */
    public enum QualityStatus { PASSED, EMPTY, LOW_QUALITY }

    private final KnowledgeBaseService knowledgeBaseService;

    /**
     * ThreadLocal 存储最近一次检索的元信息，供 SSE 流 search_info 事件使用。
     * 格式：{searchMode, vectorCount, keywordCount, mergedCount, intent}
     */
    private static final ThreadLocal<Map<String, Object>> lastSearchInfoHolder = new ThreadLocal<>();

    /**
     * ThreadLocal 存储最近一次检索到的文档来源列表，供 SSE 流 source 事件使用。
     * 每个元素格式：{document, clause}
     */
    private static final ThreadLocal<List<Map<String, String>>> lastSearchSourcesHolder = new ThreadLocal<>();

    /** 获取并清除当前线程最近一次检索的元信息 */
    public static Map<String, Object> consumeLastSearchInfo() {
        Map<String, Object> info = lastSearchInfoHolder.get();
        lastSearchInfoHolder.remove();
        return info;
    }

    /** 获取并清除当前线程最近一次检索的文档来源列表 */
    public static List<Map<String, String>> consumeLastSearchSources() {
        List<Map<String, String>> sources = lastSearchSourcesHolder.get();
        lastSearchSourcesHolder.remove();
        return sources;
    }

    public KnowledgeRetrievalTools(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Value("${app.rag.search-similarity-threshold:0.5}")
    private double searchSimilarityThreshold;

    private static final int AGENT_CONTEXT_MAX_LENGTH = 3000;

    private record SearchResult(List<Document> docs, QualityStatus quality, QualityScore score,
                                 int vectorCount, int keywordCount, int mergedCount) {}

    @Tool(description = "从本地知识库中检索与查询相关的知识文档。适用场景：需要引用内部资料、专业知识、行业数据时调用")
    public String searchKnowledge(
            @ToolParam(description = "检索查询关键词或问题") String query) {
        logger.info("RAG工具调用 - searchKnowledge: query='{}', department='{}'",
                query, DepartmentContextHolder.get());

        SearchResult result = doSearch(query, 5, searchSimilarityThreshold);

        // ★ 存储本次检索元信息供 SSE 事件使用
        lastSearchInfoHolder.set(Map.of(
                "searchMode", "hybrid",
                "vectorCount", result.vectorCount,
                "keywordCount", result.keywordCount,
                "mergedCount", result.mergedCount));

        storeSearchSources(result.docs);

        if (result.docs.isEmpty()) {
            return "【系统指令-最高优先级】\n"
                    + "知识库中未找到任何与「" + query + "」相关的文档内容。\n\n"
                    + "回复模板：\n"
                    + "抱歉，我未能在知识库中找到与您问题相关的信息。\n\n"
                    + "建议您：\n"
                    + "1. 尝试更换关键词重新提问（例如使用更具体的术语或简称）\n"
                    + "2. 联系 HR 部门获取人工帮助";
        }

        if (result.quality == QualityStatus.LOW_QUALITY) {
            String scoreInfo = result.score != null
                    ? "，最高综合分仅 " + String.format("%.1f", result.score.maxCombined())
                    : "";
            return "【系统指令-最高优先级】\n"
                    + "检索到的文档内容与用户问题「" + query + "」相关性不足"
                    + scoreInfo + "。\n\n"
                    + "回复模板：\n"
                    + "抱歉，我未能在知识库中找到与您问题高度相关的内容。\n\n"
                    + "建议您：\n"
                    + "1. 尝试更换关键词重新提问（例如使用更具体的术语或简称）\n"
                    + "2. 联系 HR 部门获取人工帮助";
        }

        return knowledgeBaseService.formatAsContext(result.docs, AGENT_CONTEXT_MAX_LENGTH);
    }

    @SuppressWarnings("unchecked")
    private SearchResult doSearch(String query, int topK, double threshold) {
        String department = DepartmentContextHolder.get();
        Map<String, Object> result = knowledgeBaseService.hybridSearch(
                query, topK, threshold, department, null);

        QualityStatus quality = QualityStatus.PASSED;
        QualityScore score = null;
        String qs = (String) result.get("qualityStatus");
        if (qs != null) {
            quality = QualityStatus.valueOf(qs);
            Object scoreObj = result.get("qualityScore");
            if (scoreObj instanceof QualityScore qScore) {
                score = qScore;
            }
        }

        int vc = toInt(result.get("vectorCount"));
        int kc = toInt(result.get("keywordCount"));
        int mc = toInt(result.get("mergedCount"));

        Object docs = result.get("documents");
        if (docs instanceof List) {
            return new SearchResult((List<Document>) docs, quality, score, vc, kc, mc);
        }
        return new SearchResult(Collections.emptyList(), quality, score, vc, kc, mc);
    }

    private static int toInt(Object val) {
        if (val instanceof Number n) return n.intValue();
        return 0;
    }

    /**
     * 从检索结果中提取文档来源信息（文档名 + 章节路径），
     * 存入 ThreadLocal 供 SSE source 事件使用。
     */
    private static void storeSearchSources(List<Document> docs) {
        if (docs == null || docs.isEmpty()) {
            lastSearchSourcesHolder.set(List.of());
            return;
        }
        List<Map<String, String>> sources = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (Document doc : docs) {
            if (doc.getMetadata() == null) continue;
            String source = (String) doc.getMetadata().getOrDefault("source", "");
            String headingPath = (String) doc.getMetadata().get(com.xiaofuzi.ai.util.AppConstants.META_HEADING_PATH);
            String stepTitle = (String) doc.getMetadata().get(com.xiaofuzi.ai.util.AppConstants.META_STEP_TITLE);
            String clause = (headingPath != null && !headingPath.isBlank())
                    ? headingPath
                    : (stepTitle != null && !stepTitle.isBlank() ? stepTitle : null);
            String key = source + "|" + (clause != null ? clause : "");
            if (!seen.add(key)) continue;
            Map<String, String> item = new LinkedHashMap<>();
            item.put("document", source);
            if (clause != null) item.put("clause", clause);
            // 传递 group_id 和 version 供前端版本切换
            String groupId = (String) doc.getMetadata().get(com.xiaofuzi.ai.util.AppConstants.META_GROUP_ID);
            if (groupId != null) item.put("group_id", groupId);
            String version = (String) doc.getMetadata().get(com.xiaofuzi.ai.util.AppConstants.META_VERSION);
            if (version != null) item.put("version", version);
            sources.add(item);
        }
        lastSearchSourcesHolder.set(sources);
    }
}
