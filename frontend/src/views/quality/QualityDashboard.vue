<template>
  <div class="quality-page">
    <!-- Stat Cards -->
    <div class="stats-row">
      <el-card shadow="never" class="stat-card">
        <div class="stat-value" style="color: var(--text-primary)">{{ fmtNum(store.overview?.totalAnswers) }}</div>
        <div class="stat-label">已回答总数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value" :style="{ color: rateColor(store.overview?.satisfactionRate) }">
          {{ fmtRate(store.overview?.satisfactionRate) }}
        </div>
        <div class="stat-label">满意度</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value" style="color: #22C55E">{{ fmtNum(store.overview?.thumbsUp) }}</div>
        <div class="stat-label">好评数</div>
      </el-card>
      <el-card shadow="never" class="stat-card">
        <div class="stat-value" style="color: #EF4444">{{ fmtNum(store.overview?.thumbsDown) }}</div>
        <div class="stat-label">差评数</div>
      </el-card>
    </div>

    <!-- Charts Row -->
    <div class="charts-row">
      <el-card shadow="never" class="chart-card">
        <template #header><strong>满意度趋势（近30天）</strong></template>
        <v-chart :option="trendOption" style="height: 300px" autoresize />
      </el-card>
      <el-card shadow="never" class="chart-card">
        <template #header><strong>每日好评 / 差评</strong></template>
        <v-chart :option="barOption" style="height: 300px" autoresize />
      </el-card>
    </div>

    <!-- Low-Rated Messages -->
    <el-card shadow="never" class="table-card">
      <template #header><strong>低质量回答</strong></template>
      <el-table :data="store.lowRatedMessages" size="small" stripe style="width: 100%">
        <el-table-column prop="userQuestion" label="用户提问" min-width="200" show-overflow-tooltip />
        <el-table-column label="回答摘要" min-width="240">
          <template #default="{ row }">
            <span class="answer-excerpt" @click="showAnswerDetail(row)">
              {{ row.assistantAnswerExcerpt || (row.assistantAnswerFull || '').substring(0, 60) + '...' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column prop="sourceDoc" label="来源文档" width="140" show-overflow-tooltip />
        <el-table-column label="评分" width="80">
          <template #default="{ row }">
            <el-tag :type="row.rating === -1 ? 'danger' : 'info'" size="small" effect="plain">
              {{ row.rating === -1 ? '差评' : row.rating }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="createTime" label="时间" width="160" />
      </el-table>
    </el-card>

    <!-- Bottom Row: Blind Spots + Department Stats -->
    <div class="bottom-row">
      <el-card shadow="never" class="bottom-card">
        <template #header><strong>知识盲区</strong></template>
        <el-table :data="store.blindSpots" size="small" stripe style="width: 100%" max-height="320">
          <el-table-column prop="sourceDoc" label="文档" show-overflow-tooltip />
          <el-table-column prop="headingPath" label="章节" width="140" show-overflow-tooltip />
          <el-table-column prop="negativeCount" label="差评次数" width="90" />
          <el-table-column prop="lastOccurrence" label="最近出现" width="160" />
        </el-table>
      </el-card>
      <el-card shadow="never" class="bottom-card">
        <template #header><strong>部门质量分布</strong></template>
        <v-chart :option="deptOption" style="height: 320px" autoresize />
      </el-card>
    </div>

    <!-- Answer Detail Dialog -->
    <el-dialog v-model="detailVisible" title="回答详情" width="650px" destroy-on-close>
      <div class="detail-section">
        <div class="detail-label">用户提问</div>
        <div class="detail-text">{{ detailRow?.userQuestion }}</div>
      </div>
      <div class="detail-section">
        <div class="detail-label">系统回答</div>
        <div class="detail-text answer-full">{{ detailRow?.assistantAnswerFull }}</div>
      </div>
      <div class="detail-meta" v-if="detailRow">
        <span>来源: {{ detailRow.sourceDoc || '-' }}</span>
        <span v-if="detailRow.headingPath">章节: {{ detailRow.headingPath }}</span>
        <span>时间: {{ detailRow.createTime }}</span>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
import { computed, ref, onMounted } from 'vue'
import { useQualityStore } from '@/stores/quality'
import type { LowRatedMessage } from '@/types'
import { use } from 'echarts/core'
import { LineChart, BarChart } from 'echarts/charts'
import { TitleComponent, TooltipComponent, GridComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import VChart from 'vue-echarts'

use([LineChart, BarChart, TitleComponent, TooltipComponent, GridComponent, CanvasRenderer])

const store = useQualityStore()

const detailVisible = ref(false)
const detailRow = ref<LowRatedMessage | null>(null)

function showAnswerDetail(row: LowRatedMessage) {
  detailRow.value = row
  detailVisible.value = true
}

function fmtNum(v: number | null | undefined): string {
  if (v == null) return '-'
  return v.toLocaleString()
}

function fmtRate(v: number | null | undefined): string {
  if (v == null) return '-'
  return (v * 100).toFixed(1) + '%'
}

function rateColor(v: number | null | undefined): string {
  if (v == null) return 'var(--text-muted)'
  if (v >= 0.9) return '#22C55E'
  if (v >= 0.7) return '#E87040'
  return '#EF4444'
}

const trendOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 20, top: 10, bottom: 24 },
  xAxis: {
    type: 'category',
    data: store.trend.map(t => t.day.substring(5)),
    axisLabel: { fontSize: 11 }
  },
  yAxis: {
    type: 'value',
    min: 0,
    max: 100,
    axisLabel: { formatter: '{value}%' }
  },
  series: [{
    type: 'line',
    data: store.trend.map(t => t.satisfactionRate != null ? +(t.satisfactionRate * 100).toFixed(1) : null),
    smooth: true,
    lineStyle: { color: '#E87040' },
    itemStyle: { color: '#E87040' },
    symbol: 'circle',
    symbolSize: 5,
    areaStyle: { color: 'rgba(232,112,64,0.08)' }
  }]
}))

