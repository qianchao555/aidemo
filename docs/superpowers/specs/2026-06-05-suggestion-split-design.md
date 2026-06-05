# 建议问题拆分 — 设计文档

**日期**: 2026-06-05
**分支**: master

---

## 背景

当前 `💡 您可以继续问：` 建议问题由 LLM 生成并混在回答文本中。前端 `extractSuggestions()` 仅按 `\n` 拆分，当 LLM 将多条建议挤在同一行时（如 `问题1？- 问题2？- 问题3？`），三条建议被当成一个整体 chip 渲染。

## 目标

- 建议问题以结构化 `string[]` 从后端传递给前端，不再依赖前端文本解析
- 多重切分兜底，覆盖 LLM 输出格式不稳定的情况

---

## 设计

### 架构

```
LLM 生成完整回答
       │
       ▼
┌─────────────────────────────┐
│ 1. extractSuggestions(text) │ → List<String> → 放入 done 事件
│ 2. stripSuggestions(text)   │ → 纯回答文本 → token 流输出
└─────────────────────────────┘
       │
       ▼
  SSE: token... → done { suggestions: [...] }
       │
       ▼
  前端: msg.suggestions = done.suggestions
```

### 后端 AgentController

**`extractSuggestions(content)`** — 正则提取 `💡 您可以继续问：` 后的文本，多重切分：
- 先按 `\n` 拆行
- 同行的再按 `？-` / `? -` 拆（覆盖挤在一行的情况）
- 清洗 `- `、数字编号等前缀，过滤空串和超长串（>50字）

**`stripSuggestions(content)`** — 从全文剔除建议段落（兼容有无 `---` 前缀）

**token 流** — 用 `stripSuggestions` 处理后的文本做分句发送

**done 事件** — 在 content map 中增加 `"suggestions"` 字段

### 前端 ChatView.vue

**`done` case** — 从 done 事件 content 中读取 `suggestions` 数组，直接设置 `msg.suggestions`

**`renderContent()`** — strip 正则增强为兼容有无 `---` 前缀的情况，作为兜底

### 前端 chat.ts Store

**`extractSuggestions()`** — 增强切分逻辑与后端一致，覆盖以下兜底场景：
- 非流式降级路径（`ragQaChat` 直接返回字符串）
- 从历史消息加载时（`switchSession` 路径）

---

## 改动文件

| 文件 | 改动 |
|------|------|
| `AgentController.java` | +`extractSuggestions` +`stripSuggestions`，修改 token 流 + done 事件 |
| `ChatView.vue` | `done` case 读取 `suggestions`，`renderContent` 增强 strip 正则 |
| `chat.ts` | `extractSuggestions` 切分逻辑增强，`switchSession` 历史消息补上 suggestions 解析 |
