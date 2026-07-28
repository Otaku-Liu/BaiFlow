import http from './http'

/** 分页查询用户列表（仅管理员） */
export function listUsers({ page = 1, size = 20, role, status } = {}) {
  return http.get('/users', { params: { page, size, role, status } })
}

/** 查询单个用户 */
export function getUser(id) {
  return http.get(`/users/${id}`)
}

/** 创建新用户 */
export function createUser({ username, password, displayName, role }) {
  return http.post('/users', { username, password, displayName, role })
}

/** 更新用户信息 */
export function updateUser(id, data) {
  return http.patch(`/users/${id}`, data)
}

/** 批量删除用户（ids 逗号分隔） */
export function batchDeleteUsers(ids) {
  return http.delete(`/users/${ids}`)
}

/** 重置用户密码 */
export function resetPassword(id, newPassword) {
  return http.post(`/users/${id}/reset-password`, { newPassword })
}
