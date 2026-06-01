# RAG 知识库增量更新、会话管理及流式问答 — 设计文档

日期：2026-06-01

## 概述

对现有公司制度知识库问答系统进行三个方向的全面改造：

1. **增量更新 RAG 文档**：支持文档的删除、重新摄入，修复 FAQ 同步重复向量问题
2. **会话管理前后端打通**：将前端从 localStorage 驱动切换为后端 API 驱动，支持跨设备访问
3. **SSE 流式问答**：提升用户体验，逐 token 渲染回答

改造坚持**方案 B（重建数据表）**：新增 `knowledge_document` 元信息表，向量表 metadata 中强制包含 `document_id`，实现按文档粒度的向量增删。

---

## 一、数据库变更

### 1.1 新增表：knowledge_document

```sql
CREATE TABLE knowledge_document (
    id              BIGSERIAL PRIMARY KEY,
    document_name   VARCHAR(512)  NOT NULL,
    document_type   VARCHAR(32)   NOT NULL,          -- pdf / docx / txt / md / manual
    file_path       VARCHAR(1024),
    file_size       BIGINT,
    category        VARCHAR(64),                     -- 请假 / 考勤 / 报销 / 入职 / 离职 / 转正
    department      VARCHAR(128),
    version         VARCHAR(32)  DEFAULT '1.0',
    effective_date  DATE,
    description     VARCHAR(512),
    chunk_count     INT         DEFAULT 0,
    status          VARCHAR(16) DEFAULT 'active',    -- active / deleted
    create_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP,
    update_time     TIMESTAMP   DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_kd_status ON knowledge_document(status);
CREATE INDEX idx_kd_category ON knowledge_document(category);
```

### 1.2 重建向量表：xiaofuzi_knowledge_base_v2

结构不变（id uuid, content text, metadata json, embedding vector），但要求 metadata 中强制包含 `document_id` 或 `faq_id`。表名从 `xiaofuzi_knowledge_base2` 切换为 `xiaofuzi_knowledge_base_v2`。

### 1.3 不变表

`chat_history` 和 `faq_entry` 表结构不变。

---

## 二、后端设计

### 2.1 新增 Entity

`KnowledgeDocument.java` — 文档元信息实体，与表 `knowledge_document` 映射。字段：id, documentName, documentType, filePath, fileSize, category, department, version, effectiveDate, description, chunkCount, status, createTime, updateTime。

`SessionSummary.java` — 会话摘要 DTO，字段：threadId, title, messageCount, lastUpdateTime。

### 2.2 新增 Mapper

`KnowledgeDocumentMapper.java` + XML，提供：
- `insert` — 插入文档元信息
- `findById` — 按 ID 查询
- `findAllActive` — 查询所有 active 状态文档
- `findByCategory` — 按分类查询
- `update` — 更新元信息（版本、分块数、状态等）
- `softDelete` — 软删除（status → deleted）

### 2.3 新增 Controller：KnowledgeDocumentController

挂载路径 `/knowledge-base/documents`：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/` | 文档列表，支持 category/status 过滤 |
| `GET` | `/{id}` | 文档详情 |
| `POST` | `/{id}/reingest` | 增量更新：删旧向量 → 解析新文件 → 写入新向量 → 更新元信息 |
| `DELETE` | `/{id}` | 删除文档：按 document_id 删全部向量 + 软删除元信息记录 |

### 2.4 改造 Service：KnowledgeBaseService

新增方法：

```java
/**
 * 按 document_id 从向量库精确删除该文档的全部 chunk。
 * 底层调用 PgVectorStore.delete(filterExpression)。
 */
public void deleteByDocumentId(Long documentId)

/**
 * 增量更新：先删旧向量 → 解析文件 → 切分 → 写入新向量 → 更新元信息。
 * 整个过程以 @Transactional 包裹。
 */
@Transactional
public void reingestDocument(Long documentId, MultipartFile file, KnowledgeDocumentMapper mapper)
```

### 2.5 改造 Controller：AgentController

新增会话管理接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/agent/sessions` | 会话列表（从 chat_history 聚合） |
| `GET` | `/agent/sessions/{threadId}/history` | 指定会话历史消息 |
| `DELETE` | `/agent/sessions/{threadId}` | 删除会话 |

