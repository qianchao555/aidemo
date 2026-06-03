<template>
  <div class="kb-page">
    <div class="kb-page-header">
      <h2 class="kb-page-title">知识库管理</h2>
      <p class="kb-page-subtitle">管理文档、上传文件、搜索知识</p>
    </div>

    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-card-label">文档总数</div>
        <div class="stat-card-value">{{ store.docCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-label">向量分块总数</div>
        <div class="stat-card-value">{{ store.chunkCount }}</div>
      </div>
      <div class="stat-card" v-if="Object.keys(store.categoryStats).length > 0">
        <div class="stat-card-label">分类统计</div>
        <div class="stat-card-tags">
          <el-tag v-for="(cnt, cat) in store.categoryStats" :key="cat" size="small" type="primary" style="margin-right: 4px">{{ cat }}: {{ cnt }}</el-tag>
        </div>
      </div>
    </div>

    <div class="kb-card">
      <div class="kb-card-header">文件上传</div>
      <div class="kb-card-body">
        <div class="upload-options">
          <div class="upload-option">
            <label class="upload-label">文档类型</label>
            <el-select v-model="uploadCategory" placeholder="选择文档类型" style="width: 180px" size="default">
              <el-option label="制度文档" value="制度" />
              <el-option label="流程文档" value="流程" />
              <el-option label="FAQ文档" value="FAQ" />
              <el-option label="自动检测" value="" />
            </el-select>
          </div>
          <div class="upload-option">
            <label class="upload-label">解析器</label>
            <el-select v-model="uploadParserCategory" placeholder="可自动检测" clearable style="width: 180px" size="default">
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
              <el-option label="Markdown" value="markdown" />
            </el-select>
          </div>
          <div class="upload-option upload-option-desc">
            <label class="upload-label">描述</label>
            <el-input v-model="uploadDescription" placeholder="可选，简要描述文档内容" style="width: 260px" />
          </div>
        </div>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="() => uploadFile = null"
          accept=".pdf,.doc,.docx,.txt,.md"
          drag
          class="styled-upload"
        >
          <el-icon class="el-icon--upload" :size="28" color="#ccc"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处 或 <em>点击上传</em></div>
          <div class="el-upload__hint">支持 PDF、Word、TXT、Markdown</div>
        </el-upload>
        <el-button type="primary" :loading="store.loading" :disabled="!uploadFile" @click="handleUpload" style="margin-top: 12px">
          上传并摄入
        </el-button>
      </div>
    </div>

    <div class="kb-card">
      <div class="kb-card-header">
        <span>文档列表</span>
        <div class="kb-card-header-actions">
          <el-select v-model="docFilterCategory" placeholder="按分类筛选" clearable size="small" style="width: 140px" @change="handleFetchDocuments">
            <el-option label="请假" value="请假" />
            <el-option label="考勤" value="考勤" />
            <el-option label="报销" value="报销" />
            <el-option label="入职" value="入职" />
            <el-option label="离职" value="离职" />
            <el-option label="转正" value="转正" />
          </el-select>
          <el-select v-model="docFilterStatus" placeholder="按状态筛选" clearable size="small" style="width: 120px; margin-left: 8px" @change="handleFetchDocuments">
            <el-option label="活跃" value="active" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <el-button size="small" @click="handleRefreshDocuments" style="margin-left: 8px">刷新</el-button>
        </div>
      </div>
      <div class="kb-card-body" style="padding-top: 0">
        <el-table :data="store.documentList" v-loading="store.documentLoading" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="documentName" label="文档名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="documentType" label="类型" width="80" />
          <el-table-column prop="category" label="分类" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.category" size="small" type="primary">{{ row.category }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="chunkCount" label="分块数" width="80" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="摄入时间" width="170" />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
              <el-button type="primary" link size="small" @click="handleReingest(row)">重新摄入</el-button>
              <el-popconfirm title="确定删除此文档及全部向量？" @confirm="handleDeleteDoc(row.id!)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!store.documentLoading && store.documentList.length === 0" description="暂无文档" />

        <el-dialog v-model="detailVisible" title="文档详情" width="560px">
          <el-descriptions v-if="detailRow" :column="2" border>
            <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailRow.status === 'active' ? 'success' : 'info'" size="small">{{ detailRow.status || '-' }}</el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="文档名称" :span="2">{{ detailRow.documentName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detailRow.documentType }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detailRow.category || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detailRow.version || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分块数">{{ detailRow.chunkCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ detailRow.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生效日期">{{ detailRow.effectiveDate || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detailRow.description" label="描述" :span="2">{{ detailRow.description }}</el-descriptions-item>
            <el-descriptions-item label="文件路径" :span="2">{{ detailRow.filePath || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ detailRow.fileSize ? formatFileSize(detailRow.fileSize) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detailRow.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间" :span="2">{{ detailRow.updateTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-dialog>

        <input ref="reingestFileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md" style="display: none" @change="onReingestFileChange" />
      </div>
    </div>

    <div class="kb-card">
      <div class="kb-card-header">知识搜索</div>
      <div class="kb-card-body">
        <div class="search-row">
          <el-input v-model="searchQuery" placeholder="输入搜索内容" style="width: 400px" @keyup.enter="handleSearch" />
          <span class="topk-label">TopK:</span>
          <el-input-number v-model="searchTopK" :min="1" :max="20" size="small" />
          <el-button type="primary" :loading="store.loading" @click="handleSearch" style="margin-left: 10px">搜索</el-button>
        </div>
        <div v-if="store.searchResult" class="search-result">
          <p class="result-meta">命中 {{ store.hitCount }} 条结果</p>
          <div class="result-card">
            <pre class="result-text">{{ store.searchResult }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge-base'
import type { KnowledgeDocument } from '@/types'

const store = useKnowledgeStore()
const uploadRef = ref()

// 文件上传
const uploadFile = ref<File | null>(null)
const uploadParserCategory = ref('')
const uploadCategory = ref('')
const uploadDescription = ref('')
function handleFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw || null
}
function triggerUpload() {
  uploadRef.value?.$el?.querySelector('input')?.click()
}
async function handleUpload() {
  if (!uploadFile.value) return
  await store.upload(
    uploadFile.value,
    uploadParserCategory.value || undefined,
    uploadCategory.value || undefined,
    uploadDescription.value || undefined
  )
  ElMessage.success('文件上传摄入成功')
  uploadFile.value = null
  uploadCategory.value = ''
  uploadDescription.value = ''
}

// 搜索
const searchQuery = ref('')
const searchTopK = ref(5)
async function handleSearch() {
  if (!searchQuery.value.trim()) {
    ElMessage.warning('请输入搜索内容')
    return
  }
  await store.search(searchQuery.value, searchTopK.value)
}

// 文档管理
const docFilterCategory = ref('')
const docFilterStatus = ref('')
const reingestFileInput = ref<HTMLInputElement>()
const reingestingDocId = ref<number | null>(null)

async function handleFetchDocuments() {
  await store.fetchDocuments({
    category: docFilterCategory.value || undefined,
    status: docFilterStatus.value || undefined
  })
}

async function handleRefreshDocuments() {
  await Promise.all([store.fetchStats(), handleFetchDocuments()])
}

function handleReingest(row: KnowledgeDocument) {
  reingestingDocId.value = row.id!
  reingestFileInput.value?.click()
}

async function onReingestFileChange(e: Event) {
  const input = e.target as HTMLInputElement
  const file = input.files?.[0]
  if (!file || reingestingDocId.value == null) return

  try {
    await store.reingest(reingestingDocId.value, file)
    ElMessage.success(`文档 #${reingestingDocId.value} 增量更新完成`)
  } catch {
    ElMessage.error('增量更新失败')
  } finally {
    reingestingDocId.value = null
    input.value = ''
  }
}

async function handleDeleteDoc(id: number) {
  try {
    await store.removeDocument(id)
    ElMessage.success('文档已删除')
  } catch {
    ElMessage.error('删除失败')
  }
}

// 文档详情
const detailVisible = ref(false)
const detailRow = ref<KnowledgeDocument | null>(null)
function showDetail(row: KnowledgeDocument) {
  detailRow.value = row
  detailVisible.value = true
}
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

onMounted(() => {
  store.fetchStats()
  store.fetchDocuments({ status: docFilterStatus.value || undefined })
})
</script>

<style scoped>
.kb-page {
  max-width: 1100px;
  margin: 0 auto;
}

.kb-page-header {
  margin-bottom: 18px;
}

.kb-page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.kb-page-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

/* Stats Row */
.stats-row {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  box-shadow: var(--shadow-card);
}

.stat-card-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.stat-card-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-card-tags {
  margin-top: 4px;
}

/* KB Card */
.kb-card {
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  margin-bottom: 14px;
  overflow: hidden;
}

.kb-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
  background: var(--surface-warm);
}

.kb-card-header-actions {
  display: flex;
  align-items: center;
}

.kb-card-body {
  padding: 16px 18px;
}

/* Upload */
.upload-options {
  display: flex;
  gap: 20px;
  margin-bottom: 14px;
  flex-wrap: wrap;
  align-items: flex-end;
}

.upload-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.styled-upload {
  width: 100%;
}

.styled-upload :deep(.el-upload) {
  width: 100%;
}

.styled-upload :deep(.el-upload-dragger) {
  width: 100%;
  border: 2px dashed #E0DCD5;
  border-radius: var(--radius-md);
  padding: 30px;
  transition: border-color 0.15s, background 0.15s;
}

.styled-upload :deep(.el-upload-dragger:hover) {
  border-color: var(--primary);
  background: rgba(232, 112, 64, 0.02);
}

.styled-upload :deep(.el-upload__text) {
  color: var(--text-secondary);
  font-size: 14px;
  margin-top: 10px;
}

.styled-upload :deep(.el-upload__text em) {
  color: var(--primary);
  font-style: normal;
}

.styled-upload :deep(.el-upload__hint) {
  color: var(--text-muted);
  font-size: 12px;
  margin-top: 4px;
}

/* Search */
.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topk-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.search-result {
  margin-top: 14px;
}

.result-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.result-card {
  background: var(--surface-warm);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 14px;
  max-height: 360px;
  overflow: auto;
}

.result-text {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  color: var(--text-primary);
}
</style>
