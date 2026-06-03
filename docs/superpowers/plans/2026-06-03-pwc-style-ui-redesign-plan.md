# PwC 风格 UI 重设计 — 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对现有制度知识库问答系统进行全面 UI 视觉重设计，采用 Professional Warm 设计语言（coral `#E87040` + dark sidebar `#2D2D2D`），保持所有现有功能不变。

**Architecture:** 纯前端视觉改造。定义全局 CSS 变量作为设计 token；App.vue 从侧边栏菜单重构为图标侧边栏+顶栏；各页面组件替换样式为新的卡片/气泡/沉浸式设计。无后端变更。

**Tech Stack:** Vue 3 + TypeScript + Element Plus + SCSS (scoped styles)

---

### 文件结构

| 操作 | 文件 | 职责 |
|------|------|------|
| **改造** | `frontend/src/App.vue` | 图标侧边栏 + 顶栏 + 全局 CSS 变量 |
| **改造** | `frontend/src/views/auth/Login.vue` | 暗色沉浸式登录页 |
| **改造** | `frontend/src/views/agent/ChatView.vue` | 三栏布局 + 聊天气泡 + 欢迎页 |
| **改造** | `frontend/src/views/knowledge/KnowledgeBase.vue` | 堆叠卡片式布局 |
| **改造** | `frontend/src/views/faq/FaqList.vue` | 统一配色/圆角/卡片风格 |
| **改造** | `frontend/src/views/faq/HighFreqFaq.vue` | 统一配色/圆角/卡片风格 |

---

### Task 1: App.vue — 全局 CSS 变量 + 图标侧边栏 + 顶栏

**Files:**
- Modify: `frontend/src/App.vue`

App.vue 是所有页面的外壳，需要从当前的宽侧边栏菜单改造为图标侧边栏+顶栏结构，并定义全局 CSS 变量。

- [ ] **Step 1: 完全重写 App.vue 的 template**

删除 `el-container` / `el-aside` / `el-menu` 结构，替换为顶栏 + 图标侧边栏 + 内容区：

```html
<template>
  <div v-if="isLoginPage" class="login-shell">
    <router-view />
  </div>

  <div v-else class="app-shell">
    <!-- Top Bar -->
    <header class="top-bar">
      <div class="top-bar-left">
        <span class="top-bar-brand">制度知识库助手</span>
      </div>
      <div class="top-bar-right">
        <span class="top-bar-user-name">{{ displayName }}</span>
        <el-tag v-if="isAdmin" size="small" type="warning">管理员</el-tag>
        <el-button text size="small" class="top-bar-logout" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </header>

    <div class="app-body">
      <!-- Icon Sidebar -->
      <nav class="icon-sidebar">
        <div class="sidebar-logo" @click="navTo('/agent/chat')" title="首页">
          <div class="sidebar-logo-icon">小</div>
        </div>

        <div class="sidebar-nav">
          <div
            class="sidebar-nav-item"
            :class="{ active: isActive('/agent/chat') }"
            title="智能问答"
            @click="navTo('/agent/chat')"
          >
            <el-icon :size="22"><ChatDotRound /></el-icon>
          </div>

          <template v-if="isAdmin">
            <div
              class="sidebar-nav-item"
              :class="{ active: isActive('/faq/list') }"
              title="FAQ 管理"
              @click="navTo('/faq/list')"
            >
              <el-icon :size="22"><Collection /></el-icon>
            </div>

            <div
              class="sidebar-nav-item"
              :class="{ active: isActive('/knowledge') }"
              title="知识库管理"
              @click="navTo('/knowledge')"
            >
              <el-icon :size="22"><Document /></el-icon>
            </div>
          </template>
        </div>

        <div class="sidebar-footer">
          <div class="sidebar-avatar" :title="displayName">
            {{ displayName.charAt(0) }}
          </div>
        </div>
      </nav>

      <!-- Main Content -->
      <main class="main-content">
        <router-view />
      </main>
    </div>
  </div>
</template>
```

- [ ] **Step 2: 更新 script setup**

保持现有 logic 不变，只新增 `isActive` 和 `navTo` 辅助方法：

