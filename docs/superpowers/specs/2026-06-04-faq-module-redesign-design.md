# FAQ 管理模块全量改进设计

> **Goal:** 对 FAQ 管理模块进行全量升级，补齐分页/排序/批量操作/导入导出/Markdown 答案/相似检测/全功能统计看板等 8 项能力，达到市面成熟 FAQ 产品水平。

**Architecture:** 前端 FaqList 主管理页全面改造（表格分页排序、批量操作、Markdown 编辑、相似检测、导入导出），新增 FaqDashboard 统计看板，HighFreqFaq 微调。后端新增统计/趋势/分类分布 API、批量操作端点、导入解析逻辑、相似检测接口。图表用 ECharts，Markdown 编辑器用 `@kangc/v-md-editor`。

**Tech Stack:** Vue 3 + Element Plus + ECharts + v-md-editor + Spring Boot + MyBatis + pgvector

---

## 一、FaqList 主管理页改造

### 1.1 Toolbar
- 左侧：分类筛选下拉 + 关键词搜索（300ms 防抖）+ 状态筛选（active / inactive / deleted / 全部）
- 右侧：批量操作按钮组（批量删除 / 批量改分类 / 批量改状态，选中行后可用）+ 导入按钮 + 导出按钮 + 新建 FAQ 按钮

### 1.2 表格
- 行首 checkbox 列（type="selection"）用于批量选择
- 去掉 `border` 属性，统用 `stripe`
- 每列 `sortable="custom"` 后端排序，白名单校验
- 新增「最近命中时间」列，去掉 ID 列
- 列宽不设固定值，表格 `width: 100%` 均匀分布
- 操作列：「编辑」「删除」（点击编辑打开弹窗即可看全信息）
- 分页器始终显示：`page-sizes: [5, 10, 20, 50]`

### 1.3 编辑弹窗
- 宽度 720px（原 600px）
- 答案字段使用 `@kangc/v-md-editor` Markdown 编辑器，分屏预览
- 问题字段输入后防抖 500ms 调后端相似检测 API，下方展示相似 FAQ 列表（如有）
- 其余字段不变：关键词、分类、状态

### 1.4 相似检测弹窗提示
- 新建/编辑时，问题输入框下方出现"相似 FAQ 提示"区域
- 列出已存在的相似问题 + 相似度百分比
- 点击可跳转编辑该 FAQ，避免重复创建

### 1.5 导入/导出
- **导入弹窗**：上传 CSV/Excel → 后端解析 → 前端展示预览表格 → 确认导入
- **导出弹窗**：选择范围（全部 / 按分类 / 选中项）+ 格式（CSV / Excel）→ 下载
- 后端新增 `/faq/faq/import` 和 `/faq/faq/export` 端点

### 1.6 批量操作
- 表格行首 checkbox 选择
- 批量删除：弹窗确认后调用后端批量软删除 `POST /faq/faq/batch-delete`
- 批量改分类：弹窗选目标分类 `POST /faq/faq/batch-update-category`
- 批量改状态：弹窗选目标状态 `POST /faq/faq/batch-update-status`

---

## 二、FaqDashboard 统计看板（新增）

### 2.1 指标卡片
- 4 个 `el-card` 横排：FAQ 总数、总命中次数、今日匹配次数、未命中查询量

### 2.2 图表
- 命中趋势折线图（近 30 天每日匹配量）— ECharts line chart
- 分类命中分布饼图 — ECharts pie chart

### 2.3 数据表格
- Top 20 高频 FAQ 排行（问题 + 命中次数 + 最近命中时间）
- 最近未匹配的用户查询列表（用于快速挖掘新候选）

### 2.4 后端 API
- `GET /faq/faq/stats` — 汇总统计（totalFaq, totalHits, todayHits, unmatchedCount）
- `GET /faq/faq/stats/trend?days=30` — 每日匹配量趋势数据
- `GET /faq/faq/stats/category-distribution` — 各分类命中分布

---

## 三、HighFreqFaq 微调
- 卡片内的答案支持 Markdown 渲染（用 v-md-editor 预览组件）
- 保持现有展开/收起交互

---

## 四、后端改动汇总

| 端点 | 方法 | 说明 |
|---|---|---|
| `/faq/faq` | GET | 加 page/size/sortBy/sortOrder/status 参数，返回 `PageResult` |
| `/faq/faq/{id}` | GET | 不变 |
| `/faq/faq/create-faq` | POST | 不变 |
| `/faq/faq/{id}` | PUT | 不变 |
| `/faq/faq/{id}` | DELETE | 不变 |
| `/faq/faq/similar` | GET | 新增：输入问题文本，返回相似 FAQ 列表 |
| `/faq/faq/batch-delete` | POST | 新增：批量软删除 |
| `/faq/faq/batch-update-category` | POST | 新增：批量改分类 |
| `/faq/faq/batch-update-status` | POST | 新增：批量改状态 |
| `/faq/faq/import` | POST | 新增：CSV/Excel 导入 |
| `/faq/faq/export` | GET | 新增：CSV/Excel 导出 |
| `/faq/faq/high-freq` | GET | 不变 |
| `/faq/faq/candidates` | GET | 不变 |
| `/faq/faq/stats` | GET | 新增：汇总统计 |
| `/faq/faq/stats/trend` | GET | 新增：命中趋势 |
| `/faq/faq/stats/category-distribution` | GET | 新增：分类分布 |

### 相似检测逻辑
- 将输入问题向量化，与所有 active FAQ 问题向量做余弦相似度计算
- 返回相似度 > 0.7 的 FAQ 列表，按相似度降序

### 命中趋势数据
- 在 `chat_history` 或 `faq_entry` 表记录每次命中的时间戳
- `faq_entry` 表新增 `last_hit_time` 列

---

## 五、前端文件改动

| 文件 | 改动 |
|---|---|
| `src/views/faq/FaqList.vue` | 全面重构：分页/排序/批量/Markdown 编辑器/相似提示/导入导出 |
| `src/views/faq/FaqDashboard.vue` | **新增**：统计看板页面 |
| `src/views/faq/HighFreqFaq.vue` | 微调：答案 Markdown 渲染 |
| `src/api/faq.ts` | 新增所有 API 封装 |
| `src/stores/faq.ts` | 扩展：分页状态、批量操作、统计数据 |
| `src/router/index.ts` | 新增 `/faq/dashboard` 路由 |
| `src/App.vue` | 侧边栏导航新增「FAQ 统计」入口 |

---

## 六、数据库改动

```sql
ALTER TABLE faq_entry ADD COLUMN last_hit_time TIMESTAMP;
```

---

## 七、依赖新增

**前端：**
- `@kangc/v-md-editor` — Markdown 编辑器（预览 + 编辑）
- `echarts` + `vue-echarts` — 图表
- `xlsx` — Excel 导入导出解析

**后端：**
- Apache POI — Excel 文件解析（已有 Spring Boot，需确认是否已引入）
