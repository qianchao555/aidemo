# 知识版本与变更追溯 — 设计文档

**日期**: 2026-06-05
**分支**: master

---

## 背景

当前 `KnowledgeDocument` 有 `version` 字段，但上传时硬编码为 `"1.0"`，无版本历史管理。同一份制度文档的 2025 版和 2026 版上传后是两个独立记录，没有关联关系。检索时无法区分或选择版本，也无法让用户知晓存在历史版本。

## 目标

- 同一份文档的多个版本建立关联，形成版本链
- 默认检索最新版本，用户可切换查询历史版本
- 答案返回后，若引用的文档存在历史版本，在引用出处面板中智能提示
- 新版本上传时，旧版本自动归档（archived），向量数据保留

---

## 一、数据模型

### 新增表 `document_group`

```sql
CREATE TABLE document_group (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(255) NOT NULL,
    latest_document_id BIGINT,
    department     VARCHAR(100),
    status         VARCHAR(20) DEFAULT 'active',
    create_time    TIMESTAMP DEFAULT NOW(),
    update_time    TIMESTAMP DEFAULT NOW()
);
```

### `knowledge_document` 变更

| 变更 | 字段 | 类型 | 说明 |
|------|------|------|------|
| 新增 | `group_id` | bigint | FK → document_group.id |
| 新增 | `is_latest` | boolean | 是否为该组最新版本，默认 true |

`version` 字段不再硬编码 `"1.0"`，上传时由用户指定或自动推断。

### 向量表 metadata 扩展

向量 chunk 的 metadata JSON 新增三个字段：

| 字段 | 值示例 | 说明 |
|------|--------|------|
| `group_id` | `"1"` | 对应 document_group.id，字符串格式 |
| `version` | `"2026"` | 对应 knowledge_document.version |
| `is_latest` | `"true"` | 是否为该组最新版本，字符串格式 |

---

## 二、上传新版本流程

1. 用户上传文件，勾选「这是已有文档的新版本」，从下拉选择要关联的旧文档（仅限同类别同部门的 active 文档）
2. 后端在一个事务中执行：
   - 找到旧文档的 `group_id`，在该 group 下创建新 `KnowledgeDocument`，`is_latest = true`
   - 将同 group 下其他文档的 `is_latest` 改为 false，`status` 改为 `archived`
   - 更新 `document_group.latest_document_id` 指向新文档
3. 向量摄入时，chunk metadata 写入 `group_id`、`version`、`is_latest = "true"`
4. 首次上传的文档（不勾选「新版本」）：自动创建新的 `document_group`，`latest_document_id` 指向该文档

---

## 三、检索逻辑变更

### 3.1 `hybridSearch` 方法

- **默认检索**：向量检索加 filter `is_latest == 'true'`，关键词检索 SQL 加 `AND (metadata->>'is_latest') = 'true'`
- **版本切换检索**：前端传入 `group_id` + `version`，向量检索加 filter `group_id == 'xxx' AND version == 'yyy'`，关键词检索同理
- 传给 LLM 重排序和 formatAsContext 的文档列表保持不变，仅检索阶段过滤

### 3.2 Agent 流式响应新增事件

在 SSE 响应中新增 `version_info` 事件，检索完成后、token 生成前发送：

```json
{
  "type": "version_info",
  "content": {
    "items": [
      {
        "group_id": 1,
        "group_name": "年假制度",
        "current_version": "2026",
        "available_versions": ["2025", "2026"]
      }
    ]
  }
}
```

`RagQaAgentService` 在检索后，根据返回文档的 `group_id` 查询 `document_group`，若某 group 下有 archived 文档（即有历史版本），则生成该事件。

### 3.3 API 参数扩展

`ChatParams` 新增可选字段：

```typescript
versionOverrides?: { group_id: number, version: string }[]
```

用于前端切换版本后重新检索时指定精确版本。

---

## 四、前端改动

### 4.1 知识库管理页（KnowledgeBase.vue）

