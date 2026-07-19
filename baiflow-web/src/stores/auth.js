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
    },

    restoreSession() {
      const saved = localStorage.getItem('baiflow_token')
      if (saved) this.token = saved
    },

    clearSession() {
      this.token = ''
      this.user = null
      localStorage.removeItem('baiflow_token')
    }
  }
})
