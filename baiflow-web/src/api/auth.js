import http from './http'

export function login(username, password) {
  // X-Device-Type 供服务端建会话（WEB 短期），出现在登录设备列表并可被强制下线
  return http.post('/auth/login', { username, password }, {
    headers: { 'X-Device-Type': 'WEB' }
  })
}

export function getCurrentUser() {
  return http.get('/users/me')
}

/** 更新当前用户的展示名称 */
export function updateProfile(displayName) {
  return http.patch('/users/me/profile', { displayName })
}

/** 上传头像（multipart/form-data） */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/users/me/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 删除当前用户头像 */
export function deleteAvatar() {
  return http.delete('/users/me/avatar')
}

/** 修改密码（需提供旧密码验证） */
export function changePassword(oldPassword, newPassword) {
  return http.post('/auth/change-password', { oldPassword, newPassword })
}

/** 当前用户的登录设备（会话）列表 */
export function listSessions() {
  return http.get('/auth/sessions')
}

/** 当前用户的登录设备列表（含历史 + 在线状态） */
export function listDevices() {
  return http.get('/auth/devices')
}

/** 强制下线指定登录设备（会话） */
export function revokeSession(id) {
  return http.delete(`/auth/sessions/${id}`)
}

/** 删除指定登录设备（撤销其全部会话 + 删除登录历史记录） */
export function deleteDevice(deviceName) {
  return http.delete('/auth/devices', { params: { deviceName } })
}
