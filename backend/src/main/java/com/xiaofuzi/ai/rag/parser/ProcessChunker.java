package com.xiaofuzi.ai.rag.parser;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 流程类文档结构切分工具。
 * 识别中文流程文档的结构要素（步骤/角色/时限/材料），将文本按步骤边界切分为带结构化元数据的段落。
 * 与 {@link HeadingChunker} 并行，在 {@code KnowledgeBaseService} 中按文档类型路由选择切分策略。
 *
 * <p>适用文档特征：包含"第N步""步骤N"或数字编号的流程说明类文档。</p>
 */
public final class ProcessChunker {

    // 步骤起始行 —— 匹配"第N步：xxx""步骤N：xxx"、数字编号"1. xxx"、"（1）xxx"等
    private static final Pattern STEP_BOUNDARY = Pattern.compile(
            "^(" + String.join("|",
                    "第\\d+步",
                    "第[一二三四五六七八九十]+步",
                    "步骤\\d+",
                    "步骤[一二三四五六七八九十]+",
                    "\\d+[、\\.．]",
                    "[（(]\\d+[）)]"
            ) + ")\\s*.+",
            Pattern.MULTILINE);

    // 流程元数据提取：材料 / 角色 / 时限 / 适用场景
    private static final Pattern MATERIALS_RE = Pattern.compile(
            "(?:所需材料|准备材料|提交材料|材料清单|携带材料)[：:]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern ROLE_RE = Pattern.compile(
            "(?:角色|责任人|审批人|经办人|承办人|处理人|负责部门)[：:]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern TIME_LIMIT_RE = Pattern.compile(
            "(?:时限|时间要求|处理时效|办理时限|办理周期|完成时限)[：:]\\s*(.+)", Pattern.MULTILINE);
    private static final Pattern SCOPE_RE = Pattern.compile(
            "(?:适用场景|适用范围|适用对象|适用条件|面向对象)[：:]\\s*(.+)", Pattern.MULTILINE);

    private ProcessChunker() {}

    /**
     * 尝试按流程步骤切分文本。无步骤结构时返回无标记的单个 Document（上游回退到 TokenTextSplitter）。
     * 返回的每个 Document 元数据包含：
     * <ul>
     *   <li>{@code step_title} — 步骤标题（如"第1步：填写申请单"）</li>
     *   <li>{@code step_role} — 责任角色</li>
     *   <li>{@code step_time_limit} — 时限要求</li>
     *   <li>{@code step_materials} — 所需材料</li>
     *   <li>{@code step_scope} — 适用场景</li>
     *   <li>{@code skip_split} — 始终为 true</li>
     * </ul>
     */
    public static List<Document> chunk(String text, String contentType) {
        List<StepRegion> regions = splitBySteps(text);

        if (regions.isEmpty()) {
            // 没有检测到步骤 —— 可能是纯流程说明文档，但仍尝试提取顶层元数据
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("content_type", contentType);
            meta.put("skip_split", true);
            extractDocumentMeta(text, meta);
            return List.of(new Document(text, meta));
        }

        List<Document> chunks = new ArrayList<>();

        // 文档级元数据：从全文提取一次，作为每个 chunk 的共享上下文
        Map<String, String> docMeta = new LinkedHashMap<>();
        extractFirst(MATERIALS_RE, text).ifPresent(v -> docMeta.put("step_materials", v));
        extractFirst(SCOPE_RE, text).ifPresent(v -> docMeta.put("step_scope", v));

        for (StepRegion region : regions) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("content_type", contentType);
            meta.put("skip_split", true);
            meta.put("step_title", region.title);

            // 段落级元数据：角色/时限优先取步骤内命中，其次取文档级
            String stepText = region.content;

            extractFirst(ROLE_RE, stepText).ifPresentOrElse(
                    v -> meta.put("step_role", v),
                    () -> extractFirst(ROLE_RE, text).ifPresent(v -> meta.put("step_role", v)));
            extractFirst(TIME_LIMIT_RE, stepText).ifPresentOrElse(
                    v -> meta.put("step_time_limit", v),
                    () -> extractFirst(TIME_LIMIT_RE, text).ifPresent(v -> meta.put("step_time_limit", v)));
            extractFirst(MATERIALS_RE, stepText).ifPresentOrElse(
                    v -> meta.put("step_materials", v),
                    () -> docMeta.forEach(meta::putIfAbsent));

            // 每个段落作为一个独立 chunk（流程文档的段落天然短小，无需再拆）
            for (String paragraph : splitParagraphs(region.content)) {
                if (paragraph.isBlank()) continue;
                // 在段落文本前添加步骤标题，增强语义锚点
                String enriched = "[" + region.title + "] " + paragraph;
                chunks.add(new Document(enriched, new LinkedHashMap<>(meta)));
            }
        }

