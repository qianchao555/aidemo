/** FAQ 条目 */
export interface FaqEntry {
  id?: number
  question: string
  answer: string
  keywords?: string
  category?: string
  sourceDoc?: string
  headingPath?: string
  hitCount?: number
  status?: string
  createTime?: string
  updateTime?: string
}

/** 聊天消息 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
}

/** 聊天会话 */
export interface ChatSession {
  threadId: string
  title: string
  createdAt: number
}

/** 知识搜索结果 */
export interface SearchResultItem {
  query: string
  hitCount: number
  results: string
}

/** API 统一响应 */
export interface ApiResponse<T = unknown> {
  status: boolean
  code: number
  msg: string
  data: T
}

/** 知识摄入请求 */
export interface IngestRequest {
  content: string
  metadata?: Record<string, unknown>
}

/** 文件路径摄入请求 */
export interface IngestFileRequest {
  filePath: string
  parserCategory?: string
}
