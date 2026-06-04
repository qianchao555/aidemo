import { defineStore } from 'pinia'
import { ref } from 'vue'
import {
  ingestText, ingestFile, uploadFile, searchKnowledge, getStats,
  listDocuments, reingestDocument, deleteDocument,
  type DocumentListParams
} from '@/api/knowledge-base'
import type { IngestRequest, IngestFileRequest, KnowledgeDocument } from '@/types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const searchResult = ref('')
  const hitCount = ref(0)
  const docCount = ref(0)
  const chunkCount = ref(0)
  const categoryStats = ref<Record<string, number>>({})
  const loading = ref(false)

  // --- 文档管理 ---
  const documentList = ref<KnowledgeDocument[]>([])
  const documentTotal = ref(0)
  const documentLoading = ref(false)

  async function fetchDocuments(params?: DocumentListParams) {
    documentLoading.value = true
    try {
      const res = await listDocuments(params)
      documentList.value = res.list
      documentTotal.value = res.total
    } finally {
      documentLoading.value = false
    }
  }

  async function removeDocument(id: number) {
    loading.value = true
    try {
      await deleteDocument(id)
      await fetchDocuments()
    } finally {
      loading.value = false
    }
  }

  async function reingest(id: number, file: File) {
    loading.value = true
    try {
      const res = await reingestDocument(id, file)
      await fetchDocuments()
      return res
    } finally {
      loading.value = false
    }
  }

  // --- 摄入 ---
  async function ingest(data: IngestRequest) {
    loading.value = true
    try {
      return await ingestText(data)
    } finally {
      loading.value = false
    }
  }

  async function ingestByPath(data: IngestFileRequest) {
    loading.value = true
    try {
      return await ingestFile(data)
    } finally {
      loading.value = false
    }
  }

  async function upload(file: File, parserCategory?: string, category?: string, description?: string, department?: string) {
    loading.value = true
    try {
      const res = await uploadFile(file, parserCategory, category, description, department)
      await fetchDocuments()
      return res
    } finally {
      loading.value = false
    }
  }

  // --- 搜索 ---
  async function search(query: string, topK: number = 5) {
    loading.value = true
    try {
      const res = await searchKnowledge(query, topK)
      searchResult.value = res.results
      hitCount.value = res.hitCount
      return res
    } finally {
      loading.value = false
    }
  }

  async function fetchStats() {
    const res = await getStats()
    docCount.value = res.documentCount
    chunkCount.value = res.chunkCount
    categoryStats.value = res.categories
  }

  return {
    searchResult, hitCount, docCount, chunkCount, categoryStats, loading,
    documentList, documentTotal, documentLoading,
    ingest, ingestByPath, upload, search, fetchStats,
    fetchDocuments, removeDocument, reingest
  }
})
