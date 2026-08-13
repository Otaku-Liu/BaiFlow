import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { getNoteProgress, saveNoteProgress } from '../api/notes'

/**
 * 笔记阅读进度 composable — 打开时自动恢复到记录位置，滚动防抖保存 SCROLL_PERCENT。
 *
 * 滚动元素由调用方提供（getScrollEl），兼容 Vditor 编辑器内部滚动容器。
 *
 * @param {import('vue').Ref<string|null>} noteIdRef 当前笔记 ID 的 ref
 */
export function useNoteProgress(noteIdRef) {
  const { t } = useI18n()
  let scrollTimer = null

  /** 打开笔记后调用：有历史进度则自动滚动到记录位置，并提示已恢复 */
  async function maybeResume(getScrollEl) {
    try {
      const { data } = await getNoteProgress(noteIdRef.value)
      const pct = data?.data?.positionValue || 0
      if (pct <= 0.01) return
      const el = getScrollEl()
      if (el) {
        // 与保存比例一致：pct 是滚动范围（scrollHeight - clientHeight）的比例
        const max = el.scrollHeight - el.clientHeight
        if (max > 0) el.scrollTop = pct * max
      }
      ElMessage({ message: t('common.resumed'), type: 'success', duration: 2000 })
    } catch { /* 无进度或查询失败，忽略 */ }
  }

  /** 滚动容器滚动时保存进度（防抖；回顶时保存 0，清除历史进度） */
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
