# 制度问答与流程指引助手 — 系统设计文档

> 本文档覆盖后端（Spring Boot 3 + Spring AI Alibaba）和前端（Vue 3）的完整设计，可指导一步步完成开发。





## 目录

1. [系统概述](#1-系统概述)
2. [需求分析](#2-需求分析)
3. [架构设计](#3-架构设计)
4. [技术选型](#4-技术选型)
5. [数据模型](#5-数据模型)
6. [API 设计](#6-api-设计)
7. [后端模块设计](#7-后端模块设计)
8. [前端设计](#8-前端设计)
9. [核心流程](#9-核心流程)
10. [开发路线图](#10-开发路线图)
11. [测试策略](#11-测试策略)





## 1. 系统概述

### 1.1 项目定位

构建一个基于 **RAG（检索增强生成）+ ReAct Agent** 的公司制度智能问答系统，支持员工通过自然语言查询员工手册、请假制度、考勤规范、报销流程、入离职流程和 FAQ。



### 1.2 核心能力

```
┌─────────────────────────────────────────────────────┐
│                  核心能力矩阵                          │
├──────────────┬──────────────┬─────────────────────────┤
│ 制度问答      │ 流程指引      │ 多轮对话                 │
│ 条款检索+回答 │ 分步引导+交互 │ 上下文理解+追问           │
├──────────────┼──────────────┼─────────────────────────┤
│ 引用溯源      │ 安全兜底      │ FAQ 闭环                 │
│ 出处标注+版本 │ 不编造+如实告 │ 高频挖掘+标准答案沉淀     │
└──────────────┴──────────────┴─────────────────────────┘
```



### 1.3 使用场景

| 场景 | 示例问题 | 系统行为 |
|------|---------|---------|
| 制度问答 | "年假怎么计算？" | 检索条款 → 精准回答 + 出处 |
| 流程指引 | "请假需要什么材料？" | 分步引导 → 第①步 → 第②步 |
| 多轮追问 | "试用期员工呢？" | 理解上下文 → 差异化回答 |
| 定义查询 | "什么是严重违纪？" | 给出定义 + 引用原文 |
| 对比查询 | "病假和年假有什么区别？" | 表格对比 + 分别标注出处 |
| 范围确认 | "实习生能不能报销？" | 确认适用范围 + 条款引用 |







## 2. 需求分析

### 2.1 功能需求拆解

```
文档管理
  ├── FR1.1  上传文档（PDF/Word/TXT）
  ├── FR1.2  自动解析 + 智能切分
  ├── FR1.3  向量化入库
  └── FR1.4  版本管理（多版本共存，is_latest 标记）

智能问答
  ├── FR2.1  自然语言理解（6 种意图分类）
  ├── FR2.2  混合检索（向量 + 关键词）
  ├── FR2.3  RAG 生成回答（带出处标注）
  └── FR2.4  SSE 流式输出

流程指引
  ├── FR3.1  多轮分步引导（概览 → 逐步 → 灵活跳转）
  └── FR3.2  3 轮收敛（第 3 轮必须给出最终答案）

FAQ 管理
  ├── FR4.1  四级匹配（精确/模糊/关键词/语义）
  ├── FR4.2  CRUD 管理
  ├── FR4.3  高频候选挖掘（语义聚类）
  └── FR4.4  同步到向量库

多轮对话
  ├── FR5.1  对话历史管理（最近 5 轮注入）
  └── FR5.2  会话管理（CRUD）

质量监控
  ├── FR6.1  检索质量评分（RRF + LLM 综合）
  ├── FR6.2  禁止编造（最高优先级指令机制）
  ├── FR6.3  用户反馈（赞/踩）
  └── FR6.4  质量看板

用户管理
  ├── FR7.1  用户认证（登录/注册）
  ├── FR7.2  角色管理（admin/user）
  └── FR7.3  多部门支持
```







## 3. 架构设计

### 3.1 系统架构总览

```
┌──────────────────────────────────────────────────────────┐
│                      前端 (Vue 3)                         │
│  聊天界面  │  知识库管理  │  FAQ 管理  │  质量看板  │  登录  │
└────────────────────────┬─────────────────────────────────┘
                         │ SSE / REST API
┌────────────────────────▼─────────────────────────────────┐
│                   接入层 (Spring MVC)                      │
│  AgentController │ KnowledgeBaseController │ AuthController│
│  FaqController   │ QualityController       │ UserController│
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                  Hook 前置处理层                            │
│  FAQ 四级匹配 → 流程续接检测 → 闲聊检测 → 意图路由 → 策略注入  │
│  (RagQaMessageHook - @HookPosition.BEFORE_MODEL)          │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                  Agent 推理层                              │
│  ReAct Agent  │  Base System Prompt  │  对话历史管理       │
│  Thought → Action (searchKnowledge) → Observation → Final │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                    工具层                                  │
│  searchKnowledge (知识检索)   							│
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                    检索层                                  │
│  向量检索(pgvector HNSW) + 关键词检索(pg_trgm)              │
│  → RRF 融合 → LLM 重排序 → 结构去重 → 质量评分             │
└────────────────────────┬─────────────────────────────────┘
                         │
┌────────────────────────▼─────────────────────────────────┐
│                    数据层                                  │
│  PostgreSQL (业务数据)  │  pgvector (向量索引)              │
└──────────────────────────────────────────────────────────┘
```





### 3.2 核心设计模式

**模式一：策略分离（Strategy Separation）**

```
Base System Prompt (~40行, 公共规则)
  ├── 角色定义
  ├── 工具声明
  ├── 最高优先级规则
  ├── 禁止编造
  └── 通用规则

Hook 动态注入 (按意图选择 1 条, ~20行)
  ├── PROCESS_GUIDE    → 流程分步引导策略
  ├── 五种事实查询     → 精准回答策略
  └── FALLBACK         → 兜底检索策略
```



**模式二：标注式路由（Hook Labels, LLM Decides）**

```
Hook 层（确定性规则）：
  - 正则匹配 → 标注意图类型 + 注入策略
  - FAQ 命中 → 直接返回标准答案
  - 闲聊检测 → 注入闲聊模式

LLM 层（不确定性推理）：
  - 读取标注，选择对应策略
  - 自行决定：何时搜索、是否追问、回答结构
```





### 3.3 模块划分

```
backend/src/main/java/com/xiaofuzi/ai/
├── AiBackendApplication.java          # 启动类
├── annotation/   RequireRole.java     # 角色注解
├── component/    DataInitializer.java # 数据初始化
├── config/                            # 配置
│   ├── RagAgentConfiguration.java     # Agent 配置（基础 Prompt）
│   ├── SkillRegistryConfiguration.java
│   ├── WebMvcConfig.java
│   └── MultiProviderChatModelConfiguration.java
├── context/                           # 上下文
│   ├── UserContext.java               # ThreadLocal 用户信息
│   └── DepartmentContextHolder.java   # ThreadLocal 部门
├── controller/                        # 控制器
│   ├── AgentController.java           # 核心：SSE 流式问答
│   ├── AuthController.java
│   ├── FaqController.java
│   ├── KnowledgeBaseController.java
│   ├── KnowledgeDocumentController.java
│   ├── QualityController.java
│   └── UserController.java
├── dto/                               # 数据传输对象
│   ├── ChatRequest.java
│   ├── ChatResponse.java
│   ├── FaqMatchResult.java
│   ├── IntentResult.java
│   └── quality/  (QualityOverview, DailyRatingTrendItem, ...)
├── entity/                            # 实体
│   ├── ChatSession.java
│   ├── ChatHistory.java
│   ├── ChatUser.java
│   ├── FaqEntry.java
│   ├── KnowledgeDocument.java
│   └── DocumentGroup.java
├── enums/  IntentType.java            # 意图枚举（6+1 种）
├── hook/    RagQaMessageHook.java     # Hook 前置处理
├── interceptor/ LoginInterceptor.java # 登录拦截
├── mapper/                            # MyBatis 映射
├── node/                              # 工作流节点（预留）
├── rag/                               # RAG 核心
│   ├── KnowledgeBaseService.java      # 混合检索 + 入库
│   ├── KnowledgeRetrievalTools.java   # Agent 工具
│   ├── QualityScore.java              # 质量评分
│   └── parser/                        # 文档解析器
│       ├── DocumentParser.java
│       ├── DocumentParserFactory.java
│       ├── PdfDocumentParser.java
│       ├── WordDocumentParser.java
│       ├── TxtDocumentParser.java
│       ├── HeadingChunker.java        # 制度文档切分
│       ├── ProcessChunker.java        # 流程文档切分
│       └── FaqChunker.java            # FAQ 切分
├── service/
│   ├── RagQaAgentService.java         # Agent 问答服务
│   ├── FaqService.java                # FAQ 管理 + 匹配
│   ├── ChitchatDetector.java          # 闲聊检测
│   └── QualityService.java            # 质量统计
├── tool/    FileSystemServerTools.java # Skills 工具
├── util/    AppConstants.java / ChatModelProvider.java
└── vo/      Result.java               # 统一响应
```







## 4. 技术选型

| 层次 | 技术 | 版本 | 选型理由 |
|------|------|------|---------|
| **框架** | Spring Boot | 3.x | Java 生态标准，DI/AOP/事务支持 |
| **AI 集成** | Spring AI Alibaba | 1.x | 原生集成 DashScope、pgvector、ReAct Agent |
| **LLM** | Qwen-Plus (DashScope) |  | 中文制度文档理解优，低温度(0.2)防幻觉 |
| **Embedding** | text-embedding-v2 | 1536维 | 中文 C-MTEB 表现优，同生态配套 |
| **向量库** | pgvector (HNSW) | 0.8.2 | pgvector 开发成本低 |
| **数据库** | PostgreSQL | 18 | 支持 pgvector 扩展 + pg_trgm |
| **ORM** | MyBatis | 3.x | 灵活的 SQL 控制，适合复杂查询 |
| **前端** | Vue 3 | 3.x | Composition API + `<script setup>`，SSE fetch 流式消费 |
| **关键词检索** | pg_trgm | 0.8.2 | PostgreSQL 内置三元组模糊匹配 |
| **构建** | Maven | 3.x | Java 生态标准 |







## 5. 数据模型

### 5.1 核心表结构

#### 用户表 `chat_user`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 用户ID |
| username | VARCHAR(50) UNIQUE | 用户名 |
| password | VARCHAR(255) | 加密密码 |
| role | VARCHAR(20) | admin / user |
| department | VARCHAR(100) | 所属部门 |
| status | VARCHAR(10) | active / inactive |
| create_time | TIMESTAMP | 创建时间 |

#### 知识文档表 `knowledge_document`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 文档ID |
| document_name | VARCHAR(255) | 文件名 |
| document_type | VARCHAR(20) | pdf / docx / txt |
| file_size | BIGINT | 文件大小(字节) |
| category | VARCHAR(50) | 制度 / 流程 / FAQ / 手册 |
| description | TEXT | 文档描述 |
| version | VARCHAR(20) | 版本号（自动提取年份） |
| status | VARCHAR(10) | active / inactive |
| department | VARCHAR(100) | 适用部门 |
| group_id | BIGINT FK | 关联文档组 |
| is_latest | BOOLEAN | 是否最新版本 |
| chunk_count | INT | 分块数量 |
| create_time | TIMESTAMP | 创建时间 |

#### 文档组表 `document_group`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 组ID |
| name | VARCHAR(255) | 组名称 |
| department | VARCHAR(100) | 部门 |
| status | VARCHAR(10) | active / inactive |
| latest_document_id | BIGINT | 最新文档ID |

#### FAQ 表 `faq_entry`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | FAQ ID |
| question | TEXT | 问题 |
| answer | TEXT | 标准答案 |
| category | VARCHAR(50) | 分类 |
| keywords | VARCHAR(500) | 关键词（逗号分隔） |
| source_doc | VARCHAR(255) | 来源文档 |
| heading_path | VARCHAR(500) | 来源章节路径 |
| hit_count | INT DEFAULT 0 | 命中次数 |
| status | VARCHAR(10) | active / inactive |
| create_time | TIMESTAMP | 创建时间 |

#### 对话会话表 `chat_session`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 会话ID |
| thread_id | VARCHAR(64) UNIQUE | 对话线程ID |
| user_id | BIGINT FK | 用户ID |
| title | VARCHAR(100) | 会话标题（取自首条提问） |
| message_count | INT DEFAULT 0 | 消息数量 |
| create_time | TIMESTAMP | 创建时间 |
| update_time | TIMESTAMP | 更新时间 |

#### 对话历史表 `chat_history`

| 字段 | 类型 | 说明 |
|------|------|------|
| id | BIGSERIAL PK | 消息ID |
| thread_id | VARCHAR(64) FK | 关联会话 |
| role | VARCHAR(20) | user / assistant / system |
| content | TEXT | 消息内容 |
| source_doc | VARCHAR(255) | 引用来源文档 |
| heading_path | VARCHAR(500) | 引用章节路径 |
| rating | INT | 赞(1) / 踩(-1) / null |
| create_time | TIMESTAMP | 创建时间 |

#### 向量存储 `xiaofuzi_knowledge_base_v2` (pgvector)

| 字段 | 类型 | 说明 |
|------|------|------|
| id | UUID PK | 自动生成 |
| content | TEXT | chunk 文本内容 |
| metadata | JSONB | 元数据 |
| embedding | VECTOR(1536) | 向量 (HNSW 索引) |

**metadata JSON 结构：**
```json
{
  "source": "员工手册.pdf",
  "document_id": "1",
  "group_id": "1",
  "version": "2026",
  "is_latest": "true",
  "department": "全公司",
  "file_type": "pdf",
  "chunk_index": 0,
  "total_chunks": 15,
  "heading_path": "第5章 休假制度 > 第5.2条 年假计算",
  "step_title": "第①步",          // 流程文档专用
  "step_role": "部门经理",         // 流程文档专用
  "step_time_limit": "1个工作日",  // 流程文档专用
  "step_materials": "请假单, 医院证明",
  "qa_question": "年假怎么计算？",  // FAQ 专用
  "content_type": "faq_entry",
  "faq_id": "1"
}
```

### 5.2 ER 关系图

```
chat_user 1──N chat_session 1──N chat_history
document_group 1──N knowledge_document 1──N vector_chunk
faq_entry (独立，同步到 vector_chunk)
```





## 6. API 设计

### 6.1 认证接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/auth/login` | 登录，返回 token |
| POST | `/auth/register` | 注册 |

### 6.2 核心问答接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/rag-qa/chat/stream` | **核心** SSE 流式问答 |

**请求体 (ContentChatRequest)：**
```json
{
  "userMessage": "年假怎么计算？",
  "threadId": "abc123",
  "department": "技术部",
  "versionOverrides": [{"group_id": "1", "version": "2025"}]
}
```

**SSE 事件流：**
```
event: thinking  → {"type":"thinking","content":"正在检索知识库..."}
event: token     → {"type":"token","content":"根据公司制度，年假计算方式如下："}
event: token     → {"type":"token","content":"..."}
event: done      → {"type":"done","content":{"threadId":"...","suggestions":["..."]}}
event: error     → {"type":"error","content":"错误信息"}
```

### 6.3 会话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/agent/sessions` | 创建会话 |
| GET | `/agent/sessions` | 获取会话列表 |
| GET | `/agent/sessions/{threadId}/history` | 获取对话历史 |
| DELETE | `/agent/sessions/{threadId}` | 删除会话 |
| POST | `/agent/sessions/{messageId}/feedback` | 提交反馈(赞/踩) |

### 6.4 知识库接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/knowledge-base/upload` | 上传文档（MultipartFile） |
| GET | `/knowledge-base/stats` | 知识库统计 |
| DELETE | `/knowledge-base/documents/{id}` | 删除文档 |
| POST | `/knowledge-base/documents/{id}/reingest` | 重新摄入 |

### 6.5 FAQ 接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/faq/list` | FAQ 列表（支持分页/筛选/排序） |
| POST | `/faq` | 新增 FAQ |
| PUT | `/faq/{id}` | 更新 FAQ |
| DELETE | `/faq/{id}` | 删除 FAQ |
| GET | `/faq/candidates` | 高频候选挖掘 |
| GET | `/faq/stats` | FAQ 统计 |
| POST | `/faq/similar` | 相似检测 |







## 8. 前端设计

### 8.1 页面结构

```
/                         聊天主页
├── 侧边栏
│   ├── 会话列表（可切换/新建/删除）
│   └── 用户信息 + 退出
├── 聊天区域
│   ├── 消息列表（用户消息 + 助手回答 + 出处标注）
│   ├── 建议问题快捷按钮
│   └── 反馈按钮（👍 / 👎）
└── 输入区域
    ├── 文本输入框
    ├── 部门选择器
    └── 发送按钮

/faq                       FAQ 管理页
├── FAQ 列表（表格 + 分页 + 筛选 + 排序）
├── FAQ 编辑弹窗（新增/修改）
├── 批量操作（分类/状态/删除）
└── 候选挖掘面板 + 一键导入

/knowledge                 知识库管理页
├── 文档上传（拖拽 + 分类 + 部门）
├── 文档列表（表格 + 版本信息）
├── 重新摄入
└── 统计卡片

/quality                   质量看板页
├── 总分卡片（平均分/好评率/回答数）
├── 每日趋势图
├── 低分消息列表
└── 部门对比
```

### 8.2 组件树

```
App
├── AuthPage（登录/注册）
├── MainLayout
│   ├── Sidebar
│   │   ├── UserInfo
│   │   └── SessionList
│   ├── ChatView
│   │   ├── MessageList
│   │   │   ├── UserMessage（气泡，右对齐）
│   │   │   └── AssistantMessage（气泡，左对齐，含引用/建议/反馈）
│   │   ├── SuggestionChips（快捷追问按钮）
│   │   └── InputArea（输入框 + 部门选择 + 发送）
│   ├── FaqManagePage（CRUD 表格 + 编辑弹窗）
│   ├── KnowledgeManagePage（上传 + 列表）
│   └── QualityDashboard（统计图表）
```

### 8.3 SSE 流式消费

```javascript
// composables/useChat.js - Vue 3 Composable
import { ref, readonly } from 'vue'

export function useChat() {
  const messages = ref([])
  const isStreaming = ref(false)
  let currentEvent = ''

  const sendMessage = async (text, threadId, department) => {
    // 添加用户消息
    messages.value.push({
      id: Date.now(),
      role: 'user',
      content: text
    })

    // 添加助手占位消息
    const assistantMsg = {
      id: Date.now() + 1,
      role: 'assistant',
      content: '',
      isStreaming: true,
      sourceDoc: null,
      headingPath: null,
      suggestions: []
    }
    messages.value.push(assistantMsg)
    isStreaming.value = true

    const response = await fetch('/agent/rag-qa/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
        userMessage: text,
        threadId: threadId,
        department: department
      })
    })

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() // 保留不完整的行

      for (const line of lines) {
        if (line.startsWith('event:')) {
          currentEvent = line.substring(6).trim()
        } else if (line.startsWith('data:')) {
          const data = JSON.parse(line.substring(5).trim())
          handleEvent(currentEvent, data, assistantMsg)
        }
      }
    }
  }

  const handleEvent = (event, data, assistantMsg) => {
    switch (event) {
      case 'thinking':
        assistantMsg.content = '正在检索知识库...'
        break
      case 'token':
        assistantMsg.content += data.content
        break
      case 'done':
        assistantMsg.isStreaming = false
        if (data.content) {
          assistantMsg.threadId = data.content.threadId
          assistantMsg.suggestions = data.content.suggestions || []
        }
        isStreaming.value = false
        break
      case 'error':
        assistantMsg.content = '抱歉，服务出现错误，请稍后重试。'
        assistantMsg.isStreaming = false
        isStreaming.value = false
        break
      case 'search_info':
        if (data.content) {
          assistantMsg.sourceDoc = data.content.sourceDoc
          assistantMsg.headingPath = data.content.headingPath
        }
        break
    }
  }

  // 提交反馈
  const submitFeedback = async (messageId, rating) => {
    await fetch(`/agent/sessions/${messageId}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ rating })
    })
    const msg = messages.value.find(m => m.id === messageId)
    if (msg) msg.rating = rating
  }

  return {
    messages: readonly(messages),
    isStreaming: readonly(isStreaming),
    sendMessage,
    submitFeedback
  }
}
```







## 9. 核心流程

### 9.1 制度问答流程（"年假怎么计算？"）

```
时间线 →

用户: "年假怎么计算？"
  │
  ├─ [Hook] FAQ 匹配 → 未命中
  ├─ [Hook] 流程续接检测 → 否
  ├─ [Hook] 闲聊检测 → 非闲聊
  ├─ [Hook] IntentType.classify()
  │   PROCESS_GUIDE.matches("年假怎么计算？")
  │   → primaryPattern: "怎么" 命中 ✓ (但 "计算" ∉ [做办弄])
  │   检查: "怎么" 匹配了吗？是的，"怎么[做办弄]" 中的 "怎么"匹配了
  │   但后面需要 [做办弄] 之一 → "计" 不在其中 → 不命中
  │   
  │   → 继续检查 POLICY_QA / DEFINITION / ... → 都未命中
  │   → FALLBACK
  │
  ├─ [Hook] selectStrategy(FALLBACK) → FALLBACK_STRATEGY
  ├─ [Hook] 注入: "【系统指令-意图类型:兜底检索】\n## 回答策略：兜底检索\n..."
  │
  ├─ [Agent] Thought: "需要查年假相关的制度文档"
  ├─ [Agent] Action: searchKnowledge("年假怎么计算？")
  │   │
  │   ├─ [检索] 向量检索(10条) + 关键词检索(10条) → RRF → LLM重排 → Top3
  │   │   第1条: "工龄1-10年每年5天年假...工龄10-20年每年10天..." (heading: 第5.2条)
  │   │   第2条: "年假须提前3个工作日申请..." (heading: 第5.2条 → 去重跳过)
  │   │   第3条: "试用期员工年假按比例折算..." (heading: 第5.3条)
  │   │   → 去重后保留第1、3条
  │   │   → QualityScore: PASSED
  │   │
  │   └─ 返回: "[参考1] 来源: 员工手册.pdf > 第5.2条 年假计算\n工龄1-10年..."
  │
  ├─ [Agent] Observation: 有2条检索结果，质量 PASSED
  └─ [Agent] Final: 
      "根据公司制度，年假计算方式如下：
      
      工龄1-10年：每年5天年假
      工龄10-20年：每年10天年假
      试用期员工年假按比例折算
      
      【出处】员工手册 > 第5章 休假制度 > 第5.2条 年假计算
      
      💡 您可以继续问：
      - 试用期年假怎么折算？
      - 年假可以跨年使用吗？"
```



### 9.2 流程指引多轮交互流程

```
=== 第1轮 ===
用户: "请假流程怎么走？"
  → 意图: PROCESS_GUIDE ✓
  → 策略: PROCESS_GUIDE_STRATEGY（第1轮）
  → Agent: "关于请假流程，主要分为以下步骤：
           ① 提交请假申请 ② 部门审批 ③ HR备案
           【出处】请假制度.pdf > 第3章 请假流程
           请问您准备好了吗？我们从第①步开始？"

=== 第2轮 ===
用户: "好的"
  → Hook: isInProcessGuidanceFlow() → true (助手消息含 "第①步")
  → Hook: isFlowContinuation("好的") → length=2 ≤6 → true
  → 策略: 流程续接指令
  → Agent: "第①步：提交请假申请
           材料：请假单、相关证明（病假需医院证明）
           时限：至少提前1个工作日
           角色：员工本人
           【出处】请假制度.pdf > 第3章 请假流程 > 第①步
           这一步清楚了吗？需要我继续讲解第②步吗？"

=== 第3轮 ===
用户: "清楚了，继续"
  → 流程续接 → 第②步
  → Agent: "第②步：部门审批..."
  
  → 3轮收敛 → 此后用户发言后给出最终答案
```



### 9.3 知识不足兜底流程

```
用户: "公司有关于宠物保险的政策吗？"
  → 意图: FALLBACK
  → 策略: FALLBACK_STRATEGY（先检索再回答）
  → Agent: searchKnowledge("宠物保险")
      → 向量检索: 0条（相似度不达标）
      → 关键词检索: 0条
      → QualityStatus: EMPTY
      → 工具返回: "【系统指令-最高优先级】
                   知识库中未找到任何与「宠物保险」相关的文档内容。
                   回复模板：抱歉，我未能在知识库中找到与您问题相关的信息。
                   建议您：1.尝试更换关键词重新提问 2.联系 HR 部门获取人工帮助"
  → Agent: 看到最高优先级指令 → 原样输出模板
  → 用户收到: "抱歉，我未能在知识库中找到与您问题相关的信息..."
```







## 10. 开发路线图

### Phase 1: 基础搭建（2-3天）

```
□ 1.1  Spring Boot 项目初始化，配置 pom.xml
□ 1.2  配置 application.yml（数据库、AI、向量库）
□ 1.3  创建 PostgreSQL 数据库 + pgvector 扩展 + pg_trgm 扩展
□ 1.4  创建所有数据库表（DDL）
□ 1.5  编写 Entity 类（ChatUser, ChatSession, ChatHistory,
        FaqEntry, KnowledgeDocument, DocumentGroup）
□ 1.6  编写 Mapper 接口 + XML（CRUD 操作）
□ 1.7  编写 DTO 类（ChatRequest, ChatResponse, Result 等）
□ 1.8  实现 AuthController（登录/注册）+ LoginInterceptor
□ 1.9  实现 DataInitializer（初始化默认管理员账号）
```

### Phase 2: 知识库（2-3天）

```
□ 2.1  实现 DocumentParser 接口 + PdfDocumentParser
□ 2.2  实现 WordDocumentParser + TxtDocumentParser
□ 2.3  实现 DocumentParserFactory
□ 2.4  实现 HeadingChunker（制度文档切分，按标题层级）
□ 2.5  实现 ProcessChunker（流程文档切分，按步骤）
□ 2.6  实现 FaqChunker（FAQ 切分，按 QA 对）
□ 2.7  实现 KnowledgeBaseService.ingestMultipartFile()
□ 2.8  实现 KnowledgeBaseController（上传/统计/删除/重摄入）
□ 2.9  测试：上传一份 PDF 员工手册，验证切分 + 入库
```

### Phase 3: RAG 检索（2天）

```
□ 3.1  实现 KnowledgeBaseService.hybridSearch()
        （向量 + 关键词 + RRF + LLM 重排 + 去重 + 质量评分）
□ 3.2  实现 KnowledgeBaseService.formatAsContext()
□ 3.3  实现 KnowledgeRetrievalTools (@Tool searchKnowledge)
□ 3.4  测试：单测验证混合检索返回正确结果
```

### Phase 4: Agent 问答（2天）

```
□ 4.1  编写 RagAgentConfiguration（BASE_SYSTEM_PROMPT + ReAct Agent Bean）
□ 4.2  编写 RagQaAgentService（ask / buildEnrichedQuestion / saveHistory）
□ 4.3  编写 AgentController（SSE 流式 / 会话管理 / 反馈）
□ 4.4  测试：通过 /agent/rag-qa/chat/stream 发送提问，验证流式返回
```

### Phase 5: 意图路由（1-2天）

```
□ 5.1  定义 IntentType 枚举（6+1 种，含正则 + classify）
□ 5.2  实现 ChitchatDetector（含业务关键词集合）
□ 5.3  实现 RagQaMessageHook（FAQ→流程续接→闲聊→意图路由→策略注入）
□ 5.4  编写三条策略常量（PROCESS_GUIDE / PRECISE_ANSWER / FALLBACK）
□ 5.5  测试：多种问法验证意图分类准确 + 策略注入正确
```

### Phase 6: FAQ 系统（2天）

```
□ 6.1  实现 FaqService.match()（四级匹配）
□ 6.2  实现 FaqService CRUD（create/update/delete/list）
□ 6.3  实现 FAQ → 向量库同步（syncToVectorStore）
□ 6.4  实现 FaqController（CRUD + 候选挖掘 + 统计）
□ 6.5  实现 getFaqCandidates()（语义聚类 + 覆盖过滤）
□ 6.6  测试：新增 FAQ → 提问验证命中 → 查看命中统计
```

### Phase 7: 质量与前端（2-3天）

```
□ 7.1  实现 QualityService（概览/趋势/低分/盲区/部门对比）
□ 7.2  实现 QualityController
□ 7.3  前端：Vue 3 项目搭建（Vite + Vue Router + Pinia）
□ 7.4  前端：聊天页面（useChat composable + 消息渲染 + 反馈）
□ 7.5  前端：知识库管理页（上传 + 列表）
□ 7.6  前端：FAQ 管理页（CRUD + 候选挖掘）
□ 7.7  前端：质量看板（统计图表）
□ 7.8  前端：登录/注册页
```





## 11. 测试

### 11.1 单元测试

| 测试对象 | 测试内容 |
|---------|---------|
| IntentType.classify() | 每个意图的正则覆盖验证（正例 + 反例） |
| ChitchatDetector.detect() | 闲聊/非闲聊边界（含误判回归） |
| ChunkSmart | 三种切分器的结构命中率 |
| RRF 融合 | 相同/不同排序列表的合并正确性 |
| FAQ 匹配 | 四级匹配的优先级和阈值 |

### 11.2 集成测试

| 测试场景 | 验证点 |
|---------|--------|
| 文档上传 → 检索 | 上传后立即可检索到 |
| 端到端问答 | 用户提问 → Hook 路由 → Agent 检索 → 生成回答 |
| 流程多轮 | 3 轮流程引导不中断 |
| FAQ 命中 | FAQ 命中后不调用 LLM |
| 兜底处理 | 知识库无相关内容时如实告知 |

> 按照 Phase 1→7 顺序开发，每个 Phase 完成后验证，再进入下一阶段。
