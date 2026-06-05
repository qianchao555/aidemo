package com.xiaofuzi.ai.unit.parser;

import com.xiaofuzi.ai.rag.parser.HeadingChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class HeadingChunkerTest {

    @Test
    @DisplayName("按章节标题切分 → 生成带 heading_path 的 chunks")
    void shouldChunkByChapterHeadings() {
        String text = "第一章 总则\n本章规定了公司制度的基本原则。\n\n第一条 目的\n为规范管理，制定本制度。\n\n第二条 适用范围\n本制度适用于全体员工。";
        List<Document> chunks = HeadingChunker.chunk(text, "policy");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);

        // 第一个 chunk 应有 heading_path = "第一章 总则"
        assertThat(chunks.get(0).getMetadata())
                .containsKey("heading_path")
                .containsEntry("content_type", "policy");

        // 应有 chunk_index 和 total_chunks
        assertThat(chunks.get(0).getMetadata()).containsKeys("chunk_index", "total_chunks");
    }

    @Test
    @DisplayName("无标题结构 → 返回单个 Document，heading_path 为空")
    void shouldReturnSingleDocumentWhenNoHeadings() {
        String text = "这是一段没有标题结构的普通文本。";
        List<Document> chunks = HeadingChunker.chunk(text, "plain");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata().get("heading_path")).isEqualTo("");
    }

    @Test
    @DisplayName("空文本 → 返回含空内容的 Document")
    void shouldHandleEmptyText() {
        String text = "";
        List<Document> chunks = HeadingChunker.chunk(text, "empty");

        assertThat(chunks).hasSize(1);
    }
}
