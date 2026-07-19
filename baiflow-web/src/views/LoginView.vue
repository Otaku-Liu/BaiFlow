<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <h2 style="margin:0;text-align:center">BaiFlow 登录</h2>
      </template>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="handleLogin">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" placeholder="请输入密码" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">
          {{ loading ? '登录中...' : '登 录' }}
        </el-button>
        <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { login, getCurrentUser } from '../api/auth'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    const { data: loginRes } = await login(form.username, form.password)
    if (loginRes.code !== 'OK') {
      errorMsg.value = loginRes.message || '登录失败'
      return
    }
    const token = loginRes.data.token
    authStore.setSession(token, null)

    // 获取用户信息
    const { data: meRes } = await getCurrentUser()
    if (meRes.code === 'OK') {
      authStore.setSession(token, meRes.data)
    }

    router.push('/')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '登录请求失败'
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  display: flex; align-items: center; justify-content: center;
  min-height: 100vh; background: var(--el-bg-color-page);
}
.login-card { width: 380px; }
.error-msg { color: var(--el-color-danger); text-align: center; margin-top: 12px; }
</style>
