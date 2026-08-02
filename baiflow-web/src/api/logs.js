import http from './http'

/** 分页查询登录日志（仅管理员），支持用户名模糊搜索、登录结果和日期范围筛选 */
export function getLoginLogs({ page = 1, size = 20, username, status, startDate, endDate } = {}) {
  return http.get('/admin/audit-logs/login', { params: { page, size, username, status, startDate, endDate } })
}
