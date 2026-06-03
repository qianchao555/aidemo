<template>
  <div class="chat-page">
    <!-- 左侧会话列表 -->
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
        <el-button type="primary" style="width: 100%; margin-bottom: 10px" @click="newChat">
          <el-icon><Plus /></el-icon> 新建对话
        </el-button>
        <div class="session-list">
          <div
            v-for="sess in chatStore.sessions"
            :key="sess.threadId"
            class="session-item"
            :class="{ active: sess.threadId === chatStore.currentThreadId }"
            @click="chatStore.switchSession(sess.threadId)"
          >
            <span class="session-title">{{ sess.title }}</span>
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

    <!-- 右侧对话区 -->
    <div class="chat-main">
      <template v-if="!chatStore.hasCurrentSession">
        <div class="welcome">
          <h2>AI 智能问答</h2>
          <p>基于 RAG 知识库的智能助手，为您提供精准回答</p>
          <div class="example-cards">
            <el-card
              v-for="q in exampleQuestions"
              :key="q"
              shadow="hover"
              class="example-card"
              @click="quickStart(q)"
            >{{ q }}</el-card>
          </div>
        </div>
      </template>

      <template v-else>
        <!-- 消息列表 -->
        <div class="message-list" ref="msgListRef">
          <div
            v-for="msg in chatStore.currentMessages"
            :key="msg.id"
            class="message-row"
            :class="msg.role"
          >
            <div class="message-bubble" :class="msg.role">
              <div class="message-content" v-html="renderContent(msg.content)" />

              <!-- 引用出处弹窗（含检索方式） -->
              <div v-if="msg.role === 'assistant' && msg.sources?.length" class="citation-trigger">
                <el-popover placement="right" :width="380" trigger="click">
                  <template #reference>
                    <el-button size="small" text type="primary" :icon="Document">
                      引用出处 ({{ msg.sources.length }})
                    </el-button>
                  </template>
                  <div class="popover-content">
                    <!-- 检索方式 -->
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

                    <!-- 引用来源列表 -->
                    <div :class="searchInfoMap[msg.id] ? 'popover-section' : ''">
                      <div class="popover-section-title">引用来源</div>
                      <div
                        v-for="(src, si) in msg.sources"
                        :key="si"
                        class="citation-item"
                      >
                        <span class="citation-index">{{ si + 1 }}.</span>
                        <span class="citation-doc">{{ src.document }}</span>
                        <span v-if="src.clause" class="citation-clause">{{ src.clause }}</span>
                      </div>
                    </div>
                  </div>
                </el-popover>
              </div>

              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
            </div>
          </div>
          <div v-if="sending" class="message-row assistant">
            <div class="message-bubble assistant typing">
              <span class="dot"></span><span class="dot"></span><span class="dot"></span>
            </div>
          </div>
        </div>

        <!-- 输入区 -->
        <div class="input-area">
          <el-input
            v-model="inputText"
            type="textarea"
            :rows="3"
            placeholder="输入您的问题，Enter 发送，Shift+Enter 换行"
            :disabled="sending"
            @keydown.enter.exact="handleSend"
          />
          <el-button
            type="primary"
            :loading="sending"
            :disabled="!inputText.trim()"
            style="margin-top: 8px"
            @click="handleSend"
          >发送</el-button>
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
import { Delete, Expand, Fold, Plus, Document } from '@element-plus/icons-vue'
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
.chat-page { display: flex; height: calc(100vh - 100px); background: #fff; border-radius: 4px; overflow: hidden; }

/* 会话面板 */
.session-panel {
  width: 260px; border-right: 1px solid #e4e7ed; display: flex; flex-direction: column;
  padding: 12px; transition: width 0.2s;
}
.session-panel.collapsed { width: 50px; padding: 12px 8px; }
.session-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8px; font-weight: 600; }
.session-list { flex: 1; overflow-y: auto; }
.session-item {
  display: flex; align-items: center; justify-content: space-between;
  padding: 10px; border-radius: 6px; cursor: pointer; margin-bottom: 4px;
}
.session-item:hover { background: #f0f2f5; }
.session-item.active { background: #ecf5ff; color: #409EFF; }
.session-title { flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; font-size: 14px; }

/* 对话主区域 */
.chat-main { flex: 1; display: flex; flex-direction: column; }
.welcome {
  flex: 1; display: flex; flex-direction: column;
  align-items: center; justify-content: center; gap: 16px;
}
.welcome h2 { font-size: 24px; color: #303133; }
.welcome p { color: #909399; }
.example-cards { display: flex; gap: 12px; flex-wrap: wrap; max-width: 600px; justify-content: center; }
.example-card { cursor: pointer; padding: 10px 16px; font-size: 13px; color: #409EFF; }
.example-card:hover { background: #ecf5ff; }

/* 消息列表 */
.message-list { flex: 1; overflow-y: auto; padding: 20px; }
.message-row { display: flex; margin-bottom: 20px; }
.message-row.user { justify-content: flex-end; }
.message-row.assistant { justify-content: flex-start; }
.message-bubble { max-width: 75%; padding: 12px 16px; border-radius: 12px; font-size: 14px; line-height: 1.6; }
.message-bubble.user { background: #409EFF; color: #fff; border-bottom-right-radius: 4px; }
.message-bubble.assistant { background: #f0f2f5; color: #303133; border-bottom-left-radius: 4px; }
.message-time { font-size: 11px; margin-top: 6px; opacity: 0.7; }
.message-bubble.user .message-content :deep(p) { margin: 0; }
.message-bubble.assistant .message-content :deep(p) { margin: 4px 0; }
.message-bubble.assistant .message-content :deep(pre) {
  background: #e8eaed; padding: 10px; border-radius: 6px; overflow-x: auto;
  font-size: 12px; margin: 8px 0;
}
.message-bubble.assistant .message-content :deep(code) {
  background: #e8eaed; padding: 1px 4px; border-radius: 3px; font-size: 12px;
}

/* typing 动画 */
.typing .dot {
  display: inline-block; width: 8px; height: 8px; border-radius: 50%;
  background: #909399; margin: 0 2px; animation: bounce 1.4s infinite both;
}
.typing .dot:nth-child(2) { animation-delay: 0.2s; }
.typing .dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

/* 引用触发按钮 */
.citation-trigger {
  margin-top: 6px;
  text-align: right;
}

/* 弹出气泡内容 */
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
  color: #303133;
  margin-bottom: 6px;
  font-size: 13px;
}
.popover-stats .stat-row {
  display: flex;
  justify-content: space-between;
  padding: 2px 0;
  color: #606266;
  font-size: 12px;
}
.popover-stats .stat-label {
  color: #909399;
}
.popover-stats .stat-value {
  font-weight: 500;
}
.stat-emphasis {
  color: #409EFF;
  font-weight: 600;
}

/* 引用列表 */
.citation-item {
  padding: 5px 0;
  border-bottom: 1px solid #f2f3f5;
  display: flex;
  gap: 6px;
}
.citation-item:last-child { border-bottom: none; }
.citation-index {
  color: #409EFF;
  font-weight: 600;
  min-width: 20px;
}
.citation-doc {
  color: #303133;
  font-weight: 500;
}
.citation-clause {
  color: #909399;
  font-size: 12px;
}
.citation-clause::before { content: '· '; }

/* 输入区 */
.input-area { padding: 12px 20px 20px; border-top: 1px solid #e4e7ed; }

/* 移除旧样式 */
.source-cards { display: none; }
</style>
