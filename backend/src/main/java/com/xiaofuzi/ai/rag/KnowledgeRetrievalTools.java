package com.xiaofuzi.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Agent RAG 工具类 - 知识检索相关工具
 *
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
        logger.info("RAG工具调用 - searchKnowledge: {}", query);
        List<Document> docs = knowledgeBaseService.search(query, 5);
        if (docs.isEmpty()) {
            return "未在知识库中找到相关信息。";
        }
        return knowledgeBaseService.formatAsContext(docs, 3000);
    }

    @Tool(description = "根据主题精确检索知识库中的参考资料，用于内容创作前搜集素材，返回最相关的知识片段")
    public String retrieveReferenceMaterials(
            @ToolParam(description = "创作主题或方向") String topic) {
        logger.info("RAG工具调用 - retrieveReferenceMaterials: {}", topic);
        List<Document> docs = knowledgeBaseService.search(topic, 10);
        if (docs.isEmpty()) {
            return "未在知识库中找到与「" + topic + "」相关的参考资料。";
        }
        return knowledgeBaseService.formatAsContext(docs, 5000);
    }

    @Tool(description = "在知识库中按关键词精确查找特定信息，适用于需要验证某个具体事实或数据时")
    public String preciseLookup(
            @ToolParam(description = "精确查找的关键词") String keyword) {
        logger.info("RAG工具调用 - preciseLookup: {}", keyword);
        List<Document> docs = knowledgeBaseService.searchWithThreshold(keyword, 3, 0.6);
        if (docs.isEmpty()) {
            return "未找到与「" + keyword + "」精确匹配的知识。";
        }
        return knowledgeBaseService.formatAsContext(docs, 2000);
    }
}