新增 SSE 流式接口：

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/agent/rag-qa/chat/stream` | SSE 流式问答，返回事件类型：thinking / token / source / done / error |

流式实现：使用 `SseEmitter`（超时 180s），在 `RagQaAgentService` 中新增 `askStream()` 方法，调用 Agent 的流式接口，将结果逐 token 以 SSE 格式推送。

SSE 事件格式：
```
data: {"type":"thinking","content":"正在检索知识库..."}
data: {"type":"token","content":"年假"}
data: {"type":"token","content":"计算"}
data: {"type":"source","content":{"document":"员工手册.pdf","clause":"第3.2条"}}
data: {"type":"done","content":""}
```

### 2.6 改造 Service：FaqService

修复 `syncToVectorStore()` 方法：更新 FAQ 时先按 `faq_id` 从向量库删除旧条目，再写入新条目。

```java
private void syncToVectorStore(FaqEntry entry) {
    // 1. 如果是更新，先删除旧向量
    //    vectorStore.delete("faq_id == 'X' AND content_type == 'faq_entry'")
    // 2. 写入新向量（metadata 中携带 faq_id）
}
```

### 2.7 配置变更

`application.yml` 中 `table-name` 从 `xiaofuzi_knowledge_base2` 改为 `xiaofuzi_knowledge_base_v2`。启动时 `initialize-schema: true` 自动建新表和索引。

---

## 三、前端设计

### 3.1 会话管理：localStorage → API 驱动

`stores/chat.ts` 改造：

- 新增 `fetchSessions()` — 调 `GET /agent/sessions` 获取会话列表
- 新增 `fetchHistory(threadId)` — 调 `GET /agent/sessions/{threadId}/history` 获取历史消息
- `deleteSession()` — 先调后端删除 API，再更新本地状态
- `onMounted` 时自动 `fetchSessions()`，有历史会话直接展示
- localStorage 降级为缓存层

`api/agent.ts` 新增调用：

```typescript
export const listSessions = () => get<SessionSummary[]>('/agent/sessions')
export const getSessionHistory = (threadId: string) => get<ChatHistoryDto[]>('/agent/sessions/' + threadId + '/history')
export const deleteSessionApi = (threadId: string) => del('/agent/sessions/' + threadId)
```

### 3.2 ChatView.vue：流式渲染

发送消息时使用 `fetch` + `ReadableStream` 消费 SSE，逐 token 追加到消息气泡。在模板中新增引用卡片区域，展示结构化来源信息。

SSE 事件处理：
- `thinking` → 显示 loading 状态
- `token` → 追加到当前 assistant 消息
- `source` → 追加引用信息到消息的 sources 数组
- `done` → 结束流式，关闭 loading

### 3.3 KnowledgeBase.vue：新增文档管理 Tab

在第 4 个 Tab「文档管理」中展示文档列表表格：
- 列：文档名称、类型、分类、版本、分块数、状态、摄入时间
- 操作按钮：详情弹窗 / 重新摄入（上传新版本触发增量更新） / 删除

### 3.4 TypeScript 类型补充

`types/index.ts` 新增：
- `KnowledgeDocument` — 文档元信息
- `SessionSummary` — 会话摘要
- `StreamEvent` — SSE 事件结构
- `ChatMessage` 扩展 `sources` 字段

---

## 四、文件变更清单

### Backend

| 操作 | 文件 |
|------|------|
| 新增 | `entity/KnowledgeDocument.java` |
| 新增 | `mapper/KnowledgeDocumentMapper.java` |
| 新增 | `resources/mapper/KnowledgeDocumentMapper.xml` |
| 新增 | `controller/KnowledgeDocumentController.java` |
| 新增 | `dto/SessionSummary.java` |
| 新增 | `resources/sql/schema-v2.sql` |
| 改造 | `rag/KnowledgeBaseService.java` — 新增 deleteByDocumentId / reingestDocument |
| 改造 | `controller/AgentController.java` — 新增 session API + SSE 流式端点 |
| 改造 | `service/RagQaAgentService.java` — 新增 askStream() |
| 改造 | `rag/FaqService.java` — 修复 syncToVectorStore |
| 改造 | `rag/RagVectorConfig.java` / `application.yml` — 表名切换为 v2 |

### Frontend

| 操作 | 文件 |
|------|------|
| 改造 | `api/agent.ts` — 新增 session + stream API |
| 改造 | `api/knowledge-base.ts` — 新增 document 管理 API |
| 改造 | `stores/chat.ts` — localStorage → API 驱动 |
| 改造 | `stores/knowledge-base.ts` — 新增 document 管理状态和方法 |
| 改造 | `types/index.ts` — 新增类型定义 |
| 改造 | `views/agent/ChatView.vue` — 流式渲染 + 引用卡片 |
| 改造 | `views/knowledge/KnowledgeBase.vue` — 新增文档管理 Tab |

---

## 五、增量更新核心流程

```
用户上传新版本 PDF (员工手册 v2.1, 对应的 document_id=1)
          │
          ▼
  POST /knowledge-base/documents/1/reingest
          │
          │  Step 1: PgVectorStore.delete(filter("document_id == '1'"))
          │          → 向量库中 document_id=1 的全部 chunk 被精确删除
          │
          │  Step 2: DocumentParser 解析新文件
          │
          │  Step 3: TokenTextSplitter 切分 chunk
          │
          │  Step 4: batchAdd() 写入新向量，metadata 带 { document_id: 1 }
          │
          │  Step 5: UPDATE knowledge_document SET version='2.1', chunk_count=N
          ▼
     完成。无重复数据，无脏向量。
```

FAQ 增量同步同理：按 `faq_id` 先 delete 后 add。
