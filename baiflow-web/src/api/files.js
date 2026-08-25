import http from './http'
import { useAuthStore } from '../stores/auth'

/** 文件列表查询参数 */
export function listFiles({ storageRootId, parentId, page = 1, size = 50, viewUserId }, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  const params = { storageRootId, page, size }
  if (parentId) params.parentId = parentId
  if (viewUserId) params.viewUserId = viewUserId
  return http.get('/files', { params, headers })
}

/** 上传文件 */
export function uploadFile({ storageRootId, parentId, file, viewUserId }, privacyToken) {
  const headers = { 'Content-Type': 'multipart/form-data' }
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  const form = new FormData()
  form.append('file', file)
  if (parentId) form.append('parentId', parentId)
  const params = { storageRootId }
  if (viewUserId) params.viewUserId = viewUserId
  return http.post('/files/upload', form, {
    params,
    headers,
    onUploadProgress: (e) => {
      // 进度回调：可在组件中按需监听
      if (e.total) console.log(`上传进度: ${Math.round((e.loaded * 100) / e.total)}%`)
    }
  })
}

/** 下载文件 */
export function downloadFile(fileId, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.get(`/files/download/${fileId}`, {
    responseType: 'blob',
    headers
  })
}

/** 分页查询文件的下载记录（本人文件；管理员可查任意） */
export function getFileDownloads(fileId, page = 1, size = 20) {
  return http.get(`/files/${fileId}/downloads`, { params: { page, size } })
}

/** 创建文件夹 */
export function createFolder({ storageRootId, parentId, name, viewUserId }, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  const params = {}
  if (viewUserId) params.viewUserId = viewUserId
  return http.post('/files/folders', { storageRootId, parentId, name }, { headers, params })
}

/** 重命名文件/文件夹 */
export function renameFile(id, newName, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.patch(`/files/${id}/rename`, { newName }, { headers })
}

/** 移动文件/文件夹 */
export function moveFile(id, targetStorageRootId, targetParentId, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.patch(`/files/${id}/move`,
    { targetStorageRootId, targetParentId: targetParentId || '' },
    { headers })
}

/** 删除文件/文件夹 */
export function deleteFile(id, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.delete(`/files/${id}`, { headers })
}

/** 设置隐私密码（隐私空间首访设密；已设置则拒绝） */
export function setPrivacy(id, password) {
  return http.post(`/files/${id}/privacy`, { password })
}

/** 验证隐私密码，返回 accessToken */
export function verifyPrivacy(id, password) {
  return http.post(`/files/${id}/privacy/verify`, { password })
}

/** 获取存储根目录列表（仅 ACTIVE） */
export function listStorageRoots() {
  return http.get('/storage-roots/active')
}

/** 获取预览文件流（inline 模式），返回预览 URL 供 <img>/<video>/<iframe> 使用 */
export function getPreviewUrl(id, privacyToken) {
  const authStore = useAuthStore()
  const params = new URLSearchParams()
  if (authStore.token) params.append('token', authStore.token)
  if (privacyToken) params.append('X-Privacy-Access-Token', privacyToken)
  const base = '/api/files'
  const query = params.toString()
  return `${base}/${encodeURIComponent(id)}/preview${query ? '?' + query : ''}`
}

/** 通过 Axios 获取预览文件 Blob（带 auth 头），返回 Object URL */
export async function fetchPreviewBlob(id, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  const resp = await http.get(`/files/${id}/preview`, { headers, responseType: 'blob' })
  return URL.createObjectURL(resp.data)
}

/** 获取文件内容文本（用于文本/代码预览） */
export function fetchPreviewContent(id, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.get(`/files/${id}/preview`, { headers, responseType: 'text' })
}

/** 查询播放/阅读进度 */
export function getProgress(id) {
  return http.get(`/files/${id}/progress`)
}

/** 保存播放/阅读进度 */
export function saveProgress(id, positionType, positionValue) {
  return http.put(`/files/${id}/progress`, { positionType, positionValue })
}
