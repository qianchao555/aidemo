import { get, post, put, del } from './request'
import type { FaqEntry, FaqCandidate } from '@/types'

export const listFaq = (params?: { category?: string; keyword?: string }) =>
  get<FaqEntry[]>('/faq/faq', params as Record<string, unknown>)

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

export const faqCandidates = (limit: number = 20) =>
  get<FaqCandidate[]>('/faq/faq/candidates', { limit })
