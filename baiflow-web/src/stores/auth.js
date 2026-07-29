import { defineStore } from 'pinia'

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: '',
    user: null
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
      localStorage.setItem('baiflow_token', token)
      if (user) {
        localStorage.setItem('baiflow_user', JSON.stringify(user))
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
      localStorage.removeItem('baiflow_token')
      localStorage.removeItem('baiflow_user')
    }
  }
})