const barOption = computed(() => ({
  tooltip: { trigger: 'axis' },
  grid: { left: 48, right: 20, top: 10, bottom: 24 },
  xAxis: {
    type: 'category',
    data: store.trend.map(t => t.day.substring(5)),
    axisLabel: { fontSize: 11 }
  },
  yAxis: { type: 'value', minInterval: 1 },
  series: [
    {
      name: '好评',
      type: 'bar',
      data: store.trend.map(t => t.thumbsUp),
      itemStyle: { color: '#22C55E', borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 18
    },
    {
      name: '差评',
      type: 'bar',
      data: store.trend.map(t => t.thumbsDown),
      itemStyle: { color: '#EF4444', borderRadius: [4, 4, 0, 0] },
      barMaxWidth: 18
    }
  ]
}))

const deptOption = computed(() => {
  const depts = [...store.departmentStats].sort((a, b) => (a.satisfactionRate ?? 0) - (b.satisfactionRate ?? 0))
  return {
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'shadow' },
      formatter: (p: { name: string; value: number }[]) => {
        const d = p[0]
        return `${d.name}<br/>满意度: ${(d.value * 100).toFixed(1)}%`
      }
    },
    grid: { left: 100, right: 30, top: 10, bottom: 20 },
    xAxis: {
      type: 'value',
      min: 0,
      max: 100,
      axisLabel: { formatter: '{value}%' }
    },
    yAxis: {
      type: 'category',
      data: depts.map(d => d.department),
      axisLabel: { fontSize: 11 },
      inverse: true
    },
    series: [{
      type: 'bar',
      data: depts.map(d => d.satisfactionRate != null ? +(d.satisfactionRate * 100).toFixed(1) : 0),
      itemStyle: {
        borderRadius: [0, 4, 4, 0],
        color: (p: { dataIndex: number }) => {
          const rate = depts[p.dataIndex]?.satisfactionRate ?? 0
          if (rate >= 0.9) return '#22C55E'
          if (rate >= 0.7) return '#E87040'
          return '#EF4444'
        }
      },
      barMaxWidth: 22,
      label: {
        show: true,
        position: 'right',
        formatter: (p: { value: number }) => p.value.toFixed(1) + '%',
        fontSize: 11,
        color: 'var(--text-secondary)'
      }
    }]
  }
})

onMounted(() => {
  store.fetchAll()
})
</script>

<style scoped>
.quality-page {
  width: 100%;
  background: var(--white);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  overflow: hidden;
}

.stats-row { display: flex; gap: 16px; padding: 20px 24px; }
.stat-card { flex: 1; text-align: center; }
.stat-value { font-size: 28px; font-weight: 700; }
.stat-label { font-size: 12px; color: var(--text-muted); margin-top: 4px; }

.charts-row { display: flex; gap: 16px; padding: 0 24px 20px; }
.chart-card { flex: 1; }

.table-card { margin: 0 24px 20px; }

.answer-excerpt {
  color: var(--primary);
  cursor: pointer;
  font-size: 13px;
}
.answer-excerpt:hover { text-decoration: underline; }

.bottom-row { display: flex; gap: 16px; padding: 0 24px 20px; }
.bottom-card { flex: 1; }

/* Answer Detail Dialog */
.detail-section { margin-bottom: 16px; }
.detail-label { font-size: 12px; color: var(--text-muted); font-weight: 600; margin-bottom: 6px; }
.detail-text { font-size: 13px; color: var(--text-primary); line-height: 1.7; }
.answer-full { max-height: 300px; overflow-y: auto; white-space: pre-wrap; background: var(--surface-warm); padding: 12px; border-radius: var(--radius-md); }
.detail-meta { display: flex; gap: 16px; font-size: 12px; color: var(--text-muted); padding-top: 8px; border-top: 1px solid var(--border-light); }
</style>
