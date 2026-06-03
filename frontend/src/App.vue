<template>
  <el-container class="app-container">
    <el-aside v-if="!isLoginPage" width="220px" class="app-sidebar">
      <div class="logo">制度知识库问答与流程指引助手</div>
      <el-menu
        :default-active="activeMenu"
        router
        :collapse="false"
        background-color="#304156"
        text-color="#bfcbd9"
        active-text-color="#409EFF"
      >
        <el-sub-menu index="chat-group">
          <template #title>
            <el-icon><ChatDotRound /></el-icon>
            <span>智能问答</span>
          </template>
          <el-menu-item index="/agent/chat">
            <el-icon><ChatLineSquare /></el-icon>
            <span>RAG 对话</span>
          </el-menu-item>
        </el-sub-menu>

        <el-sub-menu index="faq-group" v-if="isAdmin">
          <template #title>
            <el-icon><Collection /></el-icon>
            <span>FAQ 管理</span>
          </template>
          <el-menu-item index="/faq/list">
            <el-icon><List /></el-icon>
            <span>FAQ 列表</span>
          </el-menu-item>
          <el-menu-item index="/faq/high-freq">
            <el-icon><TrendCharts /></el-icon>
            <span>高频 FAQ</span>
          </el-menu-item>
        </el-sub-menu>

        <el-menu-item index="/knowledge" v-if="isAdmin">
          <el-icon><Document /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>

      <div class="sidebar-footer">
        <div class="user-info">
          <el-icon><UserFilled /></el-icon>
          <span class="user-name">{{ displayName }}</span>
          <el-tag v-if="isAdmin" size="small" type="warning">管理员</el-tag>
        </div>
        <el-button text size="small" style="color: #bfcbd9" @click="handleLogout">
          <el-icon><SwitchButton /></el-icon> 退出
        </el-button>
      </div>
    </el-aside>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { UserFilled, SwitchButton } from '@element-plus/icons-vue'
import { authLogout } from '@/api/agent'

interface UserInfo {
  id?: number
  username?: string
  displayName?: string
  role?: string
}

const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => route.path)
const isLoginPage = computed(() => route.path === '/login')

function readUserFromStorage(): UserInfo | null {
  try {
    const raw = localStorage.getItem('currentUser')
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

// 响应式的用户状态，每次路由变化时从 localStorage 重新读取
const currentUser = ref<UserInfo | null>(readUserFromStorage())
watch(() => route.fullPath, () => {
  currentUser.value = readUserFromStorage()
})

const displayName = computed(() => currentUser.value?.displayName || '')
const isAdmin = computed(() => currentUser.value?.role === 'admin')

async function handleLogout() {
  try { await authLogout() } catch { /* ignore */ }
  localStorage.removeItem('authToken')
  localStorage.removeItem('currentUser')
  currentUser.value = null
  router.push('/login')
}
</script>

<style>
* { margin: 0; padding: 0; box-sizing: border-box; }

.app-container {
  height: 100vh;
}

.app-sidebar {
  background-color: #304156;
  overflow-y: auto;
}

.logo {
  height: 60px;
  line-height: 60px;
  text-align: center;
  color: #fff;
  font-size: 18px;
  font-weight: bold;
  border-bottom: 1px solid rgba(255,255,255,0.1);
}

.app-main {
  background-color: #f0f2f5;
  padding: 20px;
  overflow-y: auto;
}

.sidebar-footer {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  padding: 12px;
  border-top: 1px solid rgba(255,255,255,0.1);
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 6px;
  color: #bfcbd9;
  font-size: 13px;
}

.user-name {
  max-width: 80px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