        // 补充分块索引
        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk_index", i);
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }
        return chunks;
    }

    // ── 内部分段实现 ──

    /** 步骤区域：一步 = 标题行 + 到下一步之前的内容 */
    private record StepRegion(String title, String content) {}

    /**
     * 按 STEP_BOUNDARY 正则将全文切分为有序的步骤区域。
     * 步骤匹配行之前的文本作为"导语区域"（title 为空字符串）。
     */
    private static List<StepRegion> splitBySteps(String text) {
        Matcher m = STEP_BOUNDARY.matcher(text);

        // 收集所有步骤匹配点
        List<int[]> hits = new ArrayList<>(); // [start, end]
        List<String> titles = new ArrayList<>();
        while (m.find()) {
            hits.add(new int[]{m.start(), m.end()});
            titles.add(m.group().trim());
        }

        if (hits.isEmpty()) return List.of();

        List<StepRegion> regions = new ArrayList<>();

        // 第一个匹配点之前的文本 → 导语
        if (hits.get(0)[0] > 0) {
            String preamble = text.substring(0, hits.get(0)[0]).trim();
            if (!preamble.isBlank()) {
                regions.add(new StepRegion("流程概述", preamble));
            }
        }

        // 逐段截取：从当前匹配点 end 到下一个匹配点 start
        for (int i = 0; i < hits.size(); i++) {
            int contentStart = hits.get(i)[1];
            int contentEnd = (i + 1 < hits.size()) ? hits.get(i + 1)[0] : text.length();
            String content = text.substring(contentStart, contentEnd).trim();
            if (!content.isBlank()) {
                regions.add(new StepRegion(titles.get(i), content));
            }
        }

        return regions;
    }

    // ── 内联元数据提取 ──

    /** 从全文提取一次文档级别的元数据，存入 meta（供无步骤结构的文档使用）。 */
    private static void extractDocumentMeta(String text, Map<String, Object> meta) {
        extractFirst(MATERIALS_RE, text).ifPresent(v -> meta.put("step_materials", v));
        extractFirst(ROLE_RE, text).ifPresent(v -> meta.put("step_role", v));
        extractFirst(TIME_LIMIT_RE, text).ifPresent(v -> meta.put("step_time_limit", v));
        extractFirst(SCOPE_RE, text).ifPresent(v -> meta.put("step_scope", v));
    }

    /** 用模式从文本中提取第一条捕获组，失败返回 Optional.empty()。 */
    private static Optional<String> extractFirst(Pattern pattern, String text) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return Optional.of(m.group(1).trim().replaceAll("\\s+", " "));
        }
        return Optional.empty();
    }

    // ── 段落拆分 ──

    /** 按空行拆分段落，去除首尾空白，过滤纯空行。 */
    private static List<String> splitParagraphs(String content) {
        List<String> ps = new ArrayList<>();
        for (String part : content.split("\\n\\s*\\n")) {
            String t = part.trim();
            if (!t.isEmpty()) ps.add(t);
        }
        return ps;
    }
}
