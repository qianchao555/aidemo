# 防幻觉与流程分步指引 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 程序化拦截低质量/空检索结果防止 LLM 编造，同时将单轮 RAG 问答升级为交互式分步流程引导。

**Architecture:** 在 hybridSearch 内嵌质量评分 → searchKnowledge 工具根据评分返回不同观测文本 → RagQaMessageHook.beforeModel 兜底注入强制指令 → 系统提示词区分流程咨询/事实查询 → 前端渲染建议问题 Chips。

**Tech Stack:** Spring Boot / MyBatis / DashScope LLM / Vue 3 + Element Plus / Pinia

---

## 文件结构

| 文件 | 职责 |
|------|------|
| `backend/.../rag/QualityScore.java` (新建) | 质量评分 record |
| `backend/.../rag/KnowledgeBaseService.java` | 嵌入 assessQuality，hybridSearch 返回质量数据 |
| `backend/.../rag/KnowledgeRetrievalTools.java` | ThreadLocal 状态，searchKnowledge 三态返回 |
| `backend/.../hook/RagQaMessageHook.java` | 质量兜底强制指令注入 |
| `backend/.../config/RagAgentConfiguration.java` | 替换系统提示词 |
| `frontend/src/types/index.ts` | ChatMessage 新增 suggestions |
| `frontend/src/stores/chat.ts` | finishMessage 解析建议问题 |
| `frontend/src/views/agent/ChatView.vue` | 建议 Chips 渲染 + quickAsk |

---

### Task 1: QualityScore 记录类

**Files:**
- Create: `backend/src/main/java/com/xiaofuzi/ai/rag/QualityScore.java`

- [ ] **Step 1: 创建 QualityScore record**

```java
package com.xiaofuzi.ai.rag;

/**
 * 检索结果质量评分 record。
 *
 * @param maxCombined 最高综合分（LLM分 × 10 + RRF分 × 100）
 * @param rrfAvg      RRF 融合分数的平均值
 * @param llmAvg      LLM 重排序分数（1-5 分）的平均值
 * @param passCount   LLM ≥ 3 且 RRF ≥ 0.01 的有效召回数量
 */
public record QualityScore(double maxCombined, double rrfAvg, double llmAvg, int passCount) {

    /** 是否通过质量门槛：至少有一条 chunk 被 LLM 评为 3 分以上且 RRF 融合排名靠前 */
    public boolean passed() {
        return passCount > 0;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/QualityScore.java
git commit -m "feat: add QualityScore record for retrieval quality assessment"
```

---

### Task 2: hybridSearch 嵌入质量评分

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java`

- [ ] **Step 1: 改造 llmRerank 方法，同时返回 LLM 评分 Map**

将 `llmRerank` 的返回值从 `List<Document>` 改为同时输出分数。在方法内将 `scores` map 存储到每个 Document 的 metadata 中，便于下游使用。

修改 `llmRerank` 方法（约第 652 行），在排序完成后，将 LLM 分数写入各 Document 的 metadata：

在方法内 `reranked.sort(...)` 之后、`return` 之前添加：

```java
// ★ 将 LLM 评分写入 metadata，供 assessQuality 使用
for (int i = 0; i < reranked.size(); i++) {
    int idx = candidates.indexOf(reranked.get(i)) + 1;
    reranked.get(i).getMetadata().put("llm_score", scores.getOrDefault(idx, 0.0));
}
```

- [ ] **Step 2: 新增 assessQuality 方法**

在 `KnowledgeBaseService.java` 中，`deduplicateByStructure` 方法之前添加：

```java
/**
 * ★ 检索结果质量评分。
 * 综合 RRF 融合分数和 LLM 重排序分数，判定是否存在有效召回。
 *
 * @param docs       LLM 重排序后的文档列表（metadata 中已含 rrf_score 和 llm_score）
 * @return QualityScore 评分结果，passCount >= 1 表示至少有一条有效召回
 */
