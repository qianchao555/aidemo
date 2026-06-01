package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.rag.parser.DocumentParser;
import com.xiaofuzi.ai.rag.parser.DocumentParserFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
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

    private final TokenTextSplitter textSplitter = TokenTextSplitter.builder()
            .build();

    public KnowledgeBaseService(VectorStore vectorStore, DocumentParserFactory parserFactory) {
        this.vectorStore = vectorStore;
        this.parserFactory = parserFactory;
    }

    public void ingestFile(String filePath) {
        ingestFile(filePath, null);
    }

    public void ingestFile(String filePath, String parserCategory) {
        try {
            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                logger.warn("文件不存在: {}", filePath);
                return;
            }
            String fileName = path.getFileName().toString();

            if (parserFactory.isSupported(fileName)) {
                try (InputStream is = Files.newInputStream(path)) {
                    DocumentParser parser = parserFactory.getParser(fileName, parserCategory);
                    List<Document> parsedDocs = parser.parse(is);

                    Map<String, Object> sharedMeta = new HashMap<>();
                    sharedMeta.put("source", fileName);
                    sharedMeta.put("file_path", filePath);
                    ingestParsedDocuments(parsedDocs, sharedMeta);

                    logger.info("文档导入完成: {}, 共 {} 个解析单元, 解析器: {}",
                            fileName, parsedDocs.size(), parser.getClass().getSimpleName());
                }
            } else {
                String content = Files.readString(path);
                ingestText(content, Map.of("source", fileName, "file_path", filePath));
                logger.info("文档导入完成: {}, 大小: {} 字符", fileName, content.length());
            }
        } catch (Exception e) {
            logger.error("读取文件失败: {}", filePath, e);
            throw new RuntimeException("知识库文件读取失败: " + e.getMessage(), e);
        }
    }

    public void ingestMultipartFile(MultipartFile file) {
        ingestMultipartFile(file, null);
    }

    public void ingestMultipartFile(MultipartFile file, String parserCategory) {
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("文件名不能为空");
        }

        if (!parserFactory.isSupported(fileName)) {
            throw new IllegalArgumentException("不支持的文件类型: " + fileName);
        }

        try (InputStream is = file.getInputStream()) {
            DocumentParser parser = parserFactory.getParser(fileName, parserCategory);
            List<Document> parsedDocs = parser.parse(is);

            Map<String, Object> sharedMeta = new HashMap<>();
            sharedMeta.put("source", fileName);
            sharedMeta.put("file_type", getFileExtension(fileName));
            ingestParsedDocuments(parsedDocs, sharedMeta);

            logger.info("文件上传导入完成: {}, 共 {} 个解析单元, 解析器: {}",
                    fileName, parsedDocs.size(), parser.getClass().getSimpleName());
        } catch (Exception e) {
            logger.error("解析上传文件失败: {}", fileName, e);
            throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
        }
    }

    public void ingestParsedDocuments(List<Document> parsedDocs, Map<String, Object> sharedMeta) {
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

        batchAdd(allChunks);
        logger.info("文档导入完成: {} 个解析单元 -> {} 个向量分块", parsedDocs.size(), allChunks.size());
    }

    private void batchAdd(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int i = 0; i < documents.size(); i += EMBEDDING_BATCH_SIZE) {
            int end = Math.min(i + EMBEDDING_BATCH_SIZE, documents.size());
            List<Document> batch = documents.subList(i, end);
            vectorStore.add(batch);
            logger.debug("向量入库批次: {}-{}/{}", i, end, documents.size());
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1).toLowerCase() : "";
    }

    public void ingestText(String content, Map<String, Object> metadata) {
        if (content == null || content.isBlank()) {
            logger.warn("内容为空，跳过导入");
            return;
        }

        Document document = new Document(content, metadata != null ? metadata : Map.of());
        List<Document> chunks = textSplitter.apply(List.of(document));

        for (int i = 0; i < chunks.size(); i++) {
            Map<String, Object> chunkMeta = new HashMap<>(chunks.get(i).getMetadata());
            chunkMeta.put("chunk_index", i);
            chunkMeta.put("total_chunks", chunks.size());
            chunks.get(i).getMetadata().putAll(chunkMeta);
        }

        batchAdd(chunks);
        logger.info("文本导入完成: {} 个分块", chunks.size());
    }

    /**
     * TokenTextSplitter
     * 按照固定token长度切分文本，适用于需要精确控制输入长度的场景。
     */
    public void ingestDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        List<Document> allChunks = new ArrayList<>();
        for (Document doc : documents) {
            allChunks.addAll(textSplitter.apply(List.of(doc)));
        }
        batchAdd(allChunks);
        logger.info("批量文档导入完成: {} 篇文档 -> {} 个分块", documents.size(), allChunks.size());
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

    public long countDocuments() {
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder().query("count").topK(1).build()).size();
        } catch (Exception e) {
            return 0;
        }
    }
}