- 上传弹窗新增复选框「这是已有文档的新版本」
- 勾选后显示文档选择器下拉，列出同类别同部门的 active 文档
- 版本列点击弹出该文档组的版本历史抽屉：展示组内所有版本、各自的状态（active/archived）、上传时间

### 4.2 聊天页（ChatView.vue）

- 引用出处面板每条来源增加版本标记（如 `v2026`）
- 有历史版本的来源，版本号为下拉按钮，点击展开可切换版本
- 切换版本后，更新 `versionOverrides` 参数，重新发起检索请求
- SSE `version_info` 事件处理器：更新 `searchInfoMap[msgId]` 中的版本信息
- 切换版本后检索无结果时，展示「该版本下未找到相关内容」+「切换回最新版本」快捷操作

### 4.3 Store / API 层

- `agent.ts` — `ChatParams` 新增 `versionOverrides`；`ragQaChatStream` / `ragQaChat` 透传
- `chat.ts` — `MessageSource` 扩展 `version`、`group_id`、`has_history` 字段
- `types/index.ts` — `MessageSource` 新增对应字段；新增 `VersionInfo`、`VersionOverride` 类型

### 4.4 UI 交互示意

引用出处面板中每条来源的展示：

```
┌──────────────────────────────────────┐
│ 引用出处                              │
├──────────────────────────────────────┤
│ ①  年假制度                          │
│    > 第三章 请假流程     v2026 [▼]    │  ← 有历史版本，可切换
├──────────────────────────────────────┤
│ ②  考勤管理办法                       │
│    > 第二条 打卡规则      v2025       │  ← 无历史版本，仅展示
└──────────────────────────────────────┘
```

---

## 五、后端改动清单

| 文件 | 变更 |
|------|------|
| 新建 `entity/DocumentGroup.java` | 实体类 |
| 新建 `mapper/DocumentGroupMapper.java` + XML | CRUD |
| `entity/KnowledgeDocument.java` | 新增 `groupId`、`isLatest` 字段 |
| `mapper/KnowledgeDocumentMapper.java` + XML | 新增 `findByGroupId`、按 group 更新 `isLatest` |
| `KnowledgeBaseService.java` | 上传逻辑修改：创建/关联 group，写入 metadata；`hybridSearch` 加版本过滤 |
| `RagQaAgentService.java` | 检索后生成 `version_info` 事件；解析 `versionOverrides` 参数 |
| `controller/AgentController.java` | stream 端点接收 `versionOverrides` |
| `controller/KnowledgeDocumentController.java` | 上传接口接收 `parentDocumentId` 参数 |
| 数据库迁移 SQL | 创建 `document_group` 表；`knowledge_document` 加列 |

---

## 六、边界情况

1. **旧版本向量数据完整性** — 归档不删除 chunk；若管理员手动软删除文档，级联删除对应向量 chunk；切换版本时若 chunk 不可用，提示「该版本数据已不可用」
2. **同一 group 所有版本被删除** — `document_group.status` 标记为 archived，不再出现在版本选择器和智能提示中
3. **已 archived 文档被重新上传** — 不作为版本链节点，作为全新文档创建新的 `document_group`
4. **无历史版本时** — 不发送 `version_info` 事件，来源面板中版本仅做文本展示，无可切换下拉
5. **切换版本检索无结果** — 返回空结果 + 提示「该版本下未找到相关内容」+ 快捷切换回最新版
6. **回退兼容** — 不传 `versionOverrides` 时行为与现有逻辑完全一致（仅查 `is_latest = true`）；现有无 `group_id` 的文档 `is_latest` 默认为 true

---

## 七、测试要点

1. 首次上传文档自动创建 group；上传新版本正确归档旧版本、更新 latest_document_id
2. 默认检索仅返回 `is_latest = true` 的 chunk
3. 指定 `group_id` + `version` 检索返回对应版本 chunk
4. 有历史版本时 SSE 正确发送 `version_info` 事件；无历史版本时不发送
5. 混合检索 + 版本过滤的组合正确性
6. 前端版本下拉 / 切换 / 重新检索 / 空结果回退的交互完整性
7. 不传版本参数时，行为与改动前完全一致