private QualityScore assessQuality(List<Document> docs) {
    if (docs == null || docs.isEmpty()) {
        return new QualityScore(0, 0, 0, 0);
    }

    int passCount = 0;
    double maxCombined = 0;
    double rrfSum = 0;
    double llmSum = 0;
    int count = 0;

    for (Document doc : docs) {
        if (doc.getMetadata() == null) continue;
        double rrf = parseMetaDouble(doc.getMetadata().get("rrf_score"));
        double llm = parseMetaDouble(doc.getMetadata().get("llm_score"));
        double combined = llm * 10 + rrf * 100;
        if (combined > maxCombined) maxCombined = combined;
        // LLM ≥ 3 分 且 RRF ≥ 0.01 → 有效召回
        if (llm >= 3 && rrf >= 0.01) passCount++;
        rrfSum += rrf;
        llmSum += llm;
        count++;
    }

    double rrfAvg = count > 0 ? rrfSum / count : 0;
    double llmAvg = count > 0 ? llmSum / count : 0;
    return new QualityScore(maxCombined, rrfAvg, llmAvg, passCount);
}

/** 安全地将 metadata Object 转为 double，支持 String 和 Number 类型 */
private double parseMetaDouble(Object val) {
    if (val == null) return 0;
    if (val instanceof Number n) return n.doubleValue();
    try {
        return Double.parseDouble(val.toString());
    } catch (NumberFormatException e) {
        return 0;
    }
}
```

- [ ] **Step 3: hybridSearch 返回结果新增质量字段**

在 `hybridSearch` 方法（约第 454 行），LLM 重排序之后、去重之前，插入质量评分调用：

```java
// 4.5 ★ 质量评分：在 LLM 重排序后、去重前评估检索结果质量
QualityScore qualityScore = assessQuality(merged);
String qualityStatus = qualityScore.passed() ? "PASSED"
        : (merged.isEmpty() ? "EMPTY" : "LOW_QUALITY");
```

在已有的 `result.put(...)` 块中添加三个新字段：

```java
Map<String, Object> result = new HashMap<>();
result.put("documents", merged);
result.put("vectorCount", vectorResults.size());
result.put("keywordCount", keywordResults.size());
result.put("mergedCount", originalMergedCount);
// ★ 新增质量字段
result.put("qualityStatus", qualityStatus);
result.put("qualityScore", qualityScore);
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java
git commit -m "feat: embed quality assessment in hybridSearch"
```

---

### Task 3: KnowledgeRetrievalTools 三态返回

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeRetrievalTools.java`

- [ ] **Step 1: 添加 ThreadLocal 状态和 QualityStatus 枚举**

替换整个文件内容：

