<template>
  <div class="faq-list-page">
    <div class="faq-tabs">
      <span class="faq-tab active">FAQ 列表</span>
      <span class="faq-tab" @click="router.push('/faq/dashboard')">统计看板</span>
    </div>

    <div class="faq-toolbar">
      <div class="faq-toolbar-left">
        <el-input
          v-model="searchKeyword"
          placeholder="搜索问题或关键词..."
          clearable size="small" style="width: 200px"
          @change="onSearch" @clear="onSearch"
        />
        <el-select v-model="filterCategory" placeholder="全部分类" clearable size="small"
          style="width: 120px; margin-left: 8px" @change="fetchPage(1)">
          <el-option v-for="cat in store.categories" :key="cat" :label="cat" :value="cat" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="全部状态" clearable size="small"
          style="width: 110px; margin-left: 8px" @change="fetchPage(1)">
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
            <el-button class="detail-link" link size="small" @click="createFromCandidate(row.question)">创建 FAQ</el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty v-else-if="!store.candidatesLoading" description="暂无候选，点击「挖掘候选」从聊天记录中发现高频问题" :image-size="60" />
    </el-card>

    <div class="faq-table-section">
      <el-table ref="tableRef" :data="store.faqList" v-loading="store.loading" stripe style="width: 100%"
        @sort-change="onSortChange" @selection-change="onSelectionChange">
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

    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑 FAQ' : '新建 FAQ'" width="720px" :close-on-click-modal="false">
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="formData.question" @input="onQuestionInput" />
        </el-form-item>
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

    <el-dialog v-model="batchCategoryVisible" title="批量修改分类" width="400px">
      <el-input v-model="batchCategoryValue" placeholder="输入新分类" />
      <template #footer>
        <el-button @click="batchCategoryVisible = false">取消</el-button>
        <el-button type="primary" @click="doBatchUpdateCategory">确定</el-button>
      </template>
    </el-dialog>

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

    <el-dialog v-model="importVisible" title="导入 FAQ" width="500px">
      <el-upload ref="importUploadRef" :auto-upload="false" :limit="1"
        :on-change="handleImportFileChange" :on-remove="() => importFile = null"
        accept=".csv,.xlsx,.xls" drag>
        <el-icon class="el-icon--upload" :size="32" color="#C8C4C0"><UploadFilled /></el-icon>
        <div class="el-upload__text">拖拽文件到此处，或点击选择</div>
        <div class="el-upload__hint">支持 CSV / Excel 文件</div>
      </el-upload>
      <template #footer>
        <el-button @click="importVisible = false">取消</el-button>
        <el-button type="primary" :loading="importing" :disabled="!importFile" @click="handleImport">确认导入</el-button>
      </template>
    </el-dialog>

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

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import type { FormInstance, FormRules } from 'element-plus'
import { useFaqStore } from '@/stores/faq'
import { similarFaq, exportFaqUrl, getFaq } from '@/api/faq'
import type { FaqEntry, SimilarFaqItem } from '@/types'
import VMdEditor from '@kangc/v-md-editor'
import '@kangc/v-md-editor/lib/style/base-editor.css'
import '@kangc/v-md-editor/lib/theme/style/github.css'

const router = useRouter()
const store = useFaqStore()

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

const similarFaqs = ref<SimilarFaqItem[]>([])
let similarTimer: ReturnType<typeof setTimeout> | null = null
function onQuestionInput(val: string) {
  if (similarTimer) clearTimeout(similarTimer)
  if (!val || val.trim().length < 3) { similarFaqs.value = []; return }
  similarTimer = setTimeout(async () => {
    try { similarFaqs.value = await similarFaq(val) } catch { similarFaqs.value = [] }
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
  try { const entry = await getFaq(id); openDialog(entry) } catch { ElMessage.error('获取 FAQ 失败') }
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
  } finally { submitting.value = false }
}

async function handleDelete(id: number) {
  await store.remove(id)
  ElMessage.success('FAQ 已删除')
  fetchPage(currentPage.value)
}

const minFrequency = ref(3)
function loadCandidates() { store.fetchCandidates(20, minFrequency.value) }
function createFromCandidate(question: string) {
  similarFaqs.value = []
  formData.value = { question, answer: '', keywords: '', category: '', status: 'active' }
  isEdit.value = false; editId.value = null
  dialogVisible.value = true
}

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

const importVisible = ref(false)
const importFile = ref<File | null>(null)
const importing = ref(false)
const importUploadRef = ref()

function handleImportFileChange(file: { raw?: File }) { importFile.value = file.raw || null }

async function handleImport() {
  if (!importFile.value) return
  importing.value = true
  try {
    const res = await store.importFile(importFile.value)
    ElMessage.success(`导入完成，共 ${(res as any).count || 0} 条`)
    importVisible.value = false; importFile.value = null
    fetchPage(1)
  } catch { ElMessage.error('导入失败') }
  finally { importing.value = false }
}

const exportVisible = ref(false)
const exportScope = ref('all')
const exportCategory = ref('')
const exportFormat = ref('csv')

function handleExport() {
  if (selectedIds.value.length > 0) {
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

<style scoped>
.faq-list-page {
  width: 100%;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.faq-tabs {
  display: flex; gap: 0;
  padding: 0 24px;
  border-bottom: 2px solid var(--border-base);
  background: var(--surface-warm);
}

.faq-tab {
  padding: 12px 24px;
  font-size: 14px; font-weight: 600;
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
  display: flex; align-items: center; justify-content: space-between;
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
  display: flex; align-items: center; gap: 8px;
  padding: 8px 12px;
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  margin-top: 6px;
}
.similar-question { flex: 1; font-size: 13px; color: var(--text-secondary); }

.detail-link { color: #4A8B8B !important; }
.detail-link:hover { color: #3A7070 !important; }
</style>

<style>
.v-md-editor { border: 1px solid var(--border-light); border-radius: var(--radius-md); box-shadow: none; }
</style>
