import { ElMessage } from 'element-plus'

let activeMsg = null

/**
 * 顶替式消息：弹出新消息前先关闭上一条，避免 ElMessage 叠加堆积。
 * 返回消息实例，可在别处手动 close。
 */
export function notify(options) {
  if (activeMsg) {
    activeMsg.close()
  }
  activeMsg = ElMessage(options)
  return activeMsg
}

/** 顶替式错误消息 */
export function notifyError(msg) {
  return notify({ message: msg, type: 'error', duration: 2500 })
}

/** 顶替式成功消息 */
export function notifySuccess(msg) {
  return notify({ message: msg, type: 'success', duration: 1500 })
}
