<template>
  <div v-if="isLoginPage" class="login-shell">
    <router-view />
  </div>

  <div v-else class="app-shell">
    <div class="app-body">
      <!-- Icon Sidebar -->
      <nav class="icon-sidebar" :class="{ expanded: sidebarExpanded }">
        <div class="sidebar-top">
          <div v-if="sidebarExpanded" class="sidebar-logo" @click="navTo('/agent/chat')" title="首页">
            <div class="sidebar-logo-icon">Bot</div>
            <span class="sidebar-logo-text">知识库助手</span>
          </div>
          <div class="sidebar-toggle" @click="sidebarExpanded = !sidebarExpanded" :title="sidebarExpanded ? '收起' : '展开'">
            <el-icon :size="18">
              <Fold v-if="sidebarExpanded" />
              <Expand v-else />
            </el-icon>
          </div>
        </div>

        <div class="sidebar-nav">
          <div
            class="sidebar-nav-item"
            :class="{ active: isActive('/agent/chat') }"
            title="智能问答"
            @click="navTo('/agent/chat')"
          >
            <el-icon :size="22"><ChatDotRound /></el-icon>
            <span v-if="sidebarExpanded" class="nav-label">智能问答</span>
          </div>

          <template v-if="isAdmin">
            <div
              class="sidebar-nav-item"
              :class="{ active: isActive('/faq/list') || isActive('/faq/dashboard') }"
              title="FAQ 管理"
              @click="navTo('/faq/list')"
            >
              <el-icon :size="22"><Collection /></el-icon>
              <span v-if="sidebarExpanded" class="nav-label">FAQ 管理</span>
            </div>

            <div
              class="sidebar-nav-item"
              :class="{ active: isActive('/knowledge') }"
              title="知识库管理"
              @click="navTo('/knowledge')"
            >
              <el-icon :size="22"><Document /></el-icon>
              <span v-if="sidebarExpanded" class="nav-label">知识库管理</span>
            </div>
          </template>
        </div>

        <!-- Department Switcher -->
        <div class="sidebar-dept" ref="deptMenuRef">
          <div v-if="sidebarExpanded" class="dept-label">当前部门</div>
          <div class="dept-toggle" :class="{ expanded: sidebarExpanded }" @click="deptMenuVisible = !deptMenuVisible">
            <span class="dept-icon">🏢</span>
            <span v-if="sidebarExpanded" class="dept-name">{{ currentDepartment }}</span>
            <span v-if="sidebarExpanded" class="dept-arrow" :class="{ open: deptMenuVisible }">▼</span>
          </div>
          <div v-if="deptMenuVisible" class="dept-menu">
            <div v-for="dept in DEPARTMENTS" :key="dept"
              class="dept-item" :class="{ active: currentDepartment === dept }"
              @click="switchDepartment(dept); deptMenuVisible = false">
              {{ dept }}
            </div>
          </div>
        </div>

        <div class="sidebar-footer">
          <div class="user-menu-wrapper" ref="userMenuRef">
            <div
              class="sidebar-avatar-row"
              :class="{ active: userMenuVisible }"
              @click="userMenuVisible = !userMenuVisible"
            >
              <div class="sidebar-avatar">{{ displayName.charAt(0) }}</div>
              <span v-if="sidebarExpanded" class="sidebar-user-name">{{ displayName }}</span>
            </div>
            <div v-if="userMenuVisible" class="user-popup">
              <div class="user-popup-info">
                <div class="user-popup-avatar">{{ displayName.charAt(0) }}</div>
                <div class="user-popup-details">
                  <div class="user-popup-name">{{ displayName }}</div>
                  <el-tag v-if="isAdmin" size="small" type="warning">管理员</el-tag>
                </div>
              </div>
              <div class="user-popup-divider"></div>
              <div class="user-popup-action" @click="handleLogout">
                <el-icon :size="16"><SwitchButton /></el-icon>
                <span>退出登录</span>
              </div>
            </div>
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
import { computed, ref, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ChatDotRound, Collection, Document, SwitchButton, Fold, Expand } from '@element-plus/icons-vue'
import { authLogout } from '@/api/agent'
import { DEPARTMENTS } from '@/constants/departments'

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
const userMenuVisible = ref(false)
const userMenuRef = ref<HTMLElement | null>(null)
const sidebarExpanded = ref(false)

const currentDepartment = ref(localStorage.getItem('selectedDepartment') || readUserFromStorage()?.department || '全公司')
const deptMenuVisible = ref(false)
const deptMenuRef = ref<HTMLElement | null>(null)

function switchDepartment(dept: string) {
  currentDepartment.value = dept
  localStorage.setItem('selectedDepartment', dept)
}

function onDocumentClick(e: MouseEvent) {
  if (userMenuRef.value && !userMenuRef.value.contains(e.target as Node)) {
    userMenuVisible.value = false
  }
  if (deptMenuRef.value && !deptMenuRef.value.contains(e.target as Node)) {
    deptMenuVisible.value = false
  }
}

onMounted(() => document.addEventListener('click', onDocumentClick))
onUnmounted(() => document.removeEventListener('click', onDocumentClick))

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
}

/* ===== App Body ===== */
.app-body {
  flex: 1;
  display: flex;
  overflow: hidden;
}

/* ===== Icon Sidebar ===== */
.icon-sidebar {
  width: 68px;
  background: var(--sidebar-bg);
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 10px 0;
  flex-shrink: 0;
  z-index: 90;
  transition: width 0.2s ease;
  overflow: visible;
}

