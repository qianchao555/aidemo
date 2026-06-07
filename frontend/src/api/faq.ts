import { get, post, put, del } from './request'
import axios from 'axios'
import type { FaqEntry, FaqCandidate, FaqListParams, FaqStats, FaqTrendItem, FaqCategoryDistItem, SimilarFaqItem } from '@/types'

export interface PageResult<T> {
  list: T[]
  total: number
}

export const listFaq = (params?: FaqListParams) =>
  get<PageResult<FaqEntry>>('/faq/faq', params as Record<string, unknown>)

export const getFaq = (id: number) =>
  get<FaqEntry>(`/faq/faq/${id}`)

export const createFaq = (data: FaqEntry) =>
  post<FaqEntry>('/faq/create-faq', data)

export const updateFaq = (id: number, data: FaqEntry) =>
  put<FaqEntry>(`/faq/faq/${id}`, data)

export const deleteFaq = (id: number) =>
  del<{ success: boolean; message: string }>(`/faq/faq/${id}`)

export const highFreqFaq = (limit: number = 10) =>
  get<FaqEntry[]>('/faq/faq/high-freq', { limit })

export const faqCandidates = (limit: number = 20, minFrequency: number = 3) =>
  get<FaqCandidate[]>('/faq/faq/candidates', { limit, minFrequency })

export const similarFaq = (question: string) =>
  get<SimilarFaqItem[]>('/faq/faq/similar', { question })

export const batchDeleteFaq = (ids: number[]) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-delete', ids)

export const batchUpdateFaqCategory = (ids: number[], category: string) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-update-category', { ids, category })

export const batchUpdateFaqStatus = (ids: number[], status: string) =>
  post<{ success: boolean; message: string; count: number }>('/faq/faq/batch-update-status', { ids, status })

export const importFaq = (file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return post<{ success: boolean; message: string; count: number }>('/faq/faq/import', fd)
}

export const downloadExportFaq = async (category?: string, format: string = 'csv') => {
  const params = new URLSearchParams()
  if (category) params.set('category', category)
  params.set('format', format)
  const token = localStorage.getItem('authToken')
  const res = await axios.get(`/faq/faq/export?${params.toString()}`, {
    responseType: 'blob',
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  const url = URL.createObjectURL(res.data)
  const a = document.createElement('a')
  a.href = url
  a.download = format === 'xlsx' ? 'faq_export.xlsx' : 'faq_export.csv'
  document.body.appendChild(a)
  a.click()
  document.body.removeChild(a)
  setTimeout(() => URL.revokeObjectURL(url), 1000)
}

export const getFaqStats = () =>
  get<FaqStats>('/faq/faq/stats')

export const getFaqTrend = (days: number = 30) =>
  get<FaqTrendItem[]>('/faq/faq/stats/trend', { days })

export const getFaqCategoryDistribution = () =>
  get<FaqCategoryDistItem[]>('/faq/faq/stats/category-distribution')
