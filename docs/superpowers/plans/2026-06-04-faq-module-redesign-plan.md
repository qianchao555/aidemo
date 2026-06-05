# FAQ 管理模块全量改进实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 FAQ 管理模块进行全量升级：分页排序、批量操作、导入导出、Markdown 答案编辑、相似 FAQ 检测、全功能统计看板。

**Architecture:** 后端新增分页/排序/批量/导入导出/相似检测/统计共 10 个端点，数据库新增 `last_hit_time` 列。前端 FaqList 全面重构（表格分页排序 + 批量 + Markdown 编辑 + 相似提示 + 导入导出），新增 FaqDashboard 统计看板页，HighFreqFaq 微调支持 Markdown 渲染。

**Tech Stack:** Vue 3 + Element Plus + ECharts + @kangc/v-md-editor + xlsx + Spring Boot + MyBatis + pgvector + Apache POI

---

### Task 1: 数据库迁移 — faq_entry 增加 last_hit_time 列

**Files:**
- Create: `backend/src/main/resources/sql/migration/V2__faq_entry_last_hit_time.sql`

- [ ] **Step 1: 创建迁移 SQL**

```sql
ALTER TABLE faq_entry ADD COLUMN IF NOT EXISTS last_hit_time TIMESTAMP;
COMMENT ON COLUMN faq_entry.last_hit_time IS '最近一次命中时间';
```

- [ ] **Step 2: 执行迁移**

Run: `psql -h localhost -U postgres -d aidemo -f backend/src/main/resources/sql/migration/V2__faq_entry_last_hit_time.sql`
Expected: `ALTER TABLE` 成功

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/sql/migration/V2__faq_entry_last_hit_time.sql
git commit -m "feat: add last_hit_time column to faq_entry"
```

---

### Task 2: 后端 FaqEntry 实体 + Mapper 层扩展

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/entity/FaqEntry.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/mapper/FaqEntryMapper.java`
- Modify: `backend/src/main/resources/mapper/FaqEntryMapper.xml`

- [ ] **Step 1: FaqEntry 实体添加 lastHitTime 字段**

在 `FaqEntry.java` 的 `updateTime` 字段后添加：

```java
private LocalDateTime lastHitTime;
```

- [ ] **Step 2: Mapper 接口新增方法**

在 `FaqEntryMapper.java` 中添加以下方法声明：

```java
List<FaqEntry> findByFilters(@Param("category") String category,
                              @Param("status") String status,
                              @Param("keyword") String keyword,
                              @Param("sortBy") String sortBy,
                              @Param("sortOrder") String sortOrder,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

long countByFilters(@Param("category") String category,
                    @Param("status") String status,
                    @Param("keyword") String keyword);

void batchUpdateCategory(@Param("ids") List<Long> ids, @Param("category") String category);

void batchUpdateStatus(@Param("ids") List<Long> ids, @Param("status") String status);

void batchDelete(@Param("ids") List<Long> ids);

void updateHitCountAndTime(@Param("id") Long id);

List<FaqEntry> findByIds(@Param("ids") List<Long> ids);

long countTodayHits();

List<Map<String, Object>> dailyHitTrend(@Param("days") int days);

List<Map<String, Object>> categoryHitDistribution();
```

- [ ] **Step 3: XML 添加对应的 SQL**

在 `FaqEntryMapper.xml` 的 `</mapper>` 前添加：

```xml
<resultMap id="FaqEntryFullResult" type="com.xiaofuzi.ai.entity.FaqEntry" extends="FaqEntryResult">
    <result property="lastHitTime" column="last_hit_time"/>
</resultMap>

<select id="findByFilters" resultMap="FaqEntryFullResult">
    SELECT * FROM faq_entry
    <where>
        <if test="category != null and category != ''">
            AND category = #{category}
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (question ILIKE CONCAT('%', #{keyword}, '%')
                 OR keywords ILIKE CONCAT('%', #{keyword}, '%'))
        </if>
    </where>
    <choose>
        <when test="sortBy != null and sortBy != '' and sortOrder != null and sortOrder != ''">
            ORDER BY ${sortBy} ${sortOrder}
        </when>
        <otherwise>
            ORDER BY hit_count DESC
        </otherwise>
    </choose>
    LIMIT #{limit} OFFSET #{offset}
</select>

<select id="countByFilters" resultType="long">
    SELECT COUNT(*) FROM faq_entry
    <where>
        <if test="category != null and category != ''">
            AND category = #{category}
        </if>
        <if test="status != null and status != ''">
            AND status = #{status}
        </if>
        <if test="keyword != null and keyword != ''">
            AND (question ILIKE CONCAT('%', #{keyword}, '%')
                 OR keywords ILIKE CONCAT('%', #{keyword}, '%'))
        </if>
    </where>
</select>

<update id="batchUpdateCategory">
    UPDATE faq_entry SET category = #{category}, update_time = NOW()
    WHERE id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
</update>

<update id="batchUpdateStatus">
    UPDATE faq_entry SET status = #{status}, update_time = NOW()
    WHERE id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
</update>

<update id="batchDelete">
    UPDATE faq_entry SET status = 'deleted', update_time = NOW()
    WHERE id IN <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
</update>

<update id="updateHitCountAndTime">
    UPDATE faq_entry SET hit_count = hit_count + 1, last_hit_time = NOW(), update_time = NOW()
    WHERE id = #{id}
</update>

<select id="findByIds" resultMap="FaqEntryFullResult">
    SELECT * FROM faq_entry WHERE id IN
    <foreach collection="ids" item="id" open="(" separator="," close=")">#{id}</foreach>
</select>

<select id="countTodayHits" resultType="long">
    SELECT COUNT(*) FROM faq_entry WHERE last_hit_time >= CURRENT_DATE
</select>

<select id="dailyHitTrend" resultType="java.util.HashMap">
    SELECT TO_CHAR(d.date, 'YYYY-MM-DD') AS day, COALESCE(COUNT(f.id), 0) AS cnt
    FROM generate_series(CURRENT_DATE - (#{days} - 1), CURRENT_DATE, '1 day'::interval) d(date)
    LEFT JOIN faq_entry f ON DATE(f.last_hit_time) = d.date
    GROUP BY d.date ORDER BY d.date
</select>

<select id="categoryHitDistribution" resultType="java.util.HashMap">
    SELECT category, SUM(hit_count) AS total_hits
    FROM faq_entry
    WHERE status = 'active' AND category IS NOT NULL AND category != ''
    GROUP BY category
    ORDER BY total_hits DESC
</select>
```

