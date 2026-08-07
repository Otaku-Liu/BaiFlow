import http from './http'

/** 服务健康检查（公开免认证，用于「重新连接」探测） */
export function getHealth() {
  return http.get('/health')
}
