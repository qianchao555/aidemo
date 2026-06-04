<template>
  <div class="dashboard-page">
    <div class="faq-tabs">
      <span class="faq-tab" @click="router.push('/faq/list')">FAQ 列表</span>
      <span class="faq-tab active">统计看板</span>
    </div>

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

.stats-row { display: flex; gap: 16px; padding: 20px 24px; }
.stat-card { flex: 1; text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; color: var(--primary); }
.stat-label { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

.charts-row { display: flex; gap: 16px; padding: 0 24px 20px; }
.chart-card { flex: 1; }

.table-card { margin: 0 24px 20px; }
</style>