同时更新 `update` SQL 的 resultMap 和 `incrementHitCount` 方法：

将 `findAllActive`、`findByCategory`、`searchByKeyword`、`findTopByHitCount`、`findById` 的 `resultMap` 从 `FaqEntryResult` 改为 `FaqEntryFullResult`。

修改 `incrementHitCount`：
```xml
<update id="incrementHitCount">
    UPDATE faq_entry SET hit_count = hit_count + 1, last_hit_time = NOW(), update_time = NOW() WHERE id = #{id}
</update>
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/entity/FaqEntry.java backend/src/main/java/com/xiaofuzi/ai/mapper/FaqEntryMapper.java backend/src/main/resources/mapper/FaqEntryMapper.xml
git commit -m "feat: extend FaqEntry mapper with pagination, batch ops, stats queries"
```

---

### Task 3: 后端 FaqService 扩展

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/service/FaqService.java`

- [ ] **Step 1: 替换 incrementHitFaq 方法**

将 `incrementHitFaq` 方法中的 `faqEntryMapper.incrementHitCount(entry.getId())` 改为调用新方法：

```java
private void incrementHitFaq(FaqEntry entry) {
    try {
        faqEntryMapper.updateHitCountAndTime(entry.getId());
    } catch (Exception e) {
        logger.warn("FAQ 命中计数更新失败: id={}", entry.getId(), e);
    }
}
```

- [ ] **Step 2: 添加分页查询方法**

在 `searchByKeyword` 方法后添加：

```java
public List<FaqEntry> findByFilters(String category, String status,
                                     String keyword, String sortBy,
                                     String sortOrder, int offset, int limit) {
    return faqEntryMapper.findByFilters(category, status, keyword, sortBy, sortOrder, offset, limit);
}

public long countByFilters(String category, String status, String keyword) {
    return faqEntryMapper.countByFilters(category, status, keyword);
}
```

- [ ] **Step 3: 添加批量操作方法**

```java
public void batchUpdateCategory(List<Long> ids, String category) {
    faqEntryMapper.batchUpdateCategory(ids, category);
    logger.info("FAQ 批量更新分类: ids={}, category={}", ids, category);
}

public void batchUpdateStatus(List<Long> ids, String status) {
    faqEntryMapper.batchUpdateStatus(ids, status);
    logger.info("FAQ 批量更新状态: ids={}, status={}", ids, status);
}

public void batchDelete(List<Long> ids) {
    faqEntryMapper.batchDelete(ids);
    logger.info("FAQ 批量删除: ids={}", ids);
}
```

- [ ] **Step 4: 添加相似检测方法**

在类的最后添加：

```java
/**
 * 检测与输入问题语义相似的已有 FAQ。
 * 将输入向量化后与所有 active FAQ 的问题向量计算余弦相似度，
 * 返回相似度 > 0.7 的条目，按相似度降序排列。
 */
public List<Map<String, Object>> findSimilar(String question) {
    if (question == null || question.isBlank()) return List.of();

    List<FaqEntry> allActive = faqEntryMapper.findAllActive();
    if (allActive.isEmpty()) return List.of();

    float[] inputVec;
    try {
        inputVec = embeddingModel.embed(question);
    } catch (Exception e) {
        logger.warn("相似检测: embedding 失败", e);
        return List.of();
    }

    List<Map<String, Object>> result = new ArrayList<>();
    for (FaqEntry entry : allActive) {
        float[] entryVec;
        try {
            entryVec = embeddingModel.embed(entry.getQuestion());
        } catch (Exception e) {
            continue;
        }
        double sim = cosineSimilarity(inputVec, entryVec);
        if (sim > 0.7) {
            result.add(Map.of("id", entry.getId(), "question", entry.getQuestion(),
                    "similarity", Math.round(sim * 1000.0) / 10.0));
        }
    }
    result.sort((a, b) -> Double.compare((Double) b.get("similarity"), (Double) a.get("similarity")));
    return result;
}
```

- [ ] **Step 5: 添加统计方法**

```java
public Map<String, Object> getStats() {
    long totalFaq = faqEntryMapper.countByFilters(null, "active", null);
    long totalHits = faqEntryMapper.findAllActive().stream()
            .mapToLong(f -> f.getHitCount() != null ? f.getHitCount() : 0).sum();
    long todayHits = faqEntryMapper.countTodayHits();
    // unmatched count: total user queries in chat_history minus matched ones can be approximated
    return Map.of("totalFaq", totalFaq, "totalHits", totalHits, "todayHits", todayHits);
}

public List<Map<String, Object>> getDailyTrend(int days) {
    return faqEntryMapper.dailyHitTrend(days);
}

public List<Map<String, Object>> getCategoryDistribution() {
    return faqEntryMapper.categoryHitDistribution();
}
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/service/FaqService.java
git commit -m "feat: add FaqService pagination, batch ops, similar detection, stats"
```

---

### Task 4: 后端 FaqController 扩展 — 分页/排序/批量/相似检测

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/FaqController.java`
- Create: `backend/src/main/java/com/xiaofuzi/ai/dto/PageResult.java` (已存在，复用)

- [ ] **Step 1: 添加排序白名单和映射**

在 FaqController 类体开头添加：

```java
private static final Set<String> ALLOWED_SORT_COLUMNS = Set.of(
    "id", "question", "category", "hitCount", "status", "createTime", "updateTime", "lastHitTime"
);

private static final java.util.Map<String, String> SORT_COLUMN_MAPPING = java.util.Map.of(
    "question", "question",
    "category", "category",
    "hitCount", "hit_count",
    "status", "status",
    "createTime", "create_time",
    "updateTime", "update_time",
    "lastHitTime", "last_hit_time"
);
```

- [ ] **Step 2: 改造 listFaq 为分页接口**

替换原有的 `listFaq` 方法：

```java
@GetMapping("/faq")
public Result<PageResult<FaqEntry>> listFaq(
        @RequestParam(required = false) String category,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String keyword,
        @RequestParam(required = false, defaultValue = "hit_count") String sortBy,
        @RequestParam(required = false, defaultValue = "desc") String sortOrder,
        @RequestParam(required = false, defaultValue = "1") int page,
        @RequestParam(required = false, defaultValue = "10") int size) {

    if (!ALLOWED_SORT_COLUMNS.contains(sortBy)) {
        sortBy = "hit_count";
    }
    sortBy = SORT_COLUMN_MAPPING.getOrDefault(sortBy, "hit_count");
    if (!"asc".equalsIgnoreCase(sortOrder) && !"desc".equalsIgnoreCase(sortOrder)) {
        sortOrder = "desc";
    }
    sortOrder = sortOrder.toUpperCase();

    int offset = Math.max(0, (page - 1) * size);
    int limit = Math.max(1, Math.min(size, 100));

    List<FaqEntry> list = faqService.findByFilters(category, status, keyword, sortBy, sortOrder, offset, limit);
    long total = faqService.countByFilters(category, status, keyword);

    return Result.success(new PageResult<>(list, total));
}
```

