import http from './http'

/** 创建分享链接，返回包含 rawToken 的链接信息 */
export function createShare({ targetFileItemId, shareType, accessMode, expiresAt, maxViews, maxDownloads, extractionCode }) {
  return http.post('/shares', { targetFileItemId, shareType, accessMode, expiresAt, maxViews, maxDownloads, extractionCode })
}

/** 分享列表 */
export function listShares({ status, page = 1, size = 20 } = {}) {
  return http.get('/shares', { params: { status, page, size } })
}

/** 分享详情 */
export function getShare(id) { return http.get(`/shares/${id}`) }

/** 更新分享 */
export function updateShare(id, data) { return http.patch(`/shares/${id}`, data) }

/** 撤销分享 */
export function revokeShare(id) { return http.delete(`/shares/${id}`) }

/**
 * 构建分享链接的公开 URL
 * @param {string} token - 创建分享后返回的 rawToken
 * @param {string} baseUrl - 服务器根地址
 * @returns {string} 分享 URL
 */
export function buildShareUrl(token, baseUrl) {
  baseUrl = (baseUrl || window.location.origin).replace(/\/+$/, '')
  return `${baseUrl}/s/${token}`
}
