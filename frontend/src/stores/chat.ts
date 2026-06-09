import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import type { ChatMessage, ChatHistoryDto, SessionSummary, MessageSource, VersionInfoItem } from '@/types'
import { createSessionApi, listSessions, getSessionHistory, deleteSessionApi } from '@/api/agent'

export const useChatStore = defineStore('chat', () => {
  const sessions = ref<SessionSummary[]>([])
  const currentThreadId = ref<string>('')
  const messages = ref<Record<string, ChatMessage[]>>({})
  const loadingSessions = ref(false)

  /** 每个消息对应的版本信息 */
  const versionInfoMap = ref<Record<string, VersionInfoItem[]>>({})

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
          sources: h.role === 'assistant' ? extractSources(h.content) : undefined,
          rating: h.rating,
          suggestions: h.role === 'assistant' ? extractSuggestions(h.content) : undefined
        }))
      } catch {
        messages.value[threadId] = []
      }
    }
    saveMessagesCache()
  }

  function extractSources(content: string): MessageSource[] {
    const sources: MessageSource[] = []
    // 匹配【出处】后的内容，支持：
    //   1. 同行：「【出处】doc > path」
    //   2. 换行：「【出处】\n- doc > path」或「【出处】\ndoc > path」
    //   3. 合并：「【出处】doc1 > path1、doc2 > path2」
    const markerRegex = /【出处】\s*/g
    let markerMatch
    while ((markerMatch = markerRegex.exec(content)) !== null) {
      const afterMarker = content.substring(markerMatch.index + markerMatch[0].length)

      // 找出该标记后直到下一个【出处】或文末的内容
      const nextMarkerIdx = afterMarker.search(/【出处】/)
      const block = nextMarkerIdx >= 0
        ? afterMarker.substring(0, nextMarkerIdx)
        : afterMarker

      // 从 block 中提取所有引用行（支持 - item 列表和纯文本行）
      const itemLines = block.split('\n')
        .map(l => l.replace(/^[-\s•\d.]*/, '').trim())
        .filter(l => l.length > 0 && l.includes('>'))

      if (itemLines.length > 0) {
        for (const line of itemLines) {
          const parts = line.split('>').map(s => s.trim())
          sources.push({
            document: parts[0] || '',
            clause: parts.slice(1).join(' > ') || undefined
          })
        }
      } else {
        // 回退：尝试同行解析（兼容无 > 分隔符的旧格式）
        const firstLine = block.split('\n')[0].trim()
        if (firstLine) {
          const parts = firstLine.split('>').map(s => s.trim())
          if (parts[0]) {
            sources.push({
              document: parts[0],
              clause: parts.slice(1).join(' > ') || undefined
            })
          }
        }
      }
    }
    return sources
  }

  /** ★ 从回答内容中解析「💡 您可以继续问：」段落的建议问题列表 */
  function extractSuggestions(content: string): string[] {
    const match = content.match(/💡\s*您可以继续问[：:]\s*\n?([\s\S]*?)$/)
    if (!match) return []
    const items: string[] = []
    for (const line of match[1].trim().split('\n')) {
      if (!line.trim()) continue
      // 同一行内按 ？- 或 ?- 拆分（覆盖 LLM 挤在一行的情况）
      for (const part of line.split(/(?<=[？?])\s*-\s*/)) {
        const cleaned = part.replace(/^[-\s•\d.、]+/, '').trim()
        if (cleaned && cleaned.length <= 50) items.push(cleaned)
      }
    }
    return items
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
      msg.suggestions = extractSuggestions(msg.content)  // ★ 解析建议问题
    }
    saveMessagesCache()
  }

  function updateMessageId(threadId: string, oldId: string, newId: string) {
    const msgs = messages.value[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === oldId)
    if (msg) {
      msg.id = newId
    }
    saveMessagesCache()
  }

  function addSource(threadId: string, msgId: string, source: MessageSource) {
    const msgs = messages.value[threadId]
    if (!msgs) return
    const msg = msgs.find(m => m.id === msgId)
    if (msg) {
      if (!msg.sources) msg.sources = []
      msg.sources.push({
        document: source.document,
        clause: source.clause,
        version: source.version,
        group_id: source.group_id,
        has_history: source.has_history
      })
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
    sessions, currentThreadId, messages, loadingSessions, versionInfoMap,
    currentMessages, hasCurrentSession,
    fetchSessions, createSession, switchSession, deleteSession,
    addMessage, appendContent, finishMessage, addSource, updateMessageId
  }
})