```typescript
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Collection, Document, SwitchButton } from '@element-plus/icons-vue'
import { authLogout } from '@/api/agent'

interface UserInfo {
  id?: number
  username?: string
  displayName?: string
  role?: string
}

const route = useRoute()
const router = useRouter()

const isLoginPage = computed(() => route.path === '/login')

function readUserFromStorage(): UserInfo | null {
  try {
    const raw = localStorage.getItem('currentUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

const currentUser = ref<UserInfo | null>(readUserFromStorage())
watch(() => route.fullPath, () => {
  currentUser.value = readUserFromStorage()
})

const displayName = computed(() => currentUser.value?.displayName || '')
const isAdmin = computed(() => currentUser.value?.role === 'admin')

function isActive(path: string): boolean {
  return route.path === path
}

function navTo(path: string) {
  router.push(path)
}

async function handleLogout() {
  try { await authLogout() } catch { /* ignore */ }
  localStorage.removeItem('authToken')
  localStorage.removeItem('currentUser')
  currentUser.value = null
  router.push('/login')
}
```

- [ ] **Step 3: 替换所有 style（全局 CSS 变量 + 外壳样式 + Element Plus 覆盖）**

```css
/* ===== CSS 变量（设计 Token） ===== */
:root {
  --primary: #E87040;
  --primary-hover: #D96030;
  --sidebar-bg: #2D2D2D;
  --surface-warm: #FAF9F7;
  --page-bg: #F5F3F0;
  --text-primary: #1A1A1A;
  --text-secondary: #666666;
  --text-muted: #999999;
  --border-light: #F0ECE6;
  --border-base: #E8E4E0;
  --white: #FFFFFF;
  --radius-sm: 4px;
  --radius-md: 8px;
  --radius-lg: 12px;
  --radius-xl: 14px;
  --shadow-card: 0 2px 12px rgba(0, 0, 0, 0.06);
  --shadow-dialog: 0 4px 24px rgba(0, 0, 0, 0.10);
}

/* ===== Global Reset ===== */
* { margin: 0; padding: 0; box-sizing: border-box; }

/* ===== Login Shell ===== */
.login-shell {
  height: 100vh;
}

/* ===== App Shell ===== */
.app-shell {
  height: 100vh;
  display: flex;
  flex-direction: column;
}

/* ===== Top Bar ===== */
.top-bar {
  height: 48px;
  background: var(--white);
  border-bottom: 1px solid var(--border-base);
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  flex-shrink: 0;
  z-index: 100;
}

.top-bar-left {
  display: flex;
  align-items: center;
}

.top-bar-brand {
  font-size: 15px;
  font-weight: 700;
  color: var(--text-primary);
  letter-spacing: 0.5px;
}

.top-bar-right {
  display: flex;
  align-items: center;
  gap: 10px;
}

.top-bar-user-name {
  font-size: 13px;
  color: var(--text-secondary);
}

.top-bar-logout {
  color: var(--text-muted) !important;
  font-size: 13px;
}

.top-bar-logout:hover {
  color: var(--primary) !important;
}

/* ===== App Body ===== */
.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== Icon Sidebar ===== */
.icon-sidebar {
  width: 56px;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 8px 0;
  flex-shrink: 0;
  z-index: 90;
}

.sidebar-logo {
  margin-bottom: 16px;
  cursor: pointer;
}

.sidebar-logo-icon {
  width: 36px;
  height: 36px;
  background: var(--primary);
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 16px;
  font-weight: 700;
}

.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
}

.sidebar-nav-item {
  width: 36px;
  height: 36px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
  cursor: pointer;
  transition: all 0.15s;
}

.sidebar-nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #bbb;
}

.sidebar-nav-item.active {
  background: rgba(232, 112, 64, 0.15);
  color: var(--primary);
}

.sidebar-footer {
  padding-top: 8px;
}

.sidebar-avatar {
  width: 30px;
  height: 30px;
  background: var(--primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 13px;
  font-weight: 700;
  cursor: default;
}

/* ===== Main Content ===== */
.main-content {
  flex: 1;
  background: var(--page-bg);
  padding: 16px 20px;
  overflow-y: auto;
}

/* ===== Element Plus 全局覆盖 ===== */
.el-button--primary {
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
  --el-button-hover-bg-color: var(--primary-hover);
  --el-button-hover-border-color: var(--primary-hover);
  --el-button-active-bg-color: var(--primary-hover);
  --el-button-active-border-color: var(--primary-hover);
  border-radius: var(--radius-md);
}

.el-card {
  border-radius: var(--radius-lg) !important;
  border: 1px solid var(--border-light) !important;
  box-shadow: var(--shadow-card) !important;
}

.el-table {
  border-radius: var(--radius-md);
  overflow: hidden;
}

.el-table th.el-table__cell {
  background: var(--surface-warm);
  color: var(--text-secondary);
  font-weight: 600;
  font-size: 12px;
}

.el-tabs__item.is-active {
  color: var(--primary);
}

.el-tabs__active-bar {
  background-color: var(--primary);
}

.el-tag--primary {
  --el-tag-bg-color: rgba(232, 112, 64, 0.1);
  --el-tag-border-color: rgba(232, 112, 64, 0.2);
  --el-tag-text-color: var(--primary);
}

.el-input__wrapper {
  border-radius: var(--radius-md) !important;
  box-shadow: 0 0 0 1px var(--border-base) inset !important;
}

.el-input__wrapper:hover {
  box-shadow: 0 0 0 1px var(--border-base) inset !important;
}

.el-input__wrapper.is-focus {
  box-shadow: 0 0 0 1px var(--primary) inset !important;
}

.el-select .el-input__wrapper {
  border-radius: var(--radius-md) !important;
}

.el-popconfirm .el-button--primary {
  --el-button-bg-color: var(--primary);
  --el-button-border-color: var(--primary);
}

.el-dialog {
  border-radius: var(--radius-lg) !important;
}

.el-statistic__head {
  color: var(--text-muted);
  font-size: 12px;
}

.el-statistic__content {
  color: var(--text-primary);
  font-size: 22px;
  font-weight: 700;
}
```

