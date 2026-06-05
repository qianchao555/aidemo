package com.xiaofuzi.ai.unit.controller;

import com.xiaofuzi.ai.controller.AgentController;
import com.xiaofuzi.ai.mapper.ChatHistoryMapper;
import com.xiaofuzi.ai.mapper.ChatSessionMapper;
import com.xiaofuzi.ai.hook.RagQaMessageHook;
import com.xiaofuzi.ai.service.RagQaAgentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class AgentControllerTest {

    @Mock RagQaAgentService ragQaAgentService;
    @Mock ChatHistoryMapper chatHistoryMapper;
    @Mock ChatSessionMapper chatSessionMapper;
    @Mock RagQaMessageHook ragQaMessageHook;

    private AgentController controller;

    @BeforeEach
    void setUp() {
        controller = new AgentController(ragQaAgentService,
                chatHistoryMapper, chatSessionMapper, ragQaMessageHook);
    }

    @Test
    @DisplayName("标准多行建议 → 拆分正确")
    void shouldExtractMultipleLines() {
        String content = "回答文本\n💡 您可以继续问：\n- 年假天数怎么计算？\n- 请假需要什么材料？";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).containsExactly("年假天数怎么计算？", "请假需要什么材料？");
    }

    @Test
    @DisplayName("单行合并建议（？-分隔）→ 拆分正确")
    void shouldSplitInlineByQuestionMarkAndDash() {
        String content = "回答\n💡 您可以继续问：\n- 年假怎么计算？- 请假提前几天？- OA如何提交？";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).containsExactly("年假怎么计算？", "请假提前几天？", "OA如何提交？");
    }

    @Test
    @DisplayName("英文冒号格式 → 正确识别")
    void shouldSupportAsciiColon() {
        String content = "answer\n💡 您可以继续问:\n- Q1?\n- Q2?";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).containsExactly("Q1?", "Q2?");
    }

    @Test
    @DisplayName("带数字编号 → 清洗前缀")
    void shouldCleanNumberedPrefixes() {
        String content = "回答\n💡 您可以继续问：\n1. 年假怎么算？\n2. 病假工资比例？";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).containsExactly("年假怎么算？", "病假工资比例？");
    }

    @Test
    @DisplayName("无建议段落 → 返回空列表")
    void shouldReturnEmptyWhenNoSuggestions() {
        String content = "这是普通回答，没有建议问题。";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null 输入 → 返回空列表")
    void shouldReturnEmptyWhenNull() {
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", (String) null);
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("空字符串输入 → 返回空列表")
    void shouldReturnEmptyWhenBlank() {
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", "");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("超过50字过滤")
    void shouldFilterOver50Chars() {
        String longQ = "A".repeat(60);
        String content = "回答\n💡 您可以继续问：\n- " + longQ + "\n- 正常问题";
        @SuppressWarnings("unchecked")
        List<String> result = (List<String>)
                ReflectionTestUtils.invokeMethod(controller, "extractSuggestions", content);
        assertThat(result).containsExactly("正常问题");
    }

    @Test
    @DisplayName("stripSuggestions — 有 --- 分隔线")
    void shouldStripWithDashSeparator() {
        String content = "答文内容\n---\n💡 您可以继续问：\n- 问题1";
        String result = ReflectionTestUtils.invokeMethod(controller, "stripSuggestions", content);
        assertThat(result).isEqualTo("答文内容");
    }

    @Test
    @DisplayName("stripSuggestions — 无 --- 分隔线")
    void shouldStripWithoutDashSeparator() {
        String content = "答文内容\n💡 您可以继续问：\n- 问题1";
        String result = ReflectionTestUtils.invokeMethod(controller, "stripSuggestions", content);
        assertThat(result).isEqualTo("答文内容");
    }

    @Test
    @DisplayName("stripSuggestions — null 输入")
    void stripShouldReturnNullWhenNull() {
        String result = ReflectionTestUtils.invokeMethod(controller, "stripSuggestions", (String) null);
        assertThat(result).isNull();
    }
}
