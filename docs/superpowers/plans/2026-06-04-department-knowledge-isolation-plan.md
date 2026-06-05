# 部门级知识隔离 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 为知识库系统引入部门维度，用户归属部门，支持跨部门查阅，文档按部门隔离。

**Architecture:** 后端新增 DB 字段 + API 参数 + 向量过滤；前端侧边栏新增部门切换器，知识库页面新增部门筛选/显示。

**Tech Stack:** Java Spring Boot / MyBatis / PostgreSQL PGVector / Vue 3 / TypeScript / Pinia

---

### Task 1: 数据库迁移

**Files:**
- Create: `backend/src/main/resources/sql/migration-add-department.sql`

- [ ] **Step 1: 创建迁移 SQL 文件**

```sql
-- 用户表添加部门字段
ALTER TABLE chat_user ADD COLUMN IF NOT EXISTS department VARCHAR(64) DEFAULT '全公司';

-- 存量文档 NULL 部门设默认值
UPDATE knowledge_document SET department = '全公司' WHERE department IS NULL;

-- 知识文档表部门字段加索引
CREATE INDEX IF NOT EXISTS idx_kd_dept ON knowledge_document(department);

-- 初始化 zhangsan 的部门
UPDATE chat_user SET department = '全公司' WHERE username = 'zhangsan' AND department IS NULL;
```

- [ ] **Step 2: 执行迁移**

Run: 手动连接 PostgreSQL 执行或在 application.yml 中启用 `spring.sql.init.mode=always`

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/sql/migration-add-department.sql
git commit -m "feat: add department column migration for chat_user and index for knowledge_document"
```

---

### Task 2: 后端实体与 DTO 新增 department 字段

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/entity/ChatUser.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/dto/LoginResponse.java`

- [ ] **Step 1: ChatUser 实体添加 department 字段**

在 `ChatUser.java` 的 `role` 字段后添加：

```java
private String department;
```

- [ ] **Step 2: LoginResponse 添加 department 字段**

在 `LoginResponse.java` 的 `role` 字段后添加：

```java
private String department;
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/entity/ChatUser.java backend/src/main/java/com/xiaofuzi/ai/dto/LoginResponse.java
git commit -m "feat: add department field to ChatUser entity and LoginResponse DTO"
```

---

### Task 3: ChatUserMapper XML 与 Java 接口更新

**Files:**
- Modify: `backend/src/main/resources/mapper/ChatUserMapper.xml`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/mapper/ChatUserMapper.java`

- [ ] **Step 1: ChatUserMapper.xml ResultMap 添加 department 映射**

在 `ChatUserMapper.xml` 的 `<resultMap id="ChatUserResult">` 中，`role` 结果映射之后添加：

```xml
<result property="department" column="department"/>
```

- [ ] **Step 2: ChatUserMapper.xml 添加 updateDepartment SQL**

在 `</mapper>` 之前添加：

```xml
<update id="updateDepartment">
    UPDATE chat_user SET department = #{department} WHERE id = #{id}
</update>
```

- [ ] **Step 3: ChatUserMapper.java 添加 updateDepartment 方法**

在接口中添加：

```java
void updateDepartment(@Param("id") Long id, @Param("department") String department);
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/mapper/ChatUserMapper.xml backend/src/main/java/com/xiaofuzi/ai/mapper/ChatUserMapper.java
git commit -m "feat: add department mapping and updateDepartment to ChatUserMapper"
```

---

### Task 4: KnowledgeDocumentMapper 添加 department 过滤参数

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/mapper/KnowledgeDocumentMapper.java`
- Modify: `backend/src/main/resources/mapper/KnowledgeDocumentMapper.xml`

- [ ] **Step 1: KnowledgeDocumentMapper.java 方法签名添加 department 参数**

修改 `findByFilters` 方法签名，在 `keyword` 后添加 `department` 参数：

```java
List<KnowledgeDocument> findByFilters(@Param("category") String category,
                                      @Param("status") String status,
                                      @Param("keyword") String keyword,
                                      @Param("department") String department,
                                      @Param("sortBy") String sortBy,
                                      @Param("sortOrder") String sortOrder,
                                      @Param("offset") int offset,
                                      @Param("limit") int limit);
```

修改 `countByFilters` 方法签名：

```java
long countByFilters(@Param("category") String category,
                    @Param("status") String status,
                    @Param("keyword") String keyword,
                    @Param("department") String department);
```

- [ ] **Step 2: KnowledgeDocumentMapper.xml 添加 department 过滤条件**