注意需要在 import 中添加 `com.xiaofuzi.ai.dto.PageResult` 和 `java.util.Set`。

- [ ] **Step 3: 添加相似检测端点**

```java
@GetMapping("/faq/similar")
public Result<List<Map<String, Object>>> similarFaq(@RequestParam String question) {
    return Result.success(faqService.findSimilar(question));
}
```

- [ ] **Step 4: 添加批量操作端点**

```java
@RequireRole("admin")
@PostMapping("/faq/batch-delete")
public Result<Map<String, Object>> batchDelete(@RequestBody List<Long> ids) {
    faqService.batchDelete(ids);
    return Result.success(Map.of("success", true, "message", "批量删除完成", "count", ids.size()));
}

@RequireRole("admin")
@PostMapping("/faq/batch-update-category")
public Result<Map<String, Object>> batchUpdateCategory(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Long> ids = ((List<Number>) body.get("ids")).stream()
            .map(Number::longValue).collect(java.util.stream.Collectors.toList());
    String category = (String) body.get("category");
    faqService.batchUpdateCategory(ids, category);
    return Result.success(Map.of("success", true, "message", "批量更新分类完成", "count", ids.size()));
}

@RequireRole("admin")
@PostMapping("/faq/batch-update-status")
public Result<Map<String, Object>> batchUpdateStatus(@RequestBody Map<String, Object> body) {
    @SuppressWarnings("unchecked")
    List<Long> ids = ((List<Number>) body.get("ids")).stream()
            .map(Number::longValue).collect(java.util.stream.Collectors.toList());
    String status = (String) body.get("status");
    faqService.batchUpdateStatus(ids, status);
    return Result.success(Map.of("success", true, "message", "批量更新状态完成", "count", ids.size()));
}
```

- [ ] **Step 5: 添加导入端点**

```java
@RequireRole("admin")
@PostMapping("/faq/import")
public Result<Map<String, Object>> importFaq(@RequestParam("file") MultipartFile file) {
    List<FaqEntry> entries = new ArrayList<>();
    try (java.io.InputStream is = file.getInputStream()) {
        org.apache.poi.ss.usermodel.Workbook workbook =
            org.apache.poi.ss.usermodel.WorkbookFactory.create(is);
        org.apache.poi.ss.usermodel.Sheet sheet = workbook.getSheetAt(0);
        for (int i = 1; i <= sheet.getLastRowNum(); i++) {
            org.apache.poi.ss.usermodel.Row row = sheet.getRow(i);
            if (row == null) continue;
            String question = getCellString(row.getCell(0));
            String answer = getCellString(row.getCell(1));
            if (question == null || question.isBlank()) continue;
            FaqEntry entry = FaqEntry.builder()
                .question(question)
                .answer(answer != null ? answer : "")
                .category(getCellString(row.getCell(2)))
                .keywords(getCellString(row.getCell(3)))
                .status("active")
                .build();
            entries.add(entry);
        }
        workbook.close();
    } catch (Exception e) {
        logger.error("FAQ 导入失败", e);
        return Result.error("文件解析失败: " + e.getMessage());
    }
    int count = 0;
    for (FaqEntry entry : entries) {
        faqService.create(entry);
        count++;
    }
    return Result.success(Map.of("success", true, "message", "导入完成", "count", count));
}

private String getCellString(org.apache.poi.ss.usermodel.Cell cell) {
    if (cell == null) return null;
    return switch (cell.getCellType()) {
        case STRING -> cell.getStringCellValue().trim();
        case NUMERIC -> String.valueOf((long) cell.getNumericCellValue());
        default -> null;
    };
}
```

需要添加 import：`org.springframework.web.multipart.MultipartFile`、`java.util.ArrayList`。

- [ ] **Step 6: 添加导出端点**

```java
@GetMapping("/faq/export")
public void exportFaq(
        @RequestParam(required = false) String category,
        @RequestParam(required = false, defaultValue = "csv") String format,
        jakarta.servlet.http.HttpServletResponse response) throws Exception {

    List<FaqEntry> list;
    if (category != null && !category.isBlank()) {
        list = faqService.listByCategory(category);
    } else {
        list = faqService.listAll();
    }

    if ("xlsx".equalsIgnoreCase(format)) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        response.setHeader("Content-Disposition", "attachment; filename=faq_export.xlsx");
        org.apache.poi.xssf.usermodel.XSSFWorkbook workbook = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
        org.apache.poi.xssf.usermodel.XSSFSheet sheet = workbook.createSheet("FAQ");
        org.apache.poi.xssf.usermodel.XSSFRow header = sheet.createRow(0);
        header.createCell(0).setCellValue("问题");
        header.createCell(1).setCellValue("答案");
        header.createCell(2).setCellValue("分类");
        header.createCell(3).setCellValue("关键词");
        int rowIdx = 1;
        for (FaqEntry f : list) {
            org.apache.poi.xssf.usermodel.XSSFRow row = sheet.createRow(rowIdx++);
            row.createCell(0).setCellValue(f.getQuestion());
            row.createCell(1).setCellValue(f.getAnswer());
            row.createCell(2).setCellValue(f.getCategory() != null ? f.getCategory() : "");
            row.createCell(3).setCellValue(f.getKeywords() != null ? f.getKeywords() : "");
        }
        workbook.write(response.getOutputStream());
        workbook.close();
    } else {
        response.setContentType("text/csv; charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=faq_export.csv");
        response.getOutputStream().write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}); // BOM
        java.io.PrintWriter writer = response.getWriter();
        writer.println("问题,答案,分类,关键词");
        for (FaqEntry f : list) {
            writer.printf("\"%s\",\"%s\",\"%s\",\"%s\"\n",
                escapeCsv(f.getQuestion()),
                escapeCsv(f.getAnswer()),
                escapeCsv(f.getCategory()),
                escapeCsv(f.getKeywords()));
        }
        writer.flush();
    }
}

private String escapeCsv(String s) {
    if (s == null) return "";
    return s.replace("\"", "\"\"");
}
```

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/FaqController.java
git commit -m "feat: add FaqController pagination, batch ops, similar, import/export"
```

---

### Task 5: 后端统计端点

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/FaqController.java`

