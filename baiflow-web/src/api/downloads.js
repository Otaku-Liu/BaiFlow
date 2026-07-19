import http from './http'

/** 创建下载任务 */
export function createDownload({ sourceUrl, targetStorageRootId, targetRelativePath }) {
  return http.post('/downloads', { sourceUrl, targetStorageRootId, targetRelativePath })
}

/** 查询下载任务列表 */
export function listDownloads({ status, page = 1, size = 20 } = {}) {
  return http.get('/downloads', { params: { status, page, size } })
}

/** 查询下载任务详情 */
export function getDownload(id) {
  return http.get(`/downloads/${id}`)
}

/** 暂停下载任务 */
export function pauseDownload(id) {
  return http.post(`/downloads/${id}/pause`)
}

/** 恢复下载任务 */
export function resumeDownload(id) {
  return http.post(`/downloads/${id}/resume`)
}

/** 删除下载任务 */
export function removeDownload(id) {
  return http.delete(`/downloads/${id}`)
}
