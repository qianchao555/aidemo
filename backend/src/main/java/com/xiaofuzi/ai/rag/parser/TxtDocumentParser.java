package com.xiaofuzi.ai.rag.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class TxtDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(TxtDocumentParser.class);

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase().endsWith(".txt");
    }

    @Override
    public List<Document> parse(InputStream inputStream) throws Exception {
        logger.info("开始解析 TXT 文档");

        String content = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))
                .lines()
                .collect(Collectors.joining("\n"));

        logger.info("TXT 文档解析完成，内容长度: {} 字符", content.length());

        Document doc = new Document(content, Map.of("content_type", com.xiaofuzi.ai.util.AppConstants.CONTENT_TYPE_TXT));
        return List.of(doc);
    }
}