- [ ] **Step 1: 添加统计端点**

在 FaqController 中添加（在 `highFreqFaq` 方法之后）：

```java
@GetMapping("/faq/stats")
public Result<Map<String, Object>> getStats() {
    return Result.success(faqService.getStats());
}

@GetMapping("/faq/stats/trend")
public Result<List<Map<String, Object>>> getDailyTrend(@RequestParam(defaultValue = "30") int days) {
    return Result.success(faqService.getDailyTrend(days));
}

@GetMapping("/faq/stats/category-distribution")
public Result<List<Map<String, Object>>> getCategoryDistribution() {
    return Result.success(faqService.getCategoryDistribution());
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/FaqController.java
git commit -m "feat: add FAQ stats, trend, category-distribution endpoints"
```

---

### Task 6: 前端依赖安装

- [ ] **Step 1: 安装新依赖**

Run: `cd frontend && npm install @kangc/v-md-editor echarts vue-echarts xlsx`

- [ ] **Step 2: Commit**

```bash
git add frontend/package.json frontend/package-lock.json
git commit -m "chore: install v-md-editor, echarts, vue-echarts, xlsx"
```

---

### Task 7: 前端 API 层扩展

**Files:**
- Modify: `frontend/src/api/faq.ts`
- Modify: `frontend/src/types/index.ts`

- [ ] **Step 1: types/index.ts 添加新类型 + 更新 FaqEntry**

在 `FaqEntry` 接口中添加 `lastHitTime` 字段：

```typescript
export interface FaqEntry {
  id?: number
  question: string
  answer: string
  keywords?: string
  category?: string
  sourceDoc?: string
  headingPath?: string
  hitCount?: number
  lastHitTime?: string
  status?: string
  createTime?: string
  updateTime?: string
}
```

在 `FaqCandidate` 接口后添加：

```typescript
export interface FaqListParams {
  category?: string
  status?: string
  keyword?: string
  sortBy?: string
  sortOrder?: string
  page?: number
  size?: number
}

export interface FaqStats {
  totalFaq: number
  totalHits: number
  todayHits: number
}

export interface FaqTrendItem {
  day: string
  cnt: number
}

export interface FaqCategoryDistItem {
  category: string
  total_hits: number
}

export interface SimilarFaqItem {
  id: number
  question: string
  similarity: number
}
```

- [ ] **Step 2: api/faq.ts 重写**

```typescript
import { get, post, put, del } from './request'
import type { FaqEntry, FaqCandidate, FaqListParams, FaqStats, FaqTrendItem, FaqCategoryDistItem, SimilarFaqItem } from '@/types'

export interface PageResult<T> {
  list: T[]
  total: number
}

export const listFaq = (params?: FaqListParams) =>
  get<PageResult<FaqEntry>>('/faq/faq', params as Record<string, unknown>)

export const getFaq = (id: number) =>
  get<FaqEntry>(`/faq/faq/${id}`)

export const createFaq = (data: FaqEntry) =>
  post<FaqEntry>('/faq/create-faq', data)

export const updateFaq = (id: number, data: FaqEntry) =>
  put<FaqEntry>(`/faq/faq/${id}`, data)

export const deleteFaq = (id: number) =>
  del<{ success: boolean; message: string }>(`/faq/faq/${id}`)

export const highFreqFaq = (limit: number = 10) =>
  get<FaqEntry[]>('/faq/faq/high-freq', { limit })

export const faqCandidates = (limit: number = 20, minFrequency: number = 3) =>
  get<FaqCandidate[]>('/faq/faq/candidates', { limit, minFrequency })

export const similarFaq = (question: string) =>
  get<SimilarFaqItem[]>('/faq/faq/similar', { question })

export const batchDeleteFaq = (ids: number[]) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-delete', ids)

export const batchUpdateFaqCategory = (ids: number[], category: string) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-update-category', { ids, category })

export const batchUpdateFaqStatus = (ids: number[], status: string) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-update-status', { ids, status })

export const importFaq = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return post<{ success: boolean; message: string; count: number }>('/faq/faq/import', fd)
}

export const exportFaqUrl = (category?: string, format: string = 'csv') => {
  const params = new URLSearchParams()
  if (category) params.set('category', category)
  params.set('format', format)
  return `/api/faq/faq/export?${params.toString()}`
}

export const getFaqStats = () =>
  get<FaqStats>('/faq/faq/stats')

export const getFaqTrend = (days: number = 30) =>
  get<FaqTrendItem[]>('/faq/faq/stats/trend', { days })

export const getFaqCategoryDistribution = () =>
  get<FaqCategoryDistItem[]>('/faq/faq/stats/category-distribution')
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/api/faq.ts frontend/src/types/index.ts
git commit -m "feat: extend FAQ API layer with all new endpoints"
```

---

### Task 8: 前端 Pinia Store 扩展

**Files:**
- Modify: `frontend/src/stores/faq.ts`

- [ ] **Step 1: 重写 faq store**

```typescript
import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listFaq, createFaq, updateFaq, deleteFaq, faqCandidates,
  batchDeleteFaq, batchUpdateFaqCategory, batchUpdateFaqStatus, importFaq,
  getFaqStats, getFaqTrend, getFaqCategoryDistribution
} from '@/api/faq'
import type { FaqEntry, FaqCandidate, FaqListParams, FaqStats, FaqTrendItem, FaqCategoryDistItem } from '@/types'

export const useFaqStore = defineStore('faq', () => {
  const faqList = ref<FaqEntry[]>([])
  const faqTotal = ref(0)
  const loading = ref(false)

  const categories = ref<string[]>([])
  const candidates = ref<FaqCandidate[]>([])
  const candidatesLoading = ref(false)

  // 统计
  const stats = ref<FaqStats | null>(null)
  const trend = ref<FaqTrendItem[]>([])
  const categoryDist = ref<FaqCategoryDistItem[]>([])

  async function fetchList(params?: FaqListParams) {
    loading.value = true
    try {
      const res = await listFaq(params)
      faqList.value = res.list
      faqTotal.value = res.total
    } finally {
      loading.value = false
    }
  }

  async function fetchCategories() {
    const res = await listFaq({ size: 1000 })
    categories.value = [...new Set(res.list.map(f => f.category).filter(Boolean) as string[])]
  }

  async function create(data: FaqEntry) {
    await createFaq(data)
    await fetchList()
    fetchCategories()
  }

  async function update(id: number, data: FaqEntry) {
    await updateFaq(id, data)
    await fetchList()
  }

  async function remove(id: number) {
    await deleteFaq(id)
    await fetchList()
    fetchCategories()
  }

  async function fetchCandidates(limit: number = 20, minFrequency: number = 3) {
    candidatesLoading.value = true
    try {
      candidates.value = await faqCandidates(limit, minFrequency)
    } finally {
      candidatesLoading.value = false
    }
  }

  async function batchDelete(ids: number[]) {
    await batchDeleteFaq(ids)
    await fetchList()
    fetchCategories()
  }

  async function batchUpdateCategory(ids: number[], category: string) {
    await batchUpdateFaqCategory(ids, category)
    await fetchList()
    fetchCategories()
  }

  async function batchUpdateStatus(ids: number[], status: string) {
    await batchUpdateFaqStatus(ids, status)
    await fetchList()
  }

  async function importFile(file: File) {
    const res = await importFaq(file)
    await fetchList()
    fetchCategories()
    return res
  }

  async function fetchStats() {
    stats.value = await getFaqStats()
  }

  async function fetchTrend(days: number = 30) {
    trend.value = await getFaqTrend(days)
  }

  async function fetchCategoryDistribution() {
    categoryDist.value = await getFaqCategoryDistribution()
  }

  return {
    faqList, faqTotal, loading, categories, candidates, candidatesLoading,
    stats, trend, categoryDist,
    fetchList, fetchCategories, create, update, remove, fetchCandidates,
    batchDelete, batchUpdateCategory, batchUpdateStatus, importFile,
    fetchStats, fetchTrend, fetchCategoryDistribution
  }
})
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/stores/faq.ts
git commit -m "feat: extend FAQ store with pagination, batch ops, stats"
```

