package com.xiaofuzi.ai.integration.controller;

import com.xiaofuzi.ai.context.UserContext;
import com.xiaofuzi.ai.entity.ChatUser;
import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.integration.config.TestConfig;
import com.xiaofuzi.ai.mapper.ChatUserMapper;
import com.xiaofuzi.ai.service.FaqService;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class FaqControllerIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ChatUserMapper chatUserMapper;

    @MockBean
    private FaqService faqService;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RagQaMessageHook ragQaMessageHook;

    @MockBean
    private VectorStore vectorStore;

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

        FaqEntry faqEntry = FaqEntry.builder()
                .id(1L)
                .question("年假怎么请")
                .answer("请登录OA系统，在考勤模块提交请假申请")
                .category("假期")
                .hitCount(100)
                .status("active")
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        when(faqService.findByFilters(any(), any(), any(),
                anyString(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of(faqEntry));
        when(faqService.countByFilters(any(), any(), any()))
                .thenReturn(1L);
        when(faqService.getStats())
                .thenReturn(Map.of("totalFaq", 50L, "totalHits", 1200L, "todayHits", 30L));
    }

    @AfterEach
    void tearDown() {
        UserContext.clear();
    }

    @Test
    @DisplayName("GET /faq/faq -> 200 返回分页的 FAQ 列表")
    void shouldReturnPaginatedFaqList() throws Exception {
        mockMvc.perform(get("/faq/faq")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].question").value("年假怎么请"))
                .andExpect(jsonPath("$.data.list[0].category").value("假期"))
                .andExpect(jsonPath("$.data.list[0].hitCount").value(100))
                .andExpect(jsonPath("$.data.total").value(1));
    }

    @Test
    @DisplayName("GET /faq/faq/stats -> 200 返回统计信息")
    void shouldReturnFaqStats() throws Exception {
        mockMvc.perform(get("/faq/faq/stats")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.totalFaq").value(50))
                .andExpect(jsonPath("$.data.totalHits").value(1200))
                .andExpect(jsonPath("$.data.todayHits").value(30));
    }

    @Test
    @DisplayName("GET /faq/faq?sortBy=invalidColumn -> 200 非法排序字段被清理为默认值")
    void shouldSanitizeInvalidSortByToDefault() throws Exception {
        mockMvc.perform(get("/faq/faq")
                        .param("sortBy", "invalidColumn")
                        .header("Authorization", "Bearer test-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(true))
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.list[0].question").value("年假怎么请"));
    }
}
