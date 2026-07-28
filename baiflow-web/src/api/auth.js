import http from './http'

export function login(username, password) {
  return http.post('/auth/login', { username, password })
}

export function getCurrentUser() {
  return http.get('/auth/me')
}

/** 更新当前用户的展示名称 */
export function updateProfile(displayName) {
  return http.patch('/auth/profile', { displayName })
}

/** 上传头像（multipart/form-data） */
export function uploadAvatar(file) {
  const formData = new FormData()
  formData.append('file', file)
  return http.post('/auth/avatar', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

/** 修改密码（需提供旧密码验证） */
export function changePassword(oldPassword, newPassword) {
  return http.post('/auth/change-password', { oldPassword, newPassword })
}
