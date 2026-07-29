<template>
  <router-view v-slot="{ Component, route }">
    <transition :name="transitionName" mode="out-in">
      <component :is="Component" :key="route.path" />
    </transition>
  </router-view>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()

const transitionName = computed(() => {
  // 登录页和其他页面之间用 fade，页面内切换用 slide
  const fromLogin = router.currentRoute.value.path === '/login'
  // 返回默认的 fade 过渡
  return 'page-fade'
})
</script>
