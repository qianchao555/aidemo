import { get, post, del } from './request'
import type { IngestRequest, IngestFileRequest, KnowledgeDocument, KnowledgeStats } from '@/types'

export const ingestText = (data: IngestRequest) =>
  post<{ success: boolean; message: string }>('/knowledge-base/ingest', data)

export const ingestFile = (data: IngestFileRequest) =>
  post<{ success: boolean; message: string; filePath: string }>('/knowledge-base/ingest-file', data)

export const uploadFile = (file: File, parserCategory?: string, category?: string, description?: string, department?: string) => {
  const fd = new FormData()
  fd.append('file', file)
  if (parserCategory) fd.append('parserCategory', parserCategory)
  if (category) fd.append('category', category)
  if (description) fd.append('description', description)
  if (department) fd.append('department', department)
  return post<{ success: boolean; message: string }>('/knowledge-base/upload', fd)
}

export const searchKnowledge = (query: string, topK: number = 5) =>
  get<{ success: boolean; query: string; hitCount: number; results: string }>(
    '/knowledge-base/search', { query, topK }
  )

export const getStats = () =>
  get<KnowledgeStats>('/knowledge-base/stats')

export interface DocumentListParams {
  category?: string
  department?: string
  status?: string
  keyword?: string
  sortBy?: string
  sortOrder?: string
  page?: number
  size?: number
}

export interface PageResult<T> {
  list: T[]
  total: number
}

/** 获取文档列表（分页） */
export const listDocuments = (params?: DocumentListParams) =>
  get<PageResult<KnowledgeDocument>>('/knowledge-base/documents', params as Record<string, unknown>)

/** 获取文档详情 */
export const getDocument = (id: number) =>
  get<KnowledgeDocument>('/knowledge-base/documents/' + id)

/** 增量更新文档（上传新版本文件，重新摄入） */
export const reingestDocument = (id: number, file: File) => {
  const fd = new FormData()
  fd.append('file', file)
  return post<{ success: boolean; message: string; documentId: number; version: string; chunkCount: number }>(
    '/knowledge-base/documents/' + id + '/reingest', fd
  )
}

/** 删除文档（含向量） */
export const deleteDocument = (id: number) =>
  del<{ success: boolean; message: string }>('/knowledge-base/documents/' + id)
