import { onMounted, onUnmounted } from 'vue'
import { useAuthStore } from '../stores/auth'

/**
 * 建立 SSE 长连接并注册事件回调。
 *
 * - 浏览器 EventSource 无法携带 Authorization 头，token 走查询参数
 *   （后端 SessionAuthenticationFilter 支持 `?token=` 鉴权）。
 * - EventSource 断线自动重连；组件卸载时关闭连接。
 *
 * @param handlers 事件名 → 回调函数，如 { NOTE_UPDATED: (e) => { JSON.parse(e.data) } }
 */
export function useSse(handlers = {}) {
  const authStore = useAuthStore()
  let es = null

  function connect() {
    if (!authStore.token) return
    es = new EventSource(`/api/events?token=${encodeURIComponent(authStore.token)}`)
    for (const name of Object.keys(handlers)) {
      if (name === '__onOpen') {
        // 首次连接 / 断线重连成功：断线期间错过的事件不会重放，主动补刷新（如刷新列表）
        es.addEventListener('open', handlers[name])
      } else {
        es.addEventListener(name, handlers[name])
      }
    }
  }

  function close() {
    if (!es) return
    for (const name of Object.keys(handlers)) {
      if (name !== '__onOpen') es.removeEventListener(name, handlers[name])
    }
    es.close()
    es = null
  }

  onMounted(connect)
  onUnmounted(close)

  return { close }
}
