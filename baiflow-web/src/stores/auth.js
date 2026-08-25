import { defineStore } from 'pinia'
import { startMonitor, resetMonitor } from '../utils/connectionMonitor'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    user: null,
    // 服务器连接超时态：保留 token 但临时展示登录页（见 docs/10-web-connection-timeout.md）
    connectionTimeout: false
  }),

  getters: {
    isLoggedIn(state) {
      return !!state.token
    },
    isAdmin(state) {
      return state.user?.role === 'ADMIN'
    }
  },

  actions: {
    setSession(token, user) {
      this.token = token
      this.user = user
      this.connectionTimeout = false
      // 清超时标志的同时重启检测（登录/重连即进入界面，重置 30s 基准与去重标志）
      startMonitor()
      localStorage.setItem('baiflow_token', token)
      if (user) {
        localStorage.setItem('baiflow_user', JSON.stringify(user))
      }
    },

    /** 更新当前用户字段并同步 localStorage（头像/展示名等；修复刷新后还原） */
    updateUser(patch) {
      if (this.user && patch) {
        this.user = { ...this.user, ...patch }
        localStorage.setItem('baiflow_user', JSON.stringify(this.user))
      }
    },

    restoreSession() {
      const savedToken = localStorage.getItem('baiflow_token')
      if (savedToken) {
        this.token = savedToken
        const savedUser = localStorage.getItem('baiflow_user')
        if (savedUser) {
          try {
            this.user = JSON.parse(savedUser)
          } catch {
            localStorage.removeItem('baiflow_user')
          }
        }
      }
    },

    clearSession() {
      this.token = ''
      this.user = null
      this.connectionTimeout = false
      localStorage.removeItem('baiflow_token')
      localStorage.removeItem('baiflow_user')
      // 登出 / 会话失效：停止连接超时检测
      resetMonitor()
    }
  }
})
