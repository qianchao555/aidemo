package com.xiaofuzi.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.dto.ContentChatRequest;
import com.xiaofuzi.ai.dto.SessionSummary;
import com.xiaofuzi.ai.entity.ChatHistory;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.service.RagQaAgentService;
import com.xiaofuzi.ai.vo.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Comparator;
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

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    public AgentController(RagQaAgentService ragQaAgentService,
                           ChatHistoryMapper chatHistoryMapper) {
        this.ragQaAgentService = ragQaAgentService;
        this.chatHistoryMapper = chatHistoryMapper;
    }

    @PostMapping("/rag-qa/chat")
    public Result<String> ragQaChat(@RequestBody ContentChatRequest contentChatRequest) {
        String message = contentChatRequest.getUserMessage();
        String threadId = contentChatRequest.getThreadId();
        String response = ragQaAgentService.ask(threadId, message);
        return Result.success(response);
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

        sseExecutor.execute(() -> {
            try {
                // 使用 ObjectMapper 显式序列化为 JSON，避免 SseEmitter 内部 toString() 问题
                String thinkingJson = objectMapper.writeValueAsString(
                        Map.of("type", "thinking", "content", "正在检索知识库..."));
                emitter.send(SseEmitter.event().name("thinking").data(thinkingJson));

                String response = ragQaAgentService.ask(finalThreadId, userMessage);

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
                        Map.of("type", "done", "content", finalThreadId));
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
            }
        });

        return emitter;
    }

    @GetMapping("/sessions")
    public Result<List<SessionSummary>> listSessions() {
        List<ChatHistory> allHistory = chatHistoryMapper.findAllThreadIds();

        Map<String, List<ChatHistory>> grouped = allHistory.stream()
                .collect(Collectors.groupingBy(ChatHistory::getThreadId));

        List<SessionSummary> sessions = grouped.entrySet().stream()
                .map(entry -> {
                    String tid = entry.getKey();
                    List<ChatHistory> msgs = entry.getValue();
                    String title = msgs.stream()
                            .filter(h -> "user".equals(h.getRole()))
                            .findFirst()
                            .map(h -> {
                                String c = h.getContent();
                                return c != null && c.length() > 30 ? c.substring(0, 30) + "..." : c;
                            })
                            .orElse("空会话");
                    LocalDateTime lastTime = msgs.stream()
                            .map(ChatHistory::getCreateTime)
                            .max(Comparator.naturalOrder())
                            .orElse(LocalDateTime.now());
                    return SessionSummary.builder()
                            .threadId(tid)
                            .title(title)
                            .messageCount(msgs.size())
                            .lastUpdateTime(lastTime)
                            .build();
                })
                .sorted((a, b) -> b.getLastUpdateTime().compareTo(a.getLastUpdateTime()))
                .collect(Collectors.toList());

        return Result.success(sessions);
    }

    @GetMapping("/sessions/{threadId}/history")
    public Result<List<ChatHistory>> getSessionHistory(@PathVariable String threadId) {
        List<ChatHistory> history = chatHistoryMapper.findByThreadId(threadId);
        return Result.success(history);
    }

    @DeleteMapping("/sessions/{threadId}")
    public Result<Map<String, Object>> deleteSession(@PathVariable String threadId) {
        chatHistoryMapper.deleteByThreadId(threadId);
        logger.info("会话已删除: threadId={}", threadId);
        return Result.success(Map.of("success", true, "message", "会话已删除"));
    }
}
