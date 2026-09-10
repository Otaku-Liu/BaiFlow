<template>
  <AuthShell :subtitle="t('setup.subtitle')">
    <el-alert
      type="info"
      :closable="false"
      show-icon
      :title="t('setup.tokenHint')"
      style="margin-bottom:16px"
    />

    <el-form ref="formRef" :model="form" :rules="rules" :key="locale" label-position="top" @submit.prevent="handleSubmit">
      <el-form-item :label="t('setup.token')" prop="setupToken">
        <el-input v-model="form.setupToken" :placeholder="t('setup.tokenPlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('setup.username')" prop="username">
        <el-input v-model="form.username" :placeholder="t('setup.usernamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('setup.displayName')" prop="displayName">
        <el-input v-model="form.displayName" :placeholder="t('setup.displayNamePlaceholder')" />
      </el-form-item>
      <el-form-item :label="t('setup.password')" prop="password">
        <el-input v-model="form.password" type="password" :placeholder="t('setup.passwordPlaceholder')" show-password />
      </el-form-item>
      <el-form-item :label="t('setup.confirmPassword')" prop="confirmPassword">
        <el-input v-model="form.confirmPassword" type="password" :placeholder="t('setup.confirmPasswordPlaceholder')" show-password />
      </el-form-item>
      <el-button type="primary" native-type="submit" :loading="loading" style="width:100%">
        {{ loading ? t('setup.submitting') : t('setup.button') }}
      </el-button>
      <p v-if="errorMsg" class="error-msg">{{ errorMsg }}</p>
    </el-form>
  </AuthShell>
</template>

<script setup>
import { reactive, ref, computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import AuthShell from '../components/AuthShell.vue'
import { initializeSystem } from '../api/setup'
import { getCurrentUser } from '../api/auth'
import { useAuthStore } from '../stores/auth'
import { useSystemStore } from '../stores/system'
import { isNetworkError } from '../utils/notify'

const router = useRouter()
const authStore = useAuthStore()
const systemStore = useSystemStore()
const { t, locale } = useI18n()
const formRef = ref(null)
const loading = ref(false)
const errorMsg = ref('')

const form = reactive({
  setupToken: '',
  username: '',
  displayName: '',
  password: '',
  confirmPassword: ''
})

// computed：切语言后校验文案随 locale 刷新（配合表单 :key="locale"）
const rules = computed(() => ({
  setupToken: [{ required: true, message: t('setup.tokenRequired'), trigger: 'blur' }],
  username: [{ required: true, message: t('setup.usernameRequired'), trigger: 'blur' }],
  password: [
    { required: true, message: t('setup.passwordRequired'), trigger: 'blur' },
    { min: 8, message: t('setup.passwordTooShort'), trigger: 'blur' }
  ],
  confirmPassword: [
    { required: true, message: t('setup.confirmPasswordRequired'), trigger: 'blur' },
    { validator: validateConfirm, trigger: 'blur' }
  ]
}))

function validateConfirm(rule, value, callback) {
  if (value !== form.password) {
    callback(new Error(t('setup.passwordMismatch')))
    return
  }
  callback()
}

async function handleSubmit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  errorMsg.value = ''

  try {
    const { data: setupRes } = await initializeSystem({
      setupToken: form.setupToken.trim(),
      username: form.username.trim(),
      password: form.password,
      // 留空则后端用用户名作为显示名
      displayName: form.displayName.trim()
    })
    if (setupRes.code !== 0) {
      errorMsg.value = setupRes.message || t('setup.failed')
      return
    }

    // 初始化成功即已签发登录会话，直接进入主界面（不再让用户重输刚设的密码）
    const token = setupRes.data.token
    authStore.setSession(token, null)
    systemStore.markInitialized()

    const { data: meRes } = await getCurrentUser()
    if (meRes.code === 0) {
      authStore.setSession(token, meRes.data)
    }
    router.push('/')
  } catch (e) {
    errorMsg.value = isNetworkError(e)
      ? t('common.cannotReachServer')
      : (e.response?.data?.message || t('setup.requestFailed'))
  } finally {
    loading.value = false
  }
}
</script>
