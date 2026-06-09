package com.xiaofuzi.ai.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.context.DepartmentContextHolder;
import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.dto.ContentChatRequest;
import com.xiaofuzi.ai.dto.VersionOverride;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import jakarta.annotation.PreDestroy;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@RestController
@RequestMapping("/agent")
public class AgentController {

    private static final Logger logger = LoggerFactory.getLogger(AgentController.class);

    private static final Pattern SUGGESTIONS_EXTRACT_PATTERN =
            Pattern.compile("💡\\s*您可以继续问[：:]\\s*\\n?([\\s\\S]*?)$");
    private static final Pattern SUGGESTIONS_STRIP_WITH_SEP =
            Pattern.compile("\\n?-*+\\n💡\\s*您可以继续问[：:][\\s\\S]*$");
    private static final Pattern SUGGESTIONS_STRIP_WITHOUT_SEP =
            Pattern.compile("\\n💡\\s*您可以继续问[：:][\\s\\S]*$");
    private static final Pattern SUGGESTIONS_STRIP_NO_NEWLINE =
            Pattern.compile("💡\\s*您可以继续问[：:][\\s\\S]*$");

    private static final String DEFAULT_SESSION_TITLE = "新对话";
    private static final String THINKING_MESSAGE = "正在检索知识库...";
    private static final String DEFAULT_ERROR_MESSAGE = "未知错误";

    private static final String SSE_TYPE_KEY = "type";
    private static final String SSE_CONTENT_KEY = "content";
    private static final String SSE_EVENT_THINKING = "thinking";
    private static final String SSE_EVENT_SEARCH_INFO = "search_info";
    private static final String SSE_EVENT_VERSION_INFO = "version_info";
    private static final String SSE_EVENT_TOKEN = "token";
    private static final String SSE_EVENT_DONE = "done";
    private static final String SSE_EVENT_ERROR = "error";

    private final RagQaAgentService ragQaAgentService;
    private final ChatHistoryMapper chatHistoryMapper;
    private final ChatSessionMapper chatSessionMapper;
    private final RagQaMessageHook ragQaMessageHook;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ExecutorService sseExecutor = Executors.newCachedThreadPool();

    @Value("${app.agent.sse.timeout-ms:180000}")
    private long sseTimeoutMs;

    @Value("${app.agent.sse.token-delay-ms:30}")
    private long tokenDelayMs;

    public AgentController(RagQaAgentService ragQaAgentService,
                           ChatHistoryMapper chatHistoryMapper,
                           ChatSessionMapper chatSessionMapper,
                           RagQaMessageHook ragQaMessageHook) {
        this.ragQaAgentService = ragQaAgentService;
        this.chatHistoryMapper = chatHistoryMapper;
        this.chatSessionMapper = chatSessionMapper;
        this.ragQaMessageHook = ragQaMessageHook;
    }

