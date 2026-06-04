<template>
  <div class="chat-page">
    <div class="session-panel" :class="{ collapsed: sidebarCollapsed }">
      <div class="session-header">
        <span v-if="!sidebarCollapsed">会话列表</span>
        <el-button
          :icon="sidebarCollapsed ? Expand : Fold"
          size="small"
          text
          @click="sidebarCollapsed = !sidebarCollapsed"
        />
      </div>
      <template v-if="!sidebarCollapsed">
        <button class="new-chat-btn" @click="newChat">
          <el-icon :size="16"><Plus /></el-icon>
          <span>新建对话</span>
        </button>
        <div class="session-list">
          <div
            v-for="sess in chatStore.sessions"
            :key="sess.threadId"
            class="session-item"
            :class="{ active: sess.threadId === chatStore.currentThreadId }"
            @click="chatStore.switchSession(sess.threadId)"
          >
            <div class="session-item-main">
              <span class="session-title">{{ sess.title }}</span>
              <span class="session-meta">{{ sess.messageCount }} 条消息 · {{ relativeTime(sess.lastUpdateTime) }}</span>
            </div>
            <el-popconfirm
              title="确定删除此会话？"
              @confirm="chatStore.deleteSession(sess.threadId)"
              @click.stop
            >
              <template #reference>
                <el-button type="danger" link size="small" :icon="Delete" @click.stop />
              </template>
            </el-popconfirm>
          </div>
          <el-empty v-if="chatStore.sessions.length === 0" description="暂无会话" :image-size="40" />
        </div>
      </template>
    </div>

    <div class="chat-main">
      <template v-if="!chatStore.hasCurrentSession">
        <div class="welcome">
          <div class="welcome-icon">
            <el-icon :size="48" color="#E87040"><ChatDotRound /></el-icon>
          </div>
          <h2 class="welcome-title">AI 智能问答</h2>
          <p class="welcome-desc">基于 RAG 知识库的智能助手，为您提供精准回答</p>
          <div class="example-cards">
            <button
              v-for="q in exampleQuestions"
              :key="q"
              class="example-card"
              @click="quickStart(q)"
            >{{ q }}</button>
          </div>
        </div>
      </template>

      <template v-else>
        <div class="message-list" ref="msgListRef">
          <div
            v-for="msg in chatStore.currentMessages"
            :key="msg.id"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-bubble" :class="msg.role">
              <div class="message-content" v-html="renderContent(msg.content)" />
              <div v-if="msg.role === 'assistant' && msg.sources?.length" class="citation-row">
                <el-popover placement="bottom-end" :width="420" trigger="click">
                  <template #reference>
                    <el-button size="small" text type="primary" :icon="Document" class="citation-btn">
                      引用出处 ({{ msg.sources.length }})
                    </el-button>
                  </template>
                  <div class="popover-content">
                    <template v-if="searchInfoMap[msg.id]">
                      <div class="popover-section">
                        <div class="popover-section-title">检索方式</div>
                        <el-tag
                          :type="searchInfoMap[msg.id].searchMode === 'hybrid' ? 'success' : 'info'"
                          size="small"
                        >
                          {{ searchInfoMap[msg.id].searchMode === 'hybrid' ? '混合检索 (向量 + 关键词)' : '向量检索' }}
                        </el-tag>
                        <div class="popover-stats" style="margin-top: 6px">
                          <div class="stat-row">
                            <span class="stat-label">向量命中</span>
                            <span class="stat-value">{{ searchInfoMap[msg.id].vectorCount }} 条</span>
                          </div>
                          <div class="stat-row">
                            <span class="stat-label">关键词命中</span>
                            <span class="stat-value">{{ searchInfoMap[msg.id].keywordCount }} 条</span>
                          </div>
                          <div class="stat-row">
                            <span class="stat-label">RRF 融合后</span>
                            <span class="stat-value stat-emphasis">{{ searchInfoMap[msg.id].mergedCount }} 条</span>
                          </div>
                          <div v-if="searchInfoMap[msg.id].intent" class="stat-row">
                            <span class="stat-label">识别意图</span>
                            <span class="stat-value">{{ searchInfoMap[msg.id].intent }}</span>
                          </div>
                        </div>
                      </div>
                    </template>
                    <div :class="searchInfoMap[msg.id] ? 'popover-section' : ''">
                      <div class="popover-section-title">引用来源</div>
                      <div v-for="(src, si) in msg.sources" :key="si" class="citation-item">
                        <span class="citation-index">{{ si + 1 }}.</span>
                        <span class="citation-doc">{{ src.document }}</span>
                        <span v-if="src.clause" class="citation-clause">{{ src.clause }}</span>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>
            </div>
          </div>
          <div v-if="sending" class="message-row assistant">
            <div class="message-bubble assistant typing">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>

        <div class="input-area">
          <div class="input-row">
            <textarea
              v-model="inputText"
              class="chat-input"
              rows="3"
              placeholder="输入您的问题，Enter 发送，Shift+Enter 换行"
              :disabled="sending"
              @keydown.enter.exact.prevent="handleSend"
            ></textarea>
            <button
              class="send-btn"
              :disabled="!inputText.trim() || sending"
              @click="handleSend"
            >
              <el-icon v-if="!sending" :size="18"><Promotion /></el-icon>
              <el-icon v-else :size="18" class="loading-icon"><Loading /></el-icon>
            </button>
          </div>
        </div>
      </template>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, watch, nextTick, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { marked } from 'marked'
