# 知识版本与变更追溯 — 实现计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 建立文档组-版本管理机制，默认检索最新版本，支持用户切换历史版本查询，实现知识版本追溯。

**Architecture:** 新增 `document_group` 表管理文档组 → `knowledge_document` 通过 `group_id` 建立版本关联 → 向量 chunk metadata 写入 `group_id`/`version`/`is_latest` → 检索阶段用 filter 过滤 → 引用出处面板展示版本并可切换。

**Tech Stack:** Spring Boot / MyBatis / PostgreSQL+pgvector / Vue 3 + Element Plus / Pinia

---

### Task 1: 数据库迁移

**Files:**
- Create: `backend/src/main/resources/db/migration/V20260605__document_group.sql`

- [ ] **Step 1: 编写迁移 SQL**

```sql
-- 创建文档组表
CREATE TABLE IF NOT EXISTS document_group (
    id                  BIGSERIAL PRIMARY KEY,
    name                VARCHAR(255) NOT NULL,
    latest_document_id  BIGINT,
    department          VARCHAR(100),
    status              VARCHAR(20) DEFAULT 'active',
    create_time         TIMESTAMP DEFAULT NOW(),
    update_time         TIMESTAMP DEFAULT NOW()
);

-- knowledge_document 新增版本追溯列
ALTER TABLE knowledge_document
    ADD COLUMN IF NOT EXISTS group_id  BIGINT,
    ADD COLUMN IF NOT EXISTS is_latest BOOLEAN DEFAULT TRUE;

-- 为已有数据补齐：每条 active 文档自成一个 group
DO $$
DECLARE
    r RECORD;
    gid BIGINT;
BEGIN
    FOR r IN SELECT id, document_name, department FROM knowledge_document WHERE status = 'active' AND group_id IS NULL
    LOOP
        INSERT INTO document_group (name, latest_document_id, department)
        VALUES (r.document_name, r.id, r.department)
        RETURNING id INTO gid;
        UPDATE knowledge_document SET group_id = gid, is_latest = TRUE WHERE id = r.id;
    END LOOP;
END $$;

-- 已删除文档无法获取足够信息用于迁移，标记 group 后可正常管理
UPDATE knowledge_document SET is_latest = FALSE WHERE status = 'archived' AND is_latest IS NULL;

CREATE INDEX IF NOT EXISTS idx_knowledge_document_group_id ON knowledge_document(group_id);
CREATE INDEX IF NOT EXISTS idx_knowledge_document_is_latest ON knowledge_document(is_latest);
```

- [ ] **Step 2: 执行迁移**

```bash
# 如果有 flyway，自动执行。否则手动执行 SQL 文件。
```

- [ ] **Step 3: 验证表结构**

```sql
\d document_group
\d knowledge_document
SELECT count(*) FROM document_group;
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/resources/db/migration/V20260605__document_group.sql
git commit -m "feat: add document_group table and version columns to knowledge_document"
```

---

### Task 2: DocumentGroup 实体与 Mapper

**Files:**
- Create: `backend/src/main/java/com/xiaofuzi/ai/entity/DocumentGroup.java`
- Create: `backend/src/main/java/com/xiaofuzi/ai/mapper/DocumentGroupMapper.java`
- Create: `backend/src/main/resources/mapper/DocumentGroupMapper.xml`

- [ ] **Step 1: 创建 DocumentGroup 实体**

```java
package com.xiaofuzi.ai.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentGroup {
    private Long id;
    private String name;
    private Long latestDocumentId;
    private String department;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
```

- [ ] **Step 2: 创建 DocumentGroupMapper 接口**

```java
package com.xiaofuzi.ai.mapper;

import com.xiaofuzi.ai.entity.DocumentGroup;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DocumentGroupMapper {

    void insert(DocumentGroup group);

    DocumentGroup findById(@Param("id") Long id);

    List<DocumentGroup> findByDepartment(@Param("department") String department);

    void updateLatestDocument(@Param("id") Long id, @Param("latestDocumentId") Long latestDocumentId);

    void updateStatus(@Param("id") Long id, @Param("status") String status);

    /** 查询某 group 下所有文档（含 archived），用于获取 available_versions */
    List<DocumentGroup> findAllActive();
}
```

