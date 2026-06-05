package com.xiaofuzi.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.context.DepartmentContextHolder;
import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.dto.ContentChatRequest;
import com.xiaofuzi.ai.dto.SessionSummary;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.entity.ChatSession;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.service.RagQaAgentService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private final RagQaAgentService ragQaAgentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final RagQaMessageHook ragQaMessageHook;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AgentController(RagQaAgentService ragQaAgentService,
                           ChatHistoryMapper chatHistoryMapper,
                           ChatSessionMapper chatSessionMapper,
                           RagQaMessageHook ragQaMessageHook) {
        this.ragQaAgentService = ragQaAgentService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.ragQaMessageHook = ragQaMessageHook;
    }

    @PostMapping("/rag-qa/chat")
    public Result<String> ragQaChat(@RequestBody ContentChatRequest contentChatRequest) {
        String message = contentChatRequest.getUserMessage();
        String threadId = contentChatRequest.getThreadId();
        Long userId = UserContext.get().getId();
        DepartmentContextHolder.set(contentChatRequest.getDepartment());
        try {
            RagQaAgentService.AskResult result = ragQaAgentService.ask(threadId, userId, message);
            return Result.success(result.response());
        } finally {
            DepartmentContextHolder.clear();
        }
    }

    @PostMapping(value = "/rag-qa/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ragQaChatStream(@RequestBody ContentChatRequest request) {
        String userMessage = request.getUserMessage();
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = UUID.randomUUID().toString().replace("-", "");
        }

        SseEmitter emitter = new SseEmitter(180_000L);
        final String finalThreadId = threadId;
        final Long finalUserId = UserContext.get().getId();

        sseExecutor.execute(() -> {
            DepartmentContextHolder.set(request.getDepartment());
            try {
                // 使用 ObjectMapper 显式序列化为 JSON，避免 SseEmitter 内部 toString() 问题
                String thinkingJson = objectMapper.writeValueAsString(
                        Map.of("type", "thinking", "content", "正在检索知识库..."));
                emitter.send(SseEmitter.event().name("thinking").data(thinkingJson));

                RagQaAgentService.AskResult result = ragQaAgentService.ask(finalThreadId, finalUserId, userMessage);
                String response = result.response();

                // 推送检索元信息给前端展示
                Map<String, Object> searchInfo = ragQaMessageHook.getLastSearchInfo();
                if (searchInfo != null) {
                    String searchJson = objectMapper.writeValueAsString(
                            Map.of("type", "search_info", "content", searchInfo));
                    emitter.send(SseEmitter.event().name("search_info").data(searchJson));
                }

                // 按句拆分逐句发送
                String[] segments = response.split("(?<=[。！？\\n])");
                for (String segment : segments) {
                    if (segment.trim().isEmpty()) continue;
                    Thread.sleep(30);
                    String tokenJson = objectMapper.writeValueAsString(
                            Map.of("type", "token", "content", segment));
                    emitter.send(SseEmitter.event().name("token").data(tokenJson));
                }

                String doneJson = objectMapper.writeValueAsString(
                        Map.of("type", "done", "content",
                                Map.of("threadId", finalThreadId,
                                       "userMsgId", result.userMsgId(),
                                       "assistantMsgId", result.assistantMsgId())));
                emitter.send(SseEmitter.event().name("done").data(doneJson));
                emitter.complete();
            } catch (Exception e) {
                logger.error("SSE 流式问答出错", e);
                try {
                    String errorJson = objectMapper.writeValueAsString(
                            Map.of("type", "error", "content", e.getMessage() != null ? e.getMessage() : "未知错误"));
                    emitter.send(SseEmitter.event().name("error").data(errorJson));
                } catch (Exception ignored) {}
                emitter.completeWithError(e);
            } finally {
                DepartmentContextHolder.clear();
            }
        });

        return emitter;
    }

    @PostMapping("/sessions/{messageId}/feedback")
    public Result<Void> submitFeedback(@PathVariable Long messageId, @RequestBody Map<String, Integer> body) {
        Integer rating = body.get("rating");
        if (rating == null || (rating != 1 && rating != -1 && rating != 0)) {
            return Result.error("rating 必须为 1（赞）、-1（踩）或 0（取消）");
        }
        Integer dbRating = rating == 0 ? null : rating;
        chatHistoryMapper.updateRating(messageId, dbRating);
        logger.info("反馈提交: messageId={}, rating={}", messageId, rating);
        return Result.success();
    }

    @PostMapping("/sessions")
    public Result<SessionSummary> createSession(@RequestBody Map<String, Object> body) {
        String threadId = (String) body.getOrDefault("threadId", UUID.randomUUID().toString());
        String title = (String) body.getOrDefault("title", "新对话");
        Long userId = UserContext.get().getId();

        ChatSession session = ChatSession.builder()
                .threadId(threadId)
                .userId(userId)
                .title(title)
                .messageCount(0)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        chatSessionMapper.insert(session);

        return Result.success(SessionSummary.builder()
                .threadId(threadId)
                .title(title)
                .messageCount(0)
                .lastUpdateTime(session.getUpdateTime())
                .build());
    }

    @GetMapping("/sessions")
    public Result<List<SessionSummary>> listSessions() {
        Long userId = UserContext.get().getId();
        List<ChatSession> sessions = chatSessionMapper.findByUserId(userId);

        List<SessionSummary> summaries = sessions.stream()
                .map(s -> SessionSummary.builder()
                        .threadId(s.getThreadId())
                        .title(s.getTitle())
                        .messageCount(s.getMessageCount())
                        .lastUpdateTime(s.getUpdateTime())
                        .build())
                .collect(Collectors.toList());

        return Result.success(summaries);
    }

    @GetMapping("/sessions/{threadId}/history")
    public Result<List<ChatHistory>> getSessionHistory(@PathVariable String threadId) {
        List<ChatHistory> history = chatHistoryMapper.findByThreadId(threadId);
        return Result.success(history);
    }

    @DeleteMapping("/sessions/{threadId}")
    public Result<Map<String, Object>> deleteSession(@PathVariable String threadId) {
        ChatSession session = chatSessionMapper.findByThreadId(threadId);
        if (session == null) {
            return Result.error("会话不存在");
        }
        Long currentUserId = UserContext.get().getId();
        if (!currentUserId.equals(session.getUserId())) {
            return Result.error("无权操作此会话");
        }
        chatHistoryMapper.deleteByThreadId(threadId);
        chatSessionMapper.deleteByThreadId(threadId);
        logger.info("会话已删除: threadId={}", threadId);
        return Result.success(Map.of("success", true, "message", "会话已删除"));
    }
}