在 `findByFilters` 的 `<where>` 块，`keyword` 条件之后添加：

```xml
<if test="department != null and department != ''">
    AND department = #{department}
</if>
```

在 `countByFilters` 的 `<where>` 块，`keyword` 条件之后也添加相同内容。

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/mapper/KnowledgeDocumentMapper.java backend/src/main/resources/mapper/KnowledgeDocumentMapper.xml
git commit -m "feat: add department filter to KnowledgeDocumentMapper findByFilters and countByFilters"
```

---

### Task 5: KnowledgeBaseService 部门支持

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java`

- [ ] **Step 1: ingestMultipartFile 添加 department 参数**

修改方法签名，在 `description` 后添加 `department` 参数：

```java
public void ingestMultipartFile(MultipartFile file, String parserCategory,
        String category, String description, String department) {
```

在 `KnowledgeDocument.builder()` 中添加：

```java
.department(department)
```

在 sharedMeta 中添加部门：

```java
if (department != null && !department.isBlank()) {
    sharedMeta.put("department", department);
}
```

- [ ] **Step 2: hybridSearch 添加 department 过滤**

修改 `keywordSearch` 方法，新增接受 `department` 参数的重载版本。

原 `keywordSearch(String query, int limit)` 内部委托给新方法 `keywordSearch(String query, int limit, String department)`：

```java
private List<Document> keywordSearch(String query, int limit) {
    return keywordSearch(query, limit, null);
}

private List<Document> keywordSearch(String query, int limit, String department) {
    try {
        StringBuilder sqlBuilder = new StringBuilder();
        sqlBuilder.append(String.format(
            "SELECT id, content, metadata, similarity(content, ?) AS keyword_score "
            + "FROM %s WHERE content %% ?"
            + " AND (metadata->>'content_type' IS DISTINCT FROM 'faq_entry')",
            qualifiedTable()));

        if (department != null && !department.isBlank()) {
            sqlBuilder.append(" AND (metadata->>'department') = ?");
        }

        sqlBuilder.append(" ORDER BY keyword_score DESC LIMIT ?");

        return vectorJdbcTemplate.query(sqlBuilder.toString(),
                ps -> {
                    int idx = 1;
                    ps.setString(idx++, query);
                    ps.setString(idx++, query);
                    if (department != null && !department.isBlank()) {
                        ps.setString(idx++, department);
                    }
                    ps.setInt(idx, limit);
                },
                (rs, rowNum) -> rowToDocument(rs));
    } catch (Exception e) {
        logger.warn("关键词检索失败: {}", e.getMessage());
        return List.of();
    }
}
```

修改 `hybridSearch` 方法签名添加 `department` 参数，并传递给 `keywordSearch`：

```java
public Map<String, Object> hybridSearch(String query, int topK,
        double similarityThreshold, String department) {
    // ... 内部调用 keywordSearch(query, topK * 2, department);
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java
git commit -m "feat: add department support to KnowledgeBaseService ingest and hybridSearch"
```

---

### Task 6: Controller 层 department 参数传递

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeBaseController.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeDocumentController.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/AuthController.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/component/DataInitializer.java`

- [ ] **Step 1: KnowledgeBaseController.uploadFile 添加 department 参数**

在 `uploadFile` 方法参数中添加：

```java
@RequestParam(value = "department", required = false) String department
```

修改 service 调用：

```java
knowledgeBaseService.ingestMultipartFile(file, parserCategory, category, description, department);
```

- [ ] **Step 2: KnowledgeDocumentController.listDocuments 添加 department 参数**

在 `listDocuments` 方法参数中添加：

```java
@RequestParam(required = false) String department,
```

修改 `findByFilters` 调用，在 `keyword` 后添加 `department`：

```java
List<KnowledgeDocument> list = documentMapper.findByFilters(
        category, status, keyword, department, sortBy, sortOrder, offset, limit);
long total = documentMapper.countByFilters(category, status, keyword, department);
```

- [ ] **Step 3: AuthController login 响应添加 department**

在 `login` 和 `me` 方法中，`LoginResponse.builder()` 添加：

```java
.department(user.getDepartment() != null ? user.getDepartment() : "全公司")
```

- [ ] **Step 4: DataInitializer 初始化默认部门**

在 `run` 方法末尾（确保 admin 角色之后）添加：

```java
// 3. 确保 zhangsan 有默认部门
if (admin != null && admin.getDepartment() == null) {
    chatUserMapper.updateDepartment(admin.getId(), "全公司");
    logger.info("已将 {} 的部门设置为 全公司", DEFAULT_ADMIN);
}
```

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeBaseController.java backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeDocumentController.java backend/src/main/java/com/xiaofuzi/ai/controller/AuthController.java backend/src/main/java/com/xiaofuzi/ai/component/DataInitializer.java
git commit -m "feat: wire department through controllers and data initializer"
```

