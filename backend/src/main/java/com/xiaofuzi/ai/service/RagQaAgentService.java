package com.xiaofuzi.ai.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class RagQaAgentService {

    private static final Logger logger = LoggerFactory.getLogger(RagQaAgentService.class);

    //最大对话轮数
    private static final int MAX_HISTORY_ROUNDS = 5;

    private final ReactAgent ragQaAgent;
    private final ChatHistoryMapper chatHistoryMapper;

    public RagQaAgentService(@Qualifier("ragQaAgent") ReactAgent ragQaAgent,
                             ChatHistoryMapper chatHistoryMapper) {
        this.ragQaAgent = ragQaAgent;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    public String ask(String threadId, String question) {
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString().replace("-", "");
        }

        logger.info("RAG问答请求 | threadId: {} | question: {}", threadId, question);

        //保存用户的对话信息
        saveHistory(threadId, "user", question, null, null);

        //压缩历史问题并注入：具备历史对话的请求
        String enrichedQuestion = buildEnrichedQuestion(threadId, question);

        try {
            AssistantMessage agentResponse = ragQaAgent.call(enrichedQuestion);
            String responseText = agentResponse.getText();
            logger.info("RAG问答完成 | threadId: {} | 响应长度: {} 字符", threadId, responseText.length());

            saveHistory(threadId, "assistant", responseText, null, null);

            return responseText;
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

    private void saveHistory(String threadId, String role, String content,
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
    }
}
