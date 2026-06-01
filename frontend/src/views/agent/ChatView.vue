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
import { Delete, Expand, Fold, Plus } from '@element-plus/icons-vue'
import { useChatStore } from '@/stores/chat'
import { ragQaChat } from '@/api/agent'

const chatStore = useChatStore()
const route = useRoute()
const router = useRouter()

const sidebarCollapsed = ref(false)
const inputText = ref('')
const sending = ref(false)
const msgListRef = ref<HTMLElement>()

const exampleQuestions = [
  '请介绍一下 Spring AI',
  '知识库中有哪些内容？',
  '如何使用 pgvector 向量存储？',
  'MCP 协议是什么？'
]

function renderContent(text: string): string {
  return marked.parse(text, { async: false }) as string
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

  sending.value = true
  try {
    const response = await ragQaChat({ userMessage: text, threadId })
    chatStore.addMessage(threadId, 'assistant', response)
    scrollToBottom()
  } catch {
    ElMessage.error('对话请求失败，请重试')
  } finally {
    sending.value = false
  }
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

onMounted(() => {
  if (chatStore.sessions.length === 0) {
    chatStore.createSession()
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

/* 输入区 */
.input-area { padding: 12px 20px 20px; border-top: 1px solid #e4e7ed; }
</style>
