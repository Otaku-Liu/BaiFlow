import { createApp } from 'vue'
import ElementPlus from 'element-plus'
import 'element-plus/dist/index.css'
import App from './App.vue'
import router from './router'
import pinia from './stores'
import { useAuthStore } from './stores/auth'
import './styles.css'

const app = createApp(App)
app.use(pinia)
app.use(router)
app.use(ElementPlus)

// 启动时恢复 token
const authStore = useAuthStore()
authStore.restoreSession()

// 路由守卫：未登录跳转登录页
router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.isLoggedIn) {
    return '/login'
  }
})

app.mount('#app')
