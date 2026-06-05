# 防幻觉与流程分步指引 — 设计文档

**日期**: 2026-06-05
**分支**: master

---

## 背景

当前系统存在两个核心问题：

1. **乱回答**：知识库没有召回或召回质量低时，LLM 可能基于训练数据编造内容，而非如实告知用户
2. **缺少分步引导**：自称「流程指引助手」但实际只是单轮 RAG 问答，对于请假、报销等流程类咨询没有分步交互式引导能力

## 目标

- **防幻觉**：检索结果为空或质量不达标时，程序化拦截，不走 LLM 生成，直接返回固定话术（B 兜底策略）
- **流程分步指引**：LLM 实时基于检索到的制度文档，分步骤引导用户走完一个业务流程，每步有清晰的指引和材料要求
- **建议问题**：每轮回答后给出 2-3 条可点击的快捷问题，用户无需打字，一路点下去即可

---

## 一、架构总览

核心改造：在 Agent 工具调用和 LLM 生成之间插入**质量判定 + 行为控制层**。

```
用户问题
    │
    ▼
┌──────────────────────────────────────┐
│  RagQaMessageHook（现有，FAQ 短路）   │
└──────────────┬───────────────────────┘
               │ 非 FAQ → 进入 Agent
               ▼
┌──────────────────────────────────────┐
│  ReAct Agent（思考-行动-观察循环）     │
│                                      │
│  1. LLM 判断：流程咨询 or 事实查询？   │
│  2. 调用 searchKnowledge 工具        │
│     ├─ hybridSearch → RRF 融合       │
│     ├─ LLM 重排序 → 相关性打分       │
│     └─ ★ 新增：质量评分 assessQuality│
│  3. ★ 质量判定（在 hybridSearch 内）  │
│     ├─ EMPTY → 工具返回"未找到"标记  │
│     ├─ LOW_QUALITY → 工具返回低质标记 │
│     └─ PASSED → 正常返回格式化上下文  │
│  4. ★ RagQaMessageHook.beforeModel() │
│     检测 EMPTY/LOW_QUALITY 状态      │
│     → 注入强制兜底 system 消息        │
│  5. LLM 生成最终回答                  │
│     ├─ 正常：分步流程指引 + 建议问题   │
│     └─ 兜底：强制输出固定话术         │
│  6. 前端渲染消息 + 可点击建议 Chips   │
└──────────────────────────────────────┘
```

---

## 二、质量评分机制（搜空洞）

### 2.0 QualityScore 定义

```java
// 位于 com.xiaofuzi.ai.rag 包，KnowledgeBaseService 同级或内部 record
public record QualityScore(double maxCombined, double rrfAvg, double llmAvg, int passCount) {}
```

### 2.1 评分方法

在 `KnowledgeBaseService.hybridSearch()` 的 LLM 重排序之后、去重之前插入 `assessQuality()`。

```java
// ★ 检索结果质量评分
// 结合 RRF 融合分数和 LLM 重排序分数综合判断
// 返回 QualityScore：(最高综合分, RRF均分, LLM均分, 有效通过数)
private QualityScore assessQuality(List<Document> docs,
        Map<String, Double> rrfScores, Map<String, Integer> llmScores) {

    int passCount = 0;
    double maxCombined = 0;
    for (Document doc : docs) {
        String docId = (String) doc.getMetadata().get("document_id");
        double rrf = rrfScores.getOrDefault(docId, 0.0);
        double llm = llmScores.getOrDefault(docId, 0);
        double combined = llm * 10 + rrf * 100;
        if (combined > maxCombined) maxCombined = combined;
        // LLM ≥ 3 分 且 RRF ≥ 0.01 → 有效召回
        if (llm >= 3 && rrf >= 0.01) passCount++;
    }
    return new QualityScore(maxCombined, rrfScores.values().stream()
            .mapToDouble(Double::doubleValue).average().orElse(0),
            llmScores.values().stream().mapToInt(Integer::intValue).average().orElse(0),
            passCount);
}
```

### 2.2 判定规则

| 条件 | 状态 | 行为 |
|------|------|------|
| `docs.isEmpty()` | `EMPTY` | B 兜底：固定话术 |
| `passCount == 0` | `LOW_QUALITY` | B 兜底：固定话术 |
| `passCount >= 1` | `PASSED` | 正常流程：分步生成 |

