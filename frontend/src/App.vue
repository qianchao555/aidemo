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

<script setup lang="ts">
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
</script>

<style>
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
</style>
