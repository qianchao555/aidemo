import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  listFaq, createFaq, updateFaq, deleteFaq, faqCandidates,
  batchDeleteFaq, batchUpdateFaqCategory, batchUpdateFaqStatus, importFaq,
  getFaqStats, getFaqTrend, getFaqCategoryDistribution
} from '@/api/faq'
import type { FaqEntry, FaqCandidate, FaqListParams, FaqStats, FaqTrendItem, FaqCategoryDistItem } from '@/types'

export const useFaqStore = defineStore('faq', () => {
  const faqList = ref<FaqEntry[]>([])
  const faqTotal = ref(0)
  const loading = ref(false)

  const categories = ref<string[]>([])
  const candidates = ref<FaqCandidate[]>([])
  const candidatesLoading = ref(false)

  const stats = ref<FaqStats | null>(null)
  const trend = ref<FaqTrendItem[]>([])
  const categoryDist = ref<FaqCategoryDistItem[]>([])

  async function fetchList(params?: FaqListParams) {
    loading.value = true
    try {
      const res = await listFaq(params)
      faqList.value = res.list
      faqTotal.value = res.total
    } finally {
      loading.value = false
    }
  }

  async function fetchCategories() {
    const res = await listFaq({ size: 1000 })
    categories.value = [...new Set(res.list.map(f => f.category).filter(Boolean) as string[])]
  }

  async function create(data: FaqEntry) {
    await createFaq(data)
    await fetchList()
    fetchCategories()
  }

  async function update(id: number, data: FaqEntry) {
    await updateFaq(id, data)
    await fetchList()
  }

  async function remove(id: number) {
    await deleteFaq(id)
    await fetchList()
    fetchCategories()
  }

  async function fetchCandidates(limit: number = 20, minFrequency: number = 3) {
    candidatesLoading.value = true
    try {
      candidates.value = await faqCandidates(limit, minFrequency)
    } finally {
      candidatesLoading.value = false
    }
  }

  async function batchDelete(ids: number[]) {
    await batchDeleteFaq(ids)
    await fetchList()
    fetchCategories()
  }

  async function batchUpdateCategory(ids: number[], category: string) {
    await batchUpdateFaqCategory(ids, category)
    await fetchList()
    fetchCategories()
  }

  async function batchUpdateStatus(ids: number[], status: string) {
    await batchUpdateFaqStatus(ids, status)
    await fetchList()
  }

  async function importFile(file: File) {
    const res = await importFaq(file)
    await fetchList()
    fetchCategories()
    return res
  }

  async function fetchStats() {
    stats.value = await getFaqStats()
  }

  async function fetchTrend(days: number = 30) {
    trend.value = await getFaqTrend(days)
  }

  async function fetchCategoryDistribution() {
    categoryDist.value = await getFaqCategoryDistribution()
  }

  return {
    faqList, faqTotal, loading, categories, candidates, candidatesLoading,
    stats, trend, categoryDist,
    fetchList, fetchCategories, create, update, remove, fetchCandidates,
    batchDelete, batchUpdateCategory, batchUpdateStatus, importFile,
    fetchStats, fetchTrend, fetchCategoryDistribution
  }
})