### 2.3 实现位置

`assessQuality` 放入 `hybridSearch` 方法内部，LLM 重排序完成后调用。评分结果存入返回的 Map：新增 `"qualityStatus"` 键。

```java
// hybridSearch 返回新增字段
result.put("qualityStatus", qualityStatus.name());  // EMPTY | LOW_QUALITY | PASSED
result.put("qualityScore", qualityScore);
result.put("rrfScores", rrfScores);
result.put("llmScores", llmScores);
```

---

## 三、searchKnowledge 工具改造（搜空洞）

`KnowledgeRetrievalTools.searchKnowledge()` 是 LLM 调用的检索入口。改造后根据质量状态返回不同文本。

### 3.1 ThreadLocal 状态存储

```java
// ★ 供 RagQaMessageHook 读取的最后一次检索质量状态（ThreadLocal 保证线程安全）
private static final ThreadLocal<QualityStatus> lastQualityStatus =
        ThreadLocal.withInitial(() -> QualityStatus.PASSED);

enum QualityStatus { PASSED, EMPTY, LOW_QUALITY }
```

### 3.2 工具方法

```java
@Tool(description = "从本地知识库中检索与查询相关的知识文档...")
public String searchKnowledge(
        @ToolParam(description = "检索查询关键词或问题") String query) {

    List<Document> docs = doSearch(query, 5, 0.0);

    if (docs.isEmpty()) {
        // ★ 空结果标记：无任何文档命中
        lastQualityStatus.set(QualityStatus.EMPTY);
        return "【系统提示】知识库中未找到任何与「" + query + "」相关的文档内容。"
                + "你必须直接告知用户未找到相关信息，禁止编造任何内容。"
                + "建议用户联系 HR 部门获取帮助。";
    }

    // ★ 从 doSearch 返回结果中读质量状态
    QualityStatus status = lastSearchQuality.get();
    if (status == QualityStatus.LOW_QUALITY) {
        lastQualityStatus.set(QualityStatus.LOW_QUALITY);
        QualityScore score = lastSearchScore.get();
        return "【系统提示】检索到的文档内容与用户问题「" + query + "」相关性不足，"
                + "最高综合分仅 " + String.format("%.1f", score.maxCombined()) + "。"
                + "你必须直接告知用户未找到相关内容，禁止据此编造任何回答。";
    }

    lastQualityStatus.set(QualityStatus.PASSED);
    return knowledgeBaseService.formatAsContext(docs, 3000);
}
```

### 3.3 doSearch 补充

`doSearch()` 调用 `hybridSearch` 后，将返回的 `qualityStatus` 写入 `ThreadLocal`：

```java
private List<Document> doSearch(String query, int topK, double threshold) {
    Map<String, Object> result = knowledgeBaseService.hybridSearch(
            query, topK, threshold, department, null);
    // ★ 写入 ThreadLocal 供 searchKnowledge 读取
    String qs = (String) result.get("qualityStatus");
    lastSearchQuality.set(qs != null ? QualityStatus.valueOf(qs) : QualityStatus.PASSED);
    QualityScore score = (QualityScore) result.get("qualityScore");
    if (score != null) lastSearchScore.set(score);
    @SuppressWarnings("unchecked")
    List<Document> docs = (List<Document>) result.get("documents");
    return docs != null ? docs : List.of();
}
```

---

## 四、RagQaMessageHook 改造（B 兜底）

在现有 FAQ 匹配逻辑之后，新增质量兜底检查。Hook 在 LLM 每次推理前触发，是程序化拦截的最后一道防线。

```java
@Override
public List<Message> beforeModel(List<Message> messages) {
    // ===== 现有 FAQ 匹配逻辑（保持不变）=====
    // ...

    // ===== ★ 新增：检索质量兜底 =====
    QualityStatus status = KnowledgeRetrievalTools.lastQualityStatus();

    if (status == QualityStatus.EMPTY || status == QualityStatus.LOW_QUALITY) {
        // 在消息列表末尾注入最高优先级强制指令
        // 覆盖 LLM 的其他行为规则，确保不会编造回答
        Message forcedInstruction = new SystemMessage("""
                【最高优先级指令 - 覆盖所有其他规则】
                知识库检索未找到与用户问题匹配的相关信息。
                你必须严格按照以下模板回复，不得编造、推测、补充任何内容：

                抱歉，我未能在知识库中找到与您问题相关的信息。

                建议您：
                1. 尝试更换关键词重新提问（例如使用更具体的术语或简称）
                2. 联系 HR 部门获取人工帮助

                禁止输出任何其他内容。禁止根据常识或训练数据给出回答。
                """);
        messages.add(forcedInstruction);

        // 重置状态，避免影响后续轮次
        KnowledgeRetrievalTools.clearQualityStatus();
    }

    return messages;
}
```

