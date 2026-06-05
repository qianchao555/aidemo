package com.xiaofuzi.ai.rag.parser;

import org.springframework.ai.document.Document;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FAQ 问答对切分工具。
 * 识别常见 FAQ 格式（Q: A: / 问：答： / 问题N：答案：），将每个 Q&A 对切分为独立段落。
 * 与 {@link HeadingChunker} / {@link ProcessChunker} 并列，在 {@code KnowledgeBaseService} 策略链中自动路由。
 */
public final class FaqChunker {

    // FAQ 边界模式 —— 匹配 "Q:" "问：" "问题1：" 等问答对起始行
    private static final Pattern QA_BOUNDARY = Pattern.compile(
            "^(" + String.join("|",
                    "[Qq]\\s*[：:.]",
                    "问\\s*[：:]",
                    "问题\\s*\\d+\\s*[：:.]",
                    "问题[一二三四五六七八九十]+\\s*[：:.]",
                    "\\d+\\s*[．.、]\\s*(?:什么|如何|怎么|为什么|是否|可以|需要|怎样|啥|哪)",
                    "FAQ\\s*\\d+\\s*[：:.]"
            ) + ")\\s*",
            Pattern.MULTILINE);

    // 答案起始标记 —— 用于在 Q&A 对内部区分问题和答案
    private static final Pattern ANSWER_MARKER = Pattern.compile(
            "^(" + String.join("|",
                    "[Aa]\\s*[：:.]",
                    "答\\s*[：:]",
                    "答案\\s*[：:]",
                    "回答\\s*[：:]"
            ) + ")\\s*",
            Pattern.MULTILINE);

    private FaqChunker() {}

    /**
     * 按 Q&A 边界切分，每个问答对作为一个独立 chunk。
     * 无 Q&A 模式时返回单个无结构 Document，由上游回退到其他切分器。
     * 返回的每个 Document 携带：
     * <ul>
     *   <li>{@code qa_question} — 问题文本</li>
     *   <li>{@code qa_answer} — 答案文本（如有）</li>
     *   <li>{@code skip_split} — 始终为 true</li>
     * </ul>
     */
    public static List<Document> chunk(String text, String contentType) {
        List<QAPair> pairs = extractQAPairs(text);

        if (pairs.isEmpty()) {
            return List.of(new Document(text, Map.of(
                    com.xiaofuzi.ai.util.AppConstants.META_CONTENT_TYPE, contentType,
                    com.xiaofuzi.ai.util.AppConstants.META_SKIP_SPLIT, true)));
        }

        List<Document> chunks = new ArrayList<>();
        for (QAPair pair : pairs) {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put(com.xiaofuzi.ai.util.AppConstants.META_CONTENT_TYPE, contentType);
            meta.put(com.xiaofuzi.ai.util.AppConstants.META_SKIP_SPLIT, true);
            meta.put("qa_question", pair.question);

            // 格式化：问题 + 答案合并为一段，便于向量检索匹配
            String chunkText = "【问题】" + pair.question + "\n【答案】" + (pair.answer.isBlank() ? pair.raw : pair.answer);
            meta.put("qa_answer", pair.answer.isBlank() ? pair.raw : pair.answer);

            chunks.add(new Document(chunkText, meta));
        }

        for (int i = 0; i < chunks.size(); i++) {
            chunks.get(i).getMetadata().put("chunk_index", i);
            chunks.get(i).getMetadata().put("total_chunks", chunks.size());
        }
        return chunks;
    }

    // ── 内部实现 ──

    private record QAPair(String question, String answer, String raw) {}

    /** 按 QA_BOUNDARY 切分全文，然后在每个切片内部用 ANSWER_MARKER 区分问和答。 */
    private static List<QAPair> extractQAPairs(String text) {
        Matcher m = QA_BOUNDARY.matcher(text);
        List<int[]> hits = new ArrayList<>(); // [start, end]
        List<String> questionHeaders = new ArrayList<>();
        while (m.find()) {
            hits.add(new int[]{m.start(), m.end()});
            questionHeaders.add(m.group().trim());
        }

        if (hits.isEmpty()) return List.of();

        List<QAPair> pairs = new ArrayList<>();

        // 第一个 Q 之前的内容作为导语，忽略
        for (int i = 0; i < hits.size(); i++) {
            int contentStart = hits.get(i)[1];
            int contentEnd = (i + 1 < hits.size()) ? hits.get(i + 1)[0] : text.length();
            String raw = text.substring(contentStart, contentEnd).trim();

            // 在切片内部区分问题和答案
            Matcher am = ANSWER_MARKER.matcher(raw);
            if (am.find()) {
                String questionPart = raw.substring(0, am.start()).trim();
                String answerPart = raw.substring(am.end()).trim();
                String fullQuestion = questionPart.isEmpty()
                        ? questionHeaders.get(i)
                        : questionHeaders.get(i) + " " + questionPart;
                pairs.add(new QAPair(fullQuestion, answerPart, raw));
            } else {
                // 无答案标记：把整个内容当作回答
                pairs.add(new QAPair(questionHeaders.get(i), "", raw));
            }
        }

        return pairs;
    }
}
