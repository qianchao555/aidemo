package com.xiaofuzi.ai.unit.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.xiaofuzi.ai.dto.VersionOverride;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.entity.ChatSession;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.mapper.DocumentGroupMapper;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.RagQaAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RagQaAgentServiceTest {

    @Mock ReactAgent ragQaAgent;
    @Mock ChatHistoryMapper chatHistoryMapper;
    @Mock ChatSessionMapper chatSessionMapper;
    @Mock KnowledgeBaseService knowledgeBaseService;
    @Mock KnowledgeDocumentMapper knowledgeDocumentMapper;
    @Mock DocumentGroupMapper documentGroupMapper;

    private RagQaAgentService service;

    @BeforeEach
    void setUp() {
        service = new RagQaAgentService(ragQaAgent, chatHistoryMapper, chatSessionMapper,
                knowledgeBaseService, knowledgeDocumentMapper, documentGroupMapper);
    }

    @Test
    @DisplayName("新建会话问答 → 返回 AskResult，保存 history")
    void shouldAskAndSaveHistory() throws GraphRunnerException {
        // Given: agent 返回带出处的回答
        String response = "根据制度，年假申请需提前3天。【出处】年假制度.pdf > 申请流程";
        when(ragQaAgent.call(anyString()))
                .thenReturn(new AssistantMessage(response));
        doNothing().when(chatHistoryMapper).insert(any(ChatHistory.class));
        // updateSession 中 chatHistoryMapper.findByThreadId 默认返回空列表 → title="新对话"
        when(chatSessionMapper.findByThreadId(anyString())).thenReturn(null);
        doNothing().when(chatSessionMapper).insert(any(ChatSession.class));

        // When: 无 threadId 的新会话
        RagQaAgentService.AskResult result = service.ask(null, 1L, "年假怎么申请？");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.response()).contains("年假制度.pdf");
        // 保存了用户消息 + 助手消息共 2 条
        verify(chatHistoryMapper, times(2)).insert(any(ChatHistory.class));
    }

    @Test
    @DisplayName("追加历史问答 → 注入对话上下文")
    void shouldAppendToExistingThread() throws GraphRunnerException {
        // Given: 已有历史记录
        String response = "继续上一话题。【出处】制度.pdf > 第2章";
        when(ragQaAgent.call(anyString()))
                .thenReturn(new AssistantMessage(response));
        ChatHistory prevUser = ChatHistory.builder()
                .threadId("thread-1").role("user").content("第一个问题").build();
        ChatHistory prevAsst = ChatHistory.builder()
                .threadId("thread-1").role("assistant")
                .content("第一个回答【出处】a.pdf").build();
        when(chatHistoryMapper.findRecentByThreadId(eq("thread-1"), anyInt()))
                .thenReturn(List.of(prevUser, prevAsst));
        doNothing().when(chatHistoryMapper).insert(any(ChatHistory.class));
        // updateSession 中 chatHistoryMapper.findByThreadId 默认返回空列表
        when(chatSessionMapper.findByThreadId("thread-1")).thenReturn(null);
        doNothing().when(chatSessionMapper).insert(any(ChatSession.class));

        // When: 在已有会话中追加问题
        RagQaAgentService.AskResult result = service.ask("thread-1", 1L, "继续问题");

        // Then
        assertThat(result).isNotNull();
        // 验证构建了包含对话历史的 prompt，前缀 "对话历史" 来源于 buildEnrichedQuestion
        verify(ragQaAgent).call(contains("对话历史"));
    }

    @Test
    @DisplayName("已有 ChatSession 时 → update 而非 insert")
    void shouldUpdateExistingSession() throws GraphRunnerException {
        // Given: agent 正常返回
        String response = "回答【出处】doc.pdf";
        when(ragQaAgent.call(anyString()))
                .thenReturn(new AssistantMessage(response));
        doNothing().when(chatHistoryMapper).insert(any(ChatHistory.class));
        // updateSession: chatHistoryMapper.findByThreadId 默认空列表 → title="新对话"
        ChatSession existingSession = ChatSession.builder()
                .threadId("thread-2").userId(1L).title("旧标题")
                .messageCount(2).build();
        when(chatSessionMapper.findByThreadId("thread-2")).thenReturn(existingSession);
        doNothing().when(chatSessionMapper).update(any(ChatSession.class));

        // When
        RagQaAgentService.AskResult result = service.ask("thread-2", 1L, "新问题");

        // Then: 应该走 update 而不是 insert
        assertThat(result).isNotNull();
        verify(chatSessionMapper, never()).insert(any(ChatSession.class));
        verify(chatSessionMapper).update(any(ChatSession.class));
    }

    @Test
    @DisplayName("buildVersionInfo — 无检索结果 → 空列表")
    void shouldReturnEmptyVersionInfoWhenNoResults() {
        when(knowledgeBaseService.hybridSearch(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(Map.of("documents", List.of()));

        List<Map<String, Object>> result = service.buildVersionInfo("测试", null, null);

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("buildVersionInfo — null documents → 空列表")
    void shouldReturnEmptyVersionInfoWhenNullDocuments() {
        when(knowledgeBaseService.hybridSearch(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(Map.of());

        List<Map<String, Object>> result = service.buildVersionInfo("测试", null, null);

        assertThat(result).isEmpty();
    }
}