import { ElMessage } from 'element-plus'
import { Delete, Expand, Fold, Plus, Document, Promotion, Loading, ChatDotRound } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { ragQaChat, ragQaChatStream } from '@/api/agent'

const chatStore = useChatStore()
const route = useRoute()
const router = useRouter()

const sidebarCollapsed = ref(false)
const inputText = ref('')
const sending = ref(false)
const msgListRef = ref<HTMLElement>()

interface SearchInfo {
  searchMode: string
  vectorCount: number
  keywordCount: number
  mergedCount: number
  intent?: string
}
/** 每条消息对应的检索元信息，流式模式下收到 search_info 事件后写入 */
const searchInfoMap = ref<Record<string, SearchInfo>>({})

const exampleQuestions = [
  '年假怎么申请？',
  '病假需要提供什么证明材料？',
  '加班费怎么计算？',
  '离职流程需要多长时间？'
]

function relativeTime(dateStr: string): string {
  if (!dateStr) return ''
  const diff = Date.now() - new Date(dateStr).getTime()
  const mins = Math.floor(diff / 60000)
  if (mins < 1) return '刚刚'
  if (mins < 60) return `${mins}分钟前`
  const hours = Math.floor(mins / 60)
  if (hours < 24) return `${hours}小时前`
  const days = Math.floor(hours / 24)
  return `${days}天前`
}

function renderContent(text: string): string {
  // 去掉答案中的 【出处】... 标记（已在引用气泡中展示）
  const cleaned = text.replace(/【出处】.*?(\n|$)/g, '').replace(/\n{3,}/g, '\n\n')
  return marked.parse(cleaned, { async: false }) as string
}

function formatTime(ts: number): string {
  return new Date(ts).toLocaleString('zh-CN', {
    month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit'
  })
}

function scrollToBottom() {
  nextTick(() => {
    if (msgListRef.value) {
      msgListRef.value.scrollTop = msgListRef.value.scrollHeight
    }
  })
}

async function handleSend() {
  const text = inputText.value.trim()
  if (!text || sending.value) return

  const threadId = chatStore.currentThreadId
  chatStore.addMessage(threadId, 'user', text)
  inputText.value = ''
  scrollToBottom()

  const assistantMsgId = chatStore.addMessage(threadId, 'assistant', '')
  sending.value = true

  try {
    const response = await ragQaChatStream({ userMessage: text, threadId })

    if (!response.ok || !response.body) {
      throw new Error('SSE not supported')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        const trimmed = line.trim()
        if (!trimmed) continue
        if (trimmed.startsWith('data:')) {
          const jsonStr = trimmed.slice(5).trim()
          if (!jsonStr) continue
          try {
            const event = JSON.parse(jsonStr)
            handleStreamEvent(event, threadId, assistantMsgId)
          } catch { /* 忽略解析失败的行 */ }
        }
      }
    }
  } catch {
    // 流式失败降级为非流式
    chatStore.appendContent(threadId, assistantMsgId, '')
    try {
      const response = await ragQaChat({ userMessage: text, threadId })
      const msgs = chatStore.messages[threadId]
      if (msgs) {
        const msg = msgs.find(m => m.id === assistantMsgId)
        if (msg) {
          msg.content = response
          msg.sources = extractSourcesFromText(response)
        }
      }
    } catch {
      ElMessage.error('对话请求失败，请重试')
      const msgs = chatStore.messages[threadId]
      if (msgs) {
        const idx = msgs.findIndex(m => m.id === assistantMsgId)
        if (idx >= 0) msgs.splice(idx, 1)
      }
    }
  } finally {
    sending.value = false
    scrollToBottom()
  }
}