```java
package com.xiaofuzi.ai.rag;

import com.xiaofuzi.ai.context.DepartmentContextHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Agent RAG 知识检索工具类。
 *
 * <p>提供 searchKnowledge 工具供 ReAct Agent 调用，并在检索结果质量不足时
 * 通过 ThreadLocal 向 RagQaMessageHook 传递状态，实现程序化兜底拦截。
 */
@Component
public class KnowledgeRetrievalTools {

    private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalTools.class);

    /** ★ 检索质量状态枚举 */
    public enum QualityStatus { PASSED, EMPTY, LOW_QUALITY }

    /** ★ 最后一次检索的质量状态（ThreadLocal，线程安全） */
    private static final ThreadLocal<QualityStatus> lastQualityStatus =
            ThreadLocal.withInitial(() -> QualityStatus.PASSED);

    /** ★ 最后一次检索的质量评分详情（ThreadLocal） */
    private static final ThreadLocal<QualityScore> lastQualityScore = new ThreadLocal<>();

    private final KnowledgeBaseService knowledgeBaseService;

    public KnowledgeRetrievalTools(KnowledgeBaseService knowledgeBaseService) {
        this.knowledgeBaseService = knowledgeBaseService;
    }

    /** ★ 供 RagQaMessageHook 读取最后一次检索质量状态 */
    public static QualityStatus getLastQualityStatus() {
        return lastQualityStatus.get();
    }

    /** ★ 供 RagQaMessageHook 清理状态，避免跨轮污染 */
    public static void clearQualityStatus() {
        lastQualityStatus.remove();
        lastQualityScore.remove();
    }

    @Tool(description = "从本地知识库中检索与查询相关的知识文档。适用场景：需要引用内部资料、专业知识、行业数据时调用")
    public String searchKnowledge(
            @ToolParam(description = "检索查询关键词或问题") String query) {
        logger.info("RAG工具调用 - searchKnowledge: query='{}', department='{}'",
                query, DepartmentContextHolder.get());

        // ★ 入口处重置状态
        lastQualityStatus.set(QualityStatus.PASSED);
        lastQualityScore.remove();

        List<Document> docs = doSearch(query, 5, 0.0);

        if (docs.isEmpty()) {
            // ★ 空结果兜底：无任何文档命中
            lastQualityStatus.set(QualityStatus.EMPTY);
            return "【系统提示】知识库中未找到任何与「" + query + "」相关的文档内容。"
                    + "你必须直接告知用户未找到相关信息，禁止编造任何内容。"
                    + "建议用户联系 HR 部门获取帮助。";
        }

        // ★ 从 doSearch 中已写入的 ThreadLocal 读取质量状态
        QualityStatus status = lastQualityStatus.get();
        if (status == QualityStatus.LOW_QUALITY) {
            QualityScore score = lastQualityScore.get();
            String scoreInfo = score != null
                    ? "，最高综合分仅 " + String.format("%.1f", score.maxCombined())
                    : "";
            return "【系统提示】检索到的文档内容与用户问题「" + query + "」相关性不足"
                    + scoreInfo + "。"
                    + "你必须直接告知用户未找到相关内容，禁止据此编造任何回答。"
                    + "建议用户：1) 更换关键词重新提问；2) 联系 HR 部门获取帮助。";
        }

        // ★ 有效召回：正常格式化上下文
        return knowledgeBaseService.formatAsContext(docs, 3000);
    }

    @SuppressWarnings("unchecked")
    private List<Document> doSearch(String query, int topK, double threshold) {
        String department = DepartmentContextHolder.get();
        Map<String, Object> result = knowledgeBaseService.hybridSearch(
                query, topK, threshold, department, null);

        // ★ 读取 hybridSearch 返回的质量状态，写入 ThreadLocal
        String qs = (String) result.get("qualityStatus");
        if (qs != null) {
            lastQualityStatus.set(QualityStatus.valueOf(qs));
            Object scoreObj = result.get("qualityScore");
            if (scoreObj instanceof QualityScore qScore) {
                lastQualityScore.set(qScore);
            }
        }

        Object docs = result.get("documents");
        if (docs instanceof List) {
            return (List<Document>) docs;
        }
        return Collections.emptyList();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeRetrievalTools.java
git commit -m "feat: add three-state quality return to searchKnowledge tool"
```

---

### Task 4: RagQaMessageHook 质量兜底

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/hook/RagQaMessageHook.java`

- [ ] **Step 1: 在 FAQ 匹配后新增质量兜底检查**

在 `beforeModel` 方法的 FAQ 非匹配分支（约第 65 行 `return new AgentCommand(previousMessages)` 之前）插入质量兜底逻辑：

替换第 63-66 行：

```java
        // 非 FAQ：不注入任何内容，交给 Agent 通过 systemPrompt + searchKnowledge 工具自行交互
        logger.debug("RAG QA Hook: 非FAQ，交由 Agent 交互式处理 | query='{}'", userQuery);
        return new AgentCommand(previousMessages);
    }
