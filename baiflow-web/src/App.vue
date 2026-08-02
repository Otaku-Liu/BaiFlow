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
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import { useI18n } from 'vue-i18n'
import zhCN from 'element-plus/es/locale/lang/zh-cn'
import en from 'element-plus/es/locale/lang/en'

const router = useRouter()
const { locale } = useI18n()

const elLocale = computed(() => locale.value === 'en' ? en : zhCN)

const transitionName = computed(() => {
  return 'page-fade'
})
</script>
