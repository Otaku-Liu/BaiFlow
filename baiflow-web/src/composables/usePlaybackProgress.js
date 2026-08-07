import { ref, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getProgress, saveProgress } from '../api/files'

/**
 * 播放/阅读进度管理 composable。
 *
 * @param {string} fileId     文件 ID
 * @param {string} positionType 进度类型：SECONDS | PAGE | SCROLL_PERCENT
 * @param {import('vue').Ref} targetRef  视频/音频的 template ref，或带 scrollTop 的容器 ref
 */
export function usePlaybackProgress(fileId, positionType) {
  const savedPosition = ref(null)
  let autoSaveTimer = null

  /** 从服务端查询进度，有则返回，无则 null */
  async function checkProgress() {
    try {
      const { data } = await getProgress(fileId)
      if (data.code === 0 && data.data) {
        savedPosition.value = data.data.positionValue
        return {
          positionType: data.data.positionType,
          positionValue: data.data.positionValue
        }
      }
    } catch { /* ignore */ }
    return null
  }

  /**
   * 如果有历史进度，弹出 Toast 提示用户跳转。
   * @param {function} onJump 用户点击「跳转」时的回调 (position) => void
   */
  async function promptResume(onJump) {
    const progress = await checkProgress()
    if (!progress || progress.positionValue <= 0) return

    let label = ''
    if (progress.positionType === 'PAGE') {
      label = `上次看到第 ${Math.round(progress.positionValue)} 页`
    } else if (progress.positionType === 'SCROLL_PERCENT') {
      label = `上次看到 ${Math.round(progress.positionValue * 100)}%`
    } else {
      const m = Math.floor(progress.positionValue / 60)
      const s = Math.floor(progress.positionValue % 60)
      label = `上次看到 ${m}:${String(s).padStart(2, '0')}`
    }

    ElMessage({
      message: label,
      type: 'info',
      duration: 5000,
      showClose: true,
      onClick: () => onJump(progress.positionValue)
    })
  }

  /**
   * 启动自动保存（每 10 秒）。
   * @param {function} getCurrentPosition 返回当前位置值的函数
   */
  function startAutoSave(getCurrentPosition) {
    stopAutoSave()
    autoSaveTimer = setInterval(() => {
      const val = getCurrentPosition()
      if (val != null) {
        saveProgress(fileId, positionType, val).catch(() => {})
      }
    }, 10000)
  }

  /** 立即保存当前进度 */
  function saveNow(positionValue) {
    if (positionValue != null) {
      saveProgress(fileId, positionType, positionValue).catch(() => {})
    }
  }

  function stopAutoSave() {
    if (autoSaveTimer) {
      clearInterval(autoSaveTimer)
      autoSaveTimer = null
    }
  }

  onUnmounted(() => stopAutoSave())

  return { checkProgress, promptResume, startAutoSave, saveNow, stopAutoSave, savedPosition }
}
