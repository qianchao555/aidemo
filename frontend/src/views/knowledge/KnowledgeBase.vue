<template>
  <div class="kb-page">
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 文本摄入 -->
<!--      <el-tab-pane label="文本摄入" name="text">-->
<!--        <el-form :model="textForm" label-width="80px">-->
<!--          <el-form-item label="文本内容">-->
<!--            <el-input v-model="textForm.content" type="textarea" :rows="5" placeholder="输入要摄入的知识文本" />-->
<!--          </el-form-item>-->
<!--          <el-form-item label="元数据">-->
<!--            <el-input v-model="textForm.metadataJson" type="textarea" :rows="2" placeholder='{"source": "manual"}' />-->
<!--          </el-form-item>-->
<!--          <el-form-item>-->
<!--            <el-button type="primary" :loading="store.loading" @click="handleIngestText">提交摄入</el-button>-->
<!--          </el-form-item>-->
<!--        </el-form>-->
<!--      </el-tab-pane>-->

      <!-- Tab 2: 文件路径摄入 -->
<!--      <el-tab-pane label="文件路径摄入" name="filepath">-->
<!--        <el-form :model="filePathForm" label-width="100px">-->
<!--          <el-form-item label="文件路径">-->
<!--            <el-input v-model="filePathForm.filePath" placeholder="/data/documents/readme.md" />-->
<!--          </el-form-item>-->
<!--          <el-form-item label="解析器类别">-->
<!--            <el-select v-model="filePathForm.parserCategory" placeholder="选择解析器" clearable style="width: 200px">-->
<!--              <el-option label="Markdown" value="markdown" />-->
<!--              <el-option label="PDF" value="pdf" />-->
<!--              <el-option label="Word" value="word" />-->
<!--              <el-option label="TXT" value="txt" />-->
<!--            </el-select>-->
<!--          </el-form-item>-->
<!--          <el-form-item>-->
<!--            <el-button type="primary" :loading="store.loading" @click="handleIngestFile">提交摄入</el-button>-->
<!--          </el-form-item>-->
<!--        </el-form>-->
<!--      </el-tab-pane>-->

      <!-- Tab 3: 文件上传摄入 -->
      <el-tab-pane label="文件上传" name="upload">
        <el-form label-width="100px">
          <el-form-item label="选择文件">
            <el-upload
              ref="uploadRef"
              :auto-upload="false"
              :limit="1"
              :on-change="handleFileChange"
              :on-remove="() => uploadFile = null"
              accept=".pdf,.doc,.docx,.txt,.md"
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处 或 <em>点击上传</em></div>
            </el-upload>
          </el-form-item>
          <el-form-item label="文档类型">
            <el-select v-model="uploadCategory" placeholder="选择文档类型" style="width: 200px">
              <el-option label="制度文档" value="制度" />
              <el-option label="流程文档" value="流程" />
              <el-option label="FAQ文档" value="FAQ" />
              <el-option label="自动检测" value="" />
            </el-select>
          </el-form-item>
          <el-form-item label="解析器">
            <el-select v-model="uploadParserCategory" placeholder="可自动检测" clearable style="width: 200px">
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
              <el-option label="Markdown" value="markdown" />
            </el-select>
          </el-form-item>
          <el-form-item label="描述">
            <el-input v-model="uploadDescription" placeholder="可选，简要描述文档内容" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="store.loading" :disabled="!uploadFile" @click="handleUpload">
              上传并摄入
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
      <!-- Tab 4: 文档管理 -->
      <el-tab-pane label="文档管理" name="documents">
        <!-- 统计卡片 -->
        <el-row :gutter="20" style="margin-bottom: 16px">
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="文档总数" :value="store.docCount" />
            </el-card>
          </el-col>
          <el-col :span="6">
            <el-card shadow="hover">
              <el-statistic title="向量分块总数" :value="store.chunkCount" />
            </el-card>
          </el-col>
        </el-row>
        <div v-if="Object.keys(store.categoryStats).length > 0" style="margin-bottom: 12px">
          <el-space wrap>
            <el-tag
              v-for="(cnt, cat) in store.categoryStats"
              :key="cat"
              type="primary"
            >{{ cat }}: {{ cnt }}</el-tag>
          </el-space>
        </div>

        <!-- 工具栏 -->
        <div class="doc-toolbar">
          <el-select v-model="docFilterCategory" placeholder="按分类筛选" clearable style="width: 160px" @change="handleFetchDocuments">
            <el-option label="请假" value="请假" />
            <el-option label="考勤" value="考勤" />
            <el-option label="报销" value="报销" />
            <el-option label="入职" value="入职" />
            <el-option label="离职" value="离职" />
            <el-option label="转正" value="转正" />
          </el-select>
          <el-select v-model="docFilterStatus" placeholder="按状态筛选" clearable style="width: 140px; margin-left: 10px" @change="handleFetchDocuments">
            <el-option label="活跃" value="active" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <el-button type="primary" @click="handleRefreshDocuments" style="margin-left: 10px">刷新</el-button>
        </div>

        <el-table :data="store.documentList" v-loading="store.documentLoading" stripe border style="width: 100%; margin-top: 12px">
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

        <!-- 文档详情弹窗 -->
        <el-dialog v-model="detailVisible" title="文档详情" width="560px">
          <el-descriptions v-if="detailRow" :column="2" border>
            <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailRow.status === 'active' ? 'success' : 'info'" size="small">
                {{ detailRow.status || '-' }}
              </el-tag>
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

        <input
          ref="reingestFileInput"
          type="file"
          accept=".pdf,.doc,.docx,.txt,.md"
          style="display: none"
          @change="onReingestFileChange"
        />

        <!-- 知识搜索 -->
        <el-divider />
        <div class="search-section">
          <h3>知识搜索</h3>
          <div class="search-row">
            <el-input v-model="searchQuery" placeholder="输入搜索内容" style="width: 400px" @keyup.enter="handleSearch" />
            <span class="topk-label">TopK:</span>
            <el-input-number v-model="searchTopK" :min="1" :max="20" size="small" />
            <el-button type="primary" :loading="store.loading" @click="handleSearch" style="margin-left: 10px">搜索</el-button>
          </div>
          <div v-if="store.searchResult" class="search-result">
            <p class="result-meta">命中 {{ store.hitCount }} 条结果</p>
            <el-card shadow="always" class="result-card">
              <pre class="result-text">{{ store.searchResult }}</pre>
            </el-card>
          </div>
        </div>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge-base'