```

为：

```java
        // 非 FAQ：检查上次 searchKnowledge 调用的质量状态
        KnowledgeRetrievalTools.QualityStatus qualityStatus =
                KnowledgeRetrievalTools.getLastQualityStatus();

        if (qualityStatus == KnowledgeRetrievalTools.QualityStatus.EMPTY
                || qualityStatus == KnowledgeRetrievalTools.QualityStatus.LOW_QUALITY) {
            return handleLowQuality(previousMessages);
        }

        // 非 FAQ 且质量通过：交给 Agent 通过 systemPrompt + searchKnowledge 工具自行交互
        logger.debug("RAG QA Hook: 非FAQ，交由 Agent 交互式处理 | query='{}'", userQuery);
        return new AgentCommand(previousMessages);
    }

    /** ★ 质量不足时注入强制兜底指令，覆盖 LLM 其他行为规则 */
    private AgentCommand handleLowQuality(List<Message> previousMessages) {
        List<Message> enriched = new ArrayList<>(previousMessages);
        Message forcedInstruction = new SystemMessage("""
                【最高优先级指令 - 覆盖所有其他规则】
                知识库检索未找到与用户问题匹配的相关信息。
                你必须严格按照以下模板回复，不得编造、推测、补充任何内容：

                抱歉，我未能在知识库中找到与您问题相关的信息。

                建议您：
                1. 尝试更换关键词重新提问（例如使用更具体的术语或简称）
                2. 联系 HR 部门获取人工帮助

                禁止输出任何其他内容。禁止根据常识或训练数据给出回答。""");
        enriched.add(forcedInstruction);
        logger.info("RAG QA Hook: 质量兜底触发，注入强制指令");
        // ★ 清理 ThreadLocal，避免影响后续轮次
        KnowledgeRetrievalTools.clearQualityStatus();
        return new AgentCommand(enriched);
    }
```

需要新增 import：

```java
import com.xiaofuzi.ai.rag.KnowledgeRetrievalTools;
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/hook/RagQaMessageHook.java
git commit -m "feat: add quality fallback forced instruction in RagQaMessageHook"
```

---

### Task 5: 系统提示词 — 流程分步指引

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/config/RagAgentConfiguration.java`

- [ ] **Step 1: 替换 INTERACTIVE_SYSTEM_PROMPT**

将第 31-66 行的 `INTERACTIVE_SYSTEM_PROMPT` 常量完整替换为：

```java
    private static final String INTERACTIVE_SYSTEM_PROMPT = """
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
            3. 格式示例（仅示例，实际步骤名称和数量必须来自检索到的文档内容）：
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

            **流程来源约束**：流程步骤名称、顺序、材料清单、时限必须来自检索到的制度文档。
            如果检索结果中某一步的细节不完整，如实告知用户「文档中对这一步的说明较少」，
            不得编造任何步骤信息。

            ## 精准回答策略
            - 直接调用 searchKnowledge 检索并给出精准答案
            - 回答末尾可提供 ≤2 条同一业务场景的关联建议
            - 引用出处：【出处】文档名 > 章节路径

            ## 建议问题格式（必须遵守）
            每轮回答末尾，必须用以下格式给出 2-3 条用户可能继续问的快捷问题：

            ---
            💡 您可以继续问：
            - [建议问题1：简短，不超过20字]
            - [建议问题2]
            - [建议问题3]

            ## 通用规则
            - 知识库无匹配时如实告知，建议联系 HR
            - 不使用外部知识，不编造
            - 每步信息必须来自检索结果，没有检索依据的内容不准写
            - 回答中引用的材料、时限、条件等必须有检索依据""";
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/config/RagAgentConfiguration.java
git commit -m "feat: replace system prompt with step-by-step flow guidance strategy"
```

---

