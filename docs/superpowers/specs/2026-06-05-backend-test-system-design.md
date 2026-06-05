# 项目后端测试体系 — 设计文档

**日期**: 2026-06-05
**分支**: master

---

## 背景

当前项目无测试代码，需建立完整的测试体系。

## 目标

- 单元测试覆盖核心业务逻辑（Service、Controller 工具方法、Parser、RAG 组件）
- 集成测试覆盖 Controller HTTP 层和 Service 全链路，Mapper 层 Mock
- Spring Boot 标准测试路径：`src/test/java/com/xiaofuzi/ai/`

---

## 一、架构

```
src/test/java/com/xiaofuzi/ai/
├── unit/                          # 单元测试（不启动 Spring）
│   ├── service/
│   │   ├── RagQaAgentServiceTest.java
│   │   ├── FaqServiceTest.java
│   │   └── QualityServiceTest.java
│   ├── controller/
│   │   └── AgentControllerTest.java
│   ├── util/
│   │   └── ResultTest.java
│   ├── rag/
│   │   ├── KnowledgeRetrievalToolsTest.java
│   │   └── QualityScoreTest.java
│   └── parser/
│       ├── HeadingChunkerTest.java
│       └── ProcessChunkerTest.java
│
└── integration/                   # 集成测试（启动 Spring，Mock Mapper）
    ├── config/
    │   └── TestConfig.java        # 公共 Mock 配置
    ├── controller/
    │   ├── AgentControllerIT.java
    │   ├── AuthControllerIT.java
    │   ├── FaqControllerIT.java
    │   └── KnowledgeBaseControllerIT.java
    └── service/
        ├── RagQaAgentServiceIT.java
        └── FaqServiceIT.java
```

- **单元测试**：JUnit 5 + Mockito，不加载 Spring，毫秒级
- **集成测试**：`@SpringBootTest` + `@MockBean`，Mock 全部 Mapper，测 HTTP 和 Service 流程
- 技术栈：`spring-boot-starter-test`（已有），JUnit 5，Mockito，MockMvc

---

## 二、单元测试用例

### AgentControllerTest

- 标准多行建议 → `["问题1", "问题2"]`
- 单行合并建议（？-分隔）→ 正确拆分
- 英文冒号格式 → 正确识别
- 带数字编号 → 清洗编号前缀
- 无建议段落 → `[]`
- null / 空串 → `[]`
- 超过 50 字过滤 → 被过滤
- stripSuggestions（有 --- / 无 ---）→ 正确剥离

### RagQaAgentServiceTest

- 新建会话问答 → 返回 AskResult，保存 history
- 追加历史问答 → 注入上下文后调用 agent
- 空问题 → 异常
- extractSourceInfo → 正确解析 doc/heading
- buildVersionInfo（无结果 / 单版本无历史）→ 正确处理

### QualityScoreTest

- PASSED / LOW_QUALITY / EMPTY 状态判定

### HeadingChunkerTest / ProcessChunkerTest

- 按标题切分 / 按步骤序号切分 / 空文档返回空列表

### ResultTest / KnowledgeRetrievalToolsTest

- 统一响应封装
- 检索工具参数校验

---

## 三、集成测试用例

### AgentControllerIT

- POST /rag-qa/chat/stream → 200 + SSE 事件
- POST /rag-qa/chat → 200 + Result
- POST /sessions → 200 + SessionSummary
- GET /sessions → 200 + 列表
- GET /sessions/{id}/history → 200 + ChatHistory[]
- POST /sessions/{id}/feedback → 200

### AuthControllerIT

- 正确凭证登录 → 200 + token
- 错误密码 → 401

### FaqControllerIT

- FAQ 分页查询 / 分类筛选 / 新增 / 更新状态

### KnowledgeBaseControllerIT

- 文档列表 / 上传 / 删除

### Service 层集成测试

- RagQaAgentServiceIT：Mock agent + mapper，验证问答流程
- FaqServiceIT：Mock mapper，验证 FAQ CRUD 流程

### TestConfig

- `@MockBean` 注入所有 Mapper（ChatHistoryMapper, ChatSessionMapper, FaqEntryMapper, KnowledgeDocumentMapper, DocumentGroupMapper, ChatUserMapper）
- `@MockBean` 注入 RagQaAgentService（Controller 集成测试时 Mock，避免真实 LLM 调用）

---

## 四、不测的范围

- Mapper 层（MyBatis SQL）— 无真实数据库，Mock 掉
- PgVector 向量检索 — 无 PG 容器，Mock 掉
- LLM 调用（DashScope / OpenAI）— 外部依赖，Mock 掉
- 配置类（Spring 自动配置验证）— 已有框架保证
