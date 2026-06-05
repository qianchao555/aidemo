# 建议问题拆分 — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 后端结构化输出建议问题列表，前端接收后直接渲染为独立 chips，避免 LLM 输出格式不一致导致的「多合一」问题。

**Architecture:** 后端在 SSE `done` 事件中新增 `suggestions: string[]` 字段，同时将建议段落从 token 流中剥离；前端从 done 事件直接取值，不再依赖文本解析。`extractSuggestions` 保留作为非流式降级和历史消息加载的兜底。

**Tech Stack:** Java 17 (Spring Boot SSE), TypeScript (Vue 3 + Pinia)

---

## 文件清单

| 文件 | 操作 | 职责 |
|------|------|------|
| `backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java` | 修改 | +extractSuggestions, +stripSuggestions, 修改 token 流和 done 事件 |
| `frontend/src/views/agent/ChatView.vue` | 修改 | done case 读 suggestions, renderContent 增强 strip, 非流式降级补 suggestions |
| `frontend/src/stores/chat.ts` | 修改 | extractSuggestions 增强切分, switchSession 历史消息补 suggestions |

---

### Task 1: 后端 — extractSuggestions 和 stripSuggestions 方法

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java`

- [ ] **Step 1: 添加 import**

在文件头部的 import 区域，`java.util.stream.Collectors` 之后添加：

```java
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
```

- [ ] **Step 2: 在 AgentController 类末尾（最后一个 `}` 之前）添加两个私有方法**

```java
    /**
     * 从回答文本中提取「💡 您可以继续问：」段落的建议问题列表。
     * 支持多条建议在同一行（用 ？- 分隔）或分行。
     */
    private List<String> extractSuggestions(String content) {
        if (content == null || content.isBlank()) return List.of();
        Pattern p = Pattern.compile("💡\\s*您可以继续问[：:]\\s*\\n?([\\s\\S]*?)$");
        Matcher m = p.matcher(content);
        if (!m.find()) return List.of();

        String raw = m.group(1).trim();
        List<String> items = new ArrayList<>();
        for (String line : raw.split("\n")) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // 同一行内按 ？- 或 ?- 拆分
            String[] parts = line.split("(?<=[？?])\\s*-\\s*");
            for (String part : parts) {
                part = part.replaceAll("^[-\\s•\\d.、]+", "").trim();
                if (!part.isEmpty() && part.length() <= 50) {
                    items.add(part);
                }
            }
        }
        return items;
    }

    /**
     * 从回答文本中移除「💡 您可以继续问：」段落（含可选的前置 --- 分隔线）。
     */
    private String stripSuggestions(String content) {
        if (content == null || content.isBlank()) return content;
        return content
                .replaceAll("\\n?-*+\\n💡\\s*您可以继续问[：:][\\s\\S]*$", "")
                .replaceAll("\\n💡\\s*您可以继续问[：:][\\s\\S]*$", "");
    }
```

- [ ] **Step 3: 编译验证后端**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java
git commit -m "feat: add extractSuggestions and stripSuggestions to AgentController"
```

---

### Task 2: 后端 — 修改 SSE token 流和 done 事件

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java`

- [ ] **Step 1: 在 `ragQaChatStream` 方法中，`result.response()` 之后插入剥离逻辑**

找到行 98：`String response = result.response();`

在其后添加：

```java
                // 剥离建议问题段落，后续通过 done 事件结构化传递
                List<String> suggestions = extractSuggestions(response);
                String cleanResponse = suggestions.isEmpty() ? response : stripSuggestions(response);
```

- [ ] **Step 2: 修改 token 流，使用 cleanResponse 替代 response**

找到行 118：`String[] segments = response.split("(?<=[。！？\\n])");`

改为：

```java
                String[] segments = cleanResponse.split("(?<=[。！？\\n])");
```

- [ ] **Step 3: 修改 done 事件，增加 suggestions 字段**

找到行 127-132：

```java
                String doneJson = objectMapper.writeValueAsString(
                        Map.of("type", "done", "content",
                                Map.of("threadId", finalThreadId,
                                       "userMsgId", result.userMsgId(),
                                       "assistantMsgId", result.assistantMsgId())));
```

改为：

```java
                String doneJson = objectMapper.writeValueAsString(
                        Map.of("type", "done", "content",
                                Map.of("threadId", finalThreadId,
                                       "userMsgId", result.userMsgId(),
                                       "assistantMsgId", result.assistantMsgId(),
                                       "suggestions", suggestions)));
```

- [ ] **Step 4: 编译验证后端**

```bash
cd backend && mvn compile -q
```

Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java
git commit -m "feat: stream cleaned response and pass structured suggestions in done event"
```

---

### Task 3: 前端 — done 事件处理和非流式降级

**Files:**
- Modify: `frontend/src/views/agent/ChatView.vue`

- [ ] **Step 1: 修改 `done` case，读取 suggestions**

找到 `handleStreamEvent` 函数中 `case 'done':` 块（约 line 364-381）。

将：

```typescript
    case 'done': {
      chatStore.finishMessage(threadId, msgId)
      const doneInfo = event.content as { threadId?: string; userMsgId?: number; assistantMsgId?: number }
```

改为：

