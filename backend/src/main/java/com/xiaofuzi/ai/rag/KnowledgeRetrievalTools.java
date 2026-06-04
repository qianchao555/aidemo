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
 * Agent RAG 工具类 - 知识检索相关工具
 */

@Component
public class KnowledgeRetrievalTools {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalTools.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrievalTools(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }



    @Tool(description = "从本地知识库中检索与查询相关的知识文档。适用场景：需要引用内部资料、专业知识、行业数据时调用")
    public String searchKnowledge(
            @ToolParam(description = "检索查询关键词或问题") String query) {
        logger.info("RAG工具调用 - searchKnowledge: query='{}', department='{}'", query, DepartmentContextHolder.get());
        List<Document> docs = doSearch(query, 5, 0.0);
        if (docs.isEmpty()) {
            return "未在知识库中找到相关信息。";
        }
        return knowledgeBaseService.formatAsContext(docs, 3000);
    }

    @SuppressWarnings("unchecked")
    private List<Document> doSearch(String query, int topK, double threshold) {
        String department = DepartmentContextHolder.get();
        Map<String, Object> result = knowledgeBaseService.hybridSearch(query, topK, threshold, department);
        Object docs = result.get("documents");
        if (docs instanceof List) {
            return (List<Document>) docs;
        }
        return Collections.emptyList();
    }


}
