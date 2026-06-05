package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.context.DepartmentContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent RAG 知识检索工具类。
 *
 * <p>提供 searchKnowledge 工具供 ReAct Agent 调用，并在检索结果质量不足时
 * 通过 ThreadLocal 向 RagQaMessageHook 传递状态，实现程序化兜底拦截。
 */
@Component
public class KnowledgeRetrievalTools {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalTools.class);

    /** ★ 检索质量状态枚举 */
    public enum QualityStatus { PASSED, EMPTY, LOW_QUALITY }

    /** ★ 最后一次检索的质量状态（ThreadLocal，线程安全） */
    private static final ThreadLocal<QualityStatus> lastQualityStatus =
            ThreadLocal.withInitial(() -> QualityStatus.PASSED);

    /** ★ 最后一次检索的质量评分详情（ThreadLocal） */
    private static final ThreadLocal<QualityScore> lastQualityScore = new ThreadLocal<>();

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrievalTools(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** ★ 供 RagQaMessageHook 读取最后一次检索质量状态 */
    public static QualityStatus getLastQualityStatus() {
        return lastQualityStatus.get();
    }

    /** ★ 供 RagQaMessageHook 清理状态，避免跨轮污染 */
    public static void clearQualityStatus() {
        lastQualityStatus.remove();
        lastQualityScore.remove();
    }

    @Tool(description = "从本地知识库中检索与查询相关的知识文档。适用场景：需要引用内部资料、专业知识、行业数据时调用")
    public String searchKnowledge(
            @ToolParam(description = "检索查询关键词或问题") String query) {
        logger.info("RAG工具调用 - searchKnowledge: query='{}', department='{}'",
                query, DepartmentContextHolder.get());

        // ★ 入口处重置状态，避免上轮残留
        lastQualityStatus.set(QualityStatus.PASSED);
        lastQualityScore.remove();

        List<Document> docs = doSearch(query, 5, 0.0);

        if (docs.isEmpty()) {
            // ★ 空结果兜底：无任何文档命中
            lastQualityStatus.set(QualityStatus.EMPTY);
            return "【系统提示】知识库中未找到任何与「" + query + "」相关的文档内容。"
                    + "你必须直接告知用户未找到相关信息，禁止编造任何内容。"
                    + "建议用户联系 HR 部门获取帮助。";
        }

        // ★ 从 doSearch 中已写入的 ThreadLocal 读取质量状态
        QualityStatus status = lastQualityStatus.get();
        if (status == QualityStatus.LOW_QUALITY) {
            QualityScore score = lastQualityScore.get();
            String scoreInfo = score != null
                    ? "，最高综合分仅 " + String.format("%.1f", score.maxCombined())
                    : "";
            return "【系统提示】检索到的文档内容与用户问题「" + query + "」相关性不足"
                    + scoreInfo + "。"
                    + "你必须直接告知用户未找到相关内容，禁止据此编造任何回答。"
                    + "建议用户：1) 更换关键词重新提问；2) 联系 HR 部门获取帮助。";
        }

        // ★ 有效召回：正常格式化上下文
        return knowledgeBaseService.formatAsContext(docs, 3000);
    }

    @SuppressWarnings("unchecked")
    private List<Document> doSearch(String query, int topK, double threshold) {
        String department = DepartmentContextHolder.get();
        Map<String, Object> result = knowledgeBaseService.hybridSearch(
                query, topK, threshold, department, null);

        // ★ 读取 hybridSearch 返回的质量状态，写入 ThreadLocal
        String qs = (String) result.get("qualityStatus");
        if (qs != null) {
            lastQualityStatus.set(QualityStatus.valueOf(qs));
            Object scoreObj = result.get("qualityScore");
            if (scoreObj instanceof QualityScore qScore) {
                lastQualityScore.set(qScore);
            }
        }

        Object docs = result.get("documents");
        if (docs instanceof List) {
            return (List<Document>) docs;
        }
        return Collections.emptyList();
    }
}
