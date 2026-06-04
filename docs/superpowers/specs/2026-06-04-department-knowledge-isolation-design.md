# 部门级知识隔离 — 设计文档

> **日期：** 2026-06-04
> **状态：** 已确认

## 目标

为知识库系统引入部门维度，实现：
- 用户归属部门，默认查看本部门知识
- 支持跨部门查阅（侧边栏切换）
- 文档上传、搜索、列表按部门隔离
- 内置"全公司"虚拟部门承载通用制度

## 数据模型

### chat_user 表变更

```sql
ALTER TABLE chat_user ADD COLUMN department VARCHAR(64) DEFAULT '全公司';
```

### knowledge_document 表变更

```sql
-- department 字段已存在，加索引
CREATE INDEX idx_kd_dept ON knowledge_document(department);
-- 存量数据 department 为 NULL 的，统一设为"全公司"
UPDATE knowledge_document SET department = '全公司' WHERE department IS NULL;
```

### 预设部门

| 部门 | 说明 |
|------|------|
| 全公司 | 通用制度，全员默认可见 |
| 人力资源 | HR 相关制度流程 |
| 财务 | 报销、预算等财务制度 |
| 研发 | 技术规范、开发流程 |
| 行政 | 办公、资产管理等 |

## 权限模型

- **管理员（admin）**：可上传/编辑所有部门的知识文档，可在部门间切换查看
- **普通用户**：只读访问，默认查看本部门知识，可在部门间切换查阅
- **编辑权**：仅管理员可操作（维持现有模式，本次不做部门级编辑权限）

## 行为规则

1. 用户登录后，默认以本部门视图展示知识库
2. 侧边栏部门切换器，可切换到任意部门（含全公司）
3. 选择"全公司"时，搜索不做部门过滤，展示全部文档
4. 上传文档时，部门为必填项，默认选中侧边栏当前部门
5. FAQ 模块本次不做部门隔离（保持不动）

## 后端改动

### ChatUser 实体

新增 `department` 字段（String）。

### LoginResponse

新增 `department` 字段，`POST /auth/login` 响应中返回。

### KnowledgeDocumentMapper

`findByFilters()` 和 `countByFilters()` 新增 `department` 参数。SQL：当 `department` 非空时追加 `AND department = #{department}`。

### KnowledgeBaseService

- `ingestMultipartFile()` — 新增 `department` 参数，写入 KnowledgeDocument
- `hybridSearch()` — 新增 `department` 参数，SQL 搜索中按 department 过滤
- 向量块 metadata — 新增 `department` 字段

### KnowledgeBaseController

- `POST /upload` — 新增 `department` 请求参数
- `GET /documents` — 新增 `department` 可选查询参数
- `GET /search` — 新增 `department` 可选参数

### DataInitializer

确保初始用户（zhangsan）默认部门为"全公司"。

## 前端改动

### Sidebar（App.vue）

导航区下方新增部门切换器：
- 展开态：显示下拉列表，当前选中部门高亮（珊瑚色）
- 收起态：只显示当前部门首字
- 切换部门时刷新知识库列表数据
- 部门列表从预设常量读取（或从后端获取）

### KnowledgeBase.vue

- 工具栏：新增部门下拉过滤器
- 表格：新增"部门"列，使用不同颜色标签
- 上传对话框：新增"部门"必选下拉框，默认值取侧边栏当前部门
- 搜索：携带当前部门参数

### Store / API

- `DocumentListParams` 新增 `department` 可选字段
- `KnowledgeDocument` 类型已有 `department` 字段
- `upload()` / `fetchDocuments()` 传递 department 参数

## 不在范围内

- FAQ 模块部门化（后续再做）
- 部门级编辑权限（管理员 vs 部门管理员）
- 部门动态增删（目前硬编码 5 个）

## 测试要点

- 用户登录后默认部门视图正确
- 切换部门后列表/搜索正确过滤
- "全公司"部门不过滤数据
- 上传时部门正确写入 DB 和向量 metadata
- 存量数据迁移（NULL → "全公司"）正确
