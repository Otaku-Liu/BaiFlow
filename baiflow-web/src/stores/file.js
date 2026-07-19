import { defineStore } from 'pinia'

/**
 * 文件浏览状态 — 跟踪当前存储根目录、浏览路径和隐私访问令牌。
 * <p>
 * 隐私访问令牌以 folderId -> token 的映射形式存储，
 * 当浏览到隐私文件夹时会自动携带对应令牌。
 */
export const useFileStore = defineStore('file', {
  state: () => ({
    /** 当前选中的 Storage Root ID */
    currentRootId: '',
    /** 当前面包屑路径（从根到当前文件夹的 FileItemInfo 列表） */
    breadcrumb: [],
    /** 当前目录下的文件列表 */
    items: [],
    /** 文件列表总条数 */
    total: 0,
    /** 当前页码 */
    page: 1,
    /** 每页条数 */
    size: 50,
    /** 隐私访问令牌映射：folderId -> accessToken */
    privacyTokens: {}
  }),

  getters: {
    /** 当前文件夹 ID（breadcrumb 的最后一个，或是 null 代表根目录） */
    currentFolderId(state) {
      return state.breadcrumb.length > 0
        ? state.breadcrumb[state.breadcrumb.length - 1].id
        : null
    },

    /** 当前路径所需的隐私访问令牌 */
    currentPrivacyToken(state) {
      return state.currentFolderId ? state.privacyTokens[state.currentFolderId] : null
    }
  },

  actions: {
    setCurrentRoot(rootId) {
      this.currentRootId = rootId
      this.breadcrumb = []
      this.items = []
      this.total = 0
      this.page = 1
    },

    setBreadcrumb(path) {
      this.breadcrumb = path
    },

    /** 保存隐私文件夹的访问令牌 */
    savePrivacyToken(folderId, token) {
      this.privacyTokens[folderId] = token
    },

    /** 清除指定隐私文件夹的访问令牌 */
    clearPrivacyToken(folderId) {
      delete this.privacyTokens[folderId]
    },

    /** 获取指定文件夹的访问令牌（若无则返回 null） */
    getPrivacyToken(folderId) {
      return this.privacyTokens[folderId] || null
    }
  }
})
