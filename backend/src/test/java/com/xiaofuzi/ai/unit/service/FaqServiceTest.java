package com.xiaofuzi.ai.unit.service;

import com.xiaofuzi.ai.dto.FaqMatchResult;
import com.xiaofuzi.ai.entity.FaqEntry;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.FaqEntryMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
import com.xiaofuzi.ai.service.FaqService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FaqServiceTest {

    @Mock FaqEntryMapper faqEntryMapper;
    @Mock ChatHistoryMapper chatHistoryMapper;
    @Mock KnowledgeBaseService knowledgeBaseService;
    @Mock VectorStore vectorStore;
    @Mock EmbeddingModel embeddingModel;

    private FaqService faqService;

    @BeforeEach
    void setUp() {
        faqService = new FaqService(faqEntryMapper, chatHistoryMapper,
                knowledgeBaseService, vectorStore, embeddingModel);
    }

    @Test
    @DisplayName("空query -> noMatch")
    void shouldReturnNoMatchWhenQueryIsNull() {
        FaqMatchResult result = faqService.match(null);
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("空query -> noMatch")
    void shouldReturnNoMatchWhenQueryIsBlank() {
        FaqMatchResult result = faqService.match("   ");
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("无活跃FAQ -> noMatch")
    void shouldReturnNoMatchWhenNoActiveFaq() {
        when(faqEntryMapper.findAllActive()).thenReturn(List.of());
        FaqMatchResult result = faqService.match("年假怎么申请？");
        assertThat(result.matched()).isFalse();
    }

    @Test
    @DisplayName("精确匹配 -> hit, type=exact")
    void shouldExactMatch() {
        FaqEntry entry = buildFaq(1L, "年假怎么申请？", "年假申请流程...");
        when(faqEntryMapper.findAllActive()).thenReturn(List.of(entry));
        doNothing().when(faqEntryMapper).updateHitCountAndTime(anyLong());

        FaqMatchResult result = faqService.match("年假怎么申请？");

        assertThat(result.matched()).isTrue();
        assertThat(result.matchType()).isEqualTo("exact");
        assertThat(result.entry().getQuestion()).isEqualTo("年假怎么申请？");
    }

    @Test
    @DisplayName("包含匹配 -> hit, type=fuzzy")
    void shouldFuzzyMatchWhenQueryContains() {
        FaqEntry entry = buildFaq(1L, "年假申请流程和材料要求", "流程...");
        when(faqEntryMapper.findAllActive()).thenReturn(List.of(entry));
        doNothing().when(faqEntryMapper).updateHitCountAndTime(anyLong());

        FaqMatchResult result = faqService.match("年假申请流程");

        assertThat(result.matched()).isTrue();
        assertThat(result.matchType()).isEqualTo("fuzzy");
    }

    @Test
    @DisplayName("关键词匹配 -> hit, type=keyword")
    void shouldKeywordMatch() {
        FaqEntry entry = buildFaq(1L, "年假相关问题", "答案...");
        entry.setKeywords("年假,休假");
        when(faqEntryMapper.findAllActive()).thenReturn(List.of(entry));
        doNothing().when(faqEntryMapper).updateHitCountAndTime(anyLong());

        FaqMatchResult result = faqService.match("我想问一下年假的事情");

        assertThat(result.matched()).isTrue();
        assertThat(result.matchType()).isEqualTo("keyword");
    }

    @Test
    @DisplayName("create -> 插入并同步向量库")
    void shouldCreateAndSyncToVectorStore() {
        FaqEntry entry = buildFaq(null, "新问题", "答案");
        doNothing().when(faqEntryMapper).insert(any(FaqEntry.class));

        FaqEntry created = faqService.create(entry);

        assertThat(created).isNotNull();
        verify(faqEntryMapper).insert(entry);
    }

    private FaqEntry buildFaq(Long id, String question, String answer) {
        FaqEntry entry = new FaqEntry();
        entry.setId(id);
        entry.setQuestion(question);
        entry.setAnswer(answer);
        entry.setStatus("active");
        return entry;
    }
}
