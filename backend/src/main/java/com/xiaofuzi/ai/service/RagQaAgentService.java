package com.xiaofuzi.ai.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xiaofuzi.ai.dto.VersionOverride;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.entity.ChatSession;
import com.xiaofuzi.ai.entity.DocumentGroup;
import com.xiaofuzi.ai.entity.KnowledgeDocument;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.mapper.DocumentGroupMapper;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class RagQaAgentService {

    private static final Logger logger = LoggerFactory.getLogger(RagQaAgentService.class);

    //最大对话轮数
    private static final int MAX_HISTORY_ROUNDS = 5;
    private static final String SOURCE_MARKER = "【出处】";
    private static final String SOURCE_SEPARATOR = ">";
    private static final int TITLE_MAX_LENGTH = 30;
    private static final String DEFAULT_SESSION_TITLE = "新对话";

    /** 匹配 LLM 未正确遵循系统指令时回显的原始模板文本 */
    private static final Pattern RAW_TEMPLATE_ECHO =
            Pattern.compile("【系统指令-最高优先级】[\\s\\S]*?回复模板：[\\s\\n]*");

    private final ReactAgent ragQaAgent;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeDocumentMapper knowledgeDocumentMapper;
    private final DocumentGroupMapper documentGroupMapper;

    public RagQaAgentService(@Qualifier("ragQaAgent") ReactAgent ragQaAgent,
                             ChatHistoryMapper chatHistoryMapper,
                             ChatSessionMapper chatSessionMapper,
                             KnowledgeBaseService knowledgeBaseService,
                             KnowledgeDocumentMapper knowledgeDocumentMapper,
                             DocumentGroupMapper documentGroupMapper) {
        this.ragQaAgent = ragQaAgent;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeDocumentMapper = knowledgeDocumentMapper;
        this.documentGroupMapper = documentGroupMapper;
    }

    public record AskResult(String response, Long userMsgId, Long assistantMsgId) {}

    public AskResult ask(String threadId, Long userId, String question) {
        if (threadId == null || threadId.isBlank()) {
            threadId = com.xiaofuzi.ai.util.AppConstants.uuidNoDash();
        }

        logger.info("RAG问答请求 | threadId: {} | userId: {} | question: {}", threadId, userId, question);

        ChatHistory userMsg = saveHistory(threadId, com.xiaofuzi.ai.util.AppConstants.CHAT_ROLE_USER, question, null, null);

        String enrichedQuestion = buildEnrichedQuestion(threadId, question);

        try {
            RagQaMessageHook.setCurrentUserQuery(question);
            AssistantMessage agentResponse = ragQaAgent.call(enrichedQuestion);
            String responseText = agentResponse.getText();
            responseText = sanitizeRawTemplateEcho(responseText);
            logger.info("RAG问答完成 | threadId: {} | 响应长度: {} 字符", threadId, responseText.length());

            String[] srcInfo = extractSourceInfo(responseText);
            ChatHistory assistantMsg = saveHistory(threadId, com.xiaofuzi.ai.util.AppConstants.CHAT_ROLE_ASSISTANT, responseText, srcInfo[0], srcInfo[1]);

            updateSession(threadId, userId);

            return new AskResult(responseText, userMsg.getId(), assistantMsg.getId());
        } catch (GraphRunnerException e) {
            logger.error("RAG问答执行出错 | threadId: {}", threadId, e);
            throw new RuntimeException("RAG问答执行出错: " + e.getMessage(), e);
        } finally {
            RagQaMessageHook.clearCurrentUserQuery();
        }
    }

    /**
     * 独立执行一次轻量检索，获取检索到的文档中涉及哪些文档组，
     * 并查询这些组是否有历史版本，生成 version_info 数据。
     */
    public List<Map<String, Object>> buildVersionInfo(String query, String department,
            List<VersionOverride> versionOverrides) {
        Map<String, Object> searchResult = knowledgeBaseService.hybridSearch(
                query, 3, 0.6, department, versionOverrides);

        @SuppressWarnings("unchecked")
        List<Document> docs = (List<Document>) searchResult.get("documents");
        if (docs == null || docs.isEmpty()) return List.of();

        Set<Long> seenGroups = new HashSet<>();
        List<Map<String, Object>> items = new ArrayList<>();

        for (Document doc : docs) {
            if (doc.getMetadata() == null) continue;
            String groupIdStr = (String) doc.getMetadata().get("group_id");
            if (groupIdStr == null || groupIdStr.isBlank()) continue;
            Long groupId = Long.parseLong(groupIdStr);
            if (!seenGroups.add(groupId)) continue;

            DocumentGroup group = documentGroupMapper.findById(groupId);
            if (group == null) continue;

            List<KnowledgeDocument> groupDocs = knowledgeDocumentMapper.findByGroupId(groupId);
            if (groupDocs.size() <= 1) continue;

            String currentVersion = (String) doc.getMetadata().get("version");
            List<String> versions = groupDocs.stream()
                    .map(KnowledgeDocument::getVersion)
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted(Comparator.reverseOrder())
                    .collect(Collectors.toList());

            items.add(Map.of(
                "group_id", groupId,
                "group_name", group.getName(),
                "current_version", currentVersion != null ? currentVersion : "",
                "available_versions", versions
            ));
        }
        return items;
    }

    private String buildEnrichedQuestion(String threadId, String currentQuestion) {
        List<ChatHistory> recentHistory = chatHistoryMapper.findRecentByThreadId(threadId, MAX_HISTORY_ROUNDS * 2);

        if (recentHistory.isEmpty()) {
            return currentQuestion;
        }

        //过滤出用户和助手的对话
        List<ChatHistory> relevant = recentHistory.stream()
                .filter(h -> !com.xiaofuzi.ai.util.AppConstants.CHAT_ROLE_SYSTEM.equals(h.getRole()))
                .toList();

        if (relevant.isEmpty()) {
            return currentQuestion;
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是本次对话的历史记录，请结合历史上下文理解当前问题：\n\n");

        for (ChatHistory h : relevant) {
            String label = com.xiaofuzi.ai.util.AppConstants.CHAT_ROLE_USER.equals(h.getRole()) ? "用户" : "助手";
            context.append("【").append(label).append("】").append(h.getContent()).append("\n\n");
        }

        context.append("---\n");
        context.append("基于以上对话历史，请回答用户的最新问题：\n");
        context.append(currentQuestion);

        logger.debug("注入对话历史 | threadId: {} | 历史轮数: {}", threadId, relevant.size() / 2);
        return context.toString();
    }

    private ChatHistory saveHistory(String threadId, String role, String content,
                                     String sourceDoc, String headingPath) {
        ChatHistory history = ChatHistory.builder()
                .threadId(threadId)
                .role(role)
                .content(content)
                .sourceDoc(sourceDoc)
                .headingPath(headingPath)
                .createTime(LocalDateTime.now())
                .build();
        chatHistoryMapper.insert(history);
        return history;
    }

    private String[] extractSourceInfo(String content) {
        if (content == null || content.isBlank()) return new String[]{null, null};
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(SOURCE_MARKER + "(.*?)(\\n|$)").matcher(content);
        if (!m.find()) return new String[]{null, null};
        String raw = m.group(1).trim();
        String[] parts = raw.split(SOURCE_SEPARATOR, 2);
        String doc = parts[0].trim();
        String heading = parts.length > 1 ? parts[1].trim() : null;
        return new String[]{doc.isEmpty() ? null : doc, heading != null && heading.isEmpty() ? null : heading};
    }

    /**
     * 清除 LLM 未能正确遵循系统指令时回显的原始模板标记。
     * 当 searchKnowledge 返回【系统指令-最高优先级】…回复模板：… 时，
     * Agent 应只输出回复模板的内容，但部分模型会直接回显整段指令。
     * 此方法在服务层兜底清洗，确保用户永远看不到原始模板文本。
     */
    private String sanitizeRawTemplateEcho(String responseText) {
        if (responseText == null || responseText.isBlank()) return responseText;
        if (!responseText.contains("【系统指令-最高优先级】")
                && !responseText.contains("回复模板：")) {
            return responseText;
        }
        String cleaned = RAW_TEMPLATE_ECHO.matcher(responseText).replaceFirst("");
        if (cleaned.isBlank()) {
            cleaned = "抱歉，我暂时无法回答您的问题，建议您联系 HR 获取帮助。";
        }
        logger.warn("Agent 回显了原始模板标记，已自动清洗。原文长度={}, 清洗后长度={}",
                responseText.length(), cleaned.length());
        return cleaned;
    }

    private void updateSession(String threadId, Long userId) {
        List<ChatHistory> history = chatHistoryMapper.findByThreadId(threadId);
        int msgCount = history.size();

        String title = history.stream()
                .filter(h -> com.xiaofuzi.ai.util.AppConstants.CHAT_ROLE_USER.equals(h.getRole()))
                .findFirst()
                .map(h -> {
                    String c = h.getContent();
                    return c != null && c.length() > TITLE_MAX_LENGTH ? c.substring(0, TITLE_MAX_LENGTH) + "..." : c;
                })
                .orElse(DEFAULT_SESSION_TITLE);

        ChatSession session = chatSessionMapper.findByThreadId(threadId);
        if (session != null) {
            session.setTitle(title);
            session.setMessageCount(msgCount);
            session.setUpdateTime(LocalDateTime.now());
            chatSessionMapper.update(session);
        } else {
            ChatSession newSession = ChatSession.builder()
                    .threadId(threadId)
                    .userId(userId)
                    .title(title)
                    .messageCount(msgCount)
                    .createTime(LocalDateTime.now())
                    .updateTime(LocalDateTime.now())
                    .build();
            chatSessionMapper.insert(newSession);
        }
    }
}
