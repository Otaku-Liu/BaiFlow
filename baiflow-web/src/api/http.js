import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { ensureMonitor, noteContact, shouldFireTimeout } from '../utils/connectionMonitor'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
    // 登录态首个请求时启动连接超时检测（仅首次生效，不重置基准；见 docs/10-web-connection-timeout.md）
    ensureMonitor()
  }
  // 传递当前语言偏好给后端，用于 i18n 错误消息
  const locale = localStorage.getItem('baiflow_locale') || 'zh-CN'
  config.headers['Accept-Language'] = locale
  return config
})

let authErrorShown = false

http.interceptors.response.use(
  (response) => {
    // 收到任何 HTTP 响应都视为服务器可达，刷新连接计时基准
    noteContact()
    return response
  },
  (error) => {
    const status = error.response?.status
    const authStore = useAuthStore()
    if (error.response) {
      // 服务器可达（任何 4xx/5xx）→ 重置连接计时
      noteContact()
      if (status === 401) {
        // 会话过期 / 被强制下线：清会话并回登录
        if (!authErrorShown) {
          authErrorShown = true
          authStore.clearSession()
          ElMessage.error('登录已过期，请重新登录')
          setTimeout(() => {
            window.location.href = '/login'
          }, 1500)
        }
      } else if (status === 403) {
        // 已登录但无权限：保留登录态，仅提示
        ElMessage.error(error.response?.data?.message || '无权限执行此操作')
      }
    } else if (shouldFireTimeout()) {
      // 网络级失败（无响应）且距上次成功联系 ≥30s：判定服务器连接超时
      // 保留会话 token，App.vue 监听标志后客户端路由跳转登录页（不整页刷新）
      authStore.connectionTimeout = true
      ElMessage.error('服务器连接超时，正在返回登录页')
    }
    return Promise.reject(error)
  }
)

export default http
