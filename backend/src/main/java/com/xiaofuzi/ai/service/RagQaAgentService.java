package com.xiaofuzi.ai.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.entity.ChatSession;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RagQaAgentService {

    private static final Logger logger = LoggerFactory.getLogger(RagQaAgentService.class);

    //最大对话轮数
    private static final int MAX_HISTORY_ROUNDS = 5;

    private final ReactAgent ragQaAgent;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatSessionMapper chatSessionMapper;

    public RagQaAgentService(@Qualifier("ragQaAgent") ReactAgent ragQaAgent,
                             ChatHistoryMapper chatHistoryMapper,
                             ChatSessionMapper chatSessionMapper) {
        this.ragQaAgent = ragQaAgent;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatSessionMapper = chatSessionMapper;
    }

    public record AskResult(String response, Long userMsgId, Long assistantMsgId) {}

    public AskResult ask(String threadId, Long userId, String question) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString().replace("-", "");
        }

        logger.info("RAG问答请求 | threadId: {} | userId: {} | question: {}", threadId, userId, question);

        ChatHistory userMsg = saveHistory(threadId, "user", question, null, null);

        String enrichedQuestion = buildEnrichedQuestion(threadId, question);

        try {
            AssistantMessage agentResponse = ragQaAgent.call(enrichedQuestion);
            String responseText = agentResponse.getText();
            logger.info("RAG问答完成 | threadId: {} | 响应长度: {} 字符", threadId, responseText.length());

            String[] srcInfo = extractSourceInfo(responseText);
            ChatHistory assistantMsg = saveHistory(threadId, "assistant", responseText, srcInfo[0], srcInfo[1]);

            updateSession(threadId, userId);

            return new AskResult(responseText, userMsg.getId(), assistantMsg.getId());
        } catch (GraphRunnerException e) {
            logger.error("RAG问答执行出错 | threadId: {}", threadId, e);
            throw new RuntimeException("RAG问答执行出错: " + e.getMessage(), e);
        }
    }

    private String buildEnrichedQuestion(String threadId, String currentQuestion) {
        List<ChatHistory> recentHistory = chatHistoryMapper.findRecentByThreadId(threadId, MAX_HISTORY_ROUNDS * 2);

        if (recentHistory.isEmpty()) {
            return currentQuestion;
        }

        //过滤出用户和助手的对话
        List<ChatHistory> relevant = recentHistory.stream()
                .filter(h -> !"system".equals(h.getRole()))
                .toList();

        if (relevant.isEmpty()) {
            return currentQuestion;
        }

        StringBuilder context = new StringBuilder();
        context.append("以下是本次对话的历史记录，请结合历史上下文理解当前问题：\n\n");

        for (ChatHistory h : relevant) {
            String label = "user".equals(h.getRole()) ? "用户" : "助手";
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
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("【出处】(.*?)(\\n|$)").matcher(content);
        if (!m.find()) return new String[]{null, null};
        String raw = m.group(1).trim();
        String[] parts = raw.split(">", 2);
        String doc = parts[0].trim();
        String heading = parts.length > 1 ? parts[1].trim() : null;
        return new String[]{doc.isEmpty() ? null : doc, heading != null && heading.isEmpty() ? null : heading};
    }

    private void updateSession(String threadId, Long userId) {
        List<ChatHistory> history = chatHistoryMapper.findByThreadId(threadId);
        int msgCount = history.size();

        String title = history.stream()
                .filter(h -> "user".equals(h.getRole()))
                .findFirst()
                .map(h -> {
                    String c = h.getContent();
                    return c != null && c.length() > 30 ? c.substring(0, 30) + "..." : c;
                })
                .orElse("新对话");

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