.icon-sidebar.expanded {
  width: 220px;
  align-items: stretch;
  padding: 10px 12px;
}

/* Sidebar top: logo + toggle */
.sidebar-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
  padding: 0 4px;
  overflow: hidden;
}

.icon-sidebar:not(.expanded) .sidebar-top {
  justify-content: center;
}

.sidebar-logo {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 10px;
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
  flex-shrink: 0;
}

.sidebar-logo-text {
  font-size: 14px;
  font-weight: 700;
  color: white;
  white-space: nowrap;
}

.sidebar-toggle {
  width: 32px;
  height: 32px;
  border-radius: var(--radius-md);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #888;
  cursor: pointer;
  transition: all 0.15s;
  flex-shrink: 0;
}

.sidebar-toggle:hover {
  background: rgba(255, 255, 255, 0.08);
  color: #bbb;
}

/* Nav items */
.sidebar-nav {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  overflow: hidden;
}

.icon-sidebar.expanded .sidebar-nav {
  align-items: stretch;
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
  gap: 0;
}

.icon-sidebar.expanded .sidebar-nav-item {
  width: 100%;
  height: 40px;
  justify-content: flex-start;
  padding: 0 8px;
  gap: 12px;
}

.sidebar-nav-item:hover {
  background: rgba(255, 255, 255, 0.06);
  color: #bbb;
}

.sidebar-nav-item.active {
  background: rgba(232, 112, 64, 0.15);
  color: var(--primary);
}

.nav-label {
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}

/* Department Switcher */
.sidebar-dept {
  padding: 0 12px 4px;
  position: relative;
}
.icon-sidebar:not(.expanded) .sidebar-dept {
  display: flex;
  justify-content: center;
  padding: 0 0 4px;
}
.dept-label {
  font-size: 10px;
  color: #555;
  text-transform: uppercase;
  padding: 0 8px 6px;
  letter-spacing: 0.5px;
}
.dept-toggle {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 8px 10px;
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.08);
  border-radius: var(--radius-md);
  cursor: pointer;
  font-size: 13px;
  color: #ccc;
  transition: background 0.15s;
}
.dept-toggle:hover { background: rgba(255,255,255,0.08); }
.dept-toggle:not(.expanded) {
  justify-content: center;
  padding: 8px;
}
.dept-icon { font-size: 14px; flex-shrink: 0; }
.dept-name { flex: 1; }
.dept-arrow {
  font-size: 9px;
  color: #666;
  transition: transform 0.15s;
}
.dept-arrow.open { transform: rotate(180deg); }
.dept-menu {
  position: absolute;
  top: 100%;
  left: 12px;
  right: 12px;
  background: #333;
  border: 1px solid rgba(255,255,255,0.1);
  border-radius: var(--radius-md);
  overflow: hidden;
  z-index: 95;
  margin-top: 2px;
}
.icon-sidebar:not(.expanded) .dept-menu {
  left: 0;
  right: auto;
  width: 180px;
}
.dept-item {
  padding: 8px 12px;
  font-size: 12px;
  color: #999;
  cursor: pointer;
  transition: all 0.1s;
}
.dept-item:hover { color: #ccc; background: rgba(255,255,255,0.04); }
.dept-item.active { color: var(--primary); background: rgba(232,112,64,0.12); }

/* Footer / user area */
.sidebar-footer {
  padding-top: 8px;
  position: relative;
  overflow: visible;
}

.icon-sidebar.expanded .sidebar-footer {
  align-self: stretch;
}

.sidebar-avatar-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10px;
  cursor: pointer;
  padding: 4px;
  border-radius: var(--radius-md);
  transition: background 0.15s;
}

.icon-sidebar.expanded .sidebar-avatar-row {
  justify-content: flex-start;
  padding: 6px 8px;
}

.sidebar-avatar-row:hover {
  background: rgba(255, 255, 255, 0.06);
}

.sidebar-avatar {
  width: 34px;
  height: 34px;
  background: var(--primary);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 14px;
  font-weight: 700;
  flex-shrink: 0;
  transition: opacity 0.15s;
}

.sidebar-avatar-row.active .sidebar-avatar {
  opacity: 0.85;
}

.sidebar-user-name {
  font-size: 13px;
  font-weight: 500;
  color: #ccc;
  white-space: nowrap;
}

/* User Popup (ChatGPT-style) */
.user-menu-wrapper {
  position: relative;
}

.user-popup {
  position: absolute;
  bottom: 46px;
  left: 4px;
  width: 200px;
  background: var(--white);
  border-radius: var(--radius-md);
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.12);
  border: 1px solid var(--border-light);
  overflow: hidden;
  z-index: 1000;
}

.user-popup-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
}

.user-popup-avatar {
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: var(--primary);
  color: white;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 13px;
  font-weight: 700;
  flex-shrink: 0;
}

.user-popup-details {
  display: flex;
  flex-direction: column;
  gap: 3px;
}

.user-popup-name {
  font-size: 13px;
  font-weight: 600;
  color: var(--text-primary);
}

.user-popup-divider {
  height: 1px;
  background: var(--border-light);
}

.user-popup-action {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 12px;
  font-size: 12px;
  color: var(--text-secondary);
  cursor: pointer;
  transition: background 0.15s;
}

.user-popup-action:hover {
  background: var(--surface-warm);
  color: var(--primary);
}

/* ===== Main Content ===== */
.main-content {
  flex: 1;
  background: var(--page-bg);
  padding: 24px 20px 16px;
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
