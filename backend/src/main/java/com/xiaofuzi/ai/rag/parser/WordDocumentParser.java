package com.xiaofuzi.ai.rag.parser;

import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;
import java.util.Map;

@Component
public class WordDocumentParser implements DocumentParser {

    private static final Logger logger = LoggerFactory.getLogger(WordDocumentParser.class);

    @Override
    public boolean supports(String fileName) {
        if (fileName == null) {
            return false;
        }
        String lower = fileName.toLowerCase();
        return lower.endsWith(".docx");
    }

    @Override
    public List<Document> parse(InputStream inputStream) throws Exception {
        logger.info("开始解析 Word 文档 (.docx)");

        XWPFDocument document = new XWPFDocument(inputStream);
        StringBuilder result = new StringBuilder();

        List<IBodyElement> bodyElements = document.getBodyElements();
        for (IBodyElement element : bodyElements) {
            switch (element.getElementType()) {
                case PARAGRAPH:
                    String paraText = extractParagraph((XWPFParagraph) element);
                    if (!paraText.isBlank()) {
                        result.append(paraText).append("\n\n");
                    }
                    break;
                case TABLE:
                    String tableMarkdown = convertTableToMarkdown((XWPFTable) element);
                    if (!tableMarkdown.isBlank()) {
                        result.append(tableMarkdown).append("\n\n");
                    }
                    break;
                default:
                    break;
            }
        }

        document.close();

        String content = normalizeNewlines(result.toString());
        logger.info("Word 文档解析完成，内容长度: {} 字符", content.length());

        Document doc = new Document(content, Map.of("content_type", "word_document"));
        return List.of(doc);
    }

    private String extractParagraph(XWPFParagraph paragraph) {
        String text = paragraph.getText();
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.trim();
    }

    private String convertTableToMarkdown(XWPFTable table) {
        int rowCount = table.getNumberOfRows();
        if (rowCount == 0) {
            return "";
        }

        List<XWPFTableRow> rows = table.getRows();
        int colCount = 0;
        for (XWPFTableRow row : rows) {
            colCount = Math.max(colCount, row.getTableCells().size());
        }
        if (colCount == 0) {
            return "";
        }

        String[][] cells = new String[rowCount][colCount];
        for (int r = 0; r < rowCount; r++) {
            XWPFTableRow row = rows.get(r);
            List<XWPFTableCell> tableCells = row.getTableCells();
            for (int c = 0; c < colCount; c++) {
                if (c < tableCells.size()) {
                    cells[r][c] = cleanCellText(tableCells.get(c).getText());
                } else {
                    cells[r][c] = "";
                }
            }
        }

        return buildMarkdownTable(cells);
    }

    private String cleanCellText(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\n", " ").replace("\r", " ").replace("|", "\\|").trim();
    }

    private String buildMarkdownTable(String[][] cells) {
        int rowCount = cells.length;
        int colCount = cells[0].length;

        int[] colWidths = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            int maxWidth = 3;
            for (int r = 0; r < rowCount; r++) {
                int len = 0;
                String cell = cells[r][c];
                for (char ch : cell.toCharArray()) {
                    if (ch >= '\u4e00' && ch <= '\u9fff' || ch >= '\u3000' && ch <= '\u303f' || ch >= '\uff00' && ch <= '\uffef') {
                        len += 2;
                    } else {
                        len += 1;
                    }
                }
                maxWidth = Math.max(maxWidth, len);
            }
            colWidths[c] = Math.min(maxWidth, 40);
        }

        StringBuilder sb = new StringBuilder();

        for (int r = 0; r < rowCount; r++) {
            sb.append("| ");
            for (int c = 0; c < colCount; c++) {
                sb.append(padRight(cells[r][c], colWidths[c]));
                sb.append(" | ");
            }
            sb.append("\n");

            if (r == 0) {
                sb.append("|");
                for (int c = 0; c < colCount; c++) {
                    sb.append("-".repeat(colWidths[c] + 2));
                    sb.append("|");
                }
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String padRight(String text, int width) {
        int currentLen = 0;
        for (char ch : text.toCharArray()) {
            if (ch >= '\u4e00' && ch <= '\u9fff' || ch >= '\u3000' && ch <= '\u303f' || ch >= '\uff00' && ch <= '\uffef') {
                currentLen += 2;
            } else {
                currentLen += 1;
            }
        }
        if (currentLen >= width) {
            return text;
        }
        int padCount = width - currentLen;
        return text + " ".repeat(padCount);
    }

    private String normalizeNewlines(String text) {
        return text.replaceAll("\n{3,}", "\n\n").trim();
    }
}
