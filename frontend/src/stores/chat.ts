import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatSession } from '@/types'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<ChatSession[]>(
    JSON.parse(localStorage.getItem('chatSessions') || '[]')
  )
  const currentThreadId = ref<string>(
    localStorage.getItem('currentThreadId') || ''
  )
  const messages = ref<Record<string, ChatMessage[]>>(
    JSON.parse(localStorage.getItem('chatMessages') || '{}')
  )

  function persist() {
    localStorage.setItem('chatSessions', JSON.stringify(sessions.value))
    localStorage.setItem('currentThreadId', currentThreadId.value)
    localStorage.setItem('chatMessages', JSON.stringify(messages.value))
  }

  function createSession(): string {
    const threadId = crypto.randomUUID()
    sessions.value.unshift({ threadId, title: '新对话', createdAt: Date.now() })
    messages.value[threadId] = []
    currentThreadId.value = threadId
    persist()
    return threadId
  }

  function switchSession(threadId: string) {
    currentThreadId.value = threadId
    persist()
  }

  function deleteSession(threadId: string) {
    sessions.value = sessions.value.filter(s => s.threadId !== threadId)
    delete messages.value[threadId]
    if (currentThreadId.value === threadId) {
      currentThreadId.value = sessions.value[0]?.threadId || ''
    }
    persist()
  }

  function addMessage(threadId: string, role: 'user' | 'assistant', content: string) {
    if (!messages.value[threadId]) messages.value[threadId] = []
    messages.value[threadId].push({
      id: crypto.randomUUID(),
      role,
      content,
      timestamp: Date.now()
    })
    if (role === 'user') {
      const userMsgs = messages.value[threadId].filter(m => m.role === 'user')
      if (userMsgs.length === 1) {
        const session = sessions.value.find(s => s.threadId === threadId)
        if (session && session.title === '新对话') {
          session.title = content.length > 30 ? content.slice(0, 30) + '...' : content
        }
      }
    }
    persist()
  }

  const currentMessages = computed(() =>
    messages.value[currentThreadId.value] || []
  )

  const hasCurrentSession = computed(() => !!currentThreadId.value)

  return {
    sessions, currentThreadId, messages,
    currentMessages, hasCurrentSession,
    createSession, switchSession, deleteSession, addMessage
  }
})
