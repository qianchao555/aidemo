package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.entity.KnowledgeDocument;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge-base/documents")
public class KnowledgeDocumentController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeDocumentController.class);

    private final KnowledgeDocumentMapper documentMapper;
    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeDocumentController(KnowledgeDocumentMapper documentMapper,
                                        KnowledgeBaseService knowledgeBaseService) {
        this.documentMapper = documentMapper;
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /**
     * 获取文档列表，支持按 category / status 过滤。
     */
    @GetMapping
    public Result<List<KnowledgeDocument>> listDocuments(
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "active") String status) {
        List<KnowledgeDocument> list;
        if (category != null && !category.isBlank()) {
            list = documentMapper.findByCategory(category);
        } else {
            list = documentMapper.findAllActive();
        }
        return Result.success(list);
    }

    /**
     * 获取单个文档详情（含元信息、版本、分块数等）。
     */
    @GetMapping("/{id}")
    public Result<KnowledgeDocument> getDocument(@PathVariable Long id) {
        KnowledgeDocument doc = documentMapper.findById(id);
        if (doc == null) {
            return Result.error("文档不存在");
        }
        return Result.success(doc);
    }

    /**
     * 增量更新文档：删旧向量 → 解析新文件 → 切分 → 写入新向量 → 更新元信息。
     * 这是增量更新机制的核心入口。
     */
    @PostMapping("/{id}/reingest")
    @Transactional
    public Result<Map<String, Object>> reingestDocument(
            @PathVariable Long id,
            @RequestParam("file") MultipartFile file) {
        KnowledgeDocument doc = documentMapper.findById(id);
        if (doc == null) {
            return Result.error("文档不存在");
        }

        int chunkCount = knowledgeBaseService.reingestDocument(id, file);

        // 更新文档元信息：版本号和分块数
        doc.setChunkCount(chunkCount);
        String oldVersion = doc.getVersion() != null ? doc.getVersion() : "1.0";
        String[] parts = oldVersion.split("\\.");
        int minor = Integer.parseInt(parts[parts.length - 1]) + 1;
        doc.setVersion(parts[0] + "." + minor);
        documentMapper.update(doc);

        logger.info("文档增量更新完成: id={}, version={}, chunks={}", id, doc.getVersion(), chunkCount);

        return Result.success(Map.of(
                "success", true,
                "message", "文档增量更新完成",
                "documentId", id,
                "version", doc.getVersion(),
                "chunkCount", chunkCount
        ));
    }

    /**
     * 删除文档：按 document_id 删全部向量 + 软删除元信息记录。
     */
    @DeleteMapping("/{id}")
    public Result<Map<String, Object>> deleteDocument(@PathVariable Long id) {
        KnowledgeDocument doc = documentMapper.findById(id);
        if (doc == null) {
            return Result.error("文档不存在");
        }

        knowledgeBaseService.deleteByDocumentId(id);
        documentMapper.softDelete(id);

        logger.info("文档删除完成: id={}, name={}", id, doc.getDocumentName());

        return Result.success(Map.of(
                "success", true,
                "message", "文档已删除",
                "documentId", id
        ));
    }
}
