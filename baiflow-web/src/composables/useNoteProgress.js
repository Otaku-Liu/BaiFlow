import { useI18n } from 'vue-i18n'
import { getNoteProgress, saveNoteProgress } from '../api/notes'

/**
 * 笔记阅读进度 composable — 打开时提示续读、预览滚动防抖保存 SCROLL_PERCENT。
 *
 * 滚动元素由调用方提供（getScrollEl），兼容 Vditor 编辑器内部滚动容器。
 * 续读提示复用父组件的统一 ConfirmDialog。
 *
 * @param {import('vue').Ref<string|null>} noteIdRef 当前笔记 ID 的 ref
 * @param {Function} confirmFn 由 useConfirmDialog 提供的 confirm()
 */
export function useNoteProgress(noteIdRef, confirmFn) {
  const { t } = useI18n()
  let scrollTimer = null

  /** 打开笔记后调用：有历史进度则弹统一确认框，确认后滚动到续读位置 */
  async function maybeResume(getScrollEl) {
    try {
      const { data } = await getNoteProgress(noteIdRef.value)
      const pct = data?.data?.positionValue || 0
      if (pct <= 0.01) return
      try {
        await confirmFn({
          title: t('notes.preview'),
          message: t('notes.resumeTo', { pct: Math.round(pct * 100) }),
          confirmText: t('common.confirm'),
          cancelText: t('common.cancel'),
          type: 'info'
        })
      } catch { return } // 用户取消续读
      const el = getScrollEl()
      if (el) el.scrollTop = pct * el.scrollHeight
    } catch { /* 无进度或查询失败，忽略 */ }
  }

  /** 滚动容器滚动时保存进度（防抖） */
  function saveFromScroll(getScrollEl) {
    const el = getScrollEl()
    if (!el || !noteIdRef.value) return
    const max = el.scrollHeight - el.clientHeight
    if (max <= 0) return
    const pct = Math.min(1, Math.max(0, el.scrollTop / max))
    if (scrollTimer) clearTimeout(scrollTimer)
    scrollTimer = setTimeout(() => saveNoteProgress(noteIdRef.value, pct).catch(() => {}), 800)
  }

  return { maybeResume, saveFromScroll }
}
