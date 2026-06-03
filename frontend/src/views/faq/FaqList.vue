<template>
  <div class="faq-list-page">
    <!-- 顶部操作栏 -->
    <div class="toolbar">
      <div class="toolbar-left">
        <el-select v-model="filterCategory" placeholder="选择分类" clearable style="width: 160px">
          <el-option
            v-for="cat in faqStore.categories"
            :key="cat" :label="cat" :value="cat"
          />
        </el-select>
        <el-input
          v-model="filterKeyword"
          placeholder="关键词搜索"
          clearable
          style="width: 200px; margin-left: 10px"
          @keyup.enter="doSearch"
        />
        <el-button type="primary" @click="doSearch" style="margin-left: 10px">搜索</el-button>
      </div>
      <el-button type="primary" @click="openDialog()">新建 FAQ</el-button>
    </div>

    <!-- FAQ 候选挖掘 -->
    <el-card shadow="never" style="margin-bottom: 16px">
      <template #header>
        <div style="display: flex; justify-content: space-between; align-items: center">
          <span>
            <strong>FAQ 候选</strong>
            <span style="color: #909399; font-size: 13px; margin-left: 8px">从聊天记录中挖掘的高频提问</span>
          </span>
          <span style="display: flex; align-items: center; gap: 8px">
            <span style="font-size: 13px; color: #606266">最低频次</span>
            <el-input-number
              v-model="minFrequency"
              :min="2"
              :max="100"
              size="small"
              style="width: 90px"
            />
            <el-button size="small" type="primary" :loading="faqStore.candidatesLoading" @click="loadCandidates">
              挖掘候选
            </el-button>
          </span>
        </div>
      </template>
      <el-table
        v-if="faqStore.candidates.length > 0"
        :data="faqStore.candidates"
        size="small"
        stripe
        style="width: 100%"
      >
        <el-table-column type="index" label="#" width="50" />
        <el-table-column prop="question" label="用户提问" min-width="300" show-overflow-tooltip />
        <el-table-column prop="frequency" label="出现次数" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="warning" size="small">{{ row.frequency }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="120" align="center">
          <template #default="{ row }">
            <el-button type="primary" link size="small" @click="createFromCandidate(row.question)">
              创建 FAQ
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <el-empty
        v-else-if="!faqStore.candidatesLoading"
        description="暂无候选，点击「挖掘候选」从聊天记录中发现高频问题"
        :image-size="60"
      />
    </el-card>

    <!-- 表格 -->
    <el-table :data="faqStore.faqList" v-loading="faqStore.loading" stripe border style="width: 100%">
      <el-table-column prop="id" label="ID" width="60" />
      <el-table-column prop="question" label="问题" min-width="180" show-overflow-tooltip />
      <el-table-column prop="answer" label="答案" min-width="220" show-overflow-tooltip />
      <el-table-column prop="category" label="分类" width="100" />
      <el-table-column prop="keywords" label="关键词" width="140" show-overflow-tooltip />
      <el-table-column prop="hitCount" label="命中" width="70" />
      <el-table-column prop="status" label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
            {{ row.status || '-' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="创建时间" width="170" />
      <el-table-column label="操作" width="180" fixed="right">
        <template #default="{ row }">
          <el-button type="primary" link size="small" @click="openDialog(row)">编辑</el-button>
          <el-popconfirm title="确定删除此 FAQ？" @confirm="handleDelete(row.id!)">
            <template #reference>
              <el-button type="danger" link size="small">删除</el-button>
            </template>
          </el-popconfirm>
        </template>
      </el-table-column>
    </el-table>

    <!-- 新增/编辑弹窗 -->
    <el-dialog
      v-model="dialogVisible"
      :title="isEdit ? '编辑 FAQ' : '新建 FAQ'"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-form ref="formRef" :model="formData" :rules="rules" label-width="80px">
        <el-form-item label="问题" prop="question">
          <el-input v-model="formData.question" />
        </el-form-item>
        <el-form-item label="答案" prop="answer">
          <el-input v-model="formData.answer" type="textarea" :rows="4" />
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
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import type { FormInstance, FormRules } from 'element-plus'
import { useFaqStore } from '@/stores/faq'
import type { FaqEntry } from '@/types'

const faqStore = useFaqStore()

const filterCategory = ref('')
const filterKeyword = ref('')
const minFrequency = ref(3)

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

function doSearch() {
  faqStore.fetchList({
    category: filterCategory.value || undefined,
    keyword: filterKeyword.value || undefined
  })
}

function openDialog(row?: FaqEntry) {
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

async function handleSubmit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    if (isEdit.value && editId.value) {
      await faqStore.update(editId.value, formData.value)
      ElMessage.success('FAQ 已更新')
    } else {
      await faqStore.create(formData.value)
      ElMessage.success('FAQ 已创建')
    }
    dialogVisible.value = false
  } finally {
    submitting.value = false
  }
}

async function handleDelete(id: number) {
  await faqStore.remove(id)
  ElMessage.success('FAQ 已删除')
}

function loadCandidates() {
  faqStore.fetchCandidates(20, minFrequency.value)
}

function createFromCandidate(question: string) {
  formData.value = { question, answer: '', keywords: '', category: '', status: 'active' }
  isEdit.value = false
  editId.value = null
  dialogVisible.value = true
}

onMounted(() => {
  faqStore.fetchList()
})
</script>

<style scoped>
.faq-list-page {
  background: var(--white);
  padding: 20px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
}
</style>
