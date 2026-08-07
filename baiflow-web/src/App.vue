<template>
  <el-config-provider :locale="elLocale">
    <router-view v-slot="{ Component, route }">
      <transition :name="transitionName" mode="out-in">
        <component :is="Component" :key="route.path" />
      </transition>
    </router-view>
  </el-config-provider>
</template>

<script setup>
import { computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCN from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'
import { useAuthStore } from './stores/auth'

const router = useRouter()
const authStore = useAuthStore()
const { locale } = useI18n()

// 服务器连接超时：提示后延迟跳转登录页（保留 token；不能整页刷新，否则守卫会弹回主页）
let timeoutNavTimer = null
watch(() => authStore.connectionTimeout, (v) => {
  if (v) {
    timeoutNavTimer = setTimeout(() => router.push('/login'), 1500)
  } else if (timeoutNavTimer) {
    // 超时态被清除（重连/重新登录）则取消未执行的跳转
    clearTimeout(timeoutNavTimer)
    timeoutNavTimer = null
  }
})

const elLocale = computed(() => locale.value === 'en' ? en : zhCN)

const transitionName = computed(() => {
  return 'page-fade'
})
</script>
