import { get, post, del } from './request'
import type { SessionSummary, ChatHistoryDto, ChatUser } from '@/types'

export interface ChatParams {
  userMessage: string
  threadId?: string
  userId?: number
}

/** 非流式 RAG 问答（保留兼容） */
export const ragQaChat = (data: ChatParams) =>
  post<string>('/agent/rag-qa/chat', data)

/** SSE 流式问答 — 返回原生 fetch Response 供 ReadableStream 消费 */
export const ragQaChatStream = (data: ChatParams): Promise<Response> =>
  fetch('/agent/rag-qa/chat/stream', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  })

/** 获取用户列表 */
export const listUsers = () =>
  get<ChatUser[]>('/agent/users')

/** 创建会话 */
export const createSessionApi = (threadId: string, userId: number, title?: string) =>
  post<SessionSummary>('/agent/sessions', { threadId, userId, title: title || '新对话' })

/** 获取会话列表 */
export const listSessions = (userId: number) =>
  get<SessionSummary[]>('/agent/sessions', { userId })

/** 获取会话历史消息 */
export const getSessionHistory = (threadId: string) =>
  get<ChatHistoryDto[]>('/agent/sessions/' + threadId + '/history')

/** 删除会话 */
export const deleteSessionApi = (threadId: string) =>
  del('/agent/sessions/' + threadId)
