import { get, post, del } from './request'
import type { SessionSummary, ChatHistoryDto, ChatUser, LoginRequest, LoginResponse } from '@/types'

export interface ChatParams {
  userMessage: string
  threadId?: string
  department?: string
}

function getToken(): string {
  return localStorage.getItem('authToken') || ''
}

/** 登录 */
export const authLogin = (data: LoginRequest) =>
  post<LoginResponse>('/auth/login', data)

/** 登出 */
export const authLogout = () =>
  post<void>('/auth/logout')

/** 当前用户信息 */
export const authMe = () =>
  get<LoginResponse>('/auth/me')

/** 非流式 RAG 问答（保留兼容） */
export const ragQaChat = (data: ChatParams) =>
  post<string>('/agent/rag-qa/chat', data)

/** SSE 流式问答 — 返回原生 fetch Response 供 ReadableStream 消费 */
export const ragQaChatStream = (data: ChatParams): Promise<Response> =>
  fetch('/agent/rag-qa/chat/stream', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + getToken()
    },
    body: JSON.stringify(data)
  })

/** 获取用户列表 */
export const listUsers = () =>
  get<ChatUser[]>('/user')

/** 创建会话 */
export const createSessionApi = (threadId: string, title?: string) =>
  post<SessionSummary>('/agent/sessions', { threadId, title: title || '新对话' })

/** 获取会话列表 */
export const listSessions = () =>
  get<SessionSummary[]>('/agent/sessions')

/** 获取会话历史消息 */
export const getSessionHistory = (threadId: string) =>
  get<ChatHistoryDto[]>('/agent/sessions/' + threadId + '/history')

/** 提交消息反馈 */
export const submitFeedback = (messageId: number, rating: number) =>
  post<void>(`/agent/sessions/${messageId}/feedback`, { rating })

/** 删除会话 */
export const deleteSessionApi = (threadId: string) =>
  del('/agent/sessions/' + threadId)