---

### Task 9: 前端 FaqList.vue 全面重构

**Files:**
- Modify: `frontend/src/views/faq/FaqList.vue`

这个文件需要从头重写。以下为完整内容。

- [ ] **Step 1: 写入新的 FaqList.vue 模板部分**

```vue
<template>
  <div class="faq-list-page">
    <!-- Tab 导航 -->
    <div class="faq-tabs">
      <span class="faq-tab active">FAQ 列表</span>
      <span class="faq-tab" @click="router.push('/faq/high-freq')">高频 FAQ</span>
      <span class="faq-tab" @click="router.push('/faq/dashboard')">统计看板</span>
    </div>

    <!-- Toolbar -->
    <div class="faq-toolbar">
      <div class="faq-toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索问题或关键词..."
          clearable
          size="small"
          style="width: 200px"
          @change="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filterCategory" placeholder="全部分类" clearable size="small" style="width: 120px; margin-left: 8px" @change="fetchPage(1)">
          <el-option v-for="cat in store.categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 110px; margin-left: 8px" @change="fetchPage(1)">
          <el-option label="活跃" value="active" />
          <el-option label="停用" value="inactive" />
          <el-option label="已删除" value="deleted" />
        </el-select>
      </div>
      <div class="faq-toolbar-right">
        <el-button size="small" :disabled="selectedIds.length === 0" @click="handleBatchDelete">批量删除</el-button>
        <el-button size="small" :disabled="selectedIds.length === 0" @click="batchCategoryVisible = true">批量改分类</el-button>
        <el-button size="small" :disabled="selectedIds.length === 0" @click="batchStatusVisible = true">批量改状态</el-button>
        <el-button size="small" @click="handleExport">导出</el-button>
        <el-button size="small" @click="importVisible = true">导入</el-button>
        <el-button type="primary" size="small" @click="openDialog()">+ 新建 FAQ</el-button>
      </div>
    </div>

    <!-- FAQ 候选挖掘 -->
    <el-card shadow="never" class="candidate-card">
      <template #header>
        <div class="candidate-header">
          <span>
            <strong>FAQ 候选</strong>
            <span class="candidate-subtitle">从聊天记录中挖掘的高频提问</span>
          </span>
          <span class="candidate-controls">
            <span class="candidate-label">最低频次</span>
            <el-input-number v-model="minFrequency" :min="2" :max="100" size="small" style="width: 90px" />
            <el-button size="small" type="primary" :loading="store.candidatesLoading" @click="loadCandidates">挖掘候选</el-button>
          </span>
        </div>
      </template>
      <el-table v-if="store.candidates.length > 0" :data="store.candidates" size="small" stripe style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="question" label="用户提问" show-overflow-tooltip />
        <el-table-column prop="frequency" label="出现次数" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.frequency }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="createFromCandidate(row.question)">创建 FAQ</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!store.candidatesLoading" description="暂无候选，点击「挖掘候选」从聊天记录中发现高频问题" :image-size="60" />
    </el-card>

    <!-- FAQ 表格 -->
    <div class="faq-table-section">
      <el-table
        ref="tableRef"
        :data="store.faqList"
        v-loading="store.loading"
        stripe
        style="width: 100%"
        @sort-change="onSortChange"
        @selection-change="onSelectionChange"
      >
        <el-table-column type="selection" width="40" />
        <el-table-column prop="question" label="问题" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="answer" label="答案" show-overflow-tooltip sortable="custom" />
        <el-table-column prop="category" label="分类" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" type="primary">{{ row.category }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="keywords" label="关键词" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中" sortable="custom" />
        <el-table-column prop="lastHitTime" label="最近命中" sortable="custom">
          <template #default="{ row }">
            <span>{{ row.lastHitTime || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">{{ row.status || '-' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="创建时间" sortable="custom" />
        <el-table-column label="操作" fixed="right">
          <template #default="{ row }">
            <el-button class="detail-link" link size="small" @click="openDialog(row)">编辑</el-button>
            <el-popconfirm title="确定删除此 FAQ？" @confirm="handleDelete(row.id!)">
              <template #reference>
                <el-button type="danger" link size="small">删除</el-button>
              </template>
            </el-popconfirm>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!store.loading && store.faqList.length === 0" description="暂无 FAQ" :image-size="60" />

      <div class="faq-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :total="store.faqTotal"
          layout="total, sizes, prev, pager, next"
          size="small"
          @current-change="fetchPage"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 FAQ' : '新建 FAQ'"
      width="720px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="formData.question" @input="onQuestionInput" />
        </el-form-item>
        <!-- 相似 FAQ 提示 -->
        <div v-if="similarFaqs.length > 0" class="similar-alert">
          <el-alert type="warning" :closable="false" show-icon title="检测到相似 FAQ" />
          <div v-for="item in similarFaqs" :key="item.id" class="similar-item">
            <span class="similar-question">{{ item.question }}</span>
            <el-tag size="small" type="warning">相似度 {{ item.similarity }}%</el-tag>
            <el-button link size="small" @click="openDialogById(item.id)">查看</el-button>
          </div>
        </div>
        <el-form-item label="答案" prop="answer">
          <v-md-editor v-model="formData.answer" height="300px" />
        </el-form-item>
        <el-form-item label="关键词" prop="keywords">
          <el-input v-model="formData.keywords" placeholder="逗号分隔" />
        </el-form-item>
        <el-form-item label="分类" prop="category">
          <el-input v-model="formData.category" />
        </el-form-item>
        <el-form-item label="状态" prop="status">
          <el-select v-model="formData.status" style="width: 100%">
            <el-option label="启用" value="active" />
            <el-option label="停用" value="inactive" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="handleSubmit">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量改分类弹窗 -->
    <el-dialog v-model="batchCategoryVisible" title="批量修改分类" width="400px">
      <el-input v-model="batchCategoryValue" placeholder="输入新分类" />
      <template #footer>
        <el-button @click="batchCategoryVisible = false">取消</el-button>
        <el-button type="primary" @click="doBatchUpdateCategory">确定</el-button>
      </template>
    </el-dialog>

    <!-- 批量改状态弹窗 -->
    <el-dialog v-model="batchStatusVisible" title="批量修改状态" width="400px">
      <el-select v-model="batchStatusValue" placeholder="选择状态" style="width: 100%">
        <el-option label="启用" value="active" />
        <el-option label="停用" value="inactive" />
      </el-select>
      <template #footer>
        <el-button @click="batchStatusVisible = false">取消</el-button>
        <el-button type="primary" @click="doBatchUpdateStatus">确定</el-button>
      </template>
    </el-dialog>

    <!-- 导入弹窗 -->
    <el-dialog v-model="importVisible" title="导入 FAQ" width="500px">
      <el-upload
        ref="importUploadRef"
        :auto-upload="false"
        :limit="1"
        :on-change="handleImportFileChange"
        :on-remove="() => importFile = null"
        accept=".csv,.xlsx,.xls"
        drag
      >
        <el-icon class="el-icon--upload" :size="32" color="#C8C4C0"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或点击选择</div>
        <div class="el-upload__hint">支持 CSV / Excel 文件</div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

    <!-- 导出弹窗 -->
    <el-dialog v-model="exportVisible" title="导出 FAQ" width="400px">
      <el-form label-width="80px">
        <el-form-item label="范围">
          <el-radio-group v-model="exportScope">
            <el-radio value="all">全部</el-radio>
            <el-radio value="category">按分类</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item v-if="exportScope === 'category'" label="分类">
          <el-select v-model="exportCategory" placeholder="选择分类" style="width: 100%">
            <el-option v-for="cat in store.categories" :key="cat" :label="cat" :value="cat" />
          </el-select>
        </el-form-item>
        <el-form-item label="格式">
          <el-radio-group v-model="exportFormat">
            <el-radio value="csv">CSV</el-radio>
            <el-radio value="xlsx">Excel</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="exportVisible = false">取消</el-button>
        <el-button type="primary" @click="doExport">导出</el-button>
      </template>
    </el-dialog>
  </div>
</template>
```

