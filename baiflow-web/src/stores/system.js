import { defineStore } from 'pinia'
import { getSetupStatus } from '../api/setup'

/**
 * 系统级状态 — 目前只维护「是否已完成首次初始化」。
 *
 * 首次部署时后端没有管理员，必须先在 /setup 完成初始化才能登录；
 * 该标记在后端由 bf_system_setting.initialized_at 单向控制，写入后永久为 true。
 */
export const useSystemStore = defineStore('system', {
  state: () => ({
    // null = 尚未查询；true/false = 查询结果
    initialized: null
  }),

  actions: {
    /**
     * 确保已获取初始化状态（每次页面加载只查一次）。
     *
     * 查询失败（服务器不可达等）时保守返回 true：不阻断路由，
     * 让既有的「连接超时」流程去提示用户，而不是把人困在向导页。
     */
    async ensureInitialized() {
      if (this.initialized !== null) {
        return this.initialized
      }
      try {
        const { data } = await getSetupStatus()
        this.initialized = data.code === 0 ? data.data.initialized : true
      } catch {
        this.initialized = true
      }
      return this.initialized
    },

    /** 初始化成功后标记本地状态，避免守卫再次跳回向导 */
    markInitialized() {
      this.initialized = true
    }
  }
})
