import { ref, onUnmounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getProgress, saveProgress } from '../api/files'

/**
 * 播放/阅读进度管理 composable。
 *
 * 参数为 ref/computed：抽屉常驻挂载时 fileItem 初始为 null，打开/切换文件时通过
 * fileIdRef 响应变化，本函数只需在 setup 顶层调用一次（不重建），避免在 watcher 里
 * 调用组合函数（useI18n/onUnmounted 必须在 setup 顶层）。
 *
 * @param {import('vue').Ref<string|undefined>} fileIdRef      文件 ID
 * @param {import('vue').Ref<string>} positionTypeRef 进度类型：SECONDS | PAGE | SCROLL_PERCENT
 */
export function usePlaybackProgress(fileIdRef, positionTypeRef) {
  const { t } = useI18n()
  const savedPosition = ref(null)
  let autoSaveTimer = null

  /** 从服务端查询进度，有则返回，无则 null */
  async function checkProgress() {
    if (!fileIdRef.value) return null
    try {
      const { data } = await getProgress(fileIdRef.value)
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
   * 有历史进度则自动恢复到记录位置，并提示「已恢复到上次观看位置」（不再弹跳转确认）。
   * @param {function} onJump 恢复位置的回调 (position) => void
   */
  async function promptResume(onJump) {
    const progress = await checkProgress()
    if (!progress || progress.positionValue <= 0) return
    onJump(progress.positionValue)
    ElMessage({
      message: t('common.resumed'),
      type: 'success',
      duration: 2000
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
      if (val != null && fileIdRef.value) {
        saveProgress(fileIdRef.value, positionTypeRef.value, val).catch(() => {})
      }
    }, 10000)
  }

  /** 立即保存当前进度 */
  function saveNow(positionValue) {
    if (positionValue != null && fileIdRef.value) {
      saveProgress(fileIdRef.value, positionTypeRef.value, positionValue).catch(() => {})
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