- [ ] **Step 2: 写入新的 FaqList.vue script 部分**

```typescript
<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useFaqStore } from '@/stores/faq'
import { similarFaq, exportFaqUrl, getFaq } from '@/api/faq'
import type { FaqEntry, SimilarFaqItem } from '@/types'

const router = useRouter()
const store = useFaqStore()

// Filters & sort
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const sortBy = ref('hit_count')
const sortOrder = ref<'asc' | 'desc'>('desc')
const currentPage = ref(1)
const pageSize = ref(10)
const selectedIds = ref<number[]>([])
const tableRef = ref()

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => fetchPage(1), 300)
}

function onSortChange({ prop, order }: { prop: string; order: string }) {
  sortBy.value = prop || 'hit_count'
  sortOrder.value = (order === 'ascending' ? 'asc' : 'desc')
  fetchPage(1)
}

function onSelectionChange(rows: FaqEntry[]) {
  selectedIds.value = rows.map(r => r.id!).filter(Boolean) as number[]
}

function onSizeChange(size: number) {
  pageSize.value = size
  fetchPage(1)
}

function fetchPage(page: number) {
  currentPage.value = page
  store.fetchList({
    category: filterCategory.value || undefined,
    status: filterStatus.value || undefined,
    keyword: searchKeyword.value || undefined,
    sortBy: sortBy.value,
    sortOrder: sortOrder.value,
    page,
    size: pageSize.value
  })
}

// Dialog
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const formRef = ref<FormInstance>()
const editId = ref<number | null>(null)

const formData = ref<FaqEntry>({
  question: '', answer: '', keywords: '', category: '', status: 'active'
})

const rules: FormRules = {
  question: [{ required: true, message: '请输入问题', trigger: 'blur' }],
  answer: [{ required: true, message: '请输入答案', trigger: 'blur' }]
}

// Similar FAQ detection
const similarFaqs = ref<SimilarFaqItem[]>([])
let similarTimer: ReturnType<typeof setTimeout> | null = null
function onQuestionInput(val: string) {
  if (similarTimer) clearTimeout(similarTimer)
  if (!val || val.trim().length < 3) {
    similarFaqs.value = []
    return
  }
  similarTimer = setTimeout(async () => {
    try {
      similarFaqs.value = await similarFaq(val)
    } catch { similarFaqs.value = [] }
  }, 500)
}

function openDialog(row?: FaqEntry) {
  similarFaqs.value = []
  if (row) {
    isEdit.value = true
    editId.value = row.id!
    formData.value = { ...row }
  } else {
    isEdit.value = false
    editId.value = null
    formData.value = { question: '', answer: '', keywords: '', category: '', status: 'active' }
  }
  dialogVisible.value = true
}

async function openDialogById(id: number) {
  dialogVisible.value = false
  try {
    const entry = await getFaq(id)
    openDialog(entry)
  } catch { ElMessage.error('获取 FAQ 失败') }
}

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await store.update(editId.value, formData.value)
      ElMessage.success('FAQ 已更新')
    } else {
      await store.create(formData.value)
      ElMessage.success('FAQ 已创建')
    }
    dialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await store.remove(id)
  ElMessage.success('FAQ 已删除')
  fetchPage(currentPage.value)
}

// Candidates
const minFrequency = ref(3)
function loadCandidates() {
  store.fetchCandidates(20, minFrequency.value)
}
function createFromCandidate(question: string) {
  similarFaqs.value = []
  formData.value = { question, answer: '', keywords: '', category: '', status: 'active' }
  isEdit.value = false
  editId.value = null
  dialogVisible.value = true
}

// Batch operations
const batchCategoryVisible = ref(false)
const batchCategoryValue = ref('')
const batchStatusVisible = ref(false)
const batchStatusValue = ref('')

async function handleBatchDelete() {
  try {
    await ElMessageBox.confirm(`确定删除选中的 ${selectedIds.value.length} 条 FAQ？`, '批量删除', { type: 'warning' })
    await store.batchDelete(selectedIds.value)
    ElMessage.success('批量删除完成')
    fetchPage(currentPage.value)
  } catch { /* cancelled */ }
}

async function doBatchUpdateCategory() {
  if (!batchCategoryValue.value) return
  await store.batchUpdateCategory(selectedIds.value, batchCategoryValue.value)
  ElMessage.success('批量更新分类完成')
  batchCategoryVisible.value = false
  batchCategoryValue.value = ''
  fetchPage(currentPage.value)
}

async function doBatchUpdateStatus() {
  if (!batchStatusValue.value) return
  await store.batchUpdateStatus(selectedIds.value, batchStatusValue.value)
  ElMessage.success('批量更新状态完成')
  batchStatusVisible.value = false
  batchStatusValue.value = ''
  fetchPage(currentPage.value)
}

// Import
const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importUploadRef = ref()

function handleImportFileChange(file: { raw?: File }) {
  importFile.value = file.raw || null
}

async function handleImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await store.importFile(importFile.value)
    ElMessage.success(`导入完成，共 ${(res as any).count || 0} 条`)
    importVisible.value = false
    importFile.value = null
    fetchPage(1)
  } catch { ElMessage.error('导入失败') }
  finally { importing.value = false }
}

// Export
const exportVisible = ref(false)
const exportScope = ref('all')
const exportCategory = ref('')
const exportFormat = ref('csv')

function handleExport() {
  if (selectedIds.value.length > 0) {
    // Export selected - download as CSV
    const rows = store.faqList.filter(f => selectedIds.value.includes(f.id!))
    const csv = '问题,答案,分类,关键词\n' + rows.map(r =>
      `"${(r.question||'').replace(/"/g,'""')}","${(r.answer||'').replace(/"/g,'""')}","${r.category||''}","${r.keywords||''}"`
    ).join('\n')
    const blob = new Blob(['﻿' + csv], { type: 'text/csv;charset=UTF-8' })
    const url = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = url; a.download = 'faq_export.csv'; a.click()
    URL.revokeObjectURL(url)
    ElMessage.success('导出完成')
  } else {
    exportVisible.value = true
  }
}

function doExport() {
  const cat = exportScope.value === 'category' ? exportCategory.value : undefined
  const url = exportFaqUrl(cat, exportFormat.value)
  window.open(url, '_blank')
  exportVisible.value = false
}

onMounted(() => {
  store.fetchCategories()
  fetchPage(1)
})
</script>
```

- [ ] **Step 3: 写入新的 FaqList.vue style 部分**

```css
<style scoped>
.faq-list-page {
  width: 100%;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.faq-tabs {
  display: flex;
  gap: 0;
  padding: 0 24px;
  border-bottom: 2px solid var(--border-base);
  background: var(--surface-warm);
}

.faq-tab {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.15s;
  user-select: none;
}

.faq-tab:hover { color: var(--text-secondary); }
.faq-tab.active { color: var(--primary); border-bottom-color: var(--primary); }

.faq-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-bottom: 1px solid var(--border-light);
}

.faq-toolbar-left { display: flex; align-items: center; }
.faq-toolbar-right { display: flex; align-items: center; gap: 6px; }

.candidate-card { margin: 16px 24px; }
.candidate-header { display: flex; justify-content: space-between; align-items: center; }
.candidate-subtitle { color: #909399; font-size: 13px; margin-left: 8px; }
.candidate-controls { display: flex; align-items: center; gap: 8px; }
.candidate-label { font-size: 13px; color: #606266; }

.faq-table-section { padding: 0 24px 16px; }

.faq-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px; }

.text-muted { color: var(--text-muted); }

.similar-alert { margin-bottom: 16px; }
.similar-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  margin-top: 6px;
}
.similar-question { flex: 1; font-size: 13px; color: var(--text-secondary); }

.detail-link { color: #4A8B8B !important; }
.detail-link:hover { color: #3A7070 !important; }
</style>
```

同时需要在 `<script setup>` 开头添加：
```typescript
import { UploadFilled } from '@element-plus/icons-vue'
```

以及 `<style scoped>` 前添加 markdown 编辑器的非 scoped 样式：
```css
<style>
/* v-md-editor theme overrides */
.v-md-editor { border: 1px solid var(--border-light); border-radius: var(--radius-md); box-shadow: none; }
</style>
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/faq/FaqList.vue
git commit -m "feat: full rewrite of FaqList with pagination, sort, batch, markdown editor, similar detection, import/export"
```

---

### Task 10: 前端 FaqDashboard.vue 新增统计看板

**Files:**
- Create: `frontend/src/views/faq/FaqDashboard.vue`

- [ ] **Step 1: 创建 FaqDashboard.vue**

```vue
<template>
  <div class="dashboard-page">
    <!-- Tab 导航 -->
    <div class="faq-tabs">
      <span class="faq-tab" @click="router.push('/faq/list')">FAQ 列表</span>
      <span class="faq-tab" @click="router.push('/faq/high-freq')">高频 FAQ</span>
      <span class="faq-tab active">统计看板</span>
    </div>

    <!-- 指标卡片 -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ store.stats?.totalFaq ?? '-' }}</div>
        <div class="stat-label">FAQ 总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ store.stats?.totalHits ?? '-' }}</div>
        <div class="stat-label">总命中次数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">{{ store.stats?.todayHits ?? '-' }}</div>
        <div class="stat-label">今日匹配次数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value">-</div>
        <div class="stat-label">待挖掘候选</div>
      </el-card>
    </div>

    <!-- 图表 -->
    <div class="charts-row">
      <el-card shadow="never" class="chart-card">
        <template #header><strong>命中趋势（近30天）</strong></template>
        <v-chart :option="trendOption" style="height: 300px" autoresize />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header><strong>分类命中分布</strong></template>
        <v-chart :option="pieOption" style="height: 300px" autoresize />
      </el-card>
    </div>

    <!-- 高频排行 + 统计汇总 -->
    <el-card shadow="never" class="table-card">
      <template #header><strong>高频 FAQ Top 20</strong></template>
      <el-table :data="topFaqs" size="small" stripe style="width: 100%">
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="question" label="问题" show-overflow-tooltip />
        <el-table-column prop="hitCount" label="命中次数" width="100" />
        <el-table-column prop="lastHitTime" label="最近命中" width="160" />
      </el-table>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useFaqStore } from '@/stores/faq'
import { highFreqFaq } from '@/api/faq'
import type { FaqEntry } from '@/types'
import { use } from 'echarts/core'
import { LineChart, PieChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([LineChart, PieChart, TitleComponent, TooltipComponent, GridComponent, LegendComponent, CanvasRenderer])

const router = useRouter()
const store = useFaqStore()
const topFaqs = ref<FaqEntry[]>([])

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 40, right: 20, top: 10, bottom: 24 },
  xAxis: { type: 'category', data: store.trend.map(t => t.day.substring(5)), axisLabel: { fontSize: 11 } },
  yAxis: { type: 'value', minInterval: 1 },
  series: [{ type: 'line', data: store.trend.map(t => t.cnt), smooth: true, lineStyle: { color: '#E87040' }, itemStyle: { color: '#E87040' } }]
}))

const pieOption = computed(() => ({
  tooltip: { trigger: 'item' },
  legend: { orient: 'vertical', right: 10, top: 'center' },
  series: [{
    type: 'pie', radius: ['40%', '70%'], center: ['40%', '50%'],
    data: store.categoryDist.map(d => ({ name: d.category, value: d.total_hits })),
    label: { show: false },
    itemStyle: { borderRadius: 2, borderColor: '#fff', borderWidth: 1 }
  }]
}))

onMounted(async () => {
  await Promise.all([
    store.fetchStats(),
    store.fetchTrend(30),
    store.fetchCategoryDistribution()
  ])
  topFaqs.value = await highFreqFaq(20)
})
</script>

<style scoped>
.dashboard-page {
  width: 100%;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.faq-tabs {
  display: flex;
  gap: 0;
  padding: 0 24px;
  border-bottom: 2px solid var(--border-base);
  background: var(--surface-warm);
}

.faq-tab {
  padding: 12px 24px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-muted);
  cursor: pointer;
  border-bottom: 2px solid transparent;
  margin-bottom: -2px;
  transition: all 0.15s;
  user-select: none;
}

.faq-tab:hover { color: var(--text-secondary); }
.faq-tab.active { color: var(--primary); border-bottom-color: var(--primary); }

.stats-row {
  display: flex;
  gap: 16px;
  padding: 20px 24px;
}

.stat-card {
  flex: 1;
  text-align: center;
}

.stat-value {
  font-size: 28px;
  font-weight: 700;
  color: var(--primary);
}

.stat-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-top: 4px;
}

.charts-row {
  display: flex;
  gap: 16px;
  padding: 0 24px 20px;
}

.chart-card {
  flex: 1;
}

.table-card {
  margin: 0 24px 20px;
}
</style>
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/faq/FaqDashboard.vue
git commit -m "feat: add FaqDashboard stats page with ECharts"
```

---

### Task 11: 前端 HighFreqFaq.vue 微调 — Markdown 渲染

**Files:**
- Modify: `frontend/src/views/faq/HighFreqFaq.vue`

- [ ] **Step 1: 更新标签页和答案渲染**

在 FaqList.vue 和 HighFreqFaq.vue 的标签页中增加「统计看板」，同时 HighFreqFaq 的答案区域改为 Markdown 预览。

具体修改 HighFreqFaq.vue：

标签页添加统计看板：
```html
<span class="faq-tab" @click="router.push('/faq/dashboard')">统计看板</span>
```

答案渲染改为：
```html
<div class="answer" v-if="expandedId === faq.id" v-html="renderedAnswer(faq)"></div>
```

添加 script 方法：
```typescript
import { createMarkdownParser } from '@/utils/markdown'

function renderedAnswer(faq: FaqEntry): string {
  return createMarkdownParser().render(faq.answer || '')
}
```

如果 `createMarkdownParser` 不存在，直接在组件内创建：
```typescript
import VMdPreview from '@kangc/v-md-editor/lib/preview'
import githubTheme from '@kangc/v-md-editor/lib/theme/github'
import createMarkdownIt from 'markdown-it'

// 在 script setup 外初始化
const md = createMarkdownIt()
VMdPreview.use(githubTheme)

function renderedAnswer(faq: FaqEntry): string {
  return md.render(faq.answer || '')
}
```

- [ ] **Step 2: Commit**

```bash
git add frontend/src/views/faq/HighFreqFaq.vue
git commit -m "feat: add markdown rendering to HighFreqFaq, dashboard tab"
```

---

### Task 12: 路由 + 侧边栏导航更新

**Files:**
- Modify: `frontend/src/router/index.ts`
- Modify: `frontend/src/App.vue`

- [ ] **Step 1: 路由添加 FaqDashboard**

在 `router/index.ts` 的 `HighFreqFaq` 路由后添加：

```typescript
{
  path: '/faq/dashboard',
  name: 'FaqDashboard',
  component: () => import('@/views/faq/FaqDashboard.vue'),
  meta: { title: 'FAQ 统计看板', roles: ['admin'] }
}
```

- [ ] **Step 2: App.vue 侧边栏更新 FAQ 管理的高亮匹配**

在 App.vue 的 FAQ 导航项中，更新 `isActive` 条件：

找到：
```html
:class="{ active: isActive('/faq/list') || isActive('/faq/high-freq') }"
```

改为：
```html
:class="{ active: isActive('/faq/list') || isActive('/faq/high-freq') || isActive('/faq/dashboard') }"
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/router/index.ts frontend/src/App.vue
git commit -m "feat: add FaqDashboard route and sidebar nav highlighting"
```

---

### Task 13: 最终验证

- [ ] **Step 1: 重启后端并测试 API**

```bash
cd backend && mvn spring-boot:run
```

测试关键 API：
```bash
curl http://localhost:8080/faq/faq?page=1&size=5
curl http://localhost:8080/faq/faq/stats
curl http://localhost:8080/faq/faq/similar?question=请假流程
```

- [ ] **Step 2: 前端编译检查**

```bash
cd frontend && npx vue-tsc --noEmit
```

- [ ] **Step 3: 前端 dev server 测试**

```bash
cd frontend && npm run dev
```

访问 `http://localhost:5174/faq/list` 验证分页/排序/批量/导入导出/Markdown 编辑器/相似检测。

访问 `http://localhost:5174/faq/dashboard` 验证统计看板图表。

- [ ] **Step 4: 最终 Commit**

```bash
git add -A
git commit -m "chore: final verification, minor fixes"
```
