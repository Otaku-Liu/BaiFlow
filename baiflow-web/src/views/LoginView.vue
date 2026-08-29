<template>
  <div class="login-wrapper">
    <el-dropdown class="login-locale" @command="handleLocaleChange">
      <span class="login-locale-trigger">
        {{ locale === 'zh-CN' ? '中文' : 'English' }}
        <el-icon class="login-locale-arrow"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
          <el-dropdown-item command="en">English</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-card class="login-card" shadow="hover">
      <template #header>
        <img src="/brand/logo-icon.png" class="login-logo" alt="BaiFlow" />
        <h2 style="margin:0;text-align:center;font-size:24px;font-weight:600;letter-spacing:-0.02em">BaiFlow</h2>
        <p style="margin:6px 0 0;text-align:center;font-size:13px;color:var(--el-text-color-secondary)">{{ t('login.subtitle') }}</p>
      </template>
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
    </el-card>
  </div>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { ArrowDown } from '@element-plus/icons-vue'
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
// computed：切语言后校验文案随 locale 刷新
const rules = computed(() => ({
  username: [{ required: true, message: t('login.usernameRequired'), trigger: 'blur' }],
  password: [{ required: true, message: t('login.passwordRequired'), trigger: 'blur' }]
}))

/** 语言切换：与主界面顶部一致，写入 localStorage.baiflow_locale */
function handleLocaleChange(lang) {
  locale.value = lang
  localStorage.setItem('baiflow_locale', lang)
}

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

<style scoped>
.login-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f5f7 0%, #ececf1 100%);
}

.login-locale {
  position: absolute;
  top: 16px;
  right: 20px;
}

.login-locale-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.login-locale-trigger:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--el-text-color-primary);
}

/* 悬浮/聚焦不显示默认黑色轮廓框 */
.login-locale-trigger,
.login-locale-trigger:hover,
.login-locale-trigger:focus,
.login-locale-trigger:focus-visible {
  outline: none;
  box-shadow: none;
}

.login-locale-arrow {
  font-size: 12px;
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

.login-logo {
  display: block;
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 18px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
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
