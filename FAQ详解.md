# FAQ 模块技术文档

## 一、架构总览

FAQ 模块是 RAG 智能问答系统的**快速应答层**，位于用户提问与 Agent 交互之间，承担"高频标准问题秒级响应"的职责。

```
                          ┌─────────────────────────┐
                          │      用户提问            │
                          └───────────┬─────────────┘
                                      │
                          ┌───────────▼─────────────┐
                          │   RagQaMessageHook      │  BEFORE_MODEL Hook
                          │   (前置拦截层)           │
                          └───────────┬─────────────┘
                                      │
                    ┌─────────────────┼─────────────────┐
                    │                 │                 │
              FAQ 命中          未命中 + 质量不足    未命中 + 质量通过
                    │                 │                 │
              ┌─────▼─────┐   ┌──────▼──────┐   ┌──────▼──────┐
              │ 直接返回   │   │ 强制兜底    │   │ Agent 交互  │
              │ 标准答案   │   │ 提示模板    │   │ + 文档检索  │
              └───────────┘   └─────────────┘   └─────────────┘
```

模块由三个核心子系统组成：

| 子系统 | 入口 | 职责 |
|--------|------|------|
| **在线匹配** | `FaqService.match()` → `RagQaMessageHook` | 用户提问时实时匹配 FAQ，命中则直接返回标准答案 |
| **候选挖掘** | `FaqService.getFaqCandidates()` → `GET /faq/candidates` | 从聊天记录中分析高频未命中提问，推荐创建新 FAQ |
| **数据管理** | `FaqController` CRUD + 导入导出 | FAQ 条目的增删改查、Excel/CSV 批量导入导出 |

---

## 二、数据模型

### 2.1 数据库表 `faq_entry`

```sql
CREATE TABLE IF NOT EXISTS faq_entry (
    id           BIGSERIAL PRIMARY KEY,
    question     VARCHAR(512)  NOT NULL,          -- 标准问题
    answer       TEXT          NOT NULL,          -- 标准答案
    keywords     VARCHAR(512),                    -- 关键词（逗号分隔，用于关键词匹配）
    category     VARCHAR(64),                     -- 分类标签
    source_doc   VARCHAR(512),                    -- 来源文档
    heading_path VARCHAR(1024),                   -- 章节路径
    hit_count    INTEGER       NOT NULL DEFAULT 0,-- 命中次数
    status       VARCHAR(16)   NOT NULL DEFAULT 'active',  -- active / inactive / deleted
    create_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_hit_time TIMESTAMP                        -- 最近命中时间
);
```

### 2.2 实体类

`FaqEntry` — 使用 Lombok `@Builder` 模式，字段与数据库一一对应。

### 2.3 匹配结果 DTO

`FaqMatchResult` — 不可变 record 风格，包含 `entry`（匹配到的 FAQ）、`matchType`（命中类型）、`matched`（是否命中）。提供静态工厂方法 `hit()` 和 `noMatch()`。

---

## 三、FAQ 摄入（数据来源）

FAQ 数据有两条摄入路径：

### 3.1 路径一：Excel/CSV 文件导入

**入口**：`FaqController.importFaq()`

```
前端上传文件 (.csv/.xlsx/.xls)
  → Apache POI WorkbookFactory 解析
  → 逐行读取: col0=问题, col1=答案, col2=分类, col3=关键词
  → 逐条调用 FaqService.create()
      ├── faqEntryMapper.insert()     → 写入 PostgreSQL faq_entry 表
      └── syncToVectorStore()         → 同步到 pgvector 向量库
```

**文件格式要求**：

| 列 | 内容 | 必填 |
|----|------|------|
| A | 问题 | ✅ |
| B | 答案 | 否（空则写入空字符串） |
| C | 分类 | 否 |
| D | 关键词 | 否 |

### 3.2 路径二：FAQ 文档解析摄入