```typescript
    case 'done': {
      chatStore.finishMessage(threadId, msgId)
      const doneInfo = event.content as { threadId?: string; userMsgId?: number; assistantMsgId?: number; suggestions?: string[] }
      if (doneInfo.suggestions?.length) {
        const msgs = chatStore.messages[threadId]
        const msg = msgs?.find(m => m.id === msgId)
        if (msg) msg.suggestions = doneInfo.suggestions
      }
```

- [ ] **Step 2: 增强 `renderContent` 的 strip 正则，兼容无 `---` 前缀**

找到 `renderContent` 函数（约 line 255-263）：

```typescript
function renderContent(text: string): string {
  const cleaned = text
    .replace(/【出处】.*?(\n|$)/g, '')
    .replace(/---\n💡\s*您可以继续问：[\s\S]*$/g, '')
    .replace(/\n{3,}/g, '\n\n')
  return marked.parse(cleaned, { async: false }) as string
}
```

改为：

```typescript
function renderContent(text: string): string {
  const cleaned = text
    .replace(/【出处】.*?(\n|$)/g, '')
    .replace(/\n?-*\n💡\s*您可以继续问[：:][\s\S]*$/g, '')
    .replace(/\n{3,}/g, '\n\n')
  return marked.parse(cleaned, { async: false }) as string
}
```

- [ ] **Step 3: 非流式降级路径补上 suggestions 提取**

找到 `handleSend` 中 catch 块的非流式降级代码（约 line 325-335），在 `msg.content = response` 之后补上：

```typescript
    chatStore.appendContent(threadId, assistantMsgId, '')
    try {
      const response = await ragQaChat({ userMessage: text, threadId, department: localStorage.getItem('selectedDepartment') || undefined })
      const msgs = chatStore.messages[threadId]
      if (msgs) {
        const msg = msgs.find(m => m.id === assistantMsgId)
        if (msg) {
          msg.content = response
          msg.sources = extractSourcesFromText(response)
          msg.suggestions = extractSuggestionsFromText(response)  // ★ 新增
        }
      }
```

在 `<script>` 区域内，`extractSourcesFromText` 函数之后，添加 `extractSuggestionsFromText` 函数：

```typescript
function extractSuggestionsFromText(content: string): string[] {
  const match = content.match(/💡\s*您可以继续问[：:]\s*\n?([\s\S]*?)$/)
  if (!match) return []
  const items: string[] = []
  for (const line of match[1].trim().split('\n')) {
    if (!line.trim()) continue
    for (const part of line.split(/(?<=[？?])\s*-\s*/)) {
      const cleaned = part.replace(/^[-\s•\d.、]+/, '').trim()
      if (cleaned && cleaned.length <= 50) items.push(cleaned)
    }
  }
  return items
}
```

- [ ] **Step 4: 前端类型检查**

```bash
cd frontend && npx vue-tsc --noEmit 2>&1 | head -20
```

Expected: no new errors related to our changes.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/agent/ChatView.vue
git commit -m "feat: consume structured suggestions from done event, enhance strip regex"
```

---

### Task 4: 前端 Store — 增强 extractSuggestions + 历史消息补全

**Files:**
- Modify: `frontend/src/stores/chat.ts`

- [ ] **Step 1: 增强 `extractSuggestions` 切分逻辑**

找到 `extractSuggestions` 函数（约 line 67-74）。

将：

```typescript
  function extractSuggestions(content: string): string[] {
    const match = content.match(/💡\s*您可以继续问：\s*\n([\s\S]*?)$/)
    if (!match) return []
    const lines = match[1].trim().split('\n')
    return lines
      .map(l => l.replace(/^-\s*/, '').trim())
      .filter(l => l.length > 0 && l.length <= 50)
  }
```

改为：

```typescript
  function extractSuggestions(content: string): string[] {
    const match = content.match(/💡\s*您可以继续问[：:]\s*\n?([\s\S]*?)$/)
    if (!match) return []
    const items: string[] = []
    for (const line of match[1].trim().split('\n')) {
      if (!line.trim()) continue
      // 同一行内按 ？- 或 ?- 拆分（覆盖 LLM 挤在一行的情况）
      for (const part of line.split(/(?<=[？?])\s*-\s*/)) {
        const cleaned = part.replace(/^[-\s•\d.、]+/, '').trim()
        if (cleaned && cleaned.length <= 50) items.push(cleaned)
      }
    }
    return items
  }
```

- [ ] **Step 2: 在 `switchSession` 中为历史 assistant 消息补充 suggestions**

找到 `switchSession` 函数（约 line 30-49），在 `history.map(...)` 中为 assistant 消息添加 `suggestions`：

将：

```typescript
        messages.value[threadId] = history.map(h => ({
          id: String(h.id),
          role: h.role,
          content: h.content,
          timestamp: new Date(h.createTime).getTime(),
          sources: extractSources(h.content),
          rating: h.rating
        }))
```

改为：

```typescript
        messages.value[threadId] = history.map(h => ({
          id: String(h.id),
          role: h.role,
          content: h.content,
          timestamp: new Date(h.createTime).getTime(),
          sources: h.role === 'assistant' ? extractSources(h.content) : undefined,
          rating: h.rating,
          suggestions: h.role === 'assistant' ? extractSuggestions(h.content) : undefined
        }))
```

- [ ] **Step 3: 前端类型检查**

```bash
cd frontend && npx vue-tsc --noEmit 2>&1 | head -20
```

Expected: no new errors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/stores/chat.ts
git commit -m "feat: enhance extractSuggestions with multi-strategy splitting, apply to history loading"
```
