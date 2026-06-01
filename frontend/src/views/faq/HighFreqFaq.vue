<template>
  <div class="high-freq-page">
    <div class="toolbar">
      <span class="label">显示条数：</span>
      <el-slider v-model="limit" :min="5" :max="50" style="width: 200px" show-input />
      <el-button type="primary" @click="fetchData" style="margin-left: 16px">刷新</el-button>
    </div>

    <div v-loading="loading" class="card-list">
      <el-empty v-if="!loading && faqList.length === 0" description="暂无数据" />
      <el-card
        v-for="(faq, index) in faqList"
        :key="faq.id"
        class="faq-card"
        shadow="hover"
      >
        <div class="card-header">
          <el-badge :value="index + 1" class="rank-badge" type="primary" />
          <span class="question">{{ faq.question }}</span>
          <el-tag type="warning" size="small">🔥 {{ faq.hitCount || 0 }} 次</el-tag>
        </div>
        <div class="answer" v-if="expandedId === faq.id">
          {{ faq.answer }}
        </div>
        <el-button
          type="primary"
          link
          size="small"
          @click="expandedId = expandedId === faq.id ? null : faq.id!"
        >
          {{ expandedId === faq.id ? '收起' : '展开答案' }}
        </el-button>
      </el-card>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { highFreqFaq } from '@/api/faq'
import type { FaqEntry } from '@/types'

const faqList = ref<FaqEntry[]>([])
const loading = ref(false)
const limit = ref(10)
const expandedId = ref<number | null>(null)

async function fetchData() {
  loading.value = true
  try {
    faqList.value = await highFreqFaq(limit.value)
  } finally {
    loading.value = false
  }
}

onMounted(() => fetchData())
</script>

<style scoped>
.high-freq-page { background: #fff; padding: 20px; border-radius: 4px; }
.toolbar { display: flex; align-items: center; margin-bottom: 20px; }
.label { font-size: 14px; color: #606266; white-space: nowrap; }
.card-list { display: flex; flex-direction: column; gap: 12px; }
.faq-card { cursor: pointer; }
.card-header { display: flex; align-items: center; gap: 12px; }
.rank-badge { flex-shrink: 0; }
.question { flex: 1; font-size: 15px; font-weight: 500; }
.answer { margin-top: 12px; padding: 10px; background: #f5f7fa; border-radius: 4px; font-size: 14px; color: #333; line-height: 1.7; }
</style>
