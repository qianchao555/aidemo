package com.xiaofuzi.ai.rag.parser;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 中文制度文档标题检测与结构切分工具。
 *
 * <p>三条设计原则：
 * <ol>
 *   <li><b>语义聚合</b> — 同一标题下的多个段落合并为一个 chunk，不拆散同一语义单元</li>
 *   <li><b>直接标题</b> — chunk 文本前缀仅包含当前标题（如"[第一条 目的]"），
 *       完整路径（"第一章 总则 > 第一条 目的"）仅存于 metadata.heading_path</li>
 *   <li><b>超长拆分</b> — 单 chunk 超过 {@value #MAX_CHUNK_CHARS} 字符时才按段落拆分</li>
 * </ol>
 */
public final class HeadingChunker {

    /** 单 chunk 最大字符数，超过此值才按段落拆分 */
    private static final int MAX_CHUNK_CHARS = 800;

    private static final Pattern HEADING_PATTERN = buildHeadingPattern();

    private HeadingChunker() {}

    /**
     * 按标题切分并聚合段落。无标题结构时返回空 structure 的单 Document。
     *
     * @return 每个 Document 的元数据包含 heading_path（完整路径）、heading_title（直接标题）、heading_level
     */
    public static List<Document> chunk(String text, String contentType) {
        List<Section> sections = extractSections(text);
        boolean hasHeadings = sections.stream().anyMatch(s -> s.level > 0);

        if (!hasHeadings) {
            // 无结构标记：空 heading_path 表示"未检测到"
            return List.of(new Document(text, Map.of(
                    com.xiaofuzi.ai.util.AppConstants.META_CONTENT_TYPE, contentType,
                    com.xiaofuzi.ai.util.AppConstants.META_HEADING_PATH, "")));
        }

        List<Document> chunks = new ArrayList<>();
        Deque<HeadingInfo> headingStack = new ArrayDeque<>();

        for (Section section : sections) {
            // 管理标题栈：弹出同级或上级标题
            if (section.level > 0) {
                while (!headingStack.isEmpty() && headingStack.peek().level >= section.level) {
                    headingStack.pop();
                }
                headingStack.push(new HeadingInfo(section.level, section.title));
            }

            String fullPath = buildHeadingPath(headingStack);
            String directTitle = section.level > 0 ? section.title : "";

            // 同一标题下聚合段落
            for (String aggregated : aggregateParagraphs(section.content)) {
                if (aggregated.isBlank()) continue;

                // chunk 文本仅标注当前标题，完整路径走 metadata
                String enriched = directTitle.isEmpty()
                        ? aggregated
                        : "[" + directTitle + "] " + aggregated;

                Map<String, Object> meta = new LinkedHashMap<>();
                meta.put(com.xiaofuzi.ai.util.AppConstants.META_CONTENT_TYPE, contentType);
                meta.put(com.xiaofuzi.ai.util.AppConstants.META_HEADING_PATH, fullPath);
                if (section.level > 0) {
                    meta.put("heading_title", directTitle);
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

    // ── 段落聚合：同一标题下的段落合并，超长才拆分 ──

    /**
     * 将同标题下的段落合并为尽量少的 chunk。
     * 段落间用空行分隔，累计超过 MAX_CHUNK_CHARS 时切出一个新 chunk。
     */
    static List<String> aggregateParagraphs(String content) {
        List<String> paragraphs = splitParagraphs(content);
        if (paragraphs.isEmpty()) return List.of();

        List<String> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String p : paragraphs) {
            if (p.isBlank()) continue;
            // 当前 chunk 不为空 且 加入新段落后会超长 → 切出
            if (!current.isEmpty() && current.length() + 2 + p.length() > MAX_CHUNK_CHARS) {
                result.add(current.toString());
                current.setLength(0);
            }
            if (!current.isEmpty()) current.append("\n\n");
            current.append(p);
        }
        if (!current.isEmpty()) result.add(current.toString());

        return result.isEmpty() ? List.of() : result;
    }

    // ── 正则与标题层级 ──

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

    // ── 文本切分 ──

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

    // ── 内部类型 ──

    private record Section(int level, String title, String content) {}
    private record HeadingMatch(int start, int end, int level, String title) {}
    private record HeadingInfo(int level, String title) {}
}
