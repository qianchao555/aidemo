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
  lastHitTime?: string
  status?: string
  createTime?: string
  updateTime?: string
}

export interface FaqListParams {
  category?: string
  status?: string
  keyword?: string
  sortBy?: string
  sortOrder?: string
  page?: number
  size?: number
}

export interface FaqStats {
  totalFaq: number
  totalHits: number
  todayHits: number
}

export interface FaqTrendItem {
  day: string
  cnt: number
}

export interface FaqCategoryDistItem {
  category: string
  total_hits: number
}

export interface SimilarFaqItem {
  id: number
  question: string
  similarity: number
}

/** 聊天消息 */
export interface ChatMessage {
  id: string
  role: 'user' | 'assistant'
  content: string
  timestamp: number
  sources?: MessageSource[]
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

/** 文档元信息 */
export interface KnowledgeDocument {
  id?: number
  documentName: string
  documentType: string
  filePath?: string
  fileSize?: number
  category?: string
  department?: string
  version?: string
  effectiveDate?: string
  description?: string
  chunkCount?: number
  status?: string
  createTime?: string
  updateTime?: string
}

/** 会话摘要 */
export interface SessionSummary {
  threadId: string
  title: string
  messageCount: number
  lastUpdateTime: string
}

/** SSE 流式事件结构 */
export interface StreamEvent {
  type: 'thinking' | 'token' | 'source' | 'done' | 'error'
  content: unknown
}

/** 后端 ChatHistory DTO */
export interface ChatHistoryDto {
  id: number
  threadId: string
  role: 'user' | 'assistant'
  content: string
  sourceDoc?: string
  headingPath?: string
  createTime: string
}

/** 知识库统计 */
export interface KnowledgeStats {
  success: boolean
  documentCount: number
  chunkCount: number
  categories: Record<string, number>
}

/** FAQ 候选 */
export interface FaqCandidate {
  question: string
  frequency: number
}

/** 用户 */
export interface ChatUser {
  id: number
  username: string
  displayName: string
  role?: string
}

/** 登录请求 */
export interface LoginRequest {
  username: string
  password: string
}

/** 登录响应 */
export interface LoginResponse {
  userId: number
  username: string
  displayName: string
  role: string
  token: string
}

/** 引用来源（ChatMessage 扩展字段） */
export interface MessageSource {
  document: string
  clause?: string
  page?: number
}