    @PostMapping(value = "/rag-qa/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ragQaChatStream(@RequestBody ContentChatRequest request) {
        String userMessage = request.getUserMessage();
        String threadId = request.getThreadId();
        if (threadId == null || threadId.isBlank()) {
            threadId = com.xiaofuzi.ai.util.AppConstants.uuidNoDash();
        }

        SseEmitter emitter = new SseEmitter(sseTimeoutMs);
        final String finalThreadId = threadId;
        final Long finalUserId = UserContext.get().getId();

        sseExecutor.execute(() -> {
            DepartmentContextHolder.set(request.getDepartment());
            // Parse version overrides from request
            List<VersionOverride> versionOverrides = null;
            if (request.getVersionOverrides() != null && !request.getVersionOverrides().isEmpty()) {
                versionOverrides = request.getVersionOverrides().stream()
                        .map(m -> new VersionOverride(
                                String.valueOf(m.get("group_id")),
                                (String) m.get("version")))
                        .collect(Collectors.toList());
            }
            try {
                // 使用 ObjectMapper 显式序列化为 JSON，避免 SseEmitter 内部 toString() 问题
                String thinkingJson = objectMapper.writeValueAsString(
                        Map.of(SSE_TYPE_KEY, SSE_EVENT_THINKING, SSE_CONTENT_KEY, THINKING_MESSAGE));
                emitter.send(SseEmitter.event().name(SSE_EVENT_THINKING).data(thinkingJson));

                RagQaAgentService.AskResult result = ragQaAgentService.ask(finalThreadId, finalUserId, userMessage);
                String response = result.response();

                // 剥离建议问题段落，后续通过 done 事件结构化传递
                List<String> suggestions = extractSuggestions(response);
                String cleanResponse = suggestions.isEmpty() ? response : stripSuggestions(response);

                // ★ 推送检索元信息：从 KnowledgeRetrievalTools ThreadLocal 获取
                Map<String, Object> searchInfo = com.xiaofuzi.ai.rag.KnowledgeRetrievalTools.consumeLastSearchInfo();
                if (searchInfo != null && !searchInfo.isEmpty()) {
                    String searchJson = objectMapper.writeValueAsString(
                            Map.of(SSE_TYPE_KEY, SSE_EVENT_SEARCH_INFO, SSE_CONTENT_KEY, searchInfo));
                    emitter.send(SseEmitter.event().name(SSE_EVENT_SEARCH_INFO).data(searchJson));
                }

                // 推送版本追溯信息给前端
                List<Map<String, Object>> versionInfo = ragQaAgentService.buildVersionInfo(
                        userMessage, request.getDepartment(), versionOverrides);
                if (!versionInfo.isEmpty()) {
                    String versionJson = objectMapper.writeValueAsString(
                            Map.of(SSE_TYPE_KEY, SSE_EVENT_VERSION_INFO, SSE_CONTENT_KEY, Map.of("items", versionInfo)));
                    emitter.send(SseEmitter.event().name(SSE_EVENT_VERSION_INFO).data(versionJson));
                }

                // 按句拆分逐句发送
                String[] segments = cleanResponse.split("(?<=[。！？\\n])");
                for (String segment : segments) {
                    if (segment.trim().isEmpty()) continue;
                    Thread.sleep(tokenDelayMs);
                    String tokenJson = objectMapper.writeValueAsString(
                            Map.of(SSE_TYPE_KEY, SSE_EVENT_TOKEN, SSE_CONTENT_KEY, segment));
                    emitter.send(SseEmitter.event().name(SSE_EVENT_TOKEN).data(tokenJson));
                }

                String doneJson = objectMapper.writeValueAsString(
                        Map.of(SSE_TYPE_KEY, SSE_EVENT_DONE, SSE_CONTENT_KEY,
                                Map.of("threadId", finalThreadId,
                                       "userMsgId", result.userMsgId(),
                                       "assistantMsgId", result.assistantMsgId(),
                                       "suggestions", suggestions)));
                emitter.send(SseEmitter.event().name(SSE_EVENT_DONE).data(doneJson));
                emitter.complete();
            } catch (Exception e) {
                logger.error("SSE 流式问答出错", e);
                try {
                    String errorJson = objectMapper.writeValueAsString(
                            Map.of(SSE_TYPE_KEY, SSE_EVENT_ERROR, SSE_CONTENT_KEY,
                                    e.getMessage() != null ? e.getMessage() : DEFAULT_ERROR_MESSAGE));
                    emitter.send(SseEmitter.event().name(SSE_EVENT_ERROR).data(errorJson));
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
        String title = (String) body.getOrDefault("title", DEFAULT_SESSION_TITLE);
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

    /**
     * 从回答文本中提取「💡 您可以继续问：」段落的建议问题列表。
     * 支持多条建议在同一行（用 ？- 分隔）或分行。
     */
    private List<String> extractSuggestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        Matcher m = SUGGESTIONS_EXTRACT_PATTERN.matcher(content);
        if (!m.find()) return List.of();

        String raw = m.group(1).trim();
        List<String> items = new ArrayList<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // 同一行内按 ？- 或 ?- 拆分
            String[] parts = line.split("(?<=[？?])\\s*-\\s*");
            for (String part : parts) {
                part = part.replaceAll("^[-\\s•\\d.、]+", "").trim();
                if (!part.isEmpty() && part.length() <= 50) {
                    items.add(part);
                }
            }
        }
        return items;
    }

    /**
     * 从回答文本中移除「💡 您可以继续问：」段落（含可选的前置 --- 分隔线）。
     */
    private String stripSuggestions(String content) {
        if (content == null || content.isBlank()) return content;
        String stripped = SUGGESTIONS_STRIP_WITH_SEP.matcher(content).replaceAll("");
        stripped = SUGGESTIONS_STRIP_WITHOUT_SEP.matcher(stripped).replaceAll("");
        stripped = SUGGESTIONS_STRIP_NO_NEWLINE.matcher(stripped).replaceAll("");
        return stripped;
    }

    @PreDestroy
    public void shutdown() {
        sseExecutor.shutdown();
        try {
            if (!sseExecutor.awaitTermination(5, TimeUnit.SECONDS)) {
                sseExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            sseExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
