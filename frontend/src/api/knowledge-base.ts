import { get, post } from './request'
import type { IngestRequest, IngestFileRequest } from '@/types'

export const ingestText = (data: IngestRequest) =>
  post<{ success: boolean; message: string }>('/knowledge-base/ingest', data)

export const ingestFile = (data: IngestFileRequest) =>
  post<{ success: boolean; message: string; filePath: string }>('/knowledge-base/ingest-file', data)

export const uploadFile = (formData: FormData) =>
  post<{ success: boolean; message: string; fileName: string }>('/knowledge-base/upload', formData)

export const searchKnowledge = (query: string, topK: number = 5) =>
  get<{ success: boolean; query: string; hitCount: number; results: string }>(
    '/knowledge-base/search', { query, topK }
  )

export const getStats = () =>
  get<{ success: boolean; documentCount: number }>('/knowledge-base/stats')