### Task 6: 前端类型扩展 + Store 建议问题解析

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/stores/chat.ts`

- [ ] **Step 1: ChatMessage 新增 suggestions 字段**

在 `frontend/src/types/index.ts` 的 `ChatMessage` 接口（第 75 行）中添加：

```typescript
/** 聊天消息 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  sources?: MessageSource[]
  rating?: number
  suggestions?: string[]  // ★ 建议问题列表，从回答末尾的「💡 您可以继续问」段落解析
}
```

- [ ] **Step 2: finishMessage 解析建议问题**

在 `frontend/src/stores/chat.ts` 的 `finishMessage` 方法中添加 `suggestions` 解析：

```typescript
function finishMessage(threadId: string, msgId: string) {
  const msgs = messages.value[threadId]
  if (!msgs) return
  const msg = msgs.find(m => m.id === msgId)
  if (msg) {
    msg.sources = extractSources(msg.content)
    msg.suggestions = extractSuggestions(msg.content)  // ★ 解析建议问题
  }
  saveMessagesCache()
}
```

在文件末尾（`return` 之前）添加 `extractSuggestions` 函数：

```typescript
/** ★ 从回答内容中解析「💡 您可以继续问：」段落的建议问题列表 */
function extractSuggestions(content: string): string[] {
  const match = content.match(/💡\s*您可以继续问：\s*\n([\s\S]*?)$/)
  if (!match) return []
  const lines = match[1].trim().split('\n')
  return lines
    .map(l => l.replace(/^-\s*/, '').trim())
    .filter(l => l.length > 0 && l.length <= 50)
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/stores/chat.ts
git commit -m "feat: add suggestion parsing from answer content"
```

---

### Task 7: ChatView.vue — 建议问题 Chips 渲染

**Files:**
- Modify: `frontend/src/views/agent/ChatView.vue`

- [ ] **Step 1: 在消息 bubble 中渲染建议 Chips**

在消息内容 `message-content` 之后、`message-actions` 之前（约第 74-75 行之间）插入：

```html
              <div class="message-content" v-html="renderContent(msg.content)" />
              <!-- ★ 建议问题 Chips -->
              <div v-if="msg.role === 'assistant' && msg.suggestions?.length" class="suggestion-chips">
                <button
                  v-for="(q, qi) in msg.suggestions"
                  :key="qi"
                  class="suggestion-chip"
                  :disabled="sending"
                  @click="quickAsk(q)"
                >{{ q }}</button>
              </div>
              <div v-if="msg.role === 'assistant'" class="message-actions">
```

- [ ] **Step 2: 添加 quickAsk 函数**

在 `<script setup>` 中，`newChat` 函数之前添加：

```typescript
/** ★ 点击建议问题 → 自动填入并发送 */
function quickAsk(question: string) {
  if (sending.value) return
  inputText.value = question
  handleSend()
}
```

- [ ] **Step 3: 更新 renderContent 过滤建议问题段落**

修改 `renderContent` 函数，在渲染前移除「💡 您可以继续问：」段落，避免 Chips 和文本重复显示：

```typescript
function renderContent(text: string): string {
  // 去掉答案中的 【出处】... 标记（已在引用气泡中展示）
  // ★ 同时去掉「💡 您可以继续问：」段落（改为 Chips 渲染）
  const cleaned = text
    .replace(/【出处】.*?(\n|$)/g, '')
    .replace(/---\n💡\s*您可以继续问：[\s\S]*$/g, '')  // ★ 移除建议问题段落
    .replace(/\n{3,}/g, '\n\n')
  return marked.parse(cleaned, { async: false }) as string
}
```

- [ ] **Step 4: 添加 CSS 样式**

在 `</style>` 之前添加：

```css
/* ★ 建议问题 Chips */
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

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/agent/ChatView.vue
git commit -m "feat: add suggestion chips rendering in chat messages"
```

---

### 验证清单

- [ ] 检索完全无结果 → `searchKnowledge` 返回 `EMPTY` → Hook 注入强制指令 → LLM 输出固定兜底话术
- [ ] 检索有结果但 LLM 重排序全部 ≤2 分 → `LOW_QUALITY` → Hook 注入强制指令 → LLM 输出固定兜底话术
- [ ] 正常检索通过 → `PASSED` → LLM 正常生成分步引导 + 建议问题
- [ ] 流程咨询意图 → LLM 先列步骤概览 → 逐步详细引导
- [ ] 事实查询意图 → LLM 精准回答 + 引用出处
- [ ] 建议问题正确解析为 Chips 并渲染
- [ ] 点击 Chip → 自动填入输入框并发送
- [ ] 文本中的「💡 您可以继续问」段落不在正文渲染（避免重复）
- [ ] FAQ 匹配逻辑不受影响
- [ ] ThreadLocal 状态在每次 searchKnowledge 入口重置 + Hook 兜底后清理，不跨轮污染
- [ ] 多轮对话中质量状态在正常会话中不影响后续轮次
