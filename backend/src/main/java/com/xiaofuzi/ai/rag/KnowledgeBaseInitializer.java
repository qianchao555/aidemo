package com.xiaofuzi.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * 知识库初始化器 - 在应用启动时自动导入示例知识文档到知识库，便于演示和测试
 *
 */
@Component
public class KnowledgeBaseInitializer implements CommandLineRunner {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeBaseInitializer.class);

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeBaseInitializer(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    @Override
    public void run(String... args) throws Exception {
        logger.info("之前已经嵌入到向量库了，不用重复操作了。");
//        try {
//            ClassPathResource resource = new ClassPathResource("knowledge/RAG-Reference.txt");
//            if (!resource.exists()) {
//                logger.info("示例知识文档不存在，跳过初始导入");
//                return;
//            }
//
//            String content = resource.getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
//            knowledgeBaseService.ingestText(content,
//                    Map.of("source", "RAG-Reference.txt", "type", "system_sample"));
//            logger.info("示例知识文档已导入知识库");
//        } catch (Exception e) {
//            logger.warn("示例知识文档导入失败（PostgreSQL/pgvector 可能未启动）: {}", e.getMessage());
//        }
    }
}