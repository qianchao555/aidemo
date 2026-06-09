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

    @Test
    @DisplayName("多章节流程文档 → 章节上下文传播到步骤 heading_path")
    void shouldPropagateChapterContextToSteps() {
        String text = "第一章 年假申请流程\n流程概览\n年假申请共3步。\n"
                + "第1步：确认年假资格\n角色：员工本人\n确认剩余年假天数。\n"
                + "第2步：提交申请\n角色：员工本人\n登录OA系统提交申请。\n"
                + "第二章 病假申请流程\n流程概览\n病假申请共2步。\n"
                + "第1步：获取诊断证明\n角色：员工本人\n到医院获取诊断证明。\n"
                + "第2步：提交病假申请\n角色：员工本人\n在OA系统提交申请。\n";

        List<Document> chunks = ProcessChunker.chunk(text, "process");

        assertThat(chunks).isNotEmpty();

        // 找到年假第1步
        Document annualStep1 = chunks.stream()
                .filter(d -> d.getText().contains("确认年假资格"))
                .findFirst().orElseThrow();
        assertThat(annualStep1.getMetadata().get("heading_path"))
                .asString()
                .startsWith("第一章 年假申请流程");
        assertThat(annualStep1.getMetadata().get("step_title"))
                .asString()
                .contains("确认年假资格");

        // 找到病假第1步
        Document sickStep1 = chunks.stream()
                .filter(d -> d.getText().contains("获取诊断证明"))
                .findFirst().orElseThrow();
        assertThat(sickStep1.getMetadata().get("heading_path"))
                .asString()
                .startsWith("第二章 病假申请流程");
        assertThat(sickStep1.getMetadata().get("step_title"))
                .asString()
                .contains("获取诊断证明");

        // 两个不同章节的第1步 heading_path 应不同（去重key会区分）
        String annualPath = (String) annualStep1.getMetadata().get("heading_path");
        String sickPath = (String) sickStep1.getMetadata().get("heading_path");
        assertThat(annualPath).isNotEqualTo(sickPath);
    }

    @Test
    @DisplayName("无章节文本 → 回退扁平切分（不设 heading_path）")
    void shouldFallbackToFlatChunkingWhenNoChapters() {
        String text = "第1步：填写申请单\n角色：员工本人\n填写请假申请单。\n"
                + "第2步：部门审批\n角色：部门负责人\n审批申请。\n";

        List<Document> chunks = ProcessChunker.chunk(text, "process");

        assertThat(chunks.size()).isGreaterThanOrEqualTo(2);
        // 无章节时 heading_path 不应设置（兼容旧行为）
        Document first = chunks.get(0);
        Object headingPath = first.getMetadata().get("heading_path");
        assertThat(headingPath).isNull();
    }
}
