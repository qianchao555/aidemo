package com.xiaofuzi.ai.integration.service;

import com.xiaofuzi.ai.dto.FaqMatchResult;
import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.integration.config.TestConfig;
import com.xiaofuzi.ai.mapper.FaqEntryMapper;
import com.xiaofuzi.ai.service.FaqService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import javax.sql.DataSource;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest(properties = {
    "spring.autoconfigure.exclude="
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration,"
            + "org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration"
})
@ActiveProfiles("test")
@Import(TestConfig.class)
class FaqServiceIT {

    @Autowired
    private FaqService faqService;

    @Autowired
    private FaqEntryMapper faqEntryMapper;

    @MockBean
    private DataSource dataSource;

    @MockBean
    private VectorStore vectorStore;

    @MockBean
    private EmbeddingModel embeddingModel;

    @MockBean
    private RagQaMessageHook ragQaMessageHook;

    @Test
    @DisplayName("match -- 完整匹配链路（Mock Mapper）")
    void shouldMatchExactThroughFullChain() {
        FaqEntry entry = new FaqEntry();
        entry.setId(1L);
        entry.setQuestion("年假怎么申请？");
        entry.setAnswer("登录OA -> 填写申请 -> 提交审批");
        entry.setStatus("active");

        when(faqEntryMapper.findAllActive()).thenReturn(List.of(entry));

        FaqMatchResult result = faqService.match("年假怎么申请？");

        assertThat(result.matched()).isTrue();
        assertThat(result.matchType()).isEqualTo("exact");
    }

    @Test
    @DisplayName("match -- 无匹配 -> noMatch")
    void shouldReturnNoMatchWhenNoFaqMatches() {
        FaqEntry entry = new FaqEntry();
        entry.setId(1L);
        entry.setQuestion("离职流程怎么走？");
        entry.setAnswer("离职流程...");
        entry.setStatus("active");

        when(faqEntryMapper.findAllActive()).thenReturn(List.of(entry));

        FaqMatchResult result = faqService.match("年假怎么申请？");

        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("listAll -> 调用 mapper.findAllActive")
    void shouldListAllActive() {
        FaqEntry e1 = new FaqEntry();
        e1.setId(1L);
        e1.setQuestion("Q1");
        e1.setStatus("active");
        when(faqEntryMapper.findAllActive()).thenReturn(List.of(e1));

        List<FaqEntry> result = faqService.listAll();

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getQuestion()).isEqualTo("Q1");
    }
}
