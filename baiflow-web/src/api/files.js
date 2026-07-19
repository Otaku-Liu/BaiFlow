import http from './http'

/** 文件列表查询参数 */
export function listFiles({ storageRootId, parentId, page = 1, size = 50 }, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.get('/files', {
    params: { storageRootId, parentId, page, size },
    headers
  })
}

/** 上传文件 */
export function uploadFile({ storageRootId, parentId, file }, privacyToken) {
  const headers = { 'Content-Type': 'multipart/form-data' }
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  const form = new FormData()
  form.append('file', file)
  if (parentId) form.append('parentId', parentId)
  return http.post('/files/upload', form, {
    params: { storageRootId },
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

/** 创建文件夹 */
export function createFolder({ storageRootId, parentId, name }, privacyToken) {
  const headers = {}
  if (privacyToken) headers['X-Privacy-Access-Token'] = privacyToken
  return http.post('/files/folders', { storageRootId, parentId, name }, { headers })
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

/** 设置隐私密码 */
export function setPrivacy(id, password) {
  return http.post(`/files/${id}/privacy`, { password })
}

/** 取消隐私保护 */
export function removePrivacy(id) {
  return http.delete(`/files/${id}/privacy`)
}

/** 验证隐私密码，返回 accessToken */
export function verifyPrivacy(id, password) {
  return http.post(`/files/${id}/privacy/verify`, { password })
}

/** 获取存储根目录列表（仅 ACTIVE） */
export function listStorageRoots() {
  return http.get('/storage-roots/active')
}