当上传的文档类别为 "FAQ/问答/常见问题" 时，`KnowledgeBaseService.chunkSmart()` 路由到 `FaqChunker`。

`FaqChunker` 支持的 Q&A 格式：

| 格式 | 示例 |
|------|------|
| 英文标记 | `Q: ...` `A: ...` |
| 中文标记 | `问：...` `答：...` |
| 编号问题 | `问题1：...` `问题一：...` |
| 数字开头 | `1. 什么/如何/怎么/为什么...` |
| FAQ 标记 | `FAQ 1：...` |

每个 Q&A 对作为一个独立 chunk，携带 `qa_question`、`qa_answer` 元数据，标记 `skip_split=true` 避免二次切分。

### 3.3 向量库同步

`FaqService.syncToVectorStore()` 的流程：

```
1. 按 faq_id + content_type='faq_entry' 删除旧向量（支持更新场景）
2. 构造元数据:
   - content_type = "faq_entry"     ← 关键标记，用于 hybridSearch 排除
   - faq_id, faq_category, source, heading_path
   - skip_split = true              ← 避免被 TokenTextSplitter 二次切分
3. 文本格式: "【FAQ】问题\n答案"
4. 调用 KnowledgeBaseService.ingestParsedDocuments() 写入向量库
```

**重要**：FAQ 向量在 `hybridSearch()` 中被 `filterNonFaq()` 排除，因为 FAQ 走前置精确匹配，不应出现在文档检索结果中。

---

## 四、在线匹配流程（核心）

### 4.1 触发时机

`RagQaMessageHook` 注册在 `HookPosition.BEFORE_MODEL`，在每次用户新发言时触发：

```
isNewUserTurn() → 检查最后一条消息是否为 UserMessage
  → 是: extractUserQuery() → faqService.match()
  → 否: 跳过（工具调用链中的循环不重复触发）
```

### 4.2 四级匹配策略

`FaqService.match()` 按优先级依次尝试：

```
用户提问
  │
  ├── ① 精确匹配 (exact)                    延迟: ~0ms
  │   normalize(用户提问) == normalize(FAQ问题)
  │   例: "怎么请假？" → normalize → "怎么请假" == "怎么请假" ✅
  │
  ├── ② 模糊匹配 (fuzzy)                    延迟: ~0ms
  │   normalize(用户提问) 包含 normalize(FAQ问题) 或反之
  │   评分: 完全相等+100, FAQ包含用户+50, 用户包含FAQ+30
  │   取最高分
  │   例: "请假申请流程是什么" 包含 "请假申请流程" ✅
  │
  ├── ③ 关键词匹配 (keyword)                 延迟: ~0ms
  │   用户提问的标准化文本 包含 FAQ 的 keywords 中任一关键词
  │   keywords 按逗号分隔
  │   例: 用户问"发工资日期", FAQ keywords="工资发放时间,发工资日期" ✅
  │
  ├── ④ 语义匹配 (semantic)                  延迟: ~100-500ms
  │   embedding(用户提问) 与 缓存的 FAQ embedding 计算余弦相似度
  │   相似度 > semanticMatchThreshold(0.70) 则命中
  │   例: 用户问"每月几号发钱", FAQ问题="工资什么时候发", 相似度 0.78 ✅
  │
  └── 未命中 → 交给 Agent + hybridSearch
```

### 4.3 文本标准化

`normalize()` 用于前三级匹配：

```java
text.trim()
    .replaceAll("[？?！!。，,、；;：:　 ]", "")  // 去标点空格
    .toLowerCase()                                // 转小写
```

### 4.4 语义匹配细节

`semanticMatch()`：

```
1. embeddingModel.embed(userQuery)  → 用户提问向量（1次API调用）
2. 遍历所有活跃 FAQ:
   └── getOrComputeFaqEmbedding(entry) → 从 ConcurrentHashMap 缓存读取
       └── 缓存未命中: embeddingModel.embed(question) → 写入缓存
3. 计算余弦相似度，取最高分
4. 最高分 > semanticMatchThreshold → 命中
```

