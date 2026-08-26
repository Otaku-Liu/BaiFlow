/**
 * 连接监控（服务器连接超时检测）。
 *
 * 检测方式：仅依赖实际请求失败。以「最后一次成功联系」为基准——收到任何 HTTP 响应
 * （含 4xx/5xx）都视为服务器可达并刷新基准；某次请求发生网络级失败（无响应）时，
 * 若距上次成功联系已 ≥ THRESHOLD_MS 则判定超时。
 *
 * 设计说明见 docs/04-frontend.md「401 与网络级失败（服务器连接超时）」：
 * - 计时从进入界面（首次登录态请求）起算；
 * - 阈值前单次失败静默，不打扰用户；
 * - timeoutFired 去重，防并发失败重复触发。
 */

const THRESHOLD_MS = 30000 // 距上次成功联系 ≥30s 判定超时

let started = false // 检测是否已启动（登录态）
let lastContactAt = 0 // 最近一次「成功联系」或进入界面的时刻
let timeoutFired = false // 本轮已触发过超时（去重）

/** 启动检测并重置基准（进入界面 / 重连成功 / 重新登录时调用） */
export function startMonitor() {
  started = true
  lastContactAt = Date.now()
  timeoutFired = false
}

/**
 * 确保检测已启动，但**不**重置基准/去重标志。
 * 供请求拦截器调用：登录态首个请求时启动，后续请求不再拨动 lastContactAt——
 * 否则每次请求都会把基准拨到「现在」，30s 判定永远无法触发（见 docs/04-frontend.md「401 与网络级失败」）。
 */
export function ensureMonitor() {
  if (!started) startMonitor()
}

/** 记录一次成功联系（收到任何 HTTP 响应），刷新基准 */
export function noteContact() {
  if (!started) return
  lastContactAt = Date.now()
}

/** 停止检测（登出 / 清会话时调用） */
export function resetMonitor() {
  started = false
  lastContactAt = 0
  timeoutFired = false
}

/**
 * 网络级失败时判断是否应触发超时。
 * @returns {boolean} true 表示判定超时（本轮只触发一次）
 */
export function shouldFireTimeout() {
  if (!started || timeoutFired) return false
  if (Date.now() - lastContactAt >= THRESHOLD_MS) {
    timeoutFired = true
    return true
  }
  return false
}
