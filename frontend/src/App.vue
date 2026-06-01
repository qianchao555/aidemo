<template>
  <el-container class="app-container">
    <el-aside width="220px" class="app-sidebar">
      <div class="logo">AI Console</div>
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

        <el-sub-menu index="faq-group">
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

        <el-menu-item index="/knowledge">
          <el-icon><Document /></el-icon>
          <span>知识库管理</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-main class="app-main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import { useRoute } from 'vue-router'

const route = useRoute()
const activeMenu = computed(() => route.path)
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
</style>
