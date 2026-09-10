import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import { useSystemStore } from '../stores/system'
import LoginView from '../views/LoginView.vue'
import SetupView from '../views/SetupView.vue'
import HomeView from '../views/HomeView.vue'

const routes = [
  {
    path: '/setup',
    name: 'setup',
    component: SetupView
  },
  {
    path: '/login',
    name: 'login',
    component: LoginView
  },
  {
    path: '/',
    name: 'home',
    component: HomeView,
    meta: { requiresAuth: true }
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach(async (to) => {
  const authStore = useAuthStore()
  const systemStore = useSystemStore()

  // 首次部署：系统还没有管理员时，任何路径都强制走初始化向导
  // （服务器不可达时 ensureInitialized 返回 true，不阻断导航，交给连接超时流程提示）
  const initialized = await systemStore.ensureInitialized()
  if (!initialized) {
    return to.path === '/setup' ? true : '/setup'
  }
  if (to.path === '/setup') {
    // 已初始化：向导入口关闭（后端同步恒返回 403）
    return authStore.isLoggedIn ? '/' : '/login'
  }

  if (to.meta.requiresAuth && !authStore.isLoggedIn) {
    return '/login'
  }
  if (to.path === '/login' && authStore.isLoggedIn && !authStore.connectionTimeout) {
    // 连接超时态（保留 token）时放行登录页，便于展示「重新连接」
    return '/'
  }
  return true
})

export default router