**缓存策略**：`ConcurrentHashMap<faqId, float[]>`，FAQ 创建/更新/删除时自动失效。每次语义匹配只需 1 次 embedding 调用（用户 query），FAQ 侧全部来自缓存。

### 4.5 命中后的处理

`RagQaMessageHook.handleFaqHit()`：

```
命中后注入 SystemMessage:
  "【FAQ 标准答案 - 命中类型：exact/fuzzy/keyword/semantic】
   以下为该问题的标准答案，请直接输出此内容，无需额外检索或修改：
   问题：{question}
   答案：{answer}
   【出处】FAQ 标准答案库"

同时: incrementHitFaq() → 更新 hit_count 和 last_hit_time
```

LLM 收到此指令后直接输出标准答案，**完全绕过 Agent 交互和知识库检索**。

---

## 五、候选挖掘（离线分析）

### 5.1 触发方式

运营人员在 FAQ 管理页面展开"候选挖掘"面板，设置最低频次后点击"开始挖掘"。

**API**：`GET /faq/candidates?limit=20&minFrequency=3`

### 5.2 五步处理流程

`FaqService.getFaqCandidates()`：

```
第一步: SQL 标准化聚合
  SELECT LOWER(REGEXP_REPLACE(TRIM(content), '[？?!！。，,、；;：:　 ]', '', 'g')) AS query,
         COUNT(*) AS cnt
  FROM chat_history WHERE role = 'user'
  GROUP BY 标准化文本
  HAVING COUNT(*) >= 2
  ORDER BY cnt DESC

第二步: minFrequency 过滤 + 频次降序排列

第三步: 批量 Embedding 向量化
  每批最多 50 条，调用 embeddingModel.embed(batch)

第四步: 贪心质心聚类 (clusterThreshold=0.85)
  按频次从高到低遍历:
    ├── 与已有簇质心计算余弦相似度
    ├── 最高相似度 > 0.85 → 归入该簇，频次加权更新质心
    └── 否则 → 新建簇

第五步: 覆盖过滤 (coverageThreshold=0.85)
  对每个簇:
    ├── 簇质心 vs 所有已有 FAQ 问题向量
    ├── 任一 FAQ 相似度 > 0.85 → 已覆盖，丢弃
    └── 否则 → 保留（真正的知识盲区）

第六步: 按簇总频次降序，取 Top N
  返回: [{question, frequency, suggestedKeywords?}, ...]
```

### 5.3 质心更新公式

```
new_centroid[d] = (centroid[d] × totalFreq + vec[d] × freq) / (totalFreq + freq)
```

高频 query 对质心的贡献更大，低频 query 只微调方向。

### 5.4 suggestedKeywords 机制

同一语义簇中，除代表问题外的其他问法自动作为 `suggestedKeywords` 返回：

```json
{
  "question": "工资什么时候发",
  "frequency": 23,
  "suggestedKeywords": "工资发放时间, 发工资日期"
}
```

前端点击"创建 FAQ"时，keywords 字段自动填入这些值，确保通过第三级关键词匹配覆盖同簇的其他问法。

---

## 六、统计与监控

### 6.1 统计概览

`GET /faq/stats` 返回：

```json
{
  "totalFaq": 156,      // 活跃 FAQ 总数
  "totalHits": 3420,    // 历史总命中次数
  "todayHits": 87       // 今日命中次数
}
```

### 6.2 命中趋势

`GET /faq/stats/trend?days=30` — 近 N 天每日命中次数折线图数据。

### 6.3 分类分布

`GET /faq/stats/category-distribution` — 各分类的命中次数饼图数据。

### 6.4 高频 FAQ

`GET /faq/high-freq?limit=10` — 命中次数 Top N 的 FAQ 列表。

### 6.5 命中计数

每次匹配命中时调用 `incrementHitFaq()`：

