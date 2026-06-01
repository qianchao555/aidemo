import { post } from './request'

export interface ChatParams {
  userMessage: string
  threadId?: string
}

export const ragQaChat = (data: ChatParams) =>
  post<string>('/agent/rag-qa/chat', data)
