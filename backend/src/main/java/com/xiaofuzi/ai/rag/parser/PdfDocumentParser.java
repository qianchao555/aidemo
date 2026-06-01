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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PdfDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(PdfDocumentParser.class);

    private static final FlexmarkHtmlConverter HTML_CONVERTER = FlexmarkHtmlConverter.builder().build();

    private static final Pattern HEADING_PATTERN = buildHeadingPattern();

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.toLowerCase().endsWith(".pdf");
    }

    @Override
    public List<Document> parse(InputStream inputStream) throws Exception {
        logger.info("开始解析 PDF 文档");

        String xhtml = parsePdfToXhtml(inputStream);
        logger.debug("PDF 转 XHTML 完成，长度: {} 字符", xhtml.length());

        String markdown = HTML_CONVERTER.convert(xhtml);
        markdown = cleanMarkdown(markdown);
        logger.info("PDF 转 Markdown 完成，长度: {} 字符", markdown.length());

        List<Document> chunks = chunkByHeadings(markdown);
        logger.info("PDF 分块完成，共 {} 个 chunk", chunks.size());

        return chunks;
    }

    private String parsePdfToXhtml(InputStream inputStream) throws Exception {
        SAXTransformerFactory factory = (SAXTransformerFactory) SAXTransformerFactory.newInstance();
        TransformerHandler handler = factory.newTransformerHandler();
        handler.getTransformer().setOutputProperty(OutputKeys.METHOD, "xml");
        handler.getTransformer().setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        handler.setResult(new StreamResult(baos));

        Metadata metadata = new Metadata();
        ParseContext context = new ParseContext();

        PDFParser parser = new PDFParser();
        parser.parse(inputStream, handler, metadata, context);

        return baos.toString(StandardCharsets.UTF_8);
    }

    private String cleanMarkdown(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return "";
        }
        markdown = markdown.replace('\u00a0', ' ');
        markdown = markdown.replaceAll("[ \\t]+\\n", "\n");
        markdown = markdown.replaceAll("\\n{3,}", "\n\n");
        return markdown.trim();
    }

    static Pattern buildHeadingPattern() {
        String chineseNum = "[一二三四五六七八九十百千]+";
        String chineseDigit = "[一二三四五六七八九十]";

        String pChapter = "(?:第" + chineseNum + "章)\\s*.+";
        String pSection = "(?:第" + chineseNum + "节)\\s*.+";
        String pClause = "(?:第" + chineseNum + "条)\\s*.+";
        String pChineseBullet = "[" + chineseDigit + "]+[、，]\\s*.+";
        String pParenthesized = "（[" + chineseDigit + "]+）\\s*.+";
        String pNumbered = "\\d+[、\\.．]\\s*.+";
        String pSubNumbered = "\\d+\\.\\d+[、\\.．]?\\s*.+";

        String all = String.join("|", pChapter, pSection, pClause, pChineseBullet, pParenthesized, pSubNumbered, pNumbered);
        return Pattern.compile("^(" + all + ")$", Pattern.MULTILINE);
    }

    private int inferHeadingLevel(String headingLine) {
        if (headingLine == null || headingLine.isBlank()) {
            return 0;
        }
        String trimmed = headingLine.trim();

        if (trimmed.matches("^第[一二三四五六七八九十百千]+章\\s*.+")) {
            return 1;
        }
        if (trimmed.matches("^第[一二三四五六七八九十百千]+节\\s*.+")) {
            return 2;
        }
        if (trimmed.matches("^[一二三四五六七八九十]+[、，]\\s*.+")) {
            return 3;
        }
        if (trimmed.matches("^（[一二三四五六七八九十]+）\\s*.+")) {
            return 4;
        }
        if (trimmed.matches("^第[一二三四五六七八九十百千]+条\\s*.+")) {
            return 3;
        }
        if (trimmed.matches("^\\d+\\.\\d+[、\\.．]?\\s*.+")) {
            return 5;
        }
        if (trimmed.matches("^\\d+[、\\.．]\\s*.+")) {
            return 5;
        }
        return 3;
    }

    private List<Document> chunkByHeadings(String markdown) {
        List<Section> sections = extractSections(markdown);
        List<Document> chunks = new ArrayList<>();

        boolean hasHeadings = sections.stream().anyMatch(s -> s.level > 0);

        if (!hasHeadings) {
            Document doc = new Document(markdown, Map.of(
                    "content_type", "pdf_document",
                    "heading_path", "",
                    "chunk_index", 0,
                    "total_chunks", 1));
            chunks.add(doc);
            return chunks;
        }

        Deque<HeadingInfo> headingStack = new ArrayDeque<>();

        for (Section section : sections) {
            if (section.level > 0) {
                while (!headingStack.isEmpty() && headingStack.peek().level >= section.level) {
                    headingStack.pop();
                }
                headingStack.push(new HeadingInfo(section.level, section.title));
            }

            String headingPath = buildHeadingPath(headingStack);
            List<String> paragraphs = splitIntoParagraphs(section.content);

            for (String paragraph : paragraphs) {
                if (paragraph.isBlank()) {
                    continue;
                }

                String enrichedContent = headingPath.isEmpty()
                        ? paragraph
                        : "[" + headingPath + "]\n" + paragraph;

                Map<String, Object> metadata = new HashMap<>();
                metadata.put("content_type", "pdf_document");
                metadata.put("heading_path", headingPath);
                metadata.put("skip_split", true);
                if (section.level > 0) {
                    metadata.put("heading_title", section.title);
                    metadata.put("heading_level", section.level);
                }

                chunks.add(new Document(enrichedContent, metadata));
            }
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk_index", i);
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }

        return chunks;
    }

    private List<Section> extractSections(String markdown) {
        List<Section> sections = new ArrayList<>();
        Matcher matcher = HEADING_PATTERN.matcher(markdown);

        List<HeadingMatch> headingMatches = new ArrayList<>();
        while (matcher.find()) {
            String headingLine = matcher.group().trim();
            int level = inferHeadingLevel(headingLine);
            headingMatches.add(new HeadingMatch(matcher.start(), matcher.end(), level, headingLine));
        }

        if (headingMatches.isEmpty()) {
            sections.add(new Section(0, "", markdown));
            return sections;
        }

        if (headingMatches.get(0).start > 0) {
            String preamble = markdown.substring(0, headingMatches.get(0).start).trim();
            if (!preamble.isEmpty()) {
                sections.add(new Section(0, "", preamble));
            }
        }

        for (int i = 0; i < headingMatches.size(); i++) {
            HeadingMatch hm = headingMatches.get(i);
            int contentEnd = (i + 1 < headingMatches.size())
                    ? headingMatches.get(i + 1).start
                    : markdown.length();

            String content = markdown.substring(hm.end, contentEnd).trim();
            sections.add(new Section(hm.level, hm.title, content));
        }

        return sections;
    }

    private List<String> splitIntoParagraphs(String content) {
        List<String> paragraphs = new ArrayList<>();
        for (String part : content.split("\\n\\s*\\n")) {
            String trimmed = part.trim();
            if (!trimmed.isEmpty()) {
                paragraphs.add(trimmed);
            }
        }
        return paragraphs;
    }

    private String buildHeadingPath(Deque<HeadingInfo> headingStack) {
        StringBuilder path = new StringBuilder();
        Iterator<HeadingInfo> it = headingStack.descendingIterator();
        while (it.hasNext()) {
            if (!path.isEmpty()) {
                path.append(" > ");
            }
            path.append(it.next().title);
        }
        return path.toString();
    }

    private static class HeadingMatch {
        final int start;
        final int end;
        final int level;
        final String title;

        HeadingMatch(int start, int end, int level, String title) {
            this.start = start;
            this.end = end;
            this.level = level;
            this.title = title;
        }
    }

    private static class Section {
        final int level;
        final String title;
        final String content;

        Section(int level, String title, String content) {
            this.level = level;
            this.title = title;
            this.content = content;
        }
    }

    private static class HeadingInfo {
        final int level;
        final String title;

        HeadingInfo(int level, String title) {
            this.level = level;
            this.title = title;
        }
    }
}
