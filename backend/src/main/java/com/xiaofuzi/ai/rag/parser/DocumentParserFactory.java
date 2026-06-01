package com.xiaofuzi.ai.rag.parser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;

@Component
public class DocumentParserFactory {

    private static final Logger logger = LoggerFactory.getLogger(DocumentParserFactory.class);

    private final List<DocumentParser> parsers;

    public DocumentParserFactory(List<DocumentParser> parsers) {
        this.parsers = parsers;
        this.parsers.sort(Comparator.comparingInt(DocumentParser::getPriority).reversed());
        logger.info("注册文档解析器: {} 个", parsers.size());
        for (DocumentParser parser : this.parsers) {
            logger.info("  - {} (优先级: {})", parser.getClass().getSimpleName(), parser.getPriority());
        }
    }

    public DocumentParser getParser(String fileName) {
        return parsers.stream()
                .filter(p -> p.supports(fileName))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("不支持的文件类型: " + fileName));
    }

    public DocumentParser getParser(String fileName, String category) {
        if (category != null && !category.isBlank()) {
            return parsers.stream()
                    .filter(p -> p.supports(fileName) && p.getClass().getSimpleName().toLowerCase().contains(category.toLowerCase()))
                    .findFirst()
                    .orElseGet(() -> getParser(fileName));
        }
        return getParser(fileName);
    }

    public boolean isSupported(String fileName) {
        return parsers.stream().anyMatch(p -> p.supports(fileName));
    }
}
