import axios from 'axios'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'

const http = axios.create({
  baseURL: '/api',
  timeout: 15000
})

http.interceptors.request.use((config) => {
  const authStore = useAuthStore()
  if (authStore.token) {
    config.headers.Authorization = `Bearer ${authStore.token}`
  }
  // 传递当前语言偏好给后端，用于 i18n 错误消息
  const locale = localStorage.getItem('baiflow_locale') || 'zh-CN'
  config.headers['Accept-Language'] = locale
  return config
})

let authErrorShown = false

http.interceptors.response.use(
  (response) => response,
  (error) => {
    const status = error.response?.status
    const authStore = useAuthStore()
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
    return Promise.reject(error)
  }
)

export default http
