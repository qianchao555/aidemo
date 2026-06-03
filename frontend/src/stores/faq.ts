import { defineStore } from 'pinia'
import { ref } from 'vue'
import { listFaq, createFaq, updateFaq, deleteFaq, faqCandidates } from '@/api/faq'
import type { FaqEntry, FaqCandidate } from '@/types'

export const useFaqStore = defineStore('faq', () => {
  const faqList = ref<FaqEntry[]>([])
  const loading = ref(false)

  const categories = ref<string[]>([])
  const candidates = ref<FaqCandidate[]>([])
  const candidatesLoading = ref(false)

  async function fetchList(params?: { category?: string; keyword?: string }) {
    loading.value = true
    try {
      faqList.value = await listFaq(params)
      categories.value = [...new Set(faqList.value.map(f => f.category).filter(Boolean) as string[])]
    } finally {
      loading.value = false
    }
  }

  async function create(data: FaqEntry) {
    await createFaq(data)
    await fetchList()
  }

  async function update(id: number, data: FaqEntry) {
    await updateFaq(id, data)
    await fetchList()
  }

  async function remove(id: number) {
    await deleteFaq(id)
    await fetchList()
  }

  async function fetchCandidates(limit: number = 20) {
    candidatesLoading.value = true
    try {
      candidates.value = await faqCandidates(limit)
    } finally {
      candidatesLoading.value = false
    }
  }

  return { faqList, loading, categories, candidates, candidatesLoading,
    fetchList, create, update, remove, fetchCandidates }
})