```sql
UPDATE faq_entry SET hit_count = hit_count + 1, last_hit_time = NOW() WHERE id = ?
```

---

## 七、配置参数

`application.yml`：

| 参数 | 默认值 | 用途 | 调优建议 |
|------|--------|------|---------|
| `cluster-threshold` | 0.85 | 贪心聚类相似度阈值 | 降低→更多小簇，升高→更少大簇 |
| `coverage-threshold` | 0.85 | 覆盖过滤阈值 | 降低→更激进过滤，升高→更保守 |
| `similarity-threshold` | 0.70 | 相似 FAQ 检测阈值（管理后台警告） | 仅作提示，误报代价低 |
| `semantic-match-threshold` | 0.70 | 语义匹配阈值（在线自动应答） | 需平衡精度和召回 |
| `embedding-batch-size` | 50 | embedding 批次大小 | 取决于 API 限制 |

**阈值梯度设计原则**：

```
0.70  similarity-threshold      管理后台警告 ← 最宽松
0.70  semantic-match-threshold  在线自动应答 ← 兜底匹配
0.85  cluster-threshold         离线聚类   ← 更严格
0.85  coverage-threshold        离线过滤   ← 最严格
```

---

## 八、API 参考

### 8.1 管理类 API（需 admin 角色）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/faq/create-faq` | 创建 FAQ |
| PUT | `/faq/faq/{id}` | 更新 FAQ |
| DELETE | `/faq/faq/{id}` | 删除 FAQ（软删除） |
| POST | `/faq/faq/batch-delete` | 批量删除 |
| POST | `/faq/faq/batch-update-category` | 批量更新分类 |
| POST | `/faq/faq/batch-update-status` | 批量更新状态 |
| POST | `/faq/faq/import` | 导入 FAQ（Excel/CSV） |
| GET | `/faq/candidates` | 候选挖掘 |

