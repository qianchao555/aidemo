package com.xiaofuzi.ai.integration.config;

import com.xiaofuzi.ai.mapper.*;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.RagQaAgentService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@TestConfiguration
public class TestConfig {

    @MockBean
    public ChatHistoryMapper chatHistoryMapper;

    @MockBean
    public ChatSessionMapper chatSessionMapper;

    @MockBean
    public ChatUserMapper chatUserMapper;

    @MockBean
    public FaqEntryMapper faqEntryMapper;

    @MockBean
    public KnowledgeDocumentMapper knowledgeDocumentMapper;

    @MockBean
    public DocumentGroupMapper documentGroupMapper;

    @MockBean
    public RagQaAgentService ragQaAgentService;

    @MockBean
    public KnowledgeBaseService knowledgeBaseService;

    @Bean
    @Primary
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
