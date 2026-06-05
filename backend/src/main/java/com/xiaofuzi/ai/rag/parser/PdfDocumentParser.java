package com.xiaofuzi.ai.rag.parser;

import com.vladsch.flexmark.html2md.converter.FlexmarkHtmlConverter;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import javax.xml.transform.OutputKeys;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * PDF 文档解析器 —— 仅负责 PDF → 文本的格式转换（PDF → XHTML → Markdown）。
 * 不在此做任何切分，交由 KnowledgeBaseService 的 chunkSmart() 管线统一处理，
 * 确保 PDF / Word / TXT 所有格式走相同的切分策略。
 */
@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(PdfDocumentParser.class);

    private static final FlexmarkHtmlConverter HTML_CONVERTER = FlexmarkHtmlConverter.builder().build();

    @Override
    public boolean supports(String fileName) {
        return fileName != null && fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> parse(InputStream inputStream) throws Exception {
        logger.info("开始解析 PDF 文档");

        String xhtml = parsePdfToXhtml(inputStream);
        logger.debug("PDF 转 XHTML 完成，长度: {} 字符", xhtml.length());

        String markdown = HTML_CONVERTER.convert(xhtml);
        markdown = cleanMarkdown(markdown);
        logger.info("PDF 解析完成 (纯文本输出，切分交由 chunkSmart 管线)，长度: {} 字符", markdown.length());

        // 不设 skip_split，让 KnowledgeBaseService.chunkSmart() 统一处理
        return List.of(new Document(markdown, Map.of("content_type", com.xiaofuzi.ai.util.AppConstants.CONTENT_TYPE_PDF)));
    }

    @Override
    public int getPriority() {
        return 10; // PDF 优先于通用解析器
    }

    // ── PDF → 文本 ──

    private String parsePdfToXhtml(InputStream inputStream) throws Exception {
        SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        handler.setResult(new StreamResult(baos));

        PDFParser parser = new PDFParser();
        parser.parse(inputStream, handler, new Metadata(), new ParseContext());

        return baos.toString(StandardCharsets.UTF_8);
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) return "";
        markdown = markdown.replace(' ', ' ');
        markdown = markdown.replaceAll("[ \\t]+\\n", "\n");
        markdown = markdown.replaceAll("\\n{3,}", "\n\n");
        return markdown.trim();
    }
}
