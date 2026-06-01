import { defineStore } from 'pinia'
import { ref } from 'vue'
import { ingestText, ingestFile, uploadFile, searchKnowledge, getStats } from '@/api/knowledge-base'
import type { IngestRequest, IngestFileRequest } from '@/types'

export const useKnowledgeStore = defineStore('knowledge', () => {
  const searchResult = ref('')
  const hitCount = ref(0)
  const docCount = ref(0)
  const loading = ref(false)

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

  async function upload(file: File, parserCategory?: string) {
    loading.value = true
    try {
      const fd = new FormData()
      fd.append('file', file)
      if (parserCategory) fd.append('parserCategory', parserCategory)
      return await uploadFile(fd)
    } finally {
      loading.value = false
    }
  }

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
  }

  return { searchResult, hitCount, docCount, loading, ingest, ingestByPath, upload, search, fetchStats }
})