import type { KnowledgeDocument } from '@/types'

const store = useKnowledgeStore()
const activeTab = ref('upload')

// 文本摄入
const textForm = reactive({ content: '', metadataJson: '' })
async function handleIngestText() {
  if (!textForm.content.trim()) {
    ElMessage.warning('请输入文本内容')
    return
  }
  let metadata: Record<string, unknown> | undefined
  if (textForm.metadataJson.trim()) {
    try { metadata = JSON.parse(textForm.metadataJson) }
    catch { ElMessage.warning('元数据 JSON 格式不正确'); return }
  }
  await store.ingest({ content: textForm.content, metadata })
  ElMessage.success('文本摄入成功')
  textForm.content = ''
  textForm.metadataJson = ''
}

// 文件路径摄入
const filePathForm = reactive({ filePath: '', parserCategory: '' })
async function handleIngestFile() {
  if (!filePathForm.filePath.trim()) {
    ElMessage.warning('请输入文件路径')
    return
  }
  await store.ingestByPath({
    filePath: filePathForm.filePath,
    parserCategory: filePathForm.parserCategory || undefined
  })
  ElMessage.success('文件摄入成功')
  filePathForm.filePath = ''
  filePathForm.parserCategory = ''
}

// 文件上传
const uploadFile = ref<File | null>(null)
const uploadParserCategory = ref('')
const uploadCategory = ref('')
const uploadDescription = ref('')
function handleFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw || null
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
.kb-page { background: #fff; padding: 20px; border-radius: 4px; }
.search-section h3, .stats-section h3 { margin-bottom: 12px; font-size: 16px; }
.search-row { display: flex; align-items: center; gap: 8px; }
.topk-label { font-size: 14px; color: #606266; margin-left: 8px; }
.search-result { margin-top: 16px; }
.result-meta { font-size: 13px; color: #909399; margin-bottom: 8px; }
.result-card { max-height: 400px; overflow: auto; }
.result-text { white-space: pre-wrap; font-size: 13px; line-height: 1.6; margin: 0; }
.doc-toolbar {
  display: flex;
  align-items: center;
}
</style>
