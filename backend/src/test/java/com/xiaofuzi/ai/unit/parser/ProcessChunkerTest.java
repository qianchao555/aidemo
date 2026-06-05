package com.xiaofuzi.ai.unit.parser;

import com.xiaofuzi.ai.rag.parser.ProcessChunker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

class ProcessChunkerTest {

    @Test
    @DisplayName("按步骤序号切分 → 生成带 step_title 的 chunks")
    void shouldSplitByStepNumbers() {
        String text = "请假流程\n\n第1步：填写请假申请单\n登录OA系统，填写请假申请单。\n\n第2步：部门审批\n直属上级审批请假申请。\n\n第3步：HR确认\n人事部最终确认。";
        List<Document> chunks = ProcessChunker.chunk(text, "process");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks.size()).isGreaterThanOrEqualTo(3);

        // 每个 chunk 应有 step_title 和 skip_split=true
        for (Document doc : chunks) {
            assertThat(doc.getMetadata())
                    .containsKey("step_title")
                    .containsEntry("skip_split", true)
                    .containsEntry("content_type", "process");
        }
    }

    @Test
    @DisplayName("带角色和材料元数据提取")
    void shouldExtractRoleAndMaterials() {
        String text = "所需材料：身份证复印件、请假条\n\n第1步：提交申请\n责任人：员工本人\n填写请假申请。";

        List<Document> chunks = ProcessChunker.chunk(text, "process");
        assertThat(chunks).isNotEmpty();

        // 材料应在文档级提取
        Document first = chunks.get(0);
        assertThat(first.getMetadata().get("step_materials")).isNotNull();
    }

    @Test
    @DisplayName("无步骤结构 → 返回单个 Document")
    void shouldReturnSingleDocumentWhenNoSteps() {
        String text = "本制度适用于全体员工，包括试用期员工。";
        List<Document> chunks = ProcessChunker.chunk(text, "policy");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).getMetadata().get("skip_split")).isEqualTo(true);
    }

    @Test
    @DisplayName("空文本 → 返回含空内容 Document")
    void shouldHandleEmptyText() {
        List<Document> chunks = ProcessChunker.chunk("", "empty");
        assertThat(chunks).hasSize(1);
    }
}