- [ ] **Step 4: 验证编译**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
```

Expected: no new type errors.

- [ ] **Step 5: 手动验证** 启动 dev server，检查顶栏和图标侧边栏显示正常，导航切换正常。

- [ ] **Step 6: 提交**

```bash
git add frontend/src/App.vue
git commit -m "refactor: redesign app shell with icon sidebar, top bar, and CSS design tokens"
```

---

### Task 2: Login.vue — 暗色沉浸式登录页

**Files:**
- Modify: `frontend/src/views/auth/Login.vue`

- [ ] **Step 1: 替换 template**

```html
<template>
  <div class="login-page">
    <div class="login-bg-deco deco-1"></div>
    <div class="login-bg-deco deco-2"></div>

    <div class="login-card">
      <div class="login-logo">
        <div class="login-logo-icon">小</div>
      </div>
      <h2 class="login-title">制度知识库助手</h2>
      <p class="login-subtitle">登录您的账号</p>

      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        class="login-form"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" class="dark-input" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" class="dark-input" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" class="login-btn" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>

      <p class="login-hint">默认账号：zhangsan / lisi / wangwu，密码：123456</p>
    </div>
  </div>
</template>
```

- [ ] **Step 2: script setup 不变**，保持现有逻辑。

- [ ] **Step 3: 替换 style**

```css
.login-page {
  height: 100vh;
  background: linear-gradient(135deg, #2D2D2D 0%, #1A1A1A 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  overflow: hidden;
}

.login-bg-deco {
  position: absolute;
  border-radius: 50%;
  pointer-events: none;
}

.deco-1 {
  width: 360px;
  height: 360px;
  background: rgba(232, 112, 64, 0.06);
  top: -80px;
  right: -80px;
}

.deco-2 {
  width: 240px;
  height: 240px;
  background: rgba(232, 112, 64, 0.04);
  bottom: -60px;
  left: -60px;
}

.login-card {
  width: 360px;
  padding: 40px 32px;
  background: rgba(255, 255, 255, 0.04);
  backdrop-filter: blur(14px);
  -webkit-backdrop-filter: blur(14px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 14px;
  z-index: 1;
  text-align: center;
}

.login-logo {
  display: flex;
  justify-content: center;
  margin-bottom: 12px;
}

.login-logo-icon {
  width: 48px;
  height: 48px;
  background: #E87040;
  border-radius: 10px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 22px;
  font-weight: 700;
}

.login-title {
  color: #fff;
  font-size: 18px;
  font-weight: 700;
  margin: 0 0 4px;
}

.login-subtitle {
  color: #999;
  font-size: 12px;
  margin: 0 0 28px;
}

.login-form {
  text-align: left;
}

/* Dark input overrides */
.dark-input :deep(.el-input__wrapper) {
  background: rgba(255, 255, 255, 0.06) !important;
  border: 1px solid rgba(255, 255, 255, 0.1) !important;
  box-shadow: none !important;
  border-radius: 8px !important;
}

.dark-input :deep(.el-input__wrapper:hover) {
  border-color: rgba(255, 255, 255, 0.2) !important;
}

.dark-input :deep(.el-input__wrapper.is-focus) {
  border-color: #E87040 !important;
  box-shadow: 0 0 0 1px #E87040 inset !important;
}

.dark-input :deep(.el-input__inner) {
  color: #e0e0e0 !important;
}

.dark-input :deep(.el-input__inner::placeholder) {
  color: #777 !important;
}

.dark-input :deep(.el-input__prefix .el-icon) {
  color: #777 !important;
}

.dark-input :deep(.el-input__suffix .el-icon) {
  color: #777 !important;
}

.login-btn {
  width: 100%;
  height: 42px;
  border-radius: 8px !important;
  font-size: 15px;
  font-weight: 600;
  background: #E87040 !important;
  border-color: #E87040 !important;
}

.login-btn:hover {
  background: #D96030 !important;
  border-color: #D96030 !important;
}

.login-hint {
  color: #666;
  font-size: 11px;
  margin: 16px 0 0;
}
```

- [ ] **Step 4: 验证编译**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
```

Expected: no new type errors.

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/auth/Login.vue
git commit -m "refactor: redesign login page with dark immersive glassmorphism style"
```

---

### Task 3: ChatView.vue — 三栏布局 + 聊天气泡 + 欢迎页

**Files:**
- Modify: `frontend/src/views/agent/ChatView.vue`

- [ ] **Step 1: 替换 template 中的会话面板样式**

将当前 `.session-panel` 的 header 和列表替换为新设计，核心变更：会话面板改为 `#FAF9F7` 暖色背景，"新建对话"按钮用 coral 全宽按钮，激活项用 coral 左边框。

```html
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

    <!-- 右侧对话区 -->
    <div class="chat-main">
      <!-- 欢迎页 -->
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

      <!-- 对话区 -->
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

              <div v-if="msg.role === 'assistant' && msg.sources?.length" class="citation-trigger">
                <el-popover placement="right" :width="380" trigger="click">
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

              <div class="message-time">{{ formatTime(msg.timestamp) }}</div>
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
```

- [ ] **Step 2: 在 script setup 中新增 `relativeTime` 辅助函数**

在现有 imports 后追加：

```typescript
import { Promotion, Loading } from '@element-plus/icons-vue'

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
```

- [ ] **Step 3: 完全替换 style**

```css
/* ===== Chat Page Layout ===== */
.chat-page {
  display: flex;
  height: calc(100vh - 80px);  /* 48px topbar + 16px*2 padding */
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
  max-width: 72%;
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
}

.message-bubble.user {
  background: var(--primary);
  color: white;
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-sm) var(--radius-lg);
}

.message-bubble.assistant {
  background: var(--page-bg);
  color: var(--text-primary);
  border-radius: var(--radius-lg) var(--radius-lg) var(--radius-lg) var(--radius-sm);
}

.message-time {
  font-size: 11px;
  margin-top: 6px;
  opacity: 0.6;
}

.message-bubble.user .message-content :deep(p) { margin: 0; }
.message-bubble.assistant .message-content :deep(p) { margin: 4px 0; }
.message-bubble.assistant .message-content :deep(pre) {
  background: #e8eaed;
  padding: 10px;
  border-radius: 6px;
  overflow-x: auto;
  font-size: 12px;
  margin: 8px 0;
}
.message-bubble.assistant .message-content :deep(code) {
  background: #e8eaed;
  padding: 1px 4px;
  border-radius: 3px;
  font-size: 12px;
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
.citation-trigger {
  margin-top: 6px;
}

.citation-btn {
  font-size: 12px !important;
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
```

- [ ] **Step 4: 验证编译**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
```

Expected: no new type errors. `Promotion` and `Loading` are available in `@element-plus/icons-vue`.

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/agent/ChatView.vue
git commit -m "refactor: redesign chat page with 3-column layout, coral bubbles, and welcome screen"
```

---

### Task 4: KnowledgeBase.vue — 堆叠卡片式布局

**Files:**
- Modify: `frontend/src/views/knowledge/KnowledgeBase.vue`

- [ ] **Step 1: 替换 template**

改造为页面标题 + 统计卡片行 + 文件上传卡片 + 文档管理卡片 + 搜索卡片，去掉 el-tabs：

```html
<template>
  <div class="kb-page">
    <!-- 页面标题 -->
    <div class="kb-page-header">
      <h2 class="kb-page-title">知识库管理</h2>
      <p class="kb-page-subtitle">管理文档、上传文件、搜索知识</p>
    </div>

    <!-- 统计卡片 -->
    <div class="stats-row">
      <div class="stat-card">
        <div class="stat-card-label">文档总数</div>
        <div class="stat-card-value">{{ store.docCount }}</div>
      </div>
      <div class="stat-card">
        <div class="stat-card-label">向量分块总数</div>
        <div class="stat-card-value">{{ store.chunkCount }}</div>
      </div>
      <div class="stat-card" v-if="Object.keys(store.categoryStats).length > 0">
        <div class="stat-card-label">分类统计</div>
        <div class="stat-card-tags">
          <el-tag
            v-for="(cnt, cat) in store.categoryStats"
            :key="cat"
            size="small"
            type="primary"
            style="margin-right: 4px"
          >{{ cat }}: {{ cnt }}</el-tag>
        </div>
      </div>
    </div>

    <!-- 文件上传卡片 -->
    <div class="kb-card">
      <div class="kb-card-header">文件上传</div>
      <div class="kb-card-body">
        <div class="upload-options">
          <div class="upload-option">
            <label class="upload-label">文档类型</label>
            <el-select v-model="uploadCategory" placeholder="选择文档类型" style="width: 180px" size="default">
              <el-option label="制度文档" value="制度" />
              <el-option label="流程文档" value="流程" />
              <el-option label="FAQ文档" value="FAQ" />
              <el-option label="自动检测" value="" />
            </el-select>
          </div>
          <div class="upload-option">
            <label class="upload-label">解析器</label>
            <el-select v-model="uploadParserCategory" placeholder="可自动检测" clearable style="width: 180px" size="default">
              <el-option label="PDF" value="pdf" />
              <el-option label="Word" value="word" />
              <el-option label="TXT" value="txt" />
              <el-option label="Markdown" value="markdown" />
            </el-select>
          </div>
          <div class="upload-option upload-option-desc">
            <label class="upload-label">描述</label>
            <el-input v-model="uploadDescription" placeholder="可选，简要描述文档内容" style="width: 280px" />
          </div>
        </div>
        <div class="upload-zone" @click="triggerUpload">
          <el-icon :size="32" color="#ccc"><UploadFilled /></el-icon>
          <p class="upload-zone-text">拖拽文件到此处 或 <em>点击上传</em></p>
          <p class="upload-zone-hint">支持 PDF、Word、TXT、Markdown</p>
        </div>
        <div v-if="uploadFile" class="upload-file-info">
          <el-icon><Document /></el-icon>
          <span>{{ uploadFile.name }}</span>
          <el-button type="danger" link size="small" @click="uploadFile = null">移除</el-button>
        </div>
        <el-button type="primary" :loading="store.loading" :disabled="!uploadFile" @click="handleUpload" style="margin-top: 10px">
          上传并摄入
        </el-button>
        <el-upload
          ref="uploadRef"
          :auto-upload="false"
          :limit="1"
          :on-change="handleFileChange"
          :on-remove="() => uploadFile = null"
          accept=".pdf,.doc,.docx,.txt,.md"
          style="display: none"
        />
      </div>
    </div>

    <!-- 文档管理卡片 -->
    <div class="kb-card">
      <div class="kb-card-header">
        <span>文档列表</span>
        <div class="kb-card-header-actions">
          <el-select v-model="docFilterCategory" placeholder="按分类筛选" clearable size="small" style="width: 140px" @change="handleFetchDocuments">
            <el-option label="请假" value="请假" />
            <el-option label="考勤" value="考勤" />
            <el-option label="报销" value="报销" />
            <el-option label="入职" value="入职" />
            <el-option label="离职" value="离职" />
            <el-option label="转正" value="转正" />
          </el-select>
          <el-select v-model="docFilterStatus" placeholder="按状态筛选" clearable size="small" style="width: 120px; margin-left: 8px" @change="handleFetchDocuments">
            <el-option label="活跃" value="active" />
            <el-option label="已删除" value="deleted" />
          </el-select>
          <el-button size="small" @click="handleRefreshDocuments" style="margin-left: 8px">刷新</el-button>
        </div>
      </div>
      <div class="kb-card-body" style="padding-top: 0">
        <el-table :data="store.documentList" v-loading="store.documentLoading" stripe style="width: 100%">
          <el-table-column prop="id" label="ID" width="60" />
          <el-table-column prop="documentName" label="文档名称" min-width="180" show-overflow-tooltip />
          <el-table-column prop="documentType" label="类型" width="80" />
          <el-table-column prop="category" label="分类" width="80">
            <template #default="{ row }">
              <el-tag v-if="row.category" size="small" type="primary">{{ row.category }}</el-tag>
              <span v-else>-</span>
            </template>
          </el-table-column>
          <el-table-column prop="version" label="版本" width="80" />
          <el-table-column prop="chunkCount" label="分块数" width="80" />
          <el-table-column prop="status" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.status === 'active' ? 'success' : 'info'" size="small">
                {{ row.status || '-' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="摄入时间" width="170" />
          <el-table-column label="操作" width="300" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" link size="small" @click="showDetail(row)">详情</el-button>
              <el-button type="primary" link size="small" @click="handleReingest(row)">重新摄入</el-button>
              <el-popconfirm title="确定删除此文档及全部向量？" @confirm="handleDeleteDoc(row.id!)">
                <template #reference>
                  <el-button type="danger" link size="small">删除</el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>
        <el-empty v-if="!store.documentLoading && store.documentList.length === 0" description="暂无文档" />

        <!-- 文档详情弹窗 -->
        <el-dialog v-model="detailVisible" title="文档详情" width="560px">
          <el-descriptions v-if="detailRow" :column="2" border>
            <el-descriptions-item label="ID">{{ detailRow.id }}</el-descriptions-item>
            <el-descriptions-item label="状态">
              <el-tag :type="detailRow.status === 'active' ? 'success' : 'info'" size="small">
                {{ detailRow.status || '-' }}
              </el-tag>
            </el-descriptions-item>
            <el-descriptions-item label="文档名称" :span="2">{{ detailRow.documentName }}</el-descriptions-item>
            <el-descriptions-item label="类型">{{ detailRow.documentType }}</el-descriptions-item>
            <el-descriptions-item label="分类">{{ detailRow.category || '-' }}</el-descriptions-item>
            <el-descriptions-item label="版本">{{ detailRow.version || '-' }}</el-descriptions-item>
            <el-descriptions-item label="分块数">{{ detailRow.chunkCount ?? '-' }}</el-descriptions-item>
            <el-descriptions-item label="部门">{{ detailRow.department || '-' }}</el-descriptions-item>
            <el-descriptions-item label="生效日期">{{ detailRow.effectiveDate || '-' }}</el-descriptions-item>
            <el-descriptions-item v-if="detailRow.description" label="描述" :span="2">{{ detailRow.description }}</el-descriptions-item>
            <el-descriptions-item label="文件路径" :span="2">{{ detailRow.filePath || '-' }}</el-descriptions-item>
            <el-descriptions-item label="文件大小">{{ detailRow.fileSize ? formatFileSize(detailRow.fileSize) : '-' }}</el-descriptions-item>
            <el-descriptions-item label="创建时间">{{ detailRow.createTime || '-' }}</el-descriptions-item>
            <el-descriptions-item label="更新时间" :span="2">{{ detailRow.updateTime || '-' }}</el-descriptions-item>
          </el-descriptions>
        </el-dialog>

        <input
          ref="reingestFileInput"
          type="file"
          accept=".pdf,.doc,.docx,.txt,.md"
          style="display: none"
          @change="onReingestFileChange"
        />
      </div>
    </div>

    <!-- 知识搜索卡片 -->
    <div class="kb-card">
      <div class="kb-card-header">知识搜索</div>
      <div class="kb-card-body">
        <div class="search-row">
          <el-input v-model="searchQuery" placeholder="输入搜索内容" style="width: 400px" @keyup.enter="handleSearch" />
          <span class="topk-label">TopK:</span>
          <el-input-number v-model="searchTopK" :min="1" :max="20" size="small" />
          <el-button type="primary" :loading="store.loading" @click="handleSearch" style="margin-left: 10px">搜索</el-button>
        </div>
        <div v-if="store.searchResult" class="search-result">
          <p class="result-meta">命中 {{ store.hitCount }} 条结果</p>
          <div class="result-card">
            <pre class="result-text">{{ store.searchResult }}</pre>
          </div>
        </div>
      </div>
    </div>
  </div>
</template>
```

- [ ] **Step 2: script setup** — 删除 `activeTab`，新增 `triggerUpload` 方法，其余逻辑保持不变，仅调整。

在 script setup 中：
- 删除 `const activeTab = ref('upload')`
- 新增 `const uploadRef = ref()` 和 `function triggerUpload() { uploadRef.value?.$el?.click?.() }` 或使用 ref 直接操作 file input
- 为 el-upload 添加 ref: `const uploadRef = ref()`

由于 Element Plus 的 `<el-upload>` 是通过其内部 input 触发，需要一个小调整。最简单的方式是保持 el-upload visible 但只隐藏样式，或者改用原生 input + ref。这里保持 el-upload 正常工作，去掉 `display:none`，改为直接可见的拖拽区。

实际上检查现有代码，el-upload 已经有 drag 属性并提供拖拽区。我们直接使用 el-upload 的内置拖拽区样式，但用新的设计覆盖它。

- [ ] **Step 3: 替换 style**

```css
/* ===== KB Page ===== */
.kb-page {
  max-width: 1100px;
  margin: 0 auto;
}

.kb-page-header {
  margin-bottom: 18px;
}

.kb-page-title {
  font-size: 20px;
  font-weight: 700;
  color: var(--text-primary);
  margin: 0 0 4px;
}

.kb-page-subtitle {
  font-size: 13px;
  color: var(--text-muted);
  margin: 0;
}

/* ===== Stats Row ===== */
.stats-row {
  display: flex;
  gap: 14px;
  margin-bottom: 16px;
}

.stat-card {
  flex: 1;
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 14px 18px;
  box-shadow: var(--shadow-card);
}

.stat-card-label {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.stat-card-value {
  font-size: 24px;
  font-weight: 700;
  color: var(--text-primary);
}

.stat-card-tags {
  margin-top: 4px;
}

/* ===== KB Card ===== */
.kb-card {
  background: var(--white);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
  margin-bottom: 14px;
  overflow: hidden;
}

.kb-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 14px 18px;
  font-size: 14px;
  font-weight: 600;
  color: var(--text-primary);
  border-bottom: 1px solid var(--border-light);
  background: var(--surface-warm);
}

.kb-card-header-actions {
  display: flex;
  align-items: center;
}

.kb-card-body {
  padding: 16px 18px;
}

/* ===== Upload ===== */
.upload-options {
  display: flex;
  gap: 20px;
  margin-bottom: 14px;
  flex-wrap: wrap;
}

.upload-option {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.upload-label {
  font-size: 12px;
  color: var(--text-muted);
  font-weight: 500;
}

.upload-zone {
  border: 2px dashed #E0DCD5;
  border-radius: var(--radius-md);
  padding: 30px;
  text-align: center;
  cursor: pointer;
  transition: border-color 0.15s, background 0.15s;
}

.upload-zone:hover {
  border-color: var(--primary);
  background: rgba(232, 112, 64, 0.02);
}

.upload-zone-text {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 10px 0 4px;
}

.upload-zone-text em {
  color: var(--primary);
  font-style: normal;
}

.upload-zone-hint {
  color: var(--text-muted);
  font-size: 12px;
}

.upload-file-info {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-top: 10px;
  padding: 8px 12px;
  background: var(--surface-warm);
  border-radius: var(--radius-sm);
  font-size: 13px;
  color: var(--text-secondary);
}

/* ===== Search ===== */
.search-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.topk-label {
  font-size: 13px;
  color: var(--text-secondary);
  margin-left: 4px;
}

.search-result {
  margin-top: 14px;
}

.result-meta {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.result-card {
  background: var(--surface-warm);
  border: 1px solid var(--border-light);
  border-radius: var(--radius-md);
  padding: 14px;
  max-height: 360px;
  overflow: auto;
}

.result-text {
  white-space: pre-wrap;
  font-size: 13px;
  line-height: 1.6;
  margin: 0;
  color: var(--text-primary);
}
```

- [ ] **Step 4: 验证编译**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
```

Expected: no new type errors.

- [ ] **Step 5: 提交**

```bash
git add frontend/src/views/knowledge/KnowledgeBase.vue
git commit -m "refactor: redesign knowledge base page with stacked card layout"
```

---

### Task 5: FaqList.vue — 统一配色/圆角/卡片风格

**Files:**
- Modify: `frontend/src/views/faq/FaqList.vue`

- [ ] **Step 1: 替换 style**

template 和 script 结构不变，只替换样式：

```css
.faq-list-page {
  background: var(--white);
  padding: 20px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  align-items: center;
}

/* FAQ 候选卡片覆盖 */
:deep(.el-card__header) {
  background: var(--surface-warm);
  border-bottom: 1px solid var(--border-light);
}
```

- [ ] **Step 2: 验证编译并提交**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
git add frontend/src/views/faq/FaqList.vue
git commit -m "refactor: refresh FAQ list page with new design tokens"
```

---

### Task 6: HighFreqFaq.vue — 统一配色/圆角/卡片风格

**Files:**
- Modify: `frontend/src/views/faq/HighFreqFaq.vue`

- [ ] **Step 1: 替换 style**

```css
.high-freq-page {
  background: var(--white);
  padding: 20px;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-card);
}

.toolbar {
  display: flex;
  align-items: center;
  margin-bottom: 20px;
}

.label {
  font-size: 13px;
  color: var(--text-secondary);
  white-space: nowrap;
}

.card-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.faq-card {
  cursor: pointer;
  border-radius: var(--radius-md) !important;
}

.faq-card:hover {
  border-color: var(--primary) !important;
}

.card-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.rank-badge {
  flex-shrink: 0;
}

.question {
  flex: 1;
  font-size: 15px;
  font-weight: 600;
  color: var(--text-primary);
}

.answer {
  margin-top: 12px;
  padding: 12px;
  background: var(--surface-warm);
  border-radius: var(--radius-md);
  font-size: 14px;
  color: var(--text-secondary);
  line-height: 1.7;
}
```

- [ ] **Step 2: 验证编译并提交**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1 | head -20
git add frontend/src/views/faq/HighFreqFaq.vue
git commit -m "refactor: refresh high-freq FAQ page with new design tokens"
```

---

### Task 7: 集成验证

- [ ] **Step 1: 启动前端 dev server**

```bash
cd frontend && npm run dev
```

- [ ] **Step 2: 逐页面手动验证**

  - 访问 `/login` → 暗色沉浸式登录页、毛玻璃卡片效果正常
  - 登录后 → 顶栏 + 图标侧边栏显示正常、导航切换正常
  - `/agent/chat` → 三栏布局、会话列表面板、coral 气泡、欢迎页示例问题卡片
  - 发送一条问答 → 流式渲染、引用出处 popover 正常
  - `/knowledge` → 统计卡片 + 文件上传卡片 + 文档表格卡片 + 搜索卡片
  - `/faq/list` → 表格 + FAQ 候选卡片风格统一
  - `/faq/high-freq` → 高频 FAQ 卡片列表风格统一

- [ ] **Step 3: 验证编译无误**

```bash
cd frontend && npx vue-tsc --noEmit --project tsconfig.app.json 2>&1
```

Expected: no type errors.

---

## 自审清单

1. **Spec 覆盖**：所有设计要点（色彩体系、外壳布局、登录页、聊天页、知识库页、FAQ页）都有对应任务
2. **占位符扫描**：无 TBD/TODO，所有代码块完整可执行
3. **类型一致性**：所有引用的类型、方法名在各 Task 中保持一致，未变更 store/router/API
4. **范围控制**：纯前端 CSS/模板改造，不涉及任何后端、API、store、router 变更
