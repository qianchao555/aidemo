<template>
  <div class="kb-page">
    <el-tabs v-model="activeTab">
      <!-- Tab 1: 文本摄入 -->
      <el-tab-pane label="文本摄入" name="text">
        <el-form :model="textForm" label-width="80px">
          <el-form-item label="文本内容">
            <el-input v-model="textForm.content" type="textarea" :rows="5" placeholder="输入要摄入的知识文本" />
          </el-form-item>
          <el-form-item label="元数据">
            <el-input v-model="textForm.metadataJson" type="textarea" :rows="2" placeholder='{"source": "manual"}' />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="store.loading" @click="handleIngestText">提交摄入</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

      <!-- Tab 2: 文件路径摄入 -->
      <el-tab-pane label="文件路径摄入" name="filepath">
        <el-form :model="filePathForm" label-width="100px">
          <el-form-item label="文件路径">
            <el-input v-model="filePathForm.filePath" placeholder="/data/documents/readme.md" />
          </el-form-item>
          <el-form-item label="解析器类别">
            <el-select v-model="filePathForm.parserCategory" placeholder="选择解析器" clearable style="width: 200px">
              <el-option label="Markdown" value="markdown" />
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="store.loading" @click="handleIngestFile">提交摄入</el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>

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
          <el-form-item label="解析器类别">
            <el-select v-model="uploadParserCategory" placeholder="可自动检测" clearable style="width: 200px">
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
              <el-option label="Markdown" value="markdown" />
            </el-select>
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="store.loading" :disabled="!uploadFile" @click="handleUpload">
              上传并摄入
            </el-button>
          </el-form-item>
        </el-form>
      </el-tab-pane>
    </el-tabs>

    <el-divider />

    <!-- 搜索区域 -->
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

    <el-divider />

    <!-- 统计区域 -->
    <div class="stats-section">
      <h3>知识库统计</h3>
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card shadow="hover">
            <el-statistic title="文档总数" :value="store.docCount" />
          </el-card>
        </el-col>
      </el-row>
      <el-button style="margin-top: 12px" @click="store.fetchStats()">刷新统计</el-button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useKnowledgeStore } from '@/stores/knowledge-base'

const store = useKnowledgeStore()
const activeTab = ref('text')

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
function handleFileChange(file: { raw?: File }) {
  uploadFile.value = file.raw || null
}
async function handleUpload() {
  if (!uploadFile.value) return
  await store.upload(uploadFile.value, uploadParserCategory.value || undefined)
  ElMessage.success('文件上传摄入成功')
  uploadFile.value = null
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

onMounted(() => {
  store.fetchStats()
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
</style>
