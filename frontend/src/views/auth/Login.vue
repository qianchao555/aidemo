<template>
  <div class="login-page">
    <div class="login-bg-deco deco-1"></div>
    <div class="login-bg-deco deco-2"></div>

    <div class="login-card">
      <div class="login-logo">
        <div class="login-logo-icon">Bot</div>
      </div>
      <h2 class="login-title">制度问答与流程指引助手</h2>
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

<!--      <p class="login-hint">默认账号：zhangsan / lisi / wangwu，密码：123456</p>-->
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { User, Lock } from '@element-plus/icons-vue'
import { authLogin } from '@/api/agent'
import type { FormInstance, FormRules } from 'element-plus'

const router = useRouter()
const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: ''
})

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const res = await authLogin(form)
    localStorage.setItem('authToken', res.token)
    localStorage.setItem('currentUser', JSON.stringify({
      id: res.userId,
      username: res.username,
      displayName: res.displayName,
      role: res.role
    }))
    ElMessage.success('登录成功')
    router.push('/agent/chat')
  } catch {
    // error handled by interceptor
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
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
</style>
