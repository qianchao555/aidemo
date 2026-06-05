package com.xiaofuzi.ai.rag;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.rag.parser.DocumentParser;
import com.xiaofuzi.ai.rag.parser.DocumentParserFactory;
import com.xiaofuzi.ai.rag.parser.FaqChunker;
import com.xiaofuzi.ai.rag.parser.HeadingChunker;
import com.xiaofuzi.ai.rag.parser.ProcessChunker;
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

import com.xiaofuzi.ai.entity.DocumentGroup;
import com.xiaofuzi.ai.entity.KnowledgeDocument;
import com.xiaofuzi.ai.mapper.DocumentGroupMapper;
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
    private final DocumentGroupMapper documentGroupMapper;
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
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String vectorTableName,
            DocumentGroupMapper documentGroupMapper) {
        this.vectorStore = vectorStore;
        this.parserFactory = parserFactory;
        this.documentMapper = documentMapper;
        this.vectorJdbcTemplate = vectorJdbcTemplate;
        this.chatModel = chatModel;
        this.schemaName = schemaName;
        this.vectorTableName = vectorTableName;
        this.documentGroupMapper = documentGroupMapper;
    }

    private String qualifiedTable() {
        return schemaName + "." + vectorTableName;
    }

    public void ingestMultipartFile(MultipartFile file, String parserCategory,
            String category, String description, String department, Long parentDocumentId) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (!parserFactory.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        DocumentGroup group;
        if (parentDocumentId != null) {
            KnowledgeDocument parent = documentMapper.findById(parentDocumentId);
            if (parent == null) {
                throw new IllegalArgumentException("父文档不存在: " + parentDocumentId);
            }
            if (parent.getGroupId() == null) {
                throw new IllegalArgumentException("父文档无关联文档组");
            }
            group = documentGroupMapper.findById(parent.getGroupId());
            if (group == null) {
                throw new IllegalArgumentException("文档组不存在");
            }
        } else {
            group = DocumentGroup.builder()
                    .name(fileName)
                    .department(department)
                    .status("active")
                    .build();
            documentGroupMapper.insert(group);
        }

        try (InputStream is = file.getInputStream()) {
            DocumentParser parser = parserFactory.getParser(fileName, parserCategory);
            List<Document> parsedDocs = parser.parse(is);

            String version = extractVersion(parsedDocs);

            KnowledgeDocument doc = KnowledgeDocument.builder()
                    .documentName(fileName)
                    .documentType(getFileExtension(fileName))
                    .fileSize(file.getSize())
                    .category(category)
                    .description(description)
                    .version(version)
                    .status("active")
                    .department(department)
                    .groupId(group.getId())
                    .isLatest(true)
                    .build();
            documentMapper.insert(doc);

            Map<String, Object> sharedMeta = new HashMap<>();
            sharedMeta.put("source", fileName);
            sharedMeta.put("file_type", getFileExtension(fileName));
            sharedMeta.put("document_id", doc.getId().toString());
            sharedMeta.put("group_id", String.valueOf(group.getId()));
            sharedMeta.put("version", version);
            sharedMeta.put("is_latest", "true");
            if (category != null && !category.isBlank()) {
                sharedMeta.put("document_category", category);
            }
            if (department != null && !department.isBlank()) {
                sharedMeta.put("department", department);
            }
            ingestParsedDocuments(parsedDocs, sharedMeta, doc.getId());

            doc.setChunkCount(countChunksInLastIngest(parsedDocs, sharedMeta));

            if (parentDocumentId != null) {
                documentMapper.markNotLatestByGroup(group.getId());
                doc.setIsLatest(true);
            }

            documentMapper.update(doc);
            documentGroupMapper.updateLatestDocument(group.getId(), doc.getId());

            logger.info("文件上传导入完成: {}, docId={}, groupId={}, version={}, 共 {} 个解析单元",
                    fileName, doc.getId(), group.getId(), version, parsedDocs.size());
        } catch (Exception e) {
            logger.error("解析上传文件失败: {}, docId 未知", fileName, e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    private int countChunksInLastIngest(List<Document> parsedDocs, Map<String, Object> sharedMeta) {
        int count = 0;
        for (Document parsedDoc : parsedDocs) {
            Map<String, Object> mergedMeta = new HashMap<>(sharedMeta);
            if (parsedDoc.getMetadata() != null) {
                mergedMeta.putAll(parsedDoc.getMetadata());
            }
            boolean skipSplit = Boolean.TRUE.equals(mergedMeta.get("skip_split"));
            if (skipSplit) {
                count += 1;
            } else {
                count += chunkSmart(new Document(parsedDoc.getText(), mergedMeta)).size();
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
                // 策略链切分：依次尝试标题/流程/FAQ 三种专用切分器，按结构命中率自动选择最优
                List<Document> chunks = chunkSmart(enrichedDoc);

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

    /**
     * 智能切分策略，按文档类别精确路由 + 自动检测兜底。
     *
     * <p>路由规则：
     * <ol>
     *   <li>有 category 元数据时，直接定位对应切分器，不遍历尝试</li>
     *   <li>category 不明确或无命中时，走自动检测链</li>
     *   <li>自动检测也失败时，回退到 TokenTextSplitter</li>
     * </ol>
     *
     * <p>category 取值映射：
     * <ul>
     *   <li>制度 / 政策 / 规范 / 手册 → {@link HeadingChunker}</li>
     *   <li>流程 / 指引 / 办理 → {@link ProcessChunker}</li>
     *   <li>FAQ / 问答 / 常见问题 → {@link FaqChunker}</li>
     *   <li>auto / 空 / 其他 → 自动检测</li>
     * </ul>
     */
    private List<Document> chunkSmart(Document doc) {
        String contentType = (String) doc.getMetadata().getOrDefault("content_type", "unknown");
        String category = (String) doc.getMetadata().getOrDefault("document_category", "");
        String text = doc.getText();

        // 第一步：根据文档类别精确路由（只有 category 明确指定时）
        if (category != null && !category.isBlank()) {
            List<Document> routed = routeByCategory(text, contentType, category);
            if (routed != null) {
                for (Document c : routed) {
                    c.getMetadata().putAll(doc.getMetadata());
                }
                return routed;
            }
        }

        // 第二步：category 未指定或指定类别未命中 → 自动检测
        // @formatter:off
        record Strategy(String name, java.util.function.BiFunction<String, String, List<Document>> fn, String marker) {}
        // @formatter:on
        Strategy[] strategies = {
                new Strategy("HeadingChunker", HeadingChunker::chunk, "heading_path"),
                new Strategy("ProcessChunker", ProcessChunker::chunk, "step_title"),
                new Strategy("FaqChunker",     FaqChunker::chunk,     "qa_question"),
        };

        for (Strategy s : strategies) {
            List<Document> chunks = s.fn.apply(text, contentType);
            if (chunks.size() > 1
                    || (chunks.size() == 1 && nonBlankMeta(chunks.get(0), s.marker))) {
                for (Document c : chunks) {
                    c.getMetadata().putAll(doc.getMetadata());
                }
                logger.debug("切分策略自动检测命中: {} → {} 个 chunk (文档 {} 字符)",
                        s.name, chunks.size(), text.length());
                return chunks;
            }
        }

        // 第三步：兜底
        return textSplitter.apply(List.of(doc));
    }

    /**
     * 根据文档类别精确路由到对应切分器。
     * 返回 null 表示指定类别的切分器未命中，由调用方回退到自动检测。
     */
    private List<Document> routeByCategory(String text, String contentType, String category) {
        String lower = category.toLowerCase();

        // 制度类 → HeadingChunker
        if (lower.contains("制度") || lower.contains("政策") || lower.contains("规范")
                || lower.contains("手册") || lower.contains("条例") || lower.contains("policy")) {
            List<Document> chunks = HeadingChunker.chunk(text, contentType);
            if (chunks.size() > 1 || (chunks.size() == 1 && nonBlankMeta(chunks.get(0), "heading_path"))) {
                logger.info("切分策略(类别路由): 制度类 → HeadingChunker → {} chunk", chunks.size());
                return chunks;
            }
            // 标记为制度但切分器未命中：该文档标题格式不规范，降级为自动检测
            logger.warn("文档标记为制度类但 HeadingChunker 未检测到标题结构，回退自动检测");
            return null;
        }

        // 流程类 → ProcessChunker
        if (lower.contains("流程") || lower.contains("指引") || lower.contains("办理")
                || lower.contains("审批") || lower.contains("process")) {
            List<Document> chunks = ProcessChunker.chunk(text, contentType);
            if (chunks.size() > 1 || (chunks.size() == 1 && nonBlankMeta(chunks.get(0), "step_title"))) {
                logger.info("切分策略(类别路由): 流程类 → ProcessChunker → {} chunk", chunks.size());
                return chunks;
            }
            logger.warn("文档标记为流程类但 ProcessChunker 未检测到步骤结构，回退自动检测");
            return null;
        }

        // FAQ 类 → FaqChunker
        if (lower.contains("faq") || lower.contains("问答") || lower.contains("常见问题")
                || lower.contains("qa")) {
            List<Document> chunks = FaqChunker.chunk(text, contentType);
            if (chunks.size() > 1 || (chunks.size() == 1 && nonBlankMeta(chunks.get(0), "qa_question"))) {
                logger.info("切分策略(类别路由): FAQ类 → FaqChunker → {} chunk", chunks.size());
                return chunks;
            }
            logger.warn("文档标记为 FAQ 类但 FaqChunker 未检测到问答结构，回退自动检测");
            return null;
        }

        // 无法识别的类别 → 自动检测
        return null;
    }

    /** 检查 Document 元数据中指定 key 是否存在非空字符串值。 */
    private static boolean nonBlankMeta(Document doc, String key) {
        Object val = doc.getMetadata() != null ? doc.getMetadata().get(key) : null;
        return val instanceof String s && !s.isBlank();
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
    public Map<String, Object> hybridSearch(String query, int topK, double similarityThreshold, String department) {
        // 1. 向量语义检索（用双倍 topK 扩大候选池，提高 RRF 融合质量）
        //    排除 FAQ 条目：FAQ 走前置精确匹配，不应混入文档检索结果
        //    部门过滤：指定部门时在向量检索阶段按 metadata.department 过滤
        SearchRequest.Builder vectorReq = SearchRequest.builder()
                .query(query)
                .topK(topK * 2)
                .similarityThreshold(similarityThreshold);
        if (department != null && !department.isBlank()) {
            vectorReq.filterExpression("department == '" + department.replace("'", "''") + "'");
        }
        List<Document> vectorResults = vectorStore.similaritySearch(vectorReq.build());
        vectorResults = filterNonFaq(vectorResults);

        // 2. 关键词模糊检索（pg_trgm 三元组，中文适用：按字符三元组切分后匹配）
        List<Document> keywordResults = keywordSearch(query, topK * 2, department);
        keywordResults = filterNonFaq(keywordResults);

        // 3. RRF 融合排序
        List<Document> merged = rrfMerge(vectorResults, keywordResults, topK);

        // 4. LLM 重排序：候选数 > 3 时，用 LLM 对每个 chunk 打分(1-5)，取 top 3
        int originalMergedCount = merged.size();
        if (merged.size() > 3) {
            merged = llmRerank(query, merged, 3);
        }

        // 5. 去重：同文档同章节/同步骤的 chunk 只保留排名最高的那条
        merged = deduplicateByStructure(merged);

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
     * 按文档 + 结构路径去重，同名文档同一章节/同一步骤只保留排名最高的那条。
     * 去重 key = source 文档名 + heading_path（制度文档）或 step_title（流程文档）。
     * 无结构标记的 chunk 不做去重，保留全部。
     */
    private List<Document> deduplicateByStructure(List<Document> docs) {
        Map<String, Document> seen = new LinkedHashMap<>();
        for (Document doc : docs) {
            String source = doc.getMetadata() != null
                    ? (String) doc.getMetadata().getOrDefault("source", "") : "";

            // 制度文档用 heading_path，流程文档用 step_title
            String structKey = "";
            if (doc.getMetadata() != null) {
                String headingPath = (String) doc.getMetadata().get("heading_path");
                String stepTitle = (String) doc.getMetadata().get("step_title");
                if (headingPath != null && !headingPath.isBlank()) {
                    structKey = headingPath;
                } else if (stepTitle != null && !stepTitle.isBlank()) {
                    structKey = stepTitle;
                }
            }

            String dedupKey = source + "|" + structKey;

            // 无结构标记的 chunk 不参与去重，直接保留
            if (structKey.isEmpty()) {
                continue;
            }

            // 同 key 只保留第一个（排名最高）
            seen.putIfAbsent(dedupKey, doc);
        }

        if (seen.isEmpty()) return docs;

        // 重建列表：保留去重后的结构化 chunk + 所有无结构 chunk
        List<Document> result = new ArrayList<>(seen.values());
        for (Document doc : docs) {
            String source = doc.getMetadata() != null
                    ? (String) doc.getMetadata().getOrDefault("source", "") : "";
            String headingPath = doc.getMetadata() != null
                    ? (String) doc.getMetadata().get("heading_path") : null;
            String stepTitle = doc.getMetadata() != null
                    ? (String) doc.getMetadata().get("step_title") : null;
            boolean hasStruct = (headingPath != null && !headingPath.isBlank())
                    || (stepTitle != null && !stepTitle.isBlank());
            if (!hasStruct) {
                result.add(doc);
            }
        }
        logger.info("引用去重: {} 条 → {} 条（去除同文档同结构重复）", docs.size(), result.size());
        return result;
    }

    /**
     * 基于 pg_trgm 的 similarity() 函数做关键词模糊检索。
     * content % query 利用 GIN 索引快速筛选候选，similarity() 计算精确相似度用于排序。
     */
    private List<Document> keywordSearch(String query, int limit) {
        return keywordSearch(query, limit, null);
    }

    private List<Document> keywordSearch(String query, int limit, String department) {
        try {
            StringBuilder sqlBuilder = new StringBuilder();
            sqlBuilder.append(String.format(
                "SELECT id, content, metadata, similarity(content, ?) AS keyword_score "
                + "FROM %s WHERE content %% ?"
                + " AND (metadata->>'content_type' IS DISTINCT FROM 'faq_entry')",
                qualifiedTable()));

            if (department != null && !department.isBlank()) {
                sqlBuilder.append(" AND (metadata->>'department') = ?");
            }

            sqlBuilder.append(" ORDER BY keyword_score DESC LIMIT ?");

            return vectorJdbcTemplate.query(sqlBuilder.toString(),
                    ps -> {
                        int idx = 1;
                        ps.setString(idx++, query);
                        ps.setString(idx++, query);
                        if (department != null && !department.isBlank()) {
                            ps.setString(idx++, department);
                        }
                        ps.setInt(idx, limit);
                    },
                    (rs, rowNum) -> rowToDocument(rs));
        } catch (Exception e) {
            logger.warn("关键词检索失败: {}", e.getMessage());
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
            Map<String, Object> meta = doc.getMetadata();
            String content = doc.getText();
            String source = meta != null ? (String) meta.getOrDefault("source", "未知来源") : "未知来源";

            // 构建结构化参考头部：制度文档显示章节路径，流程文档显示步骤/角色/时限/材料
            StringBuilder header = new StringBuilder();
            header.append(String.format("\n[参考%d] 来源: %s", i + 1, source));

            if (meta != null) {
                // 制度文档：章节路径
                String headingPath = (String) meta.get("heading_path");
                if (headingPath != null && !headingPath.isBlank()) {
                    header.append(" > ").append(headingPath);
                }
                // 流程文档：步骤标题
                String stepTitle = (String) meta.get("step_title");
                if (stepTitle != null && !stepTitle.isBlank()) {
                    header.append(" | ").append(stepTitle);
                }
                // 流程文档：角色 / 时限 / 材料
                appendIfPresent(header, meta, "step_role", " | 角色：");
                appendIfPresent(header, meta, "step_time_limit", " | 时限：");
                appendIfPresent(header, meta, "step_materials", " | 材料：");
                appendIfPresent(header, meta, "step_scope", " | 适用范围：");
                // FAQ
                String qaQuestion = (String) meta.get("qa_question");
                if (qaQuestion != null && !qaQuestion.isBlank()) {
                    header.append(" | FAQ：").append(qaQuestion);
                }
            }
            header.append("\n");

            String entry = header.toString() + content + "\n";

            if (currentLength + entry.length() > maxLength && i > 0) {
                sb.append("\n... (共").append(documents.size()).append("条，已截取前").append(i).append("条)");
                break;
            }
            sb.append(entry);
            currentLength += entry.length();
        }

        return sb.toString();
    }

    private static void appendIfPresent(StringBuilder sb, Map<String, Object> meta, String key, String prefix) {
        String val = (String) meta.get(key);
        if (val != null && !val.isBlank()) {
            sb.append(prefix).append(val);
        }
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
        KnowledgeDocument existing = documentMapper.findById(documentId);
        String category = existing != null ? existing.getCategory() : null;
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
            sharedMeta.put("file_type", getFileExtension(fileName));
            sharedMeta.put("document_id", documentId.toString());
            if (category != null && !category.isBlank()) {
                sharedMeta.put("document_category", category);
            }
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
                List<Document> chunks = chunkSmart(enrichedDoc);
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

    /**
     * 从文档文本内容中自动提取版本号。
     * 策略：正则优先匹配常见中文制度文档的年份模式，提取失败时回退为当前年份。
     */
    private String extractVersion(List<Document> parsedDocs) {
        String fullText = parsedDocs.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n"));

        // 匹配文号中的年份，如：〔2026〕、(2025)、[2026]
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("[〔\\(\\[]\\s*(20\\d{2})\\s*[〕\\)\\]]").matcher(fullText);
        if (m.find()) {
            return m.group(1);
        }

        // 匹配发布日期，如：2026年1月1日、2026-01-01
        m = java.util.regex.Pattern.compile("(20\\d{2})\\s*年|(20\\d{2})[-/]\\d{1,2}[-/]\\d{1,2}").matcher(fullText);
        if (m.find()) {
            return m.group(1) != null ? m.group(1) : m.group(2);
        }

        // 匹配标题中的版本号：v2026, V2025
        m = java.util.regex.Pattern.compile("[vV](20\\d{2})").matcher(fullText);
        if (m.find()) {
            return m.group(1);
        }

        // 回退：当前年份
        return String.valueOf(java.time.Year.now().getValue());
    }
}
