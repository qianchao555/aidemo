package com.xiaofuzi.ai.controller;

import com.xiaofuzi.ai.annotation.RequireRole;
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

    @RequireRole(com.xiaofuzi.ai.util.AppConstants.ROLE_ADMIN)
    @PostMapping("/upload")
    public Result<Map<String, Object>> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "parserCategory", required = false) String parserCategory,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "department", required = false) String department,
            @RequestParam(value = "parentDocumentId", required = false) Long parentDocumentId) {
        if (file.isEmpty()) {
            return Result.error("上传文件不能为空");
        }

        knowledgeBaseService.ingestMultipartFile(file, parserCategory, category, description, department, parentDocumentId);
        logger.info("文件上传并摄入完成: {}", file.getOriginalFilename());

        return Result.success(Map.of(
                "success", true,
                "message", "文件上传并摄入向量库成功"
        ));
    }

    private static final int SEARCH_CONTEXT_MAX_LENGTH = 5000;

    @GetMapping("/search")
    public Result<Map<String, Object>> searchKnowledge(
            @RequestParam String query,
            @RequestParam(defaultValue = "5") int topK) {
        List<Document> results = knowledgeBaseService.search(query, topK);
        String formatted = knowledgeBaseService.formatAsContext(results, SEARCH_CONTEXT_MAX_LENGTH);

        return Result.success(Map.of(
                "success", true,
                "query", query,
                "hitCount", results.size(),
                "results", formatted
        ));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(knowledgeBaseService.getStats());
    }


}
