import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatHistoryDto, SessionSummary, MessageSource } from '@/types'
import { createSessionApi, listSessions, getSessionHistory, deleteSessionApi } from '@/api/agent'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionSummary[]>([])
  const currentThreadId = ref<string>('')
  const messages = ref<Record<string, ChatMessage[]>>({})
  const loadingSessions = ref(false)

  async function fetchSessions() {
    loadingSessions.value = true
    try {
      sessions.value = await listSessions()
      localStorage.setItem('chatSessions', JSON.stringify(sessions.value))
    } catch {
      const cached = localStorage.getItem('chatSessions')
      if (cached) {
        sessions.value = JSON.parse(cached)
      }
    } finally {
      loadingSessions.value = false
    }
  }

  async function switchSession(threadId: string) {
    currentThreadId.value = threadId
    localStorage.setItem('currentThreadId', threadId)

    if (!messages.value[threadId]) {
      try {
        const history: ChatHistoryDto[] = await getSessionHistory(threadId)
        messages.value[threadId] = history.map(h => ({
          id: String(h.id),
          role: h.role,
          content: h.content,
          timestamp: new Date(h.createTime).getTime(),
          sources: extractSources(h.content),
          rating: h.rating
        }))
      } catch {
        messages.value[threadId] = []
      }
    }
    saveMessagesCache()
  }

  function extractSources(content: string): MessageSource[] {
    const sources: MessageSource[] = []
    const regex = /【出处】(.*?)(?:\n|$)/g
    let match
    while ((match = regex.exec(content)) !== null) {
      const parts = match[1].split('>').map(s => s.trim())
      sources.push({
        document: parts[0] || match[1],
        clause: parts[1] || undefined
      })
    }
    return sources
  }

  async function createSession(): Promise<string> {
    const threadId = crypto.randomUUID()
    const localSession: SessionSummary = {
      threadId,
      title: '新对话',
      messageCount: 0,
      lastUpdateTime: new Date().toISOString()
    }
    sessions.value.unshift(localSession)
    messages.value[threadId] = []
    currentThreadId.value = threadId
    saveSessionsCache()
    saveMessagesCache()
    localStorage.setItem('currentThreadId', currentThreadId.value)

    try {
      await createSessionApi(threadId)
    } catch {
      // 后端失败时前端仍保留本地会话，下次 fetchSessions 会同步
    }
    return threadId
  }

  async function deleteSession(threadId: string) {
    try {
      await deleteSessionApi(threadId)
    } catch {
      // 即使后端删除失败，前端也移除
    }
    sessions.value = sessions.value.filter(s => s.threadId !== threadId)
    delete messages.value[threadId]
    if (currentThreadId.value === threadId) {
      currentThreadId.value = sessions.value[0]?.threadId || ''
    }
    saveSessionsCache()
    saveMessagesCache()
  }

  function addMessage(threadId: string, role: 'user' | 'assistant', content: string): string {
    if (!messages.value[threadId]) messages.value[threadId] = []
    const msgId = crypto.randomUUID()
    const msg: ChatMessage = {
      id: msgId,
      role,
      content,
      timestamp: Date.now(),
      sources: role === 'assistant' ? extractSources(content) : undefined
    }
    messages.value[threadId].push(msg)

    if (role === 'user') {
      const userMsgs = messages.value[threadId].filter(m => m.role === 'user')
      if (userMsgs.length === 1) {
        const session = sessions.value.find(s => s.threadId === threadId)
        if (session && session.title === '新对话') {
          session.title = content.length > 30 ? content.slice(0, 30) + '...' : content
        }
      }
    }

    const session = sessions.value.find(s => s.threadId === threadId)
    if (session) {
      session.messageCount = messages.value[threadId].length
      session.lastUpdateTime = new Date().toISOString()
    }

    saveSessionsCache()
    saveMessagesCache()
    return msgId
  }

  function appendContent(threadId: string, msgId: string, content: string) {
    const msgs = messages.value[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === msgId)
    if (msg) {
      msg.content += content
    }
  }

  function finishMessage(threadId: string, msgId: string) {
    const msgs = messages.value[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === msgId)
    if (msg) {
      msg.sources = extractSources(msg.content)
    }
    saveMessagesCache()
  }

  function addSource(threadId: string, msgId: string, source: MessageSource) {
    const msgs = messages.value[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === msgId)
    if (msg) {
      if (!msg.sources) msg.sources = []
      msg.sources.push(source)
    }
  }

  function saveSessionsCache() {
    localStorage.setItem('chatSessions', JSON.stringify(sessions.value))
  }

  function saveMessagesCache() {
    localStorage.setItem('chatMessages', JSON.stringify(messages.value))
    localStorage.setItem('currentThreadId', currentThreadId.value)
  }

  const currentMessages = computed(() =>
    messages.value[currentThreadId.value] || []
  )

  const hasCurrentSession = computed(() => !!currentThreadId.value)

  return {
    sessions, currentThreadId, messages, loadingSessions,
    currentMessages, hasCurrentSession,
    fetchSessions, createSession, switchSession, deleteSession,
    addMessage, appendContent, finishMessage, addSource
  }
})
