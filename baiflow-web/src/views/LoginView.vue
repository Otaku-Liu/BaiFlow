<template>
  <div class="login-wrapper">
    <el-card class="login-card" shadow="hover">
      <template #header>
        <h2 style="margin:0;text-align:center;font-size:24px;font-weight:600;letter-spacing:-0.02em">BaiFlow</h2>
        <p style="margin:6px 0 0;text-align:center;font-size:13px;color:var(--el-text-color-secondary)">登录到你的账户</p>
      </template>
      <el-alert
        v-if="authStore.connectionTimeout"
        type="warning"
        :closable="false"
        show-icon
        title="无法连接服务器，请检查网络连接"
        style="margin-bottom:16px"
      />
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
      <el-button
        v-if="authStore.connectionTimeout"
        plain
        :loading="reconnecting"
        style="width:100%;margin-top:12px"
        @click="handleReconnect"
      >
        重新连接
      </el-button>
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { login, getCurrentUser } from '../api/auth'
import { getHealth } from '../api/health'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const formRef = ref(null)
const loading = ref(false)
const reconnecting = ref(false)
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

    // 重新登录成功：setSession 已清超时标志并重启检测
    router.push('/')
  } catch (e) {
    errorMsg.value = e.response?.data?.message || '登录请求失败'
  } finally {
    loading.value = false
  }
}

/** 连接超时态下的「重新连接」：探测健康 + 校验会话，恢复后直接回主界面 */
async function handleReconnect() {
  reconnecting.value = true
  try {
    const { data: healthRes } = await getHealth()
    if (healthRes.code !== 'OK') {
      ElMessage.warning('服务器未就绪，请稍后重试')
      return
    }
    const { data: meRes } = await getCurrentUser()
    if (meRes.code === 'OK') {
      // 会话仍有效：setSession 已清超时标志并重启检测，直接回主界面
      authStore.setSession(authStore.token, meRes.data)
      router.push('/')
    } else {
      // 会话异常（非 401 的业务错误）：清会话转正常登录表单
      authStore.clearSession()
      ElMessage.error('登录已过期，请重新登录')
    }
  } catch (e) {
    if (e.response?.status === 401) {
      // 会话已失效：拦截器若因 authErrorShown 已置位而未处理，这里兜底清会话
      if (authStore.isLoggedIn) {
        authStore.clearSession()
        ElMessage.error('登录已过期，请重新登录')
      }
      return
    }
    ElMessage.warning('仍无法连接服务器，请稍后重试')
  } finally {
    reconnecting.value = false
  }
}
</script>

<style scoped>
.login-wrapper {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f5f7 0%, #ececf1 100%);
}

.login-card {
  width: 400px;
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
}

.login-card :deep(.el-card__header) {
  padding: 28px 24px 0;
  border-bottom: none;
}

.login-card :deep(.el-card__body) {
  padding: 24px;
}

.login-card h2 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
}

.login-wrapper :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-transform: none;
  letter-spacing: 0;
}

.login-wrapper :deep(.el-button--primary) {
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  letter-spacing: 0.02em;
}

.error-msg {
  color: var(--el-color-danger);
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  font-weight: 500;
}
</style>