function handleStreamEvent(event: { type: string; content: unknown }, threadId: string, msgId: string) {
  switch (event.type) {
    case 'thinking':
      chatStore.appendContent(threadId, msgId, '⏳ 正在检索知识库...\n\n')
      scrollToBottom()
      break
    case 'token':
      chatStore.appendContent(threadId, msgId, event.content as string)
      scrollToBottom()
      break
    case 'source':
      chatStore.addSource(threadId, msgId, event.content as { document: string; clause?: string })
      break
    case 'done':
      chatStore.finishMessage(threadId, msgId)
      break
    case 'search_info':
      searchInfoMap.value[msgId] = event.content as SearchInfo
      break
    case 'error':
      ElMessage.error((event.content as string) || '流式输出异常')
      break
  }
}

function extractSourcesFromText(content: string): { document: string; clause?: string }[] {
  const sources: { document: string; clause?: string }[] = []
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

function newChat() {
  chatStore.createSession()
}

function quickStart(question: string) {
  if (!chatStore.hasCurrentSession) {
    chatStore.createSession()
  }
  inputText.value = question
  handleSend()
}

// 从其他页面跳转携带问题
watch(() => route.query.q, (q) => {
  if (q && typeof q === 'string') {
    quickStart(q)
    router.replace({ query: {} })
  }
}, { immediate: true })

onMounted(async () => {
  await chatStore.fetchSessions()
  if (chatStore.sessions.length > 0) {
    const lastSession = chatStore.sessions[0]
    await chatStore.switchSession(lastSession.threadId)
  }
})
</script>

<style scoped>
/* ===== Chat Page Layout ===== */
.chat-page {
  display: flex;
  height: calc(100vh - 32px);
  background: var(--white);
  border-radius: var(--radius-lg);
  overflow: hidden;
  box-shadow: var(--shadow-card);
}

/* ===== Session Panel ===== */
.session-panel {
  width: 260px;
  background: var(--surface-warm);
  border-right: 1px solid var(--border-light);
  display: flex;
  flex-direction: column;
  padding: 12px;
  transition: width 0.2s;
  flex-shrink: 0;
}

.session-panel.collapsed {
  width: 50px;
  padding: 12px 8px;
}

.session-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
  font-weight: 600;
  font-size: 14px;
  color: var(--text-primary);
}

.new-chat-btn {
  width: 100%;
  height: 38px;
  background: var(--primary);
  color: white;
  border: none;
  border-radius: var(--radius-md);
  font-size: 13px;
  font-weight: 600;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
  margin-bottom: 8px;
  transition: background 0.15s;
}

.new-chat-btn:hover {
  background: var(--primary-hover);
}

.session-list {
  flex: 1;
  overflow-y: auto;
  margin: 0 -4px;
}

.session-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border-radius: var(--radius-md);
  cursor: pointer;
  margin-bottom: 2px;
  border-left: 3px solid transparent;
  transition: all 0.15s;
}

.session-item:hover {
  background: rgba(0, 0, 0, 0.03);
}

.session-item.active {
  background: rgba(232, 112, 64, 0.06);
  border-left-color: var(--primary);
}

.session-item-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.session-title {
  font-size: 13px;
  color: var(--text-primary);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}

.session-meta {
  font-size: 11px;
  color: var(--text-muted);
}

/* ===== Chat Main ===== */
.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

/* ===== Welcome Page ===== */
.welcome {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 40px;
}

.welcome-icon {
  width: 80px;
  height: 80px;
  border-radius: 20px;
  background: rgba(232, 112, 64, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 4px;
}

.welcome-title {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0;
}

.welcome-desc {
  color: var(--text-muted);
  font-size: 14px;
  margin: 0 0 8px;
}

.example-cards {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
  max-width: 560px;
  justify-content: center;
}

.example-card {
  padding: 10px 18px;
  font-size: 13px;
  color: var(--primary);
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  cursor: pointer;
  transition: all 0.15s;
  font-family: inherit;
}

.example-card:hover {
  background: rgba(232, 112, 64, 0.05);
  border-color: var(--primary);
}

/* ===== Message List ===== */
.message-list {
  flex: 1;
  overflow-y: auto;
  padding: 20px 24px;
}

.message-row {
  display: flex;
  margin-bottom: 20px;
}

.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }

.message-bubble {
  max-width: 75%;
  padding: 14px 18px;
  font-size: 14px;
  line-height: 1.7;
}

