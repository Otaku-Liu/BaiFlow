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
    if (status === 401 || status === 403) {
      const authStore = useAuthStore()
      // 防止多个并发请求同时弹窗和跳转
      if (!authErrorShown) {
        authErrorShown = true
        authStore.clearSession()
        const msg = status === 403 ? '权限不足，请重新登录' : '登录已过期，请重新登录'
        ElMessage.error(msg)
        setTimeout(() => {
          window.location.href = '/login'
        }, 1500)
      }
    }
    return Promise.reject(error)
  }
)

export default http