**双重约束设计**：
1. 工具层：`searchKnowledge` 返回的观测文本已是"【系统提示】禁止编造"指令
2. Hook 层：在 LLM 推理前最后时刻注入 system 级强制指令

---

## 五、系统提示词改造（流程分步指引）

替换 `RagAgentConfiguration` 中的 `INTERACTIVE_SYSTEM_PROMPT`。

```
你是公司内部制度问答助手，也是「流程指引助手」。
你的核心工作方式是：先判断用户意图，再采取不同策略。

## 可用工具
- searchKnowledge(query)：搜索公司内部制度文档。仅在准备给出最终答案时调用。

## 意图判断（第一步）
收到用户问题后，先判断意图类型：
- **流程咨询**：用户想了解某件事「怎么做」「流程是什么」「需要什么材料」
  · 例如：请假、报销、离职、入职、转正、调岗、加班申请等
  · → 执行「流程分步引导」策略
- **事实查询**：用户想了解某条具体规定、标准、定义
  · 例如：年假天数标准、病假工资比例、加班费计算方式
  · → 执行「精准回答」策略

## 流程分步引导策略
当用户进行流程咨询时：

**第1轮：给出流程概览**
1. 调用 searchKnowledge 检索相关制度文档
2. 基于检索结果，列出该流程的完整步骤（通常3-6步），用序号标注
3. 格式示例：
"关于[流程名称]，根据公司制度，主要分为以下步骤：
① [步骤一名称] — [一句话说明]
② [步骤二名称] — [一句话说明]
③ [步骤三名称] — [一句话说明]
...
请问您准备好了吗？我们从第①步开始？"

**后续轮次：逐步详细引导**
用户确认后，每一步给出：
- 该步的详细说明（资格条件 / 操作方式 / 注意事项）
- 需要的材料清单（如有，逐项列出）
- 时限要求（如有）
- 完成后询问："这一步清楚了吗？需要我继续讲解第N步吗？"

**步骤间灵活跳转**：用户可以说「跳过这一步」「重新讲第X步」「直接讲最后一步」，灵活响应。

**流程来源约束**：流程步骤名称、顺序、材料清单、时限必须来自检索到的制度文档。如果检索结果中某一步的细节不完整，如实告知用户「文档中对这一步的说明较少」，不得编造。

## 精准回答策略
- 直接调用 searchKnowledge 检索并给出精准答案
- 回答末尾可提供 ≤2 条同一业务场景的关联建议
- 引用出处：【出处】文档名 > 章节路径

## 建议问题格式（必须遵守）
每轮回答末尾，必须用以下格式给出 2-3 条用户可能继续问的快捷问题：

---
💡 您可以继续问：
- [建议问题1：简短、可直接点击发送的问题，不超过20字]
- [建议问题2]
- [建议问题3]

示例：
---
💡 您可以继续问：
- 需要准备哪些材料？
- 年假天数是怎么计算的？
- 审批需要多长时间？

## 通用规则
- 知识库无匹配时如实告知，建议联系 HR
- 不使用外部知识，不编造
- 每步信息必须来自检索结果，没有检索依据的内容不准写
- 回答中引用的材料、时限、条件等必须有检索依据
```

---

## 六、前端改动

### 6.1 建议问题解析

在 `chat.ts` store 的 `finishMessage` 中解析「💡 您可以继续问：」段落，提取建议问题存入 `msg.suggestions` 数组。

```typescript
function extractSuggestions(content: string): string[] {
  const match = content.match(/💡\s*您可以继续问：\s*\n([\s\S]*?)$/)
  if (!match) return []
  const lines = match[1].trim().split('\n')
  return lines
    .map(l => l.replace(/^-\s*/, '').trim())
    .filter(l => l.length > 0)
}
```

### 6.2 ChatView.vue — 建议问题 Chips

