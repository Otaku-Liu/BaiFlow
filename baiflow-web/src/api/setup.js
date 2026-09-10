import http from './http'

/** 查询系统是否已完成首次初始化（公开接口，无需登录） */
export function getSetupStatus() {
  return http.get('/setup/status')
}

/**
 * 首次初始化：创建第一个管理员并直接返回登录会话（公开接口，需携带初始化令牌）。
 * 系统已初始化时服务端恒返回 SETUP_ALREADY_INITIALIZED。
 */
export function initializeSystem(payload) {
  // X-Device-Type 供服务端建会话（WEB 短期），与正常登录一致
  return http.post('/setup/init', payload, {
    headers: { 'X-Device-Type': 'WEB' }
  })
}
