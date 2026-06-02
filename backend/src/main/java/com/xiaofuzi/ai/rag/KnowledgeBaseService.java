package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.rag.parser.DocumentParser;
import com.xiaofuzi.ai.rag.parser.DocumentParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class KnowledgeBaseService {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseService.class);

    private static final int EMBEDDING_BATCH_SIZE = 25;

    private final VectorStore vectorStore;
    private final DocumentParserFactory parserFactory;
    private final KnowledgeDocumentMapper documentMapper;
    private final JdbcTemplate vectorJdbcTemplate;
    private final String schemaName;
    private final String vectorTableName;

    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .build();

    public KnowledgeBaseService(VectorStore vectorStore, DocumentParserFactory parserFactory,
            KnowledgeDocumentMapper documentMapper,
            @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate,
            @Value("${spring.ai.vectorstore.pgvector.schema-name}") String schemaName,
            @Value("${spring.ai.vectorstore.pgvector.table-name}") String vectorTableName) {
        this.vectorStore = vectorStore;
        this.parserFactory = parserFactory;
        this.documentMapper = documentMapper;
        this.vectorJdbcTemplate = vectorJdbcTemplate;
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
                List<Document> chunks = textSplitter.apply(List.of(enrichedDoc));

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
                List<Document> chunks = textSplitter.apply(List.of(enrichedDoc));
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
