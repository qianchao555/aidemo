package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/knowledge-base")
public class KnowledgeBaseController {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @PostMapping("/ingest")
    public Result<Map<String, Object>> ingestKnowledge(@RequestBody Map<String, Object> request) {
        String content = (String) request.get("content");
        if (content == null || content.isBlank()) {
            return Result.error("内容不能为空");
        }

        @SuppressWarnings("unchecked")
        Map<String, Object> metadata = (Map<String, Object>) request.getOrDefault("metadata", Map.of());

        knowledgeBaseService.ingestText(content, metadata);
        logger.info("API知识摄入完成");

        return Result.success(Map.of("success", true, "message", "知识摄入成功"));
    }

    @PostMapping("/ingest-file")
    public Result<Map<String, Object>> ingestFile(@RequestBody Map<String, String> request) {
        String filePath = request.get("filePath");
        if (filePath == null || filePath.isBlank()) {
            return Result.error("文件路径不能为空");
        }

        String parserCategory = request.get("parserCategory");
        knowledgeBaseService.ingestFile(filePath, parserCategory);
        logger.info("文件知识摄入完成: {}", filePath);

        return Result.success(Map.of("success", true, "message", "文件摄入向量库成功", "filePath", filePath));
    }

    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parserCategory", required = false) String parserCategory) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        String fileName = file.getOriginalFilename();
        knowledgeBaseService.ingestMultipartFile(file, parserCategory);
        logger.info("文件上传并摄入完成: {}", fileName);

        return Result.success(Map.of(
                "success", true,
                "message", "文件上传并摄入向量库成功",
                "fileName", fileName != null ? fileName : "unknown"
        ));
    }

    @GetMapping("/search")
    public Result<Map<String, Object>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = knowledgeBaseService.search(query, topK);
        String formatted = knowledgeBaseService.formatAsContext(results, 5000);

        return Result.success(Map.of(
                "success", true,
                "query", query,
                "hitCount", results.size(),
                "results", formatted
        ));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        long docCount = knowledgeBaseService.countDocuments();
        return Result.success(Map.of("success", true, "documentCount", docCount));
    }


}