在每条 assistant 消息 bubble 底部、actions 行之前渲染：

```html
<div v-if="msg.suggestions?.length" class="suggestion-chips">
  <button
    v-for="(q, qi) in msg.suggestions"
    :key="qi"
    class="suggestion-chip"
    :disabled="sending"
    @click="quickAsk(q)"
  >{{ q }}</button>
</div>
```

`quickAsk` 函数：
```typescript
function quickAsk(question: string) {
  inputText.value = question
  handleSend()
}
```

### 6.3 样式

```css
.suggestion-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 12px;
  padding-top: 10px;
  border-top: 1px solid var(--border-light);
}

.suggestion-chip {
  padding: 6px 14px;
  font-size: 12px;
  color: #4338CA;
  background: #EEF2FF;
  border: 1px solid #C7D2FE;
  border-radius: 20px;
  cursor: pointer;
  font-family: inherit;
  transition: all 0.15s;
}

.suggestion-chip:hover:not(:disabled) {
  background: #E0E7FF;
  border-color: #A5B4FC;
}

.suggestion-chip:disabled {
  opacity: 0.5;
  cursor: not-allowed;
}
```

### 6.4 类型扩展

`ChatMessage` 接口新增：
```typescript
export interface ChatMessage {
  // ...existing fields...
  suggestions?: string[]
}
```

---

## 七、改动文件清单

| 文件 | 变更 |
|------|------|
| `KnowledgeBaseService.java` | 新增 `assessQuality()` 方法；`hybridSearch` 返回新增 `qualityStatus`、`qualityScore` 字段 |
| `KnowledgeRetrievalTools.java` | `searchKnowledge` 改写，根据质量状态返回不同文本；ThreadLocal 状态管理；`doSearch` 读取质量状态 |
| `RagQaMessageHook.java` | 新增质量兜底检查，注入强制指令 |
| `RagAgentConfiguration.java` | 替换 `INTERACTIVE_SYSTEM_PROMPT`，新增意图判断 + 分步引导策略 + 建议问题格式 |
| `frontend/src/stores/chat.ts` | `finishMessage` 新增 `suggestions` 解析；`ChatMessage` 类型扩展 |
| `frontend/src/types/index.ts` | `ChatMessage` 新增 `suggestions?: string[]` |
| `frontend/src/views/agent/ChatView.vue` | 消息底部渲染建议 Chips；新增 `quickAsk` 函数；CSS 样式 |

---

## 八、边界情况

1. **空结果时 LLM 仍然编造** — Hook 注入的强制指令在消息列表最末尾，对 LLM 推理影响最大；工具返回的文本本身也是「【系统提示】禁止编造」，双重约束
2. **过渡截断** — 如果 LLM 的回答质量很低（如「我不知道」），由兜底机制直接接管
3. **建议问题格式不稳定** — `extractSuggestions` 解析失败的容错：正则匹配不到就直接返回空数组，前端无 chips 渲染，不影响正常消息显示
4. **已有 FAQ 短路机制不冲突** — FAQ 命中在质量评分之前就短路返回了，FAQ 内容被视为可靠回答，不走兜底逻辑
5. **ThreadLocal 内存泄漏** — 每次请求结束后在 Hook 中调用 `clearQualityStatus()` 清理；同时在 `searchKnowledge` 入口处也重置
6. **建议问题点在对话中途切换话题** — 用户点的建议问题是上一轮 LLM 给的，发送到新的一轮，由 LLM 在新上下文中处理，符合预期
7. **流程检索无结果时** — 走 EMPTY 兜底，固定话术告知用户未找到相关制度文档
8. **回退兼容** — 不传 `versionOverrides` 时行为与现有逻辑完全一致

---

## 九、测试要点

1. 完全空结果检索 → 返回固定话术，不编造内容
2. 有召回但 LLM 重排序全部 ≤2 分 → LOW_QUALITY 兜底，不编造
3. 正常检索 → 分步流程引导输出
4. 流程咨询意图识别 → 输出步骤概览 + 建议问题
5. 事实查询意图识别 → 精准回答 + 引用出处
6. 建议问题解析 → 前端正确渲染 Chips
7. 点击 Chip → 自动填入输入框并发送
8. FAQ 匹配 → 不影响现有短路逻辑
9. ThreadLocal 状态在请求结束后被清理
10. 多轮对话中质量状态不跨轮污染