### 8.2 查询类 API

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/faq/faq/{id}` | 获取单个 FAQ |
| GET | `/faq/faq` | 分页列表（支持 category/status/keyword 筛选和排序） |
| GET | `/faq/faq/similar` | 相似 FAQ 检测 |
| GET | `/faq/faq/export` | 导出 FAQ（CSV/Excel） |
| GET | `/faq/faq/high-freq` | 高频 FAQ Top N |
| GET | `/faq/faq/stats` | 统计概览 |
| GET | `/faq/faq/stats/trend` | 命中趋势 |
| GET | `/faq/faq/stats/category-distribution` | 分类命中分布 |

---

## 九、完整数据流图

```
┌──────────────────────────────────────────────────────────────────────┐
│                           FAQ 模块全流程                              │
├──────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  ┌─────────────────── 数据摄入 ───────────────────┐                  │
│  │                                                 │                  │
│  │  Excel/CSV 导入 ──→ FaqController.importFaq()   │                  │
│  │                        │                        │                  │
│  │                        ▼                        │                  │
│  │                  FaqService.create()             │                  │
│  │                    ├── faq_entry 表 (PostgreSQL) │                  │
│  │                    └── pgvector 向量库            │                  │
│  │                        (content_type=faq_entry)  │                  │
│  │                                                 │                  │
│  │  FAQ 文档解析 ──→ FaqChunker ──→ pgvector       │                  │
│  │                    (qa_question/qa_answer)       │                  │
│  │                                                 │                  │
│  └─────────────────────────────────────────────────┘                  │
│                                                                      │
│  ┌─────────────────── 在线匹配 ────────────────────┐                 │
│  │                                                 │                 │
│  │  用户提问                                       │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  RagQaMessageHook (BEFORE_MODEL)                │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  FaqService.match()                             │                 │
│  │    ├── ① 精确匹配 (normalize 文本比对)           │                 │
│  │    ├── ② 模糊匹配 (包含关系)                    │                 │
│  │    ├── ③ 关键词匹配 (keywords 字段)             │                 │
│  │    └── ④ 语义匹配 (embedding + 余弦相似度)      │                 │
│  │         │                                       │                 │
│  │    ┌────┴────┐                                  │                 │
│  │  命中      未命中                                │                 │
│  │    │         │                                  │                 │
│  │    ▼         ▼                                  │                 │
│  │  直接返回   Agent + hybridSearch                │                 │
│  │  标准答案   (filterNonFaq 排除 FAQ 向量)         │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  hit_count++                                    │                 │
│  │  last_hit_time = NOW()                          │                 │
│  │                                                 │                 │
│  └─────────────────────────────────────────────────┘                 │
│                                                                      │
│  ┌─────────────────── 候选挖掘 ────────────────────┐                 │
│  │                                                 │                 │
│  │  chat_history (role='user')                     │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  SQL 标准化聚合 (REGEXP_REPLACE + LOWER)         │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  minFrequency 过滤                               │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  批量 Embedding 向量化                           │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  贪心质心聚类 (clusterThreshold=0.85)            │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  覆盖过滤 (coverageThreshold=0.85)               │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  返回候选列表 + suggestedKeywords                │                 │
│  │    │                                            │                 │
│  │    ▼                                            │                 │
│  │  运营人员一键创建 FAQ                            │                 │
│  │  (keywords 自动填入同簇其他问法)                  │                 │
│  │                                                 │                 │
│  └─────────────────────────────────────────────────┘                 │
│                                                                      │
│  ┌─────────────────── 统计监控 ────────────────────┐                 │
│  │                                                 │                 │
│  │  GET /faq/stats         → 总数/总命中/今日命中   │                 │
│  │  GET /faq/stats/trend   → 近N天命中趋势          │                 │
│  │  GET /faq/stats/category-distribution → 分类分布 │                 │
│  │  GET /faq/high-freq     → 高频 Top N            │                 │
│  │                                                 │                 │
│  └─────────────────────────────────────────────────┘                 │
│                                                                      │
└──────────────────────────────────────────────────────────────────────┘
```

---

## 十、关键设计决策

| 决策 | 理由 |
|------|------|
| FAQ 走前置 Hook 而非 Agent 工具调用 | 避免 LLM 推理延迟，标准答案秒级响应 |
| 四级匹配逐级降级 | 前三级零成本（纯内存字符串操作），第四级语义兜底 |
| FAQ embedding 缓存 | 避免每次匹配都调用 N 次 embedding API |
| FAQ 向量从 hybridSearch 排除 | 避免 FAQ 在文档检索结果中重复出现 |
| 候选挖掘用贪心聚类而非 K-Means | 无需预设 K 值，按频次优先保证高频问题不被稀释 |
| suggestedKeywords 自动填入 | 让挖掘时发现的同义问法在匹配时也能命中 |

---

## 十一、文件索引

| 文件 | 职责 |
|------|------|
| `FaqEntry.java` | 实体类 |
| `FaqMatchResult.java` | 匹配结果 DTO |
| `FaqService.java` | 核心业务逻辑（匹配/CRUD/挖掘/向量同步） |
| `FaqController.java` | REST API 控制器 |
| `RagQaMessageHook.java` | 对话前置拦截 Hook |
| `FaqChunker.java` | FAQ 文档问答对切分器 |
| `FaqEntryMapper.java` / `.xml` | 数据访问层 |
| `ChatHistoryMapper.java` / `.xml` | 聊天记录查询（候选挖掘数据源） |
| `KnowledgeBaseService.java` | 知识库服务（向量入库/检索/FAQ 过滤） |
| `application.yml` | FAQ 配置参数 |
| `FaqList.vue` | FAQ 管理页面 |
| `FaqDashboard.vue` | FAQ 统计看板 |
| `faq.ts (stores)` | Pinia 状态管理 |
| `faq.ts (api)` | 前端 API 层 |
| `types/index.ts` | TypeScript 类型定义 |
