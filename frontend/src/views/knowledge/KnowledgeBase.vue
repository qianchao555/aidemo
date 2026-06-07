<template>
  <div class="kb-page">
    <!-- Header -->
    <div class="kb-header">
      <div class="kb-header-left">
        <h1>知识库管理</h1>
        <p>上传文档、管理知识库内容</p>
      </div>
      <el-button type="primary" @click="openUploadDialog">+ 上传文档</el-button>
    </div>

    <!-- Toolbar -->
    <div class="kb-toolbar">
      <div class="kb-toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索文档名称..."
          clearable
          size="small"
          style="width: 200px"
          :prefix-icon="Search"
          @change="onSearch"
          @clear="onSearch"
        />
        <el-select v-model="filterCategory" placeholder="全部分类" clearable size="small" style="width: 120px; margin-left: 8px" @change="fetchPage(1)">
          <el-option label="请假" value="请假" />
          <el-option label="考勤" value="考勤" />
          <el-option label="报销" value="报销" />
          <el-option label="入职" value="入职" />
          <el-option label="离职" value="离职" />
          <el-option label="转正" value="转正" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small" style="width: 110px; margin-left: 8px" @change="fetchPage(1)">
          <el-option label="活跃" value="active" />
          <el-option label="已删除" value="deleted" />
        </el-select>
        <el-select v-model="filterDepartment" placeholder="全部部门" clearable size="small"
          style="width: 110px; margin-left: 8px" @change="fetchPage(1)">
          <el-option v-for="dept in DEPARTMENTS" :key="dept" :label="dept" :value="dept" />
        </el-select>
      </div>
      <div class="kb-toolbar-right">
        <span class="kb-summary">共 {{ store.documentTotal }} 条记录</span>
        <el-button size="small" @click="handleRefresh">刷新</el-button>
      </div>
    </div>

    <!-- Table -->
    <div class="kb-table-section">
      <el-table
        :data="store.documentList"
        v-loading="store.documentLoading"
        stripe
        style="width: 100%"
        @sort-change="onSortChange"
      >
        <el-table-column label="文档名称" show-overflow-tooltip sortable="custom" prop="documentName">
          <template #default="{ row }">
            <span class="doc-name" @click="openDetail(row)">{{ row.documentName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="documentType" label="类型" sortable="custom" />
        <el-table-column prop="category" label="分类" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.category" size="small" type="primary">{{ row.category }}</el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="department" label="部门" width="100" sortable="custom">
          <template #default="{ row }">
            <el-tag v-if="row.department" size="small" :type="departmentTagType(row.department)" effect="plain">
              {{ row.department }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>
        <el-table-column prop="version" label="版本" width="90" sortable="custom">
          <template #default="{ row }">
            <span v-if="row.groupId" class="version-link" @click="openVersionHistory(row)">
              v{{ row.version || '-' }}
            </span>
            <span v-else class="text-muted">v{{ row.version || '-' }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="chunkCount" label="分块" sortable="custom" />
        <el-table-column prop="status" label="状态" sortable="custom">
          <template #default="{ row }">
            <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
              {{ row.status || '-' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="摄入时间" sortable="custom" />
        <el-table-column label="操作" fixed="right">
          <template #default="{ row }">
            <el-button class="detail-link" link size="small" @click="openDetail(row)">详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!store.documentLoading && store.documentList.length === 0" description="暂无文档" :image-size="60" />

      <!-- Pagination -->
      <div class="kb-pagination">
        <el-pagination
          v-model:current-page="currentPage"
          :page-size="pageSize"
          :page-sizes="[5, 10, 20, 50]"
          :total="store.documentTotal"
          layout="total, sizes, prev, pager, next"
          size="small"
          @current-change="fetchPage"
          @size-change="onSizeChange"
        />
      </div>
    </div>

    <!-- Upload Dialog -->
    <el-dialog v-model="uploadDialogVisible" title="上传文档" width="520px" :close-on-click-modal="false">
      <div class="upload-dialog-body">
        <div class="upload-form-row">
          <div class="upload-form-field">
            <label class="upload-form-label">文档类型</label>
            <el-select v-model="uploadCategory" placeholder="自动检测" style="width: 100%">
              <el-option label="制度文档" value="制度" />
              <el-option label="流程文档" value="流程" />
              <el-option label="FAQ文档" value="FAQ" />
              <el-option label="自动检测" value="" />
            </el-select>
          </div>
          <div class="upload-form-field">
            <label class="upload-form-label">解析器</label>
            <el-select v-model="uploadParserCategory" placeholder="自动检测" clearable style="width: 100%">
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
              <el-option label="Markdown" value="markdown" />
            </el-select>
          </div>
        </div>
        <div class="upload-form-field" style="margin-bottom: 14px">
          <label class="upload-form-label">部门 <span style="color:#E87040">*</span></label>
          <el-select v-model="uploadDepartment" placeholder="请选择部门" style="width: 100%">
            <el-option v-for="dept in DEPARTMENTS" :key="dept" :label="dept" :value="dept" />
          </el-select>
        </div>
        <div class="upload-form-field" style="margin-bottom: 14px">
          <label class="upload-form-label">描述（可选）</label>
          <el-input v-model="uploadDescription" placeholder="简要描述文档内容" />
        </div>
        <div class="upload-form-field" style="margin-bottom: 14px">
          <el-checkbox v-model="isNewVersion" label="这是已有文档的新版本" />
        </div>
        <div v-if="isNewVersion" class="upload-form-field" style="margin-bottom: 14px">
          <label class="upload-form-label">选择要更新的旧文档</label>
          <el-select v-model="parentDocumentId" placeholder="选择要关联的旧文档"
            filterable style="width: 100%">
            <el-option v-for="doc in parentCandidateDocuments" :key="doc.id"
              :label="doc.documentName + ' (v' + (doc.version || '-') + ')'"
              :value="doc.id!" />
          </el-select>
        </div>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="() => uploadFile = null"
          accept=".pdf,.doc,.docx,.txt,.md"
          drag
          class="upload-dialog-drop"
        >
          <el-icon class="el-icon--upload" :size="32" color="#C8C4C0"><UploadFilled /></el-icon>
          <div class="el-upload__text">拖拽文件到此处，或点击选择</div>
          <div class="el-upload__hint">支持 PDF / Word / TXT / Markdown</div>
        </el-upload>
      </div>
      <template #footer>
        <el-button @click="uploadDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="store.loading" :disabled="!uploadFile" @click="handleUpload">
          上传并摄入
        </el-button>
      </template>
    </el-dialog>

    <!-- Detail Drawer -->
    <el-drawer v-model="detailVisible" title="文档详情" size="440px" direction="rtl">
      <template v-if="detailRow">
        <div class="detail-body">
          <div class="detail-hero">
            <div class="detail-name">{{ detailRow.documentName }}</div>
            <div class="detail-tags">
              <el-tag :type="detailRow.status === 'active' ? 'success' : 'info'" size="small">{{ detailRow.status || '-' }}</el-tag>
              <span class="detail-meta-text">{{ detailRow.documentType }}</span>
              <span v-if="detailRow.fileSize" class="detail-meta-text">{{ formatFileSize(detailRow.fileSize) }}</span>
              <span class="detail-meta-text">v{{ detailRow.version || '-' }}</span>
              <el-tag v-if="detailRow.category" size="small" type="primary">{{ detailRow.category }}</el-tag>
            </div>
          </div>

          <div v-if="detailRow.description" class="detail-desc">{{ detailRow.description }}</div>

          <div class="detail-section">
            <div class="detail-section-title">基本信息</div>
            <div class="detail-field">
              <span class="detail-field-label">文档 ID</span>
              <span class="detail-field-value">{{ detailRow.id }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-field-label">部门</span>
              <span class="detail-field-value">{{ detailRow.department || '-' }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-field-label">生效日期</span>
              <span class="detail-field-value">{{ detailRow.effectiveDate || '-' }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-field-label">分块数</span>
              <span class="detail-field-value">{{ detailRow.chunkCount ?? '-' }}</span>
            </div>
          </div>

          <div class="detail-section">
            <div class="detail-section-title">文件信息</div>
            <div class="detail-field">
              <span class="detail-field-label">路径</span>
              <span class="detail-field-value" style="font-size:12px;font-family:monospace">{{ detailRow.filePath || '-' }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-field-label">摄入时间</span>
              <span class="detail-field-value">{{ detailRow.createTime || '-' }}</span>
            </div>
            <div class="detail-field">
              <span class="detail-field-label">更新时间</span>
              <span class="detail-field-value">{{ detailRow.updateTime || '-' }}</span>
            </div>
          </div>
        </div>

        <div class="detail-actions">
          <el-button style="flex:1" @click="handleReingest(detailRow); detailVisible = false">重新摄入</el-button>
          <el-popconfirm title="确定删除此文档及全部向量？" @confirm="handleDelete(detailRow.id!); detailVisible = false">
            <template #reference>
              <el-button type="danger" style="flex:1">删除文档</el-button>
            </template>
          </el-popconfirm>
        </div>
      </template>
    </el-drawer>

    <!-- Version History Drawer -->
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

    <input ref="reingestFileInput" type="file" accept=".pdf,.doc,.docx,.txt,.md" style="display: none" @change="onReingestFileChange" />
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { UploadFilled, Search } from '@element-plus/icons-vue'
import { useKnowledgeStore } from '@/stores/knowledge-base'
import { DEPARTMENTS } from '@/constants/departments'
import type { KnowledgeDocument } from '@/types'
import { getGroupVersions } from '@/api/knowledge-base'

const store = useKnowledgeStore()
const uploadRef = ref()

// Upload dialog
const uploadDialogVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploadParserCategory = ref('')
const uploadCategory = ref('')
const uploadDescription = ref('')
const filterDepartment = ref('')
const uploadDepartment = ref(readUserDepartment())

const isNewVersion = ref(false)
const parentDocumentId = ref<number | undefined>(undefined)

const parentCandidateDocuments = computed(() =>
  store.documentList.filter(d =>
    d.status === 'active' &&
    d.id !== undefined &&
    (!uploadCategory.value || d.category === uploadCategory.value) &&
    (!uploadDepartment.value || d.department === uploadDepartment.value)
  )
)

const DEPT_TAG_TYPES = ['', 'success', 'warning', 'danger', 'info'] as const

function departmentTagType(dept: string): string {
  let hash = 0
  for (let i = 0; i < dept.length; i++) hash = ((hash << 5) - hash + dept.charCodeAt(i)) | 0
  return DEPT_TAG_TYPES[Math.abs(hash) % DEPT_TAG_TYPES.length]
}

function readUserDepartment(): string {
  try {
    const raw = localStorage.getItem('currentUser')
    const user = raw ? JSON.parse(raw) : null
    return user?.department || '全公司'
  } catch { return '全公司' }
}

function openUploadDialog() {
  uploadFile.value = null
  uploadParserCategory.value = ''
  uploadCategory.value = ''
  uploadDescription.value = ''
  uploadDepartment.value = readUserDepartment()
  isNewVersion.value = false
  parentDocumentId.value = undefined
  uploadDialogVisible.value = true
}

function handleFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw || null
}

async function handleUpload() {
  if (!uploadFile.value) return
  if (isNewVersion.value && !parentDocumentId.value) {
    ElMessage.warning('请选择要关联的旧文档')
    return
  }
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

// Filters & sort
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref('')
const sortBy = ref('update_time')
const sortOrder = ref<'asc' | 'desc'>('desc')
const currentPage = ref(1)
const pageSize = ref(10)

let searchTimer: ReturnType<typeof setTimeout> | null = null
function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(() => fetchPage(1), 300)
}

function onSortChange({ prop, order }: { prop: string; order: string }) {
  sortBy.value = prop || 'update_time'
  sortOrder.value = (order === 'ascending' ? 'asc' : 'desc')
  fetchPage(1)
}

function onSizeChange(size: number) {
  pageSize.value = size
  fetchPage(1)
}

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

async function handleRefresh() {
  await store.fetchStats()
  fetchPage(1)
}

// Reingest
const reingestFileInput = ref<HTMLInputElement>()
const reingestingDocId = ref<number | null>(null)
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
    ElMessage.success('文档增量更新完成')
  } catch {
    ElMessage.error('增量更新失败')
  } finally {
    reingestingDocId.value = null
    input.value = ''
  }
}

// Delete
async function handleDelete(id: number) {
  try {
    await store.removeDocument(id)
    ElMessage.success('文档已删除')
    fetchPage(currentPage.value)
  } catch {
    ElMessage.error('删除失败')
  }
}

// Detail (drawer)
const detailVisible = ref(false)
const detailRow = ref<KnowledgeDocument | null>(null)
function openDetail(row: KnowledgeDocument) {
  detailRow.value = row
  detailVisible.value = true
}
function formatFileSize(bytes: number): string {
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / (1024 * 1024)).toFixed(1) + ' MB'
}

// Version history
const versionHistoryVisible = ref(false)
const versionGroupName = ref('')
const versionHistoryDocs = ref<KnowledgeDocument[]>([])

async function openVersionHistory(row: KnowledgeDocument) {
  if (!row.groupId) return
  versionGroupName.value = row.documentName
  versionHistoryVisible.value = true
  try {
    const res = await getGroupVersions(row.groupId)
    versionHistoryDocs.value = Array.isArray(res) ? res : (res as any).list || []
  } catch {
    versionHistoryDocs.value = []
  }
}

onMounted(() => {
  store.fetchStats()
  fetchPage(1)
})
</script>

<style scoped>
.kb-page {
  width: 100%;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

/* Header */
.kb-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 18px 24px;
  border-bottom: 1px solid var(--border-light);
  background: var(--surface-warm);
}
.kb-header-left h1 { font-size: 18px; font-weight: 700; color: var(--text-primary); margin: 0; }
.kb-header-left p { font-size: 12px; color: var(--text-muted); margin: 2px 0 0; }

/* Toolbar */
.kb-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 24px;
  border-bottom: 1px solid var(--border-light);
}
.kb-toolbar-left { display: flex; align-items: center; }
.kb-toolbar-right { display: flex; align-items: center; gap: 10px; }
.kb-summary { font-size: 12px; color: var(--text-muted); }

/* Table */
.kb-table-section { padding: 0 24px 16px; }
/* sort caret via Element Plus CSS vars */
.kb-table-section :deep(.el-table) {
  --el-color-primary: #E87040;
  --el-color-primary-light-3: #F08A60;
}
.doc-name { font-size: 13px; font-weight: 500; color: var(--text-primary); cursor: pointer; }
.doc-name:hover { color: var(--primary); }
.text-muted { color: var(--text-muted); }

.detail-link { color: #4A8B8B !important; }
.detail-link:hover { color: #3A7070 !important; }

/* Pagination */
.kb-pagination { display: flex; justify-content: flex-end; padding: 12px 0 4px; }

/* Upload Dialog */
.upload-dialog-body :deep(.el-upload) { width: 100%; }
.upload-dialog-body :deep(.el-upload-dragger) {
  width: 100%;
  border: 2px dashed #D8D4CE;
  border-radius: var(--radius-md);
  padding: 28px 20px;
  transition: border-color 0.15s;
}
.upload-dialog-body :deep(.el-upload-dragger):hover { border-color: var(--primary); }
.upload-dialog-body :deep(.el-upload__text) { color: var(--text-secondary); font-size: 13px; margin-top: 8px; }
.upload-dialog-body :deep(.el-upload__hint) { color: var(--text-muted); font-size: 11px; margin-top: 2px; }

.upload-form-row { display: flex; gap: 12px; margin-bottom: 14px; }
.upload-form-field { display: flex; flex-direction: column; gap: 4px; flex: 1; }
.upload-form-label { font-size: 11px; color: var(--text-muted); font-weight: 500; letter-spacing: 0.2px; }

/* Detail Drawer */
.detail-body { padding: 0 4px; }

.detail-hero { margin-bottom: 16px; }
.detail-name { font-size: 16px; font-weight: 700; color: var(--text-primary); margin-bottom: 8px; line-height: 1.4; }
.detail-tags { display: flex; gap: 8px; flex-wrap: wrap; align-items: center; }
.detail-meta-text { font-size: 12px; color: var(--text-muted); }

.detail-desc {
  background: var(--surface-warm);
  padding: 12px 14px;
  border-radius: var(--radius-md);
  margin-bottom: 18px;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.6;
}

.detail-section { margin-bottom: 18px; }
.detail-section-title {
  font-size: 11px;
  font-weight: 600;
  color: var(--text-muted);
  text-transform: uppercase;
  letter-spacing: 0.4px;
  margin-bottom: 8px;
}
.detail-field { display: flex; padding: 8px 0; border-bottom: 1px solid var(--border-light); }
.detail-field-label { width: 80px; font-size: 12px; color: var(--text-muted); flex-shrink: 0; }
.detail-field-value { font-size: 13px; color: var(--text-primary); flex: 1; word-break: break-all; }

.detail-actions {
  display: flex;
  gap: 10px;
  padding: 14px 0 0;
  border-top: 1px solid var(--border-light);
  margin-top: 4px;
}

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
</style>