---

### Task 7: 前端 API 与 Store 添加 department 支持

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/knowledge-base.ts`
- Modify: `frontend/src/stores/knowledge-base.ts`

- [ ] **Step 1: types/index.ts 添加 department 字段**

在 `ChatUser` 接口中添加：

```typescript
department?: string
```

在 `LoginResponse` 接口中添加：

```typescript
department?: string
```

- [ ] **Step 2: api/knowledge-base.ts DocumentListParams 添加 department**

在 `DocumentListParams` 接口中添加：

```typescript
department?: string
```

修改 `uploadFile` 函数签名，添加 `department` 参数：

```typescript
export const uploadFile = (file: File, parserCategory?: string, category?: string, description?: string, department?: string) => {
  const fd = new FormData()
  fd.append('file', file)
  if (parserCategory) fd.append('parserCategory', parserCategory)
  if (category) fd.append('category', category)
  if (description) fd.append('description', description)
  if (department) fd.append('department', department)
  return post<{ success: boolean; message: string }>('/knowledge-base/upload', fd)
}
```

- [ ] **Step 3: stores/knowledge-base.ts upload 添加 department**

修改 `upload` 函数签名：

```typescript
async function upload(file: File, parserCategory?: string, category?: string, description?: string, department?: string) {
  loading.value = true
  try {
    const res = await uploadFile(file, parserCategory, category, description, department)
    await fetchDocuments()
    return res
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/knowledge-base.ts frontend/src/stores/knowledge-base.ts
git commit -m "feat: add department to frontend API, store, and types"
```

---

### Task 8: 前端侧边栏部门切换器

**Files:**
- Modify: `frontend/src/App.vue`
- Create: `frontend/src/constants/departments.ts`

- [ ] **Step 1: 创建部门常量文件**

`frontend/src/constants/departments.ts`：

```typescript
export const DEPARTMENTS = ['全公司', '人力资源', '财务', '研发', '行政'] as const
```

- [ ] **Step 2: App.vue 添加部门切换器**

在 `<script setup>` 中添加：

```typescript
import { DEPARTMENTS } from '@/constants/departments'

const currentDepartment = ref(localStorage.getItem('selectedDepartment') || readUserFromStorage()?.department || '全公司')

function switchDepartment(dept: string) {
  currentDepartment.value = dept
  localStorage.setItem('selectedDepartment', dept)
}

// 在 user 信息更新时同步
watch(() => route.fullPath, () => {
  const user = readUserFromStorage()
  if (user?.department && !localStorage.getItem('selectedDepartment')) {
    currentDepartment.value = user.department
  }
})
```

在侧边栏模板中，导航区之后、sidebar-footer 之前添加部门切换器：

```html
<!-- Department Switcher -->
<div class="sidebar-dept">
  <div v-if="sidebarExpanded" class="dept-label">当前部门</div>
  <div class="dept-toggle" :class="{ expanded: sidebarExpanded }" @click="deptMenuVisible = !deptMenuVisible">
    <span class="dept-icon">🏢</span>
    <span v-if="sidebarExpanded" class="dept-name">{{ currentDepartment }}</span>
    <span v-if="sidebarExpanded" class="dept-arrow" :class="{ open: deptMenuVisible }">▼</span>
  </div>
  <div v-if="deptMenuVisible" class="dept-menu">
    <div v-for="dept in DEPARTMENTS" :key="dept"
      class="dept-item" :class="{ active: currentDepartment === dept }"
      @click="switchDepartment(dept); deptMenuVisible = false">
      {{ dept }}
    </div>
  </div>
</div>
```

添加对应的 ref：

```typescript
const deptMenuVisible = ref(false)
```

在 `onDocumentClick` 中也关闭部门菜单：

修改 userMenuRef 点击外部检测，增加对部门菜单的引用：

```typescript
const deptMenuRef = ref<HTMLElement | null>(null)

function onDocumentClick(e: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    userMenuVisible.value = false
  }
  if (deptMenuRef.value && !deptMenuRef.value.contains(e.target as Node)) {
    deptMenuVisible.value = false
  }
}
```

- [ ] **Step 3: 添加部门切换器 CSS**

在 `<style scoped>` 的 `.sidebar-footer` 之前添加：

```css
/* Department Switcher */
.sidebar-dept {
  padding: 0 12px 4px;
  position: relative;
}
.icon-sidebar:not(.expanded) .sidebar-dept {
  display: flex;
  justify-content: center;
  padding: 0 0 4px;
}
.dept-label {
  font-size: 10px;
  color: #555;
  text-transform: uppercase;
  padding: 0 8px 6px;
  letter-spacing: 0.5px;
}
.dept-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: #ccc;
  transition: background 0.15s;
}
.dept-toggle:hover { background: rgba(255,255,255,0.08); }
.dept-toggle:not(.expanded) {
  justify-content: center;
  padding: 8px;
}
.dept-icon { font-size: 14px; flex-shrink: 0; }
.dept-name { flex: 1; }
.dept-arrow {
  font-size: 9px;
  color: #666;
  transition: transform 0.15s;
}
.dept-arrow.open { transform: rotate(180deg); }
.dept-menu {
  position: absolute;
  top: 100%;
  left: 12px;
  right: 12px;
  background: #333;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: var(--radius-md);
  overflow: hidden;
  z-index: 95;
  margin-top: 2px;
}
.icon-sidebar:not(.expanded) .dept-menu {
  left: 0;
  right: auto;
  width: 180px;
}
.dept-item {
  padding: 8px 12px;
  font-size: 12px;
  color: #999;
  cursor: pointer;
  transition: all 0.1s;
}
.dept-item:hover { color: #ccc; background: rgba(255,255,255,0.04); }
.dept-item.active { color: var(--primary); background: rgba(232,112,64,0.12); }
```

- [ ] **Step 4: Provide currentDepartment via localStorage pattern**

修改 `knowledge-base.ts` store，让 `fetchDocuments` 使用 localStorage 中的 `selectedDepartment`。无需传递 provide/inject，直接从 localStorage 读取。

- [ ] **Step 5: Commit**

```bash
git add frontend/src/App.vue frontend/src/constants/departments.ts
git commit -m "feat: add department switcher to sidebar"
```

---

### Task 9: 知识库页面部门展示与筛选

**Files:**
- Modify: `frontend/src/views/knowledge/KnowledgeBase.vue`

- [ ] **Step 1: 工具栏添加部门过滤器**

在 `filterStatus` 的 `el-select` 之后添加：

```html
<el-select v-model="filterDepartment" placeholder="全部部门" clearable size="small"
  style="width: 110px; margin-left: 8px" @change="fetchPage(1)">
  <el-option v-for="dept in DEPARTMENTS" :key="dept" :label="dept" :value="dept" />
</el-select>
```

- [ ] **Step 2: 导入部门常量**

```typescript
import { DEPARTMENTS } from '@/constants/departments'
```

- [ ] **Step 3: 添加 filterDepartment ref**

```typescript
const filterDepartment = ref('')
```

- [ ] **Step 4: 表格添加部门列**

在分类列之后、分块列之前添加：

```html
<el-table-column prop="department" label="部门" width="100" sortable="custom">
  <template #default="{ row }">
    <el-tag v-if="row.department" size="small" :type="departmentTagType(row.department)" effect="plain">
      {{ row.department }}
    </el-tag>
    <span v-else class="text-muted">-</span>
  </template>
</el-table-column>
```

- [ ] **Step 5: 添加 departmentTagType 函数**

```typescript
const DEPT_TAG_TYPES = ['', 'success', 'warning', 'danger', 'info'] as const

function departmentTagType(dept: string): string {
  let hash = 0
  for (let i = 0; i < dept.length; i++) hash = ((hash << 5) - hash + dept.charCodeAt(i)) | 0
  return DEPT_TAG_TYPES[Math.abs(hash) % DEPT_TAG_TYPES.length]
}
```

- [ ] **Step 6: 上传对话框添加部门字段**

在上传对话框中，描述字段之前添加：

```html
<div class="upload-form-field" style="margin-bottom: 14px">
  <label class="upload-form-label">部门 <span style="color:#E87040">*</span></label>
  <el-select v-model="uploadDepartment" placeholder="请选择部门" style="width: 100%">
    <el-option v-for="dept in DEPARTMENTS" :key="dept" :label="dept" :value="dept" />
  </el-select>
</div>
```

添加 ref：

```typescript
const uploadDepartment = ref(readUserDepartment())
```

添加辅助函数：

```typescript
function readUserDepartment(): string {
  try {
    const raw = localStorage.getItem('currentUser')
    const user = raw ? JSON.parse(raw) : null
    return user?.department || '全公司'
  } catch { return '全公司' }
}
```

- [ ] **Step 7: 修改 fetchPage 传递 department**

```typescript
function fetchPage(page: number) {
  currentPage.value = page
  store.fetchDocuments({
    category: filterCategory.value || undefined,
    status: filterStatus.value || undefined,
    keyword: searchKeyword.value || undefined,
    department: filterDepartment.value || undefined,
    sortBy: sortBy.value,
    sortOrder: sortOrder.value,
    page,
    size: pageSize.value
  })
}
```

- [ ] **Step 8: 修改 handleUpload 传递 department**

```typescript
await store.upload(
  uploadFile.value,
  uploadParserCategory.value || undefined,
  uploadCategory.value || undefined,
  uploadDescription.value || undefined,
  uploadDepartment.value || undefined
)
```

- [ ] **Step 9: 修改 openUploadDialog 重置 department**

```typescript
function openUploadDialog() {
  uploadFile.value = null
  uploadParserCategory.value = ''
  uploadCategory.value = ''
  uploadDescription.value = ''
  uploadDepartment.value = readUserDepartment()
  uploadDialogVisible.value = true
}
```

- [ ] **Step 10: 修改 sortBy 白名单添加 department**

在 sortBy ref 相关逻辑中，`ALLOWED_SORT_COLUMNS` 是后端概念，前端排序通过 prop 指定 `sortable="custom"` 即可。无需额外改动。

- [ ] **Step 11: Commit**

```bash
git add frontend/src/views/knowledge/KnowledgeBase.vue
git commit -m "feat: add department filter, column, and upload field to KnowledgeBase page"
```

---

### Task 10: 端到端验证

- [ ] **Step 1: 重启后端服务**

```bash
cd backend && mvn spring-boot:run
```

- [ ] **Step 2: 重启前端 dev server**

```bash
cd frontend && npm run dev
```

- [ ] **Step 3: 验证清单**

| 验证项 | 步骤 |
|--------|------|
| 用户登录返回 department | 登录 zhangsan，检查 localStorage currentUser 含 department |
| 侧边栏部门切换 | 点击部门切换器，选择一个部门，确认 UI 更新 |
| 切换部门后刷新页面 | F5，确认部门选择保持（localStorage） |
| 上传文档选部门 | 管理员上传文档，选择部门为"财务"，上传成功 |
| 文档列表按部门过滤 | 选择财务部门，列表仅显示财务部门文档 |
| 文档详情显示部门 | 打开详情抽屉，确认部门字段显示 |
| 表格部门列多彩标签 | 不同部门标签颜色不同 |
| 全公司不看过滤 | 选择"全公司"，列表显示全部文档 |
| 存量数据迁移 | 查询 knowledge_document WHERE department IS NULL → 0 rows |

- [ ] **Step 4: Commit 修复问题（如有）**

---

### 文件改动汇总

| 文件 | 操作 | 任务 |
|------|------|------|
| `backend/src/main/resources/sql/migration-add-department.sql` | 新建 | Task 1 |
| `backend/.../entity/ChatUser.java` | 修改 | Task 2 |
| `backend/.../dto/LoginResponse.java` | 修改 | Task 2 |
| `backend/.../mapper/ChatUserMapper.java` | 修改 | Task 3 |
| `backend/.../mapper/ChatUserMapper.xml` | 修改 | Task 3 |
| `backend/.../mapper/KnowledgeDocumentMapper.java` | 修改 | Task 4 |
| `backend/.../mapper/KnowledgeDocumentMapper.xml` | 修改 | Task 4 |
| `backend/.../rag/KnowledgeBaseService.java` | 修改 | Task 5 |
| `backend/.../controller/KnowledgeBaseController.java` | 修改 | Task 6 |
| `backend/.../controller/KnowledgeDocumentController.java` | 修改 | Task 6 |
| `backend/.../controller/AuthController.java` | 修改 | Task 6 |
| `backend/.../component/DataInitializer.java` | 修改 | Task 6 |
| `frontend/src/types/index.ts` | 修改 | Task 7 |
| `frontend/src/api/knowledge-base.ts` | 修改 | Task 7 |
| `frontend/src/stores/knowledge-base.ts` | 修改 | Task 7 |
| `frontend/src/constants/departments.ts` | 新建 | Task 8 |
| `frontend/src/App.vue` | 修改 | Task 8 |
| `frontend/src/views/knowledge/KnowledgeBase.vue` | 修改 | Task 9 |
