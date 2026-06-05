import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  getQualityOverview, getQualityTrend, getLowRatedMessages,
  getBlindSpots, getDepartmentQualityStats
} from '@/api/quality'
import type {
  QualityOverview, DailyRatingTrendItem, LowRatedMessage,
  BlindSpotItem, DepartmentQualityItem
} from '@/types'

export const useQualityStore = defineStore('quality', () => {
  const overview = ref<QualityOverview | null>(null)
  const trend = ref<DailyRatingTrendItem[]>([])
  const lowRatedMessages = ref<LowRatedMessage[]>([])
  const blindSpots = ref<BlindSpotItem[]>([])
  const departmentStats = ref<DepartmentQualityItem[]>([])
  const loading = ref(false)

  async function fetchOverview() {
    overview.value = await getQualityOverview()
  }

  async function fetchTrend(days: number = 30) {
    trend.value = await getQualityTrend(days)
  }

  async function fetchLowRated(limit: number = 20) {
    lowRatedMessages.value = await getLowRatedMessages(limit)
  }

  async function fetchBlindSpots(limit: number = 20) {
    blindSpots.value = await getBlindSpots(limit)
  }

  async function fetchDepartmentStats() {
    departmentStats.value = await getDepartmentQualityStats()
  }

  async function fetchAll() {
    loading.value = true
    try {
      await Promise.all([
        fetchOverview(), fetchTrend(30), fetchLowRated(20),
        fetchBlindSpots(20), fetchDepartmentStats()
      ])
    } finally {
      loading.value = false
    }
  }

  return {
    overview, trend, lowRatedMessages, blindSpots, departmentStats, loading,
    fetchOverview, fetchTrend, fetchLowRated, fetchBlindSpots,
    fetchDepartmentStats, fetchAll
  }
})
