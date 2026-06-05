package com.xiaofuzi.ai.integration.service;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.integration.config.TestConfig;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.mapper.DocumentGroupMapper;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.RagQaAgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@ActiveProfiles("test")
@Import(TestConfig.class)
class RagQaAgentServiceIT {

    /**
     * Overrides the {@code @MockBean RagQaAgentService} defined in TestConfig
     * with the real service, so we test actual service logic.
     */
    @TestConfiguration
    static class RealServiceOverride {
        @Bean
        @Primary
        RagQaAgentService realRagQaAgentService(
                @Qualifier("ragQaAgent") ReactAgent ragQaAgent,
                ChatHistoryMapper chatHistoryMapper,
                ChatSessionMapper chatSessionMapper,
                KnowledgeBaseService knowledgeBaseService,
                KnowledgeDocumentMapper knowledgeDocumentMapper,
                DocumentGroupMapper documentGroupMapper) {
            return new RagQaAgentService(ragQaAgent, chatHistoryMapper, chatSessionMapper,
                    knowledgeBaseService, knowledgeDocumentMapper, documentGroupMapper);
        }
    }

    @Autowired
    private RagQaAgentService ragQaAgentService;

    @Autowired
    private KnowledgeBaseService knowledgeBaseService;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private RagQaMessageHook ragQaMessageHook;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    @Qualifier("ragQaAgent")
    private ReactAgent ragQaAgent;

    @Test
    @DisplayName("buildVersionInfo -> 有检索结果但无历史版本 -> 空列表")
    void shouldReturnEmptyWhenNoVersionHistory() {
        when(knowledgeBaseService.hybridSearch(anyString(), anyInt(), anyDouble(), any(), any()))
                .thenReturn(Map.of("documents", List.of()));

        List<Map<String, Object>> result =
                ragQaAgentService.buildVersionInfo("年假", null, null);

        assertThat(result).isEmpty();
    }
}
