package com.xiaofuzi.ai.integration.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.dto.ContentChatRequest;
import com.xiaofuzi.ai.entity.ChatSession;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.integration.config.TestConfig;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import com.xiaofuzi.ai.service.FaqService;
import com.xiaofuzi.ai.service.RagQaAgentService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.autoconfigure.exclude="
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
                + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestConfig.class)
class AgentControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RagQaAgentService ragQaAgentService;

    @Autowired
    private ChatUserMapper chatUserMapper;

    @Autowired
    private ChatSessionMapper chatSessionMapper;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RagQaMessageHook ragQaMessageHook;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private FaqService faqService;

    @BeforeEach
    void setUp() {
        ChatUser testUser = ChatUser.builder()
                .id(1L)
                .username("testuser")
                .displayName("Test User")
                .role("user")
                .department("全公司")
                .authToken("test-token")
                .build();
        when(chatUserMapper.findByToken(anyString())).thenReturn(testUser);
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /agent/sessions -> 200 返回会话列表")
    void shouldReturnSessionList() throws Exception {
        ChatSession session = ChatSession.builder()
                .id(1L)
                .threadId("thread-1")
                .userId(1L)
                .title("测试会话")
                .messageCount(5)
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();
        when(chatSessionMapper.findByUserId(1L)).thenReturn(List.of(session));

        mockMvc.perform(get("/agent/sessions")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data[0].threadId").value("thread-1"))
                .andExpect(jsonPath("$.data[0].title").value("测试会话"))
                .andExpect(jsonPath("$.data[0].messageCount").value(5));
    }

    @Test
    @DisplayName("POST /agent/sessions/{messageId}/feedback 赞 -> 200")
    void shouldSubmitPositiveFeedback() throws Exception {
        mockMvc.perform(post("/agent/sessions/100/feedback")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    @DisplayName("POST /agent/sessions/{messageId}/feedback 无效 rating -> 500 错误")
    void shouldRejectInvalidFeedbackRating() throws Exception {
        mockMvc.perform(post("/agent/sessions/100/feedback")
                        .header("Authorization", "Bearer test-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"rating\": 99}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.code").value(500));
    }
}