- [ ] **Step 3: 创建 DocumentGroupMapper.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "http://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="com.xiaofuzi.ai.mapper.DocumentGroupMapper">

    <resultMap id="DocumentGroupResult" type="com.xiaofuzi.ai.entity.DocumentGroup">
        <id property="id" column="id"/>
        <result property="name" column="name"/>
        <result property="latestDocumentId" column="latest_document_id"/>
        <result property="department" column="department"/>
        <result property="status" column="status"/>
        <result property="createTime" column="create_time"/>
        <result property="updateTime" column="update_time"/>
    </resultMap>

    <insert id="insert" parameterType="com.xiaofuzi.ai.entity.DocumentGroup"
            useGeneratedKeys="true" keyProperty="id">
        INSERT INTO document_group (name, latest_document_id, department, status, create_time, update_time)
        VALUES (#{name}, #{latestDocumentId}, #{department}, COALESCE(#{status}, 'active'), NOW(), NOW())
    </insert>

    <select id="findById" resultMap="DocumentGroupResult">
        SELECT * FROM document_group WHERE id = #{id}
    </select>

    <select id="findByDepartment" resultMap="DocumentGroupResult">
        SELECT * FROM document_group WHERE department = #{department} AND status = 'active'
    </select>

    <select id="findAllActive" resultMap="DocumentGroupResult">
        SELECT * FROM document_group WHERE status = 'active'
    </select>

    <update id="updateLatestDocument">
        UPDATE document_group SET latest_document_id = #{latestDocumentId}, update_time = NOW()
        WHERE id = #{id}
    </update>

    <update id="updateStatus">
        UPDATE document_group SET status = #{status}, update_time = NOW() WHERE id = #{id}
    </update>

</mapper>
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/entity/DocumentGroup.java \
        backend/src/main/java/com/xiaofuzi/ai/mapper/DocumentGroupMapper.java \
        backend/src/main/resources/mapper/DocumentGroupMapper.xml
git commit -m "feat: add DocumentGroup entity and mapper"
```

---

### Task 3: KnowledgeDocument 实体与 Mapper 扩展

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/entity/KnowledgeDocument.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/mapper/KnowledgeDocumentMapper.java`
- Modify: `backend/src/main/resources/mapper/KnowledgeDocumentMapper.xml`

- [ ] **Step 1: KnowledgeDocument 新增字段**

在 `KnowledgeDocument.java` 中添加：

```java
private Long groupId;      // 在 version 字段附近
private Boolean isLatest;
```

同时更新 `@Builder` 的 `@AllArgsConstructor` lombok 会自动处理。还需在 resultMap 和 insert 中对应添加。

- [ ] **Step 2: Mapper 接口新增方法**

在 `KnowledgeDocumentMapper.java` 中添加：

```java
List<KnowledgeDocument> findByGroupId(@Param("groupId") Long groupId);

void markNotLatestByGroup(@Param("groupId") Long groupId);
```

- [ ] **Step 3: Mapper XML 新增查询**

在 `KnowledgeDocumentMapper.xml` 中添加：

```xml
<select id="findByGroupId" resultMap="KnowledgeDocumentResult">
    SELECT * FROM knowledge_document WHERE group_id = #{groupId} ORDER BY create_time DESC
</select>

<update id="markNotLatestByGroup">
    UPDATE knowledge_document SET is_latest = FALSE, status = 'archived', update_time = NOW()
    WHERE group_id = #{groupId} AND is_latest = TRUE
</update>
```

同时更新已有的 `insert` 语句，添加 `group_id` 和 `is_latest` 列：

```xml
<insert id="insert" parameterType="com.xiaofuzi.ai.entity.KnowledgeDocument"
        useGeneratedKeys="true" keyProperty="id">
    INSERT INTO knowledge_document (document_name, document_type, file_path, file_size,
        category, department, version, effective_date, description, chunk_count,
        group_id, is_latest, status, create_time, update_time)
    VALUES (#{documentName}, #{documentType}, #{filePath}, #{fileSize},
        #{category}, #{department}, #{version}, #{effectiveDate}, #{description},
        COALESCE(#{chunkCount}, 0), #{groupId},
        COALESCE(#{isLatest}, TRUE), COALESCE(#{status}, 'active'), NOW(), NOW())
</insert>
```

更新 `resultMap` 添加新列映射：

```xml
<result property="groupId" column="group_id"/>
<result property="isLatest" column="is_latest"/>
```

更新 `update` 语句添加 `group_id` 和 `is_latest`：

```xml
<update id="update" parameterType="com.xiaofuzi.ai.entity.KnowledgeDocument">
    UPDATE knowledge_document
    SET document_name = #{documentName},
        document_type = #{documentType},
        file_path     = #{filePath},
        file_size     = #{fileSize},
        category      = #{category},
        department    = #{department},
        version       = #{version},
        group_id      = #{groupId},
        is_latest     = #{isLatest},
        effective_date = #{effectiveDate},
        description   = #{description},
        chunk_count   = #{chunkCount},
        update_time   = NOW()
    WHERE id = #{id}
</update>
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/entity/KnowledgeDocument.java \
        backend/src/main/java/com/xiaofuzi/ai/mapper/KnowledgeDocumentMapper.java \
        backend/src/main/resources/mapper/KnowledgeDocumentMapper.xml
git commit -m "feat: extend KnowledgeDocument with groupId and isLatest fields"
```

---

### Task 4: 版本号自动提取

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java`

- [ ] **Step 1: 添加版本提取方法**

在 `KnowledgeBaseService.java` 中添加：

```java
/**
 * 从文档文本内容中自动提取版本号。
 * 策略：正则优先匹配常见中文制度文档的年份模式，提取失败时回退为当前年份。
 */
private String extractVersion(List<Document> parsedDocs) {
    String fullText = parsedDocs.stream()
            .map(Document::getText)
            .collect(Collectors.joining("\n"));

    // 匹配文号中的年份，如：〔2026〕、(2025)、[2026]
    java.util.regex.Matcher m = java.util.regex.Pattern.compile("[〔\\(\\[]\\s*(20\\d{2})\\s*[〕\\)\\]]").matcher(fullText);
    if (m.find()) {
        return m.group(1);
    }

    // 匹配发布日期，如：2026年1月1日、2026-01-01
    m = java.util.regex.Pattern.compile("(20\\d{2})\\s*年|(20\\d{2})[-/]\\d{1,2}[-/]\\d{1,2}").matcher(fullText);
    if (m.find()) {
        return m.group(1) != null ? m.group(1) : m.group(2);
    }

    // 匹配标题中的版本号：v2026, V2025
    m = java.util.regex.Pattern.compile("[vV](20\\d{2})").matcher(fullText);
    if (m.find()) {
        return m.group(1);
    }

    // 回退：当前年份
    return String.valueOf(java.time.Year.now().getValue());
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java
git commit -m "feat: add version auto-extraction from document content"
```

---

### Task 5: 上传流程改造

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeBaseController.java`

- [ ] **Step 1: 注入依赖**

在 `KnowledgeBaseService` 构造函数中添加 `DocumentGroupMapper`：

```java
private final DocumentGroupMapper documentGroupMapper;

public KnowledgeBaseService(VectorStore vectorStore, DocumentParserFactory parserFactory,
        KnowledgeDocumentMapper documentMapper,
        @Qualifier("vectorJdbcTemplate") JdbcTemplate vectorJdbcTemplate,
        ChatModel chatModel,
        @Value("${spring.ai.vectorstore.pgvector.schema-name}") String schemaName,
        @Value("${spring.ai.vectorstore.pgvector.table-name}") String vectorTableName,
        DocumentGroupMapper documentGroupMapper) {
    // ... existing assignments ...
    this.documentGroupMapper = documentGroupMapper;
}
```

- [ ] **Step 2: 改造 ingestMultipartFile，支持 parentDocumentId 参数**

修改 `ingestMultipartFile` 签名，新增 `Long parentDocumentId` 参数。完整替换方法：

```java
public void ingestMultipartFile(MultipartFile file, String parserCategory,
        String category, String description, String department, Long parentDocumentId) {
    String fileName = file.getOriginalFilename();
    if (fileName == null || fileName.isBlank()) {
        throw new IllegalArgumentException("文件名不能为空");
    }

    if (!parserFactory.isSupported(fileName)) {
        throw new IllegalArgumentException("不支持的文件类型: " + fileName);
    }

    DocumentGroup group;
    if (parentDocumentId != null) {
        // 这是已有文档的新版本
        KnowledgeDocument parent = documentMapper.findById(parentDocumentId);
        if (parent == null) {
            throw new IllegalArgumentException("父文档不存在: " + parentDocumentId);
        }
        if (parent.getGroupId() == null) {
            throw new IllegalArgumentException("父文档无关联文档组");
        }
        group = documentGroupMapper.findById(parent.getGroupId());
        if (group == null) {
            throw new IllegalArgumentException("文档组不存在");
        }
    } else {
        // 首次上传 — 创建新文档组
        group = DocumentGroup.builder()
                .name(fileName)
                .department(department)
                .status("active")
                .build();
        documentGroupMapper.insert(group);
    }

    try (InputStream is = file.getInputStream()) {
        DocumentParser parser = parserFactory.getParser(fileName, parserCategory);
        List<Document> parsedDocs = parser.parse(is);

        // 自动提取版本号
        String version = extractVersion(parsedDocs);

        KnowledgeDocument doc = KnowledgeDocument.builder()
                .documentName(fileName)
                .documentType(getFileExtension(fileName))
                .fileSize(file.getSize())
                .category(category)
                .description(description)
                .version(version)
                .status("active")
                .department(department)
                .groupId(group.getId())
                .isLatest(true)
                .build();
        documentMapper.insert(doc);

        Map<String, Object> sharedMeta = new HashMap<>();
        sharedMeta.put("source", fileName);
        sharedMeta.put("file_type", getFileExtension(fileName));
        sharedMeta.put("document_id", doc.getId().toString());
        sharedMeta.put("group_id", String.valueOf(group.getId()));
        sharedMeta.put("version", version);
        sharedMeta.put("is_latest", "true");
        if (category != null && !category.isBlank()) {
            sharedMeta.put("document_category", category);
        }
        if (department != null && !department.isBlank()) {
            sharedMeta.put("department", department);
        }
        ingestParsedDocuments(parsedDocs, sharedMeta, doc.getId());

        doc.setChunkCount(countChunksInLastIngest(parsedDocs, sharedMeta));

        // 如果这是新版本，归档旧版本
        if (parentDocumentId != null) {
            documentMapper.markNotLatestByGroup(group.getId());
            doc.setIsLatest(true);
        }

        documentMapper.update(doc);

        // 更新 group 的 latest_document_id
        documentGroupMapper.updateLatestDocument(group.getId(), doc.getId());

        logger.info("文件上传导入完成: {}, docId={}, groupId={}, version={}, 共 {} 个解析单元",
                fileName, doc.getId(), group.getId(), version, parsedDocs.size());
    } catch (Exception e) {
        logger.error("解析上传文件失败: {}, docId 未知", fileName, e);
        throw new RuntimeException("文件解析失败: " + e.getMessage(), e);
    }
}
```

- [ ] **Step 3: 更新 Controller 上传接口**

在 `KnowledgeBaseController.java` 的 `uploadFile` 方法中添加 `parentDocumentId` 参数：

```java
@RequireRole("admin")
@PostMapping("/upload")
public Result<Map<String, Object>> uploadFile(
        @RequestParam("file") MultipartFile file,
        @RequestParam(value = "parserCategory", required = false) String parserCategory,
        @RequestParam(value = "category", required = false) String category,
        @RequestParam(value = "description", required = false) String description,
        @RequestParam(value = "department", required = false) String department,
        @RequestParam(value = "parentDocumentId", required = false) Long parentDocumentId) {
    if (file.isEmpty()) {
        return Result.error("上传文件不能为空");
    }

    knowledgeBaseService.ingestMultipartFile(file, parserCategory, category, description,
            department, parentDocumentId);
    logger.info("文件上传并摄入完成: {}", file.getOriginalFilename());

    return Result.success(Map.of(
            "success", true,
            "message", "文件上传并摄入向量库成功"
    ));
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java \
        backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeBaseController.java
git commit -m "feat: add parentDocumentId support to upload flow for version chaining"
```

---

### Task 6: 检索逻辑 — hybridSearch 版本过滤

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java`

- [ ] **Step 1: hybridSearch 加 is_latest 过滤**

修改 `hybridSearch` 方法，添加 `versionOverrides` 参数。当前方法签名为：

```java
public Map<String, Object> hybridSearch(String query, int topK, double similarityThreshold, String department)
```

改为：

```java
public Map<String, Object> hybridSearch(String query, int topK, double similarityThreshold,
        String department, List<VersionOverride> versionOverrides)
```

在 `KnowledgeBaseService.java` 同级目录创建 `VersionOverride` record（或作为内部 record）。

在向量检索和关键词检索处添加版本过滤逻辑：

```java
// 在 hybridSearch 方法中，构建 filter expression
String filterExpr = null;
if (versionOverrides != null && !versionOverrides.isEmpty()) {
    // 版本切换：按 group_id + version 精确过滤
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < versionOverrides.size(); i++) {
        VersionOverride vo = versionOverrides.get(i);
        if (i > 0) sb.append(" OR ");
        sb.append("(group_id == '").append(vo.groupId()).append("' AND version == '").append(vo.version()).append("')");
    }
    filterExpr = sb.toString();
} else {
    // 默认：只看最新版本
    filterExpr = "is_latest == 'true'";
}

SearchRequest.Builder vectorReq = SearchRequest.builder()
        .query(query)
        .topK(topK * 2)
        .similarityThreshold(similarityThreshold)
        .filterExpression(filterExpr);
```

关键词检索 SQL 对应修改：

```java
private List<Document> keywordSearch(String query, int limit, String department,
        List<VersionOverride> versionOverrides) {
    // 构建版本过滤条件
    StringBuilder versionCondition = new StringBuilder();
    if (versionOverrides != null && !versionOverrides.isEmpty()) {
        // 精确版本过滤
        versionCondition.append(" AND (");
        for (int i = 0; i < versionOverrides.size(); i++) {
            VersionOverride vo = versionOverrides.get(i);
            if (i > 0) versionCondition.append(" OR ");
            versionCondition.append(String.format(
                "(metadata->>'group_id' = '%s' AND metadata->>'version' = '%s')",
                vo.groupId(), vo.version()));
        }
        versionCondition.append(")");
    } else {
        // 默认最新版本
        versionCondition.append(" AND (metadata->>'is_latest') = 'true'");
    }
    // 追加到 SQL WHERE 子句
}
```

- [ ] **Step 2: 新增 VersionOverride DTO**

创建 `backend/src/main/java/com/xiaofuzi/ai/dto/VersionOverride.java`：

```java
package com.xiaofuzi.ai.dto;

public record VersionOverride(String groupId, String version) {}
```

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/rag/KnowledgeBaseService.java
git commit -m "feat: add version filtering to hybridSearch"
```

---

### Task 7: Agent 流式响应 — version_info 事件 + 版本追溯

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/dto/ContentChatRequest.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/service/RagQaAgentService.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java`
- Modify: `backend/src/main/java/com/xiaofuzi/ai/hook/RagQaMessageHook.java`

- [ ] **Step 1: ContentChatRequest 新增 versionOverrides**

在 `ContentChatRequest.java` 中添加：

```java
private List<Map<String, Object>> versionOverrides;
```

- [ ] **Step 2: RagQaAgentService 新增 version_info 查询方法**

在 `RagQaAgentService.java` 中添加依赖：

```java
// 添加依赖
private final KnowledgeBaseService knowledgeBaseService;
private final KnowledgeDocumentMapper knowledgeDocumentMapper;
private final DocumentGroupMapper documentGroupMapper;

// 构造函数中注入
```

新增方法：

```java
/**
 * 独立执行一次轻量检索，获取检索到的文档中涉及哪些文档组，
 * 并查询这些组是否有历史版本，生成 version_info 数据。
 *
 * 因为 ReactAgent 内部执行检索的结果不对外暴露，
 * 这里用相同的 query 做一次 3 条 topK 的 hybridSearch 来近似获取引用的文档组。
 */
public List<Map<String, Object>> buildVersionInfo(String query, String department,
        List<VersionOverride> versionOverrides) {
    Map<String, Object> searchResult = knowledgeBaseService.hybridSearch(
            query, 3, 0.6, department, versionOverrides);

    @SuppressWarnings("unchecked")
    List<Document> docs = (List<Document>) searchResult.get("documents");
    if (docs == null || docs.isEmpty()) return List.of();

    Set<Long> seenGroups = new HashSet<>();
    List<Map<String, Object>> items = new ArrayList<>();

    for (Document doc : docs) {
        if (doc.getMetadata() == null) continue;
        String groupIdStr = (String) doc.getMetadata().get("group_id");
        if (groupIdStr == null || groupIdStr.isBlank()) continue;
        Long groupId = Long.parseLong(groupIdStr);
        if (!seenGroups.add(groupId)) continue;

        DocumentGroup group = documentGroupMapper.findById(groupId);
        if (group == null) continue;

        List<KnowledgeDocument> groupDocs = knowledgeDocumentMapper.findByGroupId(groupId);
        if (groupDocs.size() <= 1) continue;

        String currentVersion = (String) doc.getMetadata().get("version");
        List<String> versions = groupDocs.stream()
                .map(KnowledgeDocument::getVersion)
                .filter(Objects::nonNull)
                .distinct()
                .sorted(Comparator.reverseOrder())
                .collect(Collectors.toList());

        items.add(Map.of(
            "group_id", groupId,
            "group_name", group.getName(),
            "current_version", currentVersion != null ? currentVersion : "",
            "available_versions", versions
        ));
    }
    return items;
}
```

同时导入 Task 6 中创建的 `VersionOverride` DTO：

```java
import com.xiaofuzi.ai.dto.VersionOverride;
import com.xiaofuzi.ai.entity.DocumentGroup;
import com.xiaofuzi.ai.entity.KnowledgeDocument;
import com.xiaofuzi.ai.mapper.DocumentGroupMapper;
import com.xiaofuzi.ai.mapper.KnowledgeDocumentMapper;
import com.xiaofuzi.ai.rag.KnowledgeBaseService;
```

- [ ] **Step 3: AgentController 解析 versionOverrides 并发送 version_info 事件**

在 `ragQaChatStream` 方法中：

```java
// 从 ContentChatRequest 中解析 versionOverrides
List<VersionOverride> versionOverrides = null;
if (request.getVersionOverrides() != null && !request.getVersionOverrides().isEmpty()) {
    versionOverrides = request.getVersionOverrides().stream()
            .map(m -> new VersionOverride(
                    String.valueOf(m.get("group_id")),
                    (String) m.get("version")))
            .collect(Collectors.toList());
}
```

在 `ragQaAgentService.ask()` 返回后、发送 token 之前添加：

```java
// 查询 version info 并推送 SSE 事件
List<Map<String, Object>> versionInfo = ragQaAgentService.buildVersionInfo(
        userMessage, request.getDepartment(), versionOverrides);
if (!versionInfo.isEmpty()) {
    String versionJson = objectMapper.writeValueAsString(
            Map.of("type", "version_info", "content", Map.of("items", versionInfo)));
    emitter.send(SseEmitter.event().name("version_info").data(versionJson));
}
```

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/dto/ContentChatRequest.java \
        backend/src/main/java/com/xiaofuzi/ai/service/RagQaAgentService.java \
        backend/src/main/java/com/xiaofuzi/ai/controller/AgentController.java \
        backend/src/main/java/com/xiaofuzi/ai/hook/RagQaMessageHook.java
git commit -m "feat: add version_info SSE event for version tracing"
```

---

### Task 8: 前端类型与 API 扩展

**Files:**
- Modify: `frontend/src/types/index.ts`
- Modify: `frontend/src/api/agent.ts`
- Modify: `frontend/src/api/knowledge-base.ts`

- [ ] **Step 1: types/index.ts 扩展**

添加新类型并扩展 `MessageSource`：

```typescript
/** 版本覆盖参数 */
export interface VersionOverride {
  group_id: number
  version: string
}

/** version_info SSE 事件内容 */
export interface VersionInfoItem {
  group_id: number
  group_name: string
  current_version: string
  available_versions: string[]
}

/** 文档组 */
export interface DocumentGroup {
  id?: number
  name: string
  latestDocumentId?: number
  department?: string
  status?: string
  createTime?: string
  updateTime?: string
}

// 扩展 MessageSource
export interface MessageSource {
  document: string
  clause?: string
  page?: number
  version?: string
  group_id?: number
  has_history?: boolean
}

// 扩展 KnowledgeDocument
export interface KnowledgeDocument {
  // ...existing fields...
  groupId?: number
  isLatest?: boolean
  version?: string
  // ...
}
```

`KnowledgeDocument` 接口中新增：

```typescript
groupId?: number
isLatest?: boolean
```

`StreamEvent` type 新增 `'version_info'`：

```typescript
export interface StreamEvent {
  type: 'thinking' | 'token' | 'source' | 'done' | 'error' | 'version_info' | 'search_info'
  content: unknown
}
```

- [ ] **Step 2: agent.ts — ChatParams 扩展**

```typescript
import type { VersionOverride } from '@/types'

export interface ChatParams {
  userMessage: string
  threadId?: string
  department?: string
  versionOverrides?: VersionOverride[]
}
```

- [ ] **Step 3: knowledge-base.ts — uploadFile 扩展**

```typescript
export const uploadFile = (file: File, parserCategory?: string, category?: string,
    description?: string, department?: string, parentDocumentId?: number) => {
  const fd = new FormData()
  fd.append('file', file)
  if (parserCategory) fd.append('parserCategory', parserCategory)
  if (category) fd.append('category', category)
  if (description) fd.append('description', description)
  if (department) fd.append('department', department)
  if (parentDocumentId) fd.append('parentDocumentId', String(parentDocumentId))
  return post<{ success: boolean; message: string }>('/knowledge-base/upload', fd)
}
```

同时新增 API：

```typescript
/** 获取某文档组的版本历史 */
export const getGroupVersions = (groupId: number) =>
  get<KnowledgeDocument[]>('/knowledge-base/documents/group/' + groupId)
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/types/index.ts frontend/src/api/agent.ts frontend/src/api/knowledge-base.ts
git commit -m "feat: extend frontend types and API for version traceability"
```

---

### Task 9: 前端 Store 扩展

**Files:**
- Modify: `frontend/src/stores/chat.ts`
- Modify: `frontend/src/stores/knowledge-base.ts`

- [ ] **Step 1: chat store — version info 管理**

在 `chat.ts` 的 `addSource` 方法中扩展 source 对象，加入 `version`、`group_id`、`has_history` 字段。当前 `addSource` 只写入 `document` 和 `clause`，需要支持后端通过 `source` 事件传递版本信息。

更新 `addSource` 接受完整 `MessageSource`：

```typescript
function addSource(threadId: string, msgId: string, source: MessageSource) {
  const msgs = messages.value[threadId]
  if (!msgs) return
  const msg = msgs.find(m => m.id === msgId)
  if (msg) {
    if (!msg.sources) msg.sources = []
    msg.sources.push({
      document: source.document,
      clause: source.clause,
      version: source.version,
      group_id: source.group_id,
      has_history: source.has_history
    })
  }
}
```

新增 `versionInfoMap` 状态（类似 `searchInfoMap`）：

```typescript
/** 每个消息对应的版本信息 */
const versionInfoMap = ref<Record<string, VersionInfoItem[]>>({})
```

- [ ] **Step 2: knowledge-base store — upload 支持 parentDocumentId**

在 `knowledge-base.ts` 的 `upload` 方法中添加 `parentDocumentId` 参数：

```typescript
async function upload(file: File, parserCategory?: string, category?: string,
    description?: string, department?: string, parentDocumentId?: number) {
  loading.value = true
  try {
    const res = await uploadFile(file, parserCategory, category, description, department, parentDocumentId)
    await fetchDocuments()
    return res
  } finally {
    loading.value = false
  }
}
```

- [ ] **Step 3: Commit**

```bash
git add frontend/src/stores/chat.ts frontend/src/stores/knowledge-base.ts
git commit -m "feat: extend stores for version info and parentDocumentId support"
```

---

### Task 10: 知识库管理页 — 上传弹窗改造 + 版本历史

**Files:**
- Modify: `frontend/src/views/knowledge/KnowledgeBase.vue`

- [ ] **Step 1: 上传弹窗新增「这是已有文档的新版本」复选框**

在 `upload-dialog-body` 中，描述字段下方、上传区域上方添加：

```html
<div class="upload-form-field" style="margin-bottom: 14px">
  <el-checkbox v-model="isNewVersion" label="这是已有文档的新版本" />
</div>
<div v-if="isNewVersion" class="upload-form-field" style="margin-bottom: 14px">
  <label class="upload-form-label">选择要更新的旧文档</label>
  <el-select v-model="parentDocumentId" placeholder="选择同类别同部门的 active 文档"
    filterable style="width: 100%">
    <el-option v-for="doc in parentCandidateDocuments" :key="doc.id"
      :label="doc.documentName + ' (v' + (doc.version || '-') + ')'"
      :value="doc.id!" />
  </el-select>
</div>
```

在 `<script>` 中添加：

```typescript
const isNewVersion = ref(false)
const parentDocumentId = ref<number | undefined>(undefined)

// 候选文档列表：同类别同部门的 active 文档
const parentCandidateDocuments = computed(() =>
  store.documentList.filter(d =>
    d.status === 'active' &&
    d.id !== undefined &&
    (!uploadCategory.value || d.category === uploadCategory.value) &&
    (!uploadDepartment.value || d.department === uploadDepartment.value)
  )
)
```

`openUploadDialog` 重置新字段：

```typescript
function openUploadDialog() {
  // ... existing reset ...
  isNewVersion.value = false
  parentDocumentId.value = undefined
  // ...
}
```

`handleUpload` 传递 `parentDocumentId`：

```typescript
async function handleUpload() {
  if (!uploadFile.value) return
  await store.upload(
    uploadFile.value,
    uploadParserCategory.value || undefined,
    uploadCategory.value || undefined,
    uploadDescription.value || undefined,
    uploadDepartment.value || undefined,
    isNewVersion.value ? parentDocumentId.value : undefined
  )
  ElMessage.success('文件上传摄入成功')
  uploadDialogVisible.value = false
  handleRefresh()
}
```

- [ ] **Step 2: 文档列表新增版本列 + 点击打开版本历史抽屉**

在 `<el-table>` 中添加 version 列（在 department 列后）：

```html
<el-table-column prop="version" label="版本" width="90" sortable="custom">
  <template #default="{ row }">
    <span v-if="row.groupId" class="version-link" @click="openVersionHistory(row)">
      v{{ row.version || '-' }}
    </span>
    <span v-else class="text-muted">v{{ row.version || '-' }}</span>
  </template>
</el-table-column>
```

版本历史抽屉：

```html
<el-drawer v-model="versionHistoryVisible" :title="'版本历史 · ' + versionGroupName" size="400px" direction="rtl">
  <div class="detail-body">
    <div v-for="doc in versionHistoryDocs" :key="doc.id" class="version-item" :class="{ latest: doc.isLatest }">
      <div class="version-item-header">
        <span class="version-item-version">v{{ doc.version || '-' }}</span>
        <el-tag v-if="doc.isLatest" size="small" type="success">最新</el-tag>
        <el-tag v-else-if="doc.status === 'archived'" size="small" type="info">已归档</el-tag>
      </div>
      <div class="version-item-meta">
        <span>{{ doc.createTime || '-' }}</span>
        <span>{{ doc.chunkCount ?? 0 }} 分块</span>
      </div>
    </div>
    <el-empty v-if="versionHistoryDocs.length === 0" description="该文档无版本历史" :image-size="40" />
  </div>
</el-drawer>
```

`<script>` 添加：

```typescript
import { getGroupVersions } from '@/api/knowledge-base'

const versionHistoryVisible = ref(false)
const versionGroupName = ref('')
const versionHistoryDocs = ref<KnowledgeDocument[]>([])

async function openVersionHistory(row: KnowledgeDocument) {
  if (!row.groupId) return
  versionGroupName.value = row.documentName
  versionHistoryVisible.value = true
  try {
    const res = await getGroupVersions(row.groupId)
    versionHistoryDocs.value = res.list || res
  } catch {
    versionHistoryDocs.value = []
  }
}
```

- [ ] **Step 3: 添加样式**

```css
.version-link { color: var(--primary); cursor: pointer; font-weight: 500; }
.version-link:hover { text-decoration: underline; }

.version-item {
  padding: 12px 0;
  border-bottom: 1px solid var(--border-light);
}
.version-item.latest { background: rgba(232,112,64,0.03); margin: 0 -8px; padding-left: 8px; padding-right: 8px; }
.version-item-header { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.version-item-version { font-weight: 600; font-size: 14px; color: var(--text-primary); }
.version-item-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-muted); }
```

- [ ] **Step 4: Commit**

```bash
git add frontend/src/views/knowledge/KnowledgeBase.vue
git commit -m "feat: add new-version checkbox in upload dialog and version history drawer"
```

---

### Task 11: 聊天页 — 引用出处版本展示与切换

**Files:**
- Modify: `frontend/src/views/agent/ChatView.vue`

- [ ] **Step 1: version_info 事件处理**

在 `handleStreamEvent` 函数中添加：

```typescript
case 'version_info':
  const versionItems = (event.content as { items: VersionInfoItem[] }).items
  versionItems.forEach(item => {
    // 匹配 source 并更新
    const msgs = chatStore.messages[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === msgId)
    if (!msg?.sources) return
    msg.sources.forEach(src => {
      if (src.group_id === item.group_id) {
        src.version = item.current_version
        src.has_history = item.available_versions.length > 1
      }
    })
  })
  // 存储到 searchInfoMap 中，供 source 面板使用
  if (versionItems.length > 0) {
    searchInfoMap.value[msgId] = {
      ...searchInfoMap.value[msgId],
      version_items: versionItems
    } as unknown as SearchInfo
  }
  break
```

import `VersionInfoItem`:

```typescript
import type { VersionInfoItem } from '@/types'
```

- [ ] **Step 2: 引用出处面板 — 版本标记 + 下拉切换**

在 sources 面板中每条 source 的 `source-body` 里添加版本标记：

```html
<div class="source-body">
  <div class="source-doc-row">
    <span class="source-doc">{{ src.document }}</span>
    <span v-if="src.version" class="source-version-tag"
          :class="{ clickable: src.has_history }"
          @click="src.has_history && toggleVersionSelect(src)">
      v{{ src.version }}
      <span v-if="src.has_history" class="version-arrow">▾</span>
    </span>
  </div>
  <span v-if="src.clause" class="source-clause">{{ src.clause }}</span>

  <!-- 版本切换下拉 -->
  <div v-if="src.has_history && activeVersionSelect === src" class="version-dropdown">
    <div
      v-for="v in getAvailableVersions(src)"
      :key="v"
      class="version-option"
      :class="{ active: v === src.version }"
      @click="switchSourceVersion(src, v)"
    >v{{ v }}</div>
  </div>
</div>
```

- [ ] **Step 3: 版本切换逻辑**

```typescript
const activeVersionSelect = ref<MessageSource | null>(null)

function toggleVersionSelect(src: MessageSource) {
  activeVersionSelect.value = activeVersionSelect.value === src ? null : src
}

function getAvailableVersions(src: MessageSource): string[] {
  const info = Object.values(searchInfoMap.value).find((si: any) =>
    si.version_items?.some((vi: VersionInfoItem) => vi.group_id === src.group_id)
  ) as any
  const vi = info?.version_items?.find((v: VersionInfoItem) => v.group_id === src.group_id)
  return vi?.available_versions || []
}

async function switchSourceVersion(src: MessageSource, version: string) {
  activeVersionSelect.value = null
  if (version === src.version) return

  const threadId = chatStore.currentThreadId
  const text = chatStore.currentMessages.find(m => m.role === 'user')?.content || ''
  if (!text) return

  // 构建 versionOverrides
  const overrides = chatStore.currentMessages
    .flatMap(m => m.sources || [])
    .filter(s => s.has_history)
    .map(s => ({
      group_id: s.group_id!,
      version: s.group_id === src.group_id ? version : (s.version || '')
    }))
    .filter(o => o.version)

  // 重新发起查询
  chatStore.addMessage(threadId, 'user', `[查询 v${version} 版本] ${text}`)
  scrollToBottom()
  sending.value = true
  const assistantMsgId = chatStore.addMessage(threadId, 'assistant', '')

  try {
    const dept = localStorage.getItem('selectedDepartment') || undefined
    const response = await ragQaChatStream({
      userMessage: text,
      threadId,
      department: dept,
      versionOverrides: overrides
    })
    // ... 同 handleSend 的流式处理逻辑 ...
  } catch { /* ... */ } finally { sending.value = false; scrollToBottom() }
}
```

- [ ] **Step 4: 添加样式**

```css
.source-doc-row {
  display: flex;
  align-items: center;
  gap: 6px;
}
.source-version-tag {
  font-size: 10px;
  padding: 1px 6px;
  border-radius: 3px;
  background: #E8F5E9;
  color: #2E7D32;
  font-weight: 500;
  white-space: nowrap;
}
.source-version-tag.clickable {
  cursor: pointer;
  padding-right: 3px;
}
.source-version-tag.clickable:hover {
  background: #C8E6C9;
}
.version-arrow {
  font-size: 8px;
  margin-left: 2px;
}
.version-dropdown {
  position: absolute;
  top: 100%;
  right: 0;
  margin-top: 4px;
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  box-shadow: var(--shadow-card);
  z-index: 10;
  min-width: 80px;
}
.version-option {
  padding: 6px 12px;
  font-size: 12px;
  cursor: pointer;
}
.version-option:hover { background: var(--surface-warm); }
.version-option.active { color: var(--primary); font-weight: 600; }
```

- [ ] **Step 5: Commit**

```bash
git add frontend/src/views/agent/ChatView.vue
git commit -m "feat: add version display and switching in citation panel"
```

---

### Task 12: 后端 API — 文档组版本历史查询

**Files:**
- Modify: `backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeDocumentController.java`

- [ ] **Step 1: 新增 GET /group/{groupId} 端点**

在 `KnowledgeDocumentController.java` 中添加：

```java
@GetMapping("/group/{groupId}")
public Result<List<KnowledgeDocument>> getGroupDocuments(@PathVariable Long groupId) {
    List<KnowledgeDocument> docs = documentMapper.findByGroupId(groupId);
    return Result.success(docs);
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/src/main/java/com/xiaofuzi/ai/controller/KnowledgeDocumentController.java
git commit -m "feat: add group version history endpoint"
```

---

### 验证清单

- [ ] 首次上传文档：自动创建 `document_group`，`version` 从文档内容提取，`is_latest = true`
- [ ] 上传新版本：旧版本 `is_latest → false`、`status → archived`，新版本 `is_latest → true`，`group.latest_document_id` 更新
- [ ] 默认检索：向量 SQL 仅返回 `is_latest = true` 的 chunk
- [ ] 版本切换检索：`group_id + version` 精确过滤
- [ ] SSE 有历史版本时发送 `version_info` 事件；无历史版本不发送
- [ ] 前端引用出处面板展示版本标记，有历史版本可下拉切换
- [ ] 不传 `versionOverrides` 时行为与改动前一致
