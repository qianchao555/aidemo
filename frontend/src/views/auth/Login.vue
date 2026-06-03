<template>
  <div class="login-container">
    <el-card class="login-card" shadow="always">
      <h2 class="login-title">制度知识库问答与流程指引助手</h2>
      <el-form
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="0"
        size="large"
        @keyup.enter="handleLogin"
      >
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input v-model="form.password" type="password" placeholder="密码" show-password :prefix-icon="Lock" />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" style="width: 100%" :loading="loading" @click="handleLogin">登 录</el-button>
        </el-form-item>
      </el-form>
      <p class="login-hint">默认账号：zhangsan / lisi / wangwu，密码：123456</p>
    </el-card>
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
.login-container {
  height: calc(100vh - 100px);
  display: flex;
  align-items: center;
  justify-content: center;
}

.login-card {
  width: 400px;
  padding: 10px;
}

.login-title {
  text-align: center;
  color: #303133;
  margin-bottom: 30px;
  font-size: 20px;
}

.login-hint {
  text-align: center;
  color: #909399;
  font-size: 12px;
  margin-top: -10px;
}
</style>
