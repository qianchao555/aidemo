package com.xiaofuzi.ai.rag.parser;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文制度文档标题检测与结构切分工具。
 * 识别中文制度文档的标题层级（章/节/条/款），将长文本按标题边界切分为带标题路径的结构化段落。
 * PDF 解析器已内置此逻辑（设置 skip_split=true）；Word/通用解析器的输出由本工具后处理时使用。
 */
public final class HeadingChunker {

    private static final Pattern HEADING_PATTERN = buildHeadingPattern();

    private HeadingChunker() {}

    /**
     * 尝试按标题切分文本。如果没有检测到标题，返回仅含一个无结构标记的 Document。
     * 返回的每个 Document 都带有 heading_path、heading_title、heading_level、skip_split=true 等元数据。
     */
    public static List<Document> chunk(String text, String contentType) {
        List<Section> sections = extractSections(text);
        boolean hasHeadings = sections.stream().anyMatch(s -> s.level > 0);

        if (!hasHeadings) {
            return List.of(new Document(text, mapOf(
                    "content_type", contentType,
                    "heading_path", "",
                    "skip_split", true)));
        }

        List<Document> chunks = new ArrayList<>();
        Deque<HeadingInfo> headingStack = new ArrayDeque<>();

        for (Section section : sections) {
            if (section.level > 0) {
                while (!headingStack.isEmpty() && headingStack.peek().level >= section.level) {
                    headingStack.pop();
                }
                headingStack.push(new HeadingInfo(section.level, section.title));
            }

            String headingPath = buildHeadingPath(headingStack);
            for (String paragraph : splitParagraphs(section.content)) {
                if (paragraph.isBlank()) continue;
                String enriched = headingPath.isEmpty()
                        ? paragraph
                        : "[" + headingPath + "]\n" + paragraph;
                Map<String, Object> meta = new HashMap<>();
                meta.put("content_type", contentType);
                meta.put("heading_path", headingPath);
                meta.put("skip_split", true);
                if (section.level > 0) {
                    meta.put("heading_title", section.title);
                    meta.put("heading_level", section.level);
                }
                chunks.add(new Document(enriched, meta));
            }
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk_index", i);
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }
        return chunks;
    }

    // ── 内部实现 ──

    private static Pattern buildHeadingPattern() {
        String cn = "[一二三四五六七八九十百千]+";
        String cd = "[一二三四五六七八九十]";
        return Pattern.compile("^(" + String.join("|",
                "(?:第" + cn + "章)\\s*.+",
                "(?:第" + cn + "节)\\s*.+",
                "(?:第" + cn + "条)\\s*.+",
                "[" + cd + "]+[、，]\\s*.+",
                "（[" + cd + "]+）\\s*.+",
                "\\d+[、\\.．]\\s*.+",
                "\\d+\\.\\d+[、\\.．]?\\s*.+"
        ) + ")$", Pattern.MULTILINE);
    }

    private static int inferLevel(String line) {
        if (line == null || line.isBlank()) return 0;
        String t = line.trim();
        if (t.matches("^第[一二三四五六七八九十百千]+章\\s*.+")) return 1;
        if (t.matches("^第[一二三四五六七八九十百千]+节\\s*.+")) return 2;
        if (t.matches("^[一二三四五六七八九十]+[、，]\\s*.+")) return 3;
        if (t.matches("^（[一二三四五六七八九十]+）\\s*.+")) return 4;
        if (t.matches("^第[一二三四五六七八九十百千]+条\\s*.+")) return 3;
        if (t.matches("^\\d+\\.\\d+[、\\.．]?\\s*.+")) return 5;
        if (t.matches("^\\d+[、\\.．]\\s*.+")) return 5;
        return 3;
    }

    private static List<Section> extractSections(String text) {
        List<Section> sections = new ArrayList<>();
        Matcher m = HEADING_PATTERN.matcher(text);
        List<HeadingMatch> matches = new ArrayList<>();
        while (m.find()) {
            String line = m.group().trim();
            matches.add(new HeadingMatch(m.start(), m.end(), inferLevel(line), line));
        }
        if (matches.isEmpty()) {
            sections.add(new Section(0, "", text));
            return sections;
        }
        if (matches.get(0).start > 0) {
            String preamble = text.substring(0, matches.get(0).start).trim();
            if (!preamble.isEmpty()) sections.add(new Section(0, "", preamble));
        }
        for (int i = 0; i < matches.size(); i++) {
            HeadingMatch hm = matches.get(i);
            int end = (i + 1 < matches.size()) ? matches.get(i + 1).start : text.length();
            sections.add(new Section(hm.level, hm.title, text.substring(hm.end, end).trim()));
        }
        return sections;
    }

    private static List<String> splitParagraphs(String content) {
        List<String> ps = new ArrayList<>();
        for (String part : content.split("\\n\\s*\\n")) {
            String t = part.trim();
            if (!t.isEmpty()) ps.add(t);
        }
        return ps;
    }

    private static String buildHeadingPath(Deque<HeadingInfo> stack) {
        StringBuilder sb = new StringBuilder();
        Iterator<HeadingInfo> it = stack.descendingIterator();
        while (it.hasNext()) {
            if (!sb.isEmpty()) sb.append(" > ");
            sb.append(it.next().title);
        }
        return sb.toString();
    }

    private static Map<String, Object> mapOf(String key, Object value, Object... rest) {
        Map<String, Object> m = new HashMap<>();
        m.put(key, value);
        for (int i = 0; i < rest.length; i += 2) {
            m.put((String) rest[i], rest[i + 1]);
        }
        return m;
    }

    // ── 内部类型 ──

    private record Section(int level, String title, String content) {}
    private record HeadingMatch(int start, int end, int level, String title) {}
    private record HeadingInfo(int level, String title) {}
}
