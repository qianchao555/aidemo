package com.xiaofuzi.ai.integration.controller;

import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.integration.config.TestConfig;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.FaqService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
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
class KnowledgeBaseControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatUserMapper chatUserMapper;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

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
                .role("admin")
                .department("全公司")
                .authToken("test-token")
                .build();
        when(chatUserMapper.findByToken(anyString())).thenReturn(testUser);

        Document doc = new Document("年假申请流程：员工需提前3天在OA系统提交请假申请...",
                Map.of("source", "请假制度.pdf"));
        when(knowledgeBaseService.search(eq("年假"), eq(5)))
                .thenReturn(List.of(doc));
        when(knowledgeBaseService.formatAsContext(anyList(), eq(5000)))
                .thenReturn("年假申请流程：员工需提前3天在OA系统提交请假申请...");
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /knowledge-base/search?query=年假 -> 200 返回搜索结果")
    void shouldReturnSearchResults() throws Exception {
        mockMvc.perform(get("/knowledge-base/search")
                        .param("query", "年假")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.query").value("年假"))
                .andExpect(jsonPath("$.data.hitCount").value(1))
                .andExpect(jsonPath("$.data.results").isString());
    }

    @Test
    @DisplayName("POST /knowledge-base/upload 空文件 -> 返回错误")
    void shouldRejectEmptyFile() throws Exception {
        MockMultipartFile emptyFile = new MockMultipartFile(
                "file", "empty.pdf", "application/pdf", new byte[0]);

        mockMvc.perform(multipart("/knowledge-base/upload")
                        .file(emptyFile)
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(false))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg").value("上传文件不能为空"));
    }
}
