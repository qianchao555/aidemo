package com.xiaofuzi.ai.rag.parser;

import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * 通用文档解析器，基于 Apache Tika 的自动检测能力，处理 PDF/Word/TXT 之外的文件格式，
 * 包括但不限于 .doc（旧版 Word）、.md（Markdown）、.html、.rtf 等。
 * 优先级低于专用解析器（Pdf/Word/Txt），只作为兜底。
 */
@Component
public class GenericDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(GenericDocumentParser.class);

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) return false;
        String lower = fileName.toLowerCase();
        return lower.endsWith(".doc") || lower.endsWith(".md")
                || lower.endsWith(".html") || lower.endsWith(".htm")
                || lower.endsWith(".rtf");
    }

    @Override
    public List<Document> parse(InputStream inputStream) throws Exception {
        logger.info("开始解析通用文档 (Tika 自动检测)");

        BodyContentHandler handler = new BodyContentHandler(-1);
        AutoDetectParser parser = new AutoDetectParser();
        org.xml.sax.helpers.DefaultHandler dummy = new org.xml.sax.helpers.DefaultHandler();
        parser.parse(inputStream, handler, new org.apache.tika.metadata.Metadata());

        String content = handler.toString().trim();
        logger.info("通用文档解析完成，内容长度: {} 字符", content.length());

        Document doc = new Document(content, Map.of("content_type", "generic_document"));
        return List.of(doc);
    }

    @Override
    public int getPriority() {
        return 0; // 最低优先级，专用解析器优先匹配
    }
}
