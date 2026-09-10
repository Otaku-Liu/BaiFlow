import { reactive } from 'vue'

/**
 * 确认弹窗 composable — 提供 promise 式 API，搭配 ConfirmDialog 组件使用。
 *
 * 用法：
 *   const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()
 *   // 在 template 中：<ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />
 *   // 在逻辑中：await confirm({ title: '...', message: '...', confirmText: '删除', type: 'warning' })
 *
 * 取消/关闭时 promise 以字符串 reject：默认恒为 `'cancel'`；传 `distinguishClose: true` 时，
 * 点「取消按钮」reject `'cancel'`、点 X / ESC / 遮罩 reject `'close'`（用于两者语义不同的场景）。
 */
export function useConfirmDialog() {
  const state = reactive({
    visible: false,
    title: '',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    type: 'warning',
    distinguishClose: false,
    resolve: null,
    reject: null
  })

  function confirm(options = {}) {
    return new Promise((resolve, reject) => {
      state.title = options.title || '确认'
      state.message = options.message || ''
      state.confirmText = options.confirmText || '确认'
      state.cancelText = options.cancelText || '取消'
      state.type = options.type || 'warning'
      state.distinguishClose = !!options.distinguishClose
      state.resolve = resolve
      state.reject = reject
      state.visible = true
    })
  }

  function onConfirm() {
    state.visible = false
    state.resolve?.()
    state.resolve = null
    state.reject = null
  }

  /** @param {'cancel'|'close'} reason 「取消按钮」还是「关闭」 */
  function onCancel(reason = 'cancel') {
    state.visible = false
    state.reject?.(reason)
    state.resolve = null
    state.reject = null
  }

  return {
    /** 直接 v-bind 到 ConfirmDialog 组件 */
    bindings: state,
    confirm,
    onConfirm,
    onCancel
  }
}
