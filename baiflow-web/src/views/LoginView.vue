<template>
  <AuthShell :subtitle="t('login.subtitle')">
    <el-alert
      v-if="authStore.connectionTimeout"
      type="warning"
      :closable="false"
      show-icon
      :title="t('login.cannotReach')"
      style="margin-bottom:16px"
    />
    <el-form ref="formRef" :model="form" :rules="rules" :key="locale" label-position="top" @submit.prevent="handleLogin">
      <el-form-item :label="t('login.username')" prop="username">
        <el-input v-model="form.username" :placeholder="t('login.usernamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('login.password')" prop="password">
        <el-input v-model="form.password" type="password" :placeholder="t('login.passwordPlaceholder')" show-password />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">
        {{ loading ? t('login.loggingIn') : t('login.button') }}
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
      {{ t('login.reconnect') }}
    </el-button>
  </AuthShell>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import AuthShell from '../components/AuthShell.vue'
import { login, getCurrentUser } from '../api/auth'
import { getHealth } from '../api/health'
import { useAuthStore } from '../stores/auth'
import { isNetworkError } from '../utils/notify'

const router = useRouter()
const authStore = useAuthStore()
const { t, locale } = useI18n()
const formRef = ref(null)
const loading = ref(false)
const reconnecting = ref(false)
const errorMsg = ref('')

const form = reactive({ username: '', password: '' })
// computed：切语言后校验文案随 locale 刷新（配合表单 :key="locale"）
const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

async function handleLogin() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    const { data: loginRes } = await login(form.username, form.password)
    if (loginRes.code !== 0) {
      errorMsg.value = loginRes.message || t('login.failed')
      return
    }
    const token = loginRes.data.token
    authStore.setSession(token, null)

    // 获取用户信息
    const { data: meRes } = await getCurrentUser()
    if (meRes.code === 0) {
      authStore.setSession(token, meRes.data)
    }

    // 重新登录成功：setSession 已清超时标志并重启检测
    router.push('/')
  } catch (e) {
    errorMsg.value = isNetworkError(e) ? t('common.cannotReachServer') : (e.response?.data?.message || t('login.requestFailed'))
  } finally {
    loading.value = false
  }
}

/** 连接超时态下的「重新连接」：探测健康 + 校验会话，恢复后直接回主界面 */
async function handleReconnect() {
  reconnecting.value = true
  try {
    const { data: healthRes } = await getHealth()
    if (healthRes.code !== 0) {
      ElMessage.warning(t('login.serverNotReady'))
      return
    }
    const { data: meRes } = await getCurrentUser()
    if (meRes.code === 0) {
      // 会话仍有效：setSession 已清超时标志并重启检测，直接回主界面
      authStore.setSession(authStore.token, meRes.data)
      router.push('/')
    } else {
      // 会话异常（非 401 的业务错误）：清会话转正常登录表单
      authStore.clearSession()
      ElMessage.error(t('login.sessionExpired'))
    }
  } catch (e) {
    if (e.response?.status === 401) {
      // 会话已失效：拦截器若因 authErrorShown 已置位而未处理，这里兜底清会话
      if (authStore.isLoggedIn) {
        authStore.clearSession()
        ElMessage.error(t('login.sessionExpired'))
      }
      return
    }
    ElMessage.warning(t('login.stillCannotReach'))
  } finally {
    reconnecting.value = false
  }
}
</script>