.message-bubble.user {
  background: var(--primary);
  color: white;
  border-radius: var(--radius-lg) var(--radius-lg) 4px var(--radius-lg);
  width: fit-content;
  max-width: 42%;
  padding: 8px 12px;
}

.message-bubble.assistant {
  background: var(--white);
  color: var(--text-primary);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) 4px;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.04);
  max-width: 75%;
  padding: 14px 18px;
}

/* Citation row — inside answer bubble, right-aligned */
.citation-row {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
  padding-top: 8px;
  border-top: 1px solid var(--border-light);
}

.message-bubble.user .message-content :deep(p) { margin: 0; }
.message-bubble.user .message-content { color: white; }
.message-bubble.assistant .message-content :deep(p) { margin: 6px 0; }
.message-bubble.assistant .message-content :deep(p:first-child) { margin-top: 0; }
.message-bubble.assistant .message-content :deep(p:last-child) { margin-bottom: 0; }
.message-bubble.assistant .message-content :deep(ul),
.message-bubble.assistant .message-content :deep(ol) {
  padding-left: 20px;
  margin: 6px 0;
}
.message-bubble.assistant .message-content :deep(li) {
  margin: 2px 0;
}
.message-bubble.assistant .message-content :deep(pre) {
  background: #f4f5f7;
  padding: 12px 14px;
  border-radius: 8px;
  overflow-x: auto;
  font-size: 13px;
  margin: 10px 0;
  border: 1px solid var(--border-light);
}
.message-bubble.assistant .message-content :deep(code) {
  background: rgba(232, 112, 64, 0.08);
  color: var(--primary);
  padding: 2px 5px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 500;
}
.message-bubble.assistant .message-content :deep(pre code) {
  background: transparent;
  color: var(--text-primary);
  padding: 0;
  font-weight: 400;
}

/* Typing animation */
.typing .dot {
  display: inline-block;
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--text-muted);
  margin: 0 2px;
  animation: bounce 1.4s infinite both;
}
.typing .dot:nth-child(2) { animation-delay: 0.2s; }
.typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* Citation */
.citation-btn {
  font-size: 12px !important;
  padding: 2px 4px !important;
  color: var(--primary) !important;
}

.popover-content {
  font-size: 13px;
  line-height: 1.6;
}

.popover-section {
  margin-bottom: 6px;
}

.popover-section:not(:last-child) {
  padding-bottom: 8px;
  margin-bottom: 10px;
  border-bottom: 1px solid #ebeef5;
}

.popover-section-title {
  font-weight: 600;
  color: var(--text-primary);
  margin-bottom: 6px;
  font-size: 13px;
}

.popover-stats .stat-row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
  color: var(--text-secondary);
  font-size: 12px;
}

.popover-stats .stat-label { color: var(--text-muted); }
.popover-stats .stat-value { font-weight: 500; }
.stat-emphasis { color: var(--primary); font-weight: 600; }

.citation-item {
  padding: 5px 0;
  border-bottom: 1px solid #f2f3f5;
  display: flex;
  gap: 6px;
}
.citation-item:last-child { border-bottom: none; }
.citation-index { color: var(--primary); font-weight: 600; min-width: 20px; }
.citation-doc { color: var(--text-primary); font-weight: 500; }
.citation-clause { color: var(--text-muted); font-size: 12px; }
.citation-clause::before { content: '· '; }

/* ===== Input Area ===== */
.input-area {
  padding: 12px 20px 16px;
  border-top: 1px solid var(--border-light);
}

.input-row {
  display: flex;
  gap: 8px;
  align-items: flex-end;
}

.chat-input {
  flex: 1;
  resize: none;
  border: 1px solid var(--border-base);
  border-radius: var(--radius-md);
  padding: 10px 14px;
  font-size: 14px;
  font-family: inherit;
  line-height: 1.5;
  outline: none;
  transition: border-color 0.15s;
  background: var(--surface-warm);
}

.chat-input:focus {
  border-color: var(--primary);
}

.chat-input::placeholder {
  color: var(--text-muted);
}

.send-btn {
  width: 42px;
  height: 42px;
  border-radius: var(--radius-md);
  background: var(--primary);
  color: white;
  border: none;
  cursor: pointer;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: background 0.15s;
  flex-shrink: 0;
}

.send-btn:hover:not(:disabled) {
  background: var(--primary-hover);
}

.send-btn:disabled {
  background: #ccc;
  cursor: not-allowed;
}

.loading-icon {
  animation: spin 1s linear infinite;
}

@keyframes spin {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
</style>
