import { ElMessage } from 'element-plus'
import i18n from '../locales'

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

/** 网络级失败（连不上服务器/超时/断网，无 HTTP 响应）判定 */
export function isNetworkError(e) {
  return !e?.response
}

/**
 * 请求错误统一提示：网络级失败 → 统一「无法连接服务器」；否则显示服务端消息或业务兜底文案。
 * 视图 catch 里用它替代「e.response?.data?.message || t('xxx.failed')」，保证后端没跑时提示一致。
 */
export function notifyRequestError(e, fallbackMsg) {
  if (isNetworkError(e)) {
    return notifyError(i18n.global.t('common.cannotReachServer'))
  }
  return notifyError(e?.response?.data?.message || fallbackMsg)
}
