package com.xiaofuzi.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.rag.parser.DocumentParser;
import com.xiaofuzi.ai.rag.parser.DocumentParserFactory;
import com.xiaofuzi.ai.rag.parser.HeadingChunker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.xiaofuzi.ai.entity.KnowledgeDocument;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Qualifier;

import java.io.InputStream;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final int EMBEDDING_BATCH_SIZE = 25;

    private final VectorStore vectorStore;
    private final DocumentParserFactory parserFactory;
    private final KnowledgeDocumentMapper documentMapper;
    private final JdbcTemplate vectorJdbcTemplate;
    private final ChatModel chatModel;
    private final String schemaName;
    private final String vectorTableName;

    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .build();

    private final ObjectMapper objectMapper = new ObjectMapper();

    public KnowledgeBaseService(VectorStore vectorStore, DocumentParserFactory parserFactory,
            KnowledgeDocumentMapper documentMapper,
            @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate,
            ChatModel chatModel,
            @Value("${spring.ai.vectorstore.pgvector.schema-name}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String vectorTableName) {
        this.vectorStore = vectorStore;
        this.parserFactory = parserFactory;
        this.documentMapper = documentMapper;
        this.vectorJdbcTemplate = vectorJdbcTemplate;
        this.chatModel = chatModel;
        this.schemaName = schemaName;
        this.vectorTableName = vectorTableName;
    }

    private String qualifiedTable() {
        return schemaName + "." + vectorTableName;
    }

    public void ingestMultipartFile(MultipartFile file, String parserCategory,
            String category, String description) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (!parserFactory.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .documentName(fileName)
                .documentType(getFileExtension(fileName))
                .fileSize(file.getSize())
                .category(category)
                .description(description)
                .version("1.0")
                .status("active")
                .build();
        documentMapper.insert(doc);

        try (InputStream is = file.getInputStream()) {
            DocumentParser parser = parserFactory.getParser(fileName, parserCategory);
            List<Document> parsedDocs = parser.parse(is);

            Map<String, Object> sharedMeta = new HashMap<>();
            sharedMeta.put("source", fileName);
            sharedMeta.put("file_type", getFileExtension(fileName));
            sharedMeta.put("document_id", doc.getId().toString());
            ingestParsedDocuments(parsedDocs, sharedMeta, doc.getId());

            doc.setChunkCount(countChunksInLastIngest(parsedDocs));
            documentMapper.update(doc);

            logger.info("文件上传导入完成: {}, docId={}, 共 {} 个解析单元, 解析器: {}",
                    fileName, doc.getId(), parsedDocs.size(), parser.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("解析上传文件失败: {}, docId={}", fileName, doc.getId(), e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    private int countChunksInLastIngest(List<Document> parsedDocs) {
        int count = 0;
        for (Document parsedDoc : parsedDocs) {
            boolean skipSplit = Boolean.TRUE.equals(parsedDoc.getMetadata() != null
                    && Boolean.TRUE.equals(parsedDoc.getMetadata().get("skip_split")));
            if (skipSplit) {
                count += 1;
            } else {
                count += textSplitter.apply(List.of(parsedDoc)).size();
            }
        }
        return count;
    }

    public void ingestParsedDocuments(List<Document> parsedDocs, Map<String, Object> sharedMeta,
            Long documentId) {
        List<Document> allChunks = new ArrayList<>();

        for (Document parsedDoc : parsedDocs) {
            Map<String, Object> mergedMeta = new HashMap<>(sharedMeta);
            if (parsedDoc.getMetadata() != null) {
                mergedMeta.putAll(parsedDoc.getMetadata());
            }

            boolean skipSplit = Boolean.TRUE.equals(mergedMeta.get("skip_split"));
            mergedMeta.remove("skip_split");

            Document enrichedDoc = new Document(parsedDoc.getText(), mergedMeta);

            if (skipSplit) {
                Map<String, Object> chunkMeta = new HashMap<>(mergedMeta);
                chunkMeta.put("chunk_index", 0);
                chunkMeta.put("total_chunks", 1);
                Document singleChunk = new Document(enrichedDoc.getText(), chunkMeta);
                allChunks.add(singleChunk);
            } else {
                // 优先尝试标题结构切分（中文制度文档），检测不到标题时回退到 TokenTextSplitter
                List<Document> chunks = chunkWithHeadingFallback(enrichedDoc);

                for (int i = 0; i < chunks.size(); i++) {
                    Map<String, Object> chunkMeta = new HashMap<>(chunks.get(i).getMetadata());
                    chunkMeta.put("chunk_index", i);
                    chunkMeta.put("total_chunks", chunks.size());
                    chunks.get(i).getMetadata().putAll(chunkMeta);
                }

                allChunks.addAll(chunks);
            }
        }

        batchAdd(allChunks, documentId);
        logger.info("文档导入完成: {} 个解析单元 -> {} 个向量分块", parsedDocs.size(), allChunks.size());
    }

    /** 优先使用标题结构切分（中文制度文档），检测不到标题时回退到 TokenTextSplitter。 */
    private List<Document> chunkWithHeadingFallback(Document doc) {
        String contentType = (String) doc.getMetadata().getOrDefault("content_type", "unknown");
        List<Document> headingChunks = HeadingChunker.chunk(doc.getText(), contentType);

        boolean hasHeadings = headingChunks.size() > 1
                || (headingChunks.size() == 1
                        && headingChunks.get(0).getMetadata() != null
                        && headingChunks.get(0).getMetadata().get("heading_path") instanceof String s
                        && !s.isEmpty());

        if (hasHeadings) {
            for (Document hc : headingChunks) {
                hc.getMetadata().putAll(doc.getMetadata());
            }
            return headingChunks;
        }
        return textSplitter.apply(List.of(doc));
    }

    private void batchAdd(List<Document> documents, Long documentId) {
        if (documents == null || documents.isEmpty()) {
            return;
        }

        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            logger.debug("向量入库批次: {}-{}/{}", i, end, documents.size());
        }

        if (documentId != null) {
            String docIdStr = documentId.toString();
            String sql = String.format(
                    "UPDATE %s SET document_id = ? WHERE metadata->>'document_id' = ? AND document_id IS NULL",
                    qualifiedTable());
            vectorJdbcTemplate.update(sql, documentId, docIdStr);
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }


    public List<Document> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder().query(query).topK(topK).build());
        logger.info("知识库检索: query='{}', 命中 {} 条", query, results.size());
        return results;
    }

    public List<Document> searchWithThreshold(String query, int topK, double threshold) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK)
                        .similarityThreshold(threshold)
                        .build());
        logger.info("知识库检索(阈值={}): query='{}', 命中 {} 条", threshold, query, results.size());
        return results;
    }

    /**
     * 混合检索：向量语义检索 + 关键词模糊匹配（pg_trgm），通过 RRF（倒数排序融合）合并结果。
     *
     * RRF 公式：score(d) = 1/(k + rank_vector) + 1/(k + rank_keyword)
     * 其中 k=60（经典取值），rank 从 1 开始。两个检索结果的 rank 分别计算再加权求和。
     *
     * @return Map 包含:
     *         "documents"   -> List&lt;Document&gt; 合并排序后的文档列表（每篇附带 rrf_score 元数据）
     *         "vectorCount" -> int 向量检索命中数
     *         "keywordCount"-> int 关键词检索命中数
     *         "mergedCount" -> int 融合后最终条数
     */
    public Map<String, Object> hybridSearch(String query, int topK, double similarityThreshold) {
        // 1. 向量语义检索（用双倍 topK 扩大候选池，提高 RRF 融合质量）
        //    排除 FAQ 条目：FAQ 走前置精确匹配，不应混入文档检索结果
        List<Document> vectorResults = vectorStore.similaritySearch(
                SearchRequest.builder()
                        .query(query)
                        .topK(topK * 2)
                        .similarityThreshold(similarityThreshold)
                        .build());
        vectorResults = filterNonFaq(vectorResults);

        // 2. 关键词模糊检索（pg_trgm 三元组，中文适用：按字符三元组切分后匹配）
        List<Document> keywordResults = keywordSearch(query, topK * 2);
        keywordResults = filterNonFaq(keywordResults);

        // 3. RRF 融合排序
        List<Document> merged = rrfMerge(vectorResults, keywordResults, topK);

        // 4. LLM 重排序：候选数 > 3 时，用 LLM 对每个 chunk 打分(1-5)，取 top 3
        int originalMergedCount = merged.size();
        if (merged.size() > 3) {
            merged = llmRerank(query, merged, 3);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("documents", merged);
        result.put("vectorCount", vectorResults.size());
        result.put("keywordCount", keywordResults.size());
        result.put("mergedCount", originalMergedCount);

        logger.info("混合检索: query='{}', 向量命中={}, 关键词命中={}, RRF融合={}, LLM重排后={}",
                query, vectorResults.size(), keywordResults.size(), originalMergedCount, merged.size());
        return result;
    }

    /** 过滤掉标记为 FAQ 条目的向量（FAQ 走前置精确匹配，不应出现在文档检索结果中） */
    private List<Document> filterNonFaq(List<Document> docs) {
        return docs.stream()
                .filter(d -> d.getMetadata() == null
                        || !"faq_entry".equals(d.getMetadata().get("content_type")))
                .collect(Collectors.toList());
    }

    /**
     * 基于 pg_trgm 的 similarity() 函数做关键词模糊检索。
     * content % query 利用 GIN 索引快速筛选候选，similarity() 计算精确相似度用于排序。
     */
    private List<Document> keywordSearch(String query, int limit) {
        try {
            // FAQ 条目有 content_type='faq_entry' 标记，检索时排除，避免污染文档搜索结果
            String sql = String.format(
                    "SELECT id, content, metadata, similarity(content, ?) AS keyword_score "
                    + "FROM %s WHERE content %% ?"
                    + " AND (metadata->>'content_type' IS DISTINCT FROM 'faq_entry')"
                    + " ORDER BY keyword_score DESC LIMIT ?",
                    qualifiedTable());
            return vectorJdbcTemplate.query(sql,
                    ps -> {
                        ps.setString(1, query);
                        ps.setString(2, query);
                        ps.setInt(3, limit);
                    },
                    (rs, rowNum) -> rowToDocument(rs));
        } catch (Exception e) {
            logger.warn("关键词检索失败（pg_trgm 扩展可能未安装）: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 从 JDBC ResultSet 构建 Spring AI Document，解析 metadata JSON 并附加 keyword_score。
     */
    private Document rowToDocument(ResultSet rs) throws java.sql.SQLException {
        String id = rs.getString("id");
        String content = rs.getString("content");
        String metadataJson = rs.getString("metadata");
        Map<String, Object> meta = parseMetadataJson(metadataJson);
        meta.put("keyword_score", rs.getDouble("keyword_score"));
        return new Document(id, content, meta);
    }

    /**
     * 解析 metadata JSON 字段（postgres json 类型）为 Map。
     */
    private Map<String, Object> parseMetadataJson(String json) {
        if (json == null || json.isBlank()) {
            return new HashMap<>();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            logger.warn("元数据 JSON 解析失败: {}", e.getMessage());
            return new HashMap<>();
        }
    }

    /**
     * RRF（Reciprocal Rank Fusion）合并两个排序列表。
     * 对于只在某一侧出现的文档，仅用该侧的 rank 计算得分。
     * k=60 是信息检索领域的经典取值，确保排名靠前的文档获得显著更高的得分。
     */
    private List<Document> rrfMerge(List<Document> vectorResults,
                                     List<Document> keywordResults, int topK) {
        final double k = 60.0;
        Map<String, Document> docMap = new LinkedHashMap<>();

        // 登记向量检索排名（rank 从 1 开始）
        Map<String, Double> rrfScores = new LinkedHashMap<>();
        for (int i = 0; i < vectorResults.size(); i++) {
            String id = vectorResults.get(i).getId();
            rrfScores.put(id, 1.0 / (k + i + 1));
            docMap.putIfAbsent(id, vectorResults.get(i));
        }

        // 累加关键词检索排名
        for (int i = 0; i < keywordResults.size(); i++) {
            String id = keywordResults.get(i).getId();
            double ks = 1.0 / (k + i + 1);
            rrfScores.merge(id, ks, Double::sum);
            docMap.putIfAbsent(id, keywordResults.get(i));
        }

        // 按 RRF 总分降序排列，取 topK
        return rrfScores.entrySet().stream()
                .sorted(Map.Entry.<String, Double>comparingByValue().reversed())
                .limit(topK)
                .map(e -> {
                    Document doc = docMap.get(e.getKey());
                    doc.getMetadata().put("rrf_score", String.format("%.4f", e.getValue()));
                    return doc;
                })
                .collect(Collectors.toList());
    }

    /** 用 LLM 对候选 chunk 逐一打分（1-5 分），按得分降序取 topN。候选数 ≤ 3 时不触发，避免无谓延迟。 */
    private List<Document> llmRerank(String query, List<Document> candidates, int topN) {
        if (candidates.size() <= topN) {
            return candidates;
        }

        // 截断每个 chunk 到 500 字以内，节省 token
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < candidates.size(); i++) {
            String text = candidates.get(i).getText();
            if (text.length() > 500) {
                text = text.substring(0, 500) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(text).append("\n\n");
        }

        String systemPrompt = "你是一个检索相关性评估器。根据用户问题，对以下文档片段逐一评分（1-5分，5分最相关）。"
                + "只输出 JSON 数组，格式：[{\"id\":1,\"score\":5},...]。不要输出其他内容。";
        String userPrompt = "用户问题：" + query + "\n\n文档片段：\n" + sb.toString();

        try {
            org.springframework.ai.chat.model.ChatResponse response = chatModel.call(new Prompt(List.of(
                    new org.springframework.ai.chat.messages.SystemMessage(systemPrompt),
                    new org.springframework.ai.chat.messages.UserMessage(userPrompt))));
            String text = response.getResult() != null ? response.getResult().getOutput().getText() : "";

            // 从响应中解析分数：[{"id":1,"score":5},...]
            Map<Integer, Double> scores = new HashMap<>();
            Matcher m = Pattern.compile("\"id\"\\s*:\\s*(\\d+)\\s*,\\s*\"score\"\\s*:\\s*(\\d+)").matcher(text);
            while (m.find()) {
                int id = Integer.parseInt(m.group(1));
                double score = Double.parseDouble(m.group(2));
                scores.put(id, score);
            }

            if (scores.isEmpty()) {
                logger.warn("LLM 重排序无法解析分数，回退到原始顺序: {}", text);
                return candidates.subList(0, Math.min(topN, candidates.size()));
            }

            List<Document> reranked = new ArrayList<>(candidates);
            reranked.sort((a, b) -> {
                int ia = candidates.indexOf(a) + 1;
                int ib = candidates.indexOf(b) + 1;
                return Double.compare(scores.getOrDefault(ib, 0.0), scores.getOrDefault(ia, 0.0));
            });

            logger.info("LLM 重排序完成: {}/{} 候选 → top {}", reranked.size(), candidates.size(), topN);
            return reranked.subList(0, Math.min(topN, reranked.size()));
        } catch (Exception e) {
            logger.warn("LLM 重排序调用失败，回退到 RRF 原始排序: {}", e.getMessage());
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
    }

    public String formatAsContext(List<Document> documents, int maxLength) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("【知识库参考资料】\n");

        int currentLength = sb.length();
        for (int i = 0; i < documents.size(); i++) {
            Document doc = documents.get(i);
            String content = doc.getText();
            String source = doc.getMetadata() != null
                    ? (String) doc.getMetadata().getOrDefault("source", "未知来源")
                    : "未知来源";
            Object pageNumber = doc.getMetadata() != null
                    ? doc.getMetadata().get("start_page")
                    : null;
            Object clauseTitle = doc.getMetadata() != null
                    ? doc.getMetadata().get("clause_title")
                    : null;

            StringBuilder headerBuilder = new StringBuilder();
            headerBuilder.append(String.format("\n[参考%d] (来源: %s", i + 1, source));
            if (clauseTitle != null) {
                headerBuilder.append(", ").append(clauseTitle);
            }
            if (pageNumber != null) {
                headerBuilder.append(", 第").append(pageNumber).append("页");
            }
            headerBuilder.append(")\n");

            String entry = headerBuilder + content + "\n";

            if (currentLength + entry.length() > maxLength && i > 0) {
                sb.append("\n... (共").append(documents.size()).append("条，已截取前").append(i).append("条)");
                break;
            }
            sb.append(entry);
            currentLength += entry.length();
        }

        return sb.toString();
    }

    public Map<String, Object> getStats() {
        long documentCount = documentMapper.countActive();
        long chunkCount = documentMapper.sumChunks();
        List<Map<String, Object>> categoryRows = documentMapper.countByCategory();

        Map<String, Long> categories = new HashMap<>();
        for (Map<String, Object> row : categoryRows) {
            String cat = (String) row.get("category");
            Object cnt = row.get("cnt");
            if (cat != null) {
                categories.put(cat, cnt instanceof Number ? ((Number) cnt).longValue() : 0L);
            }
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("success", true);
        stats.put("documentCount", documentCount);
        stats.put("chunkCount", chunkCount);
        stats.put("categories", categories);
        return stats;
    }

    public void deleteByDocumentId(Long documentId) {
        try {
            String sql = String.format("DELETE FROM %s WHERE document_id = ?", qualifiedTable());
            int deleted = vectorJdbcTemplate.update(sql, documentId);
            logger.info("删除向量: document_id={}, table={}, 删除 {} 行", documentId, vectorTableName, deleted);
        } catch (Exception e) {
            logger.error("删除向量失败: document_id={}", documentId, e);
            throw new RuntimeException("删除向量失败: " + e.getMessage(), e);
        }
    }

    public int reingestDocument(Long documentId, MultipartFile file) {
        deleteByDocumentId(documentId);

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (!parserFactory.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        try (InputStream is = file.getInputStream()) {
            DocumentParser parser = parserFactory.getParser(fileName, null);
            List<Document> parsedDocs = parser.parse(is);

            Map<String, Object> sharedMeta = new HashMap<>();
            sharedMeta.put("source", fileName);
            sharedMeta.put("document_id", documentId.toString());
            int chunkCount = ingestParsedDocumentsCount(parsedDocs, sharedMeta, documentId);

            logger.info("文档重摄入完成: id={}, fileName={}, chunks={}", documentId, fileName, chunkCount);
            return chunkCount;
        } catch (Exception e) {
            logger.error("文档重摄入失败: id={}", documentId, e);
            throw new RuntimeException("文档重摄入失败: " + e.getMessage(), e);
        }
    }

    public int ingestParsedDocumentsCount(List<Document> parsedDocs, Map<String, Object> sharedMeta,
            Long documentId) {
        List<Document> allChunks = new ArrayList<>();
        for (Document parsedDoc : parsedDocs) {
            Map<String, Object> mergedMeta = new HashMap<>(sharedMeta);
            if (parsedDoc.getMetadata() != null) {
                mergedMeta.putAll(parsedDoc.getMetadata());
            }
            boolean skipSplit = Boolean.TRUE.equals(mergedMeta.get("skip_split"));
            mergedMeta.remove("skip_split");
            Document enrichedDoc = new Document(parsedDoc.getText(), mergedMeta);
            if (skipSplit) {
                Map<String, Object> chunkMeta = new HashMap<>(mergedMeta);
                chunkMeta.put("chunk_index", 0);
                chunkMeta.put("total_chunks", 1);
                allChunks.add(new Document(enrichedDoc.getText(), chunkMeta));
            } else {
                List<Document> chunks = chunkWithHeadingFallback(enrichedDoc);
                for (int i = 0; i < chunks.size(); i++) {
                    Map<String, Object> chunkMeta = new HashMap<>(chunks.get(i).getMetadata());
                    chunkMeta.put("chunk_index", i);
                    chunkMeta.put("total_chunks", chunks.size());
                    chunks.get(i).getMetadata().putAll(chunkMeta);
                }
                allChunks.addAll(chunks);
            }
        }
        batchAdd(allChunks, documentId);
        logger.info("文档解析并入库: {} 个解析单元 -> {} 个向量分块", parsedDocs.size(), allChunks.size());
        return allChunks.size();
    }
}
