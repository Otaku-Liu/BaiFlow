import { reactive } from 'vue'

/**
 * 确认弹窗 composable — 提供 promise 式 API，搭配 ConfirmDialog 组件使用。
 *
 * 用法：
 *   const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()
 *   // 在 template 中：<ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />
 *   // 在逻辑中：await confirm({ title: '...', message: '...', confirmText: '删除', type: 'warning' })
 */
export function useConfirmDialog() {
  const state = reactive({
    visible: false,
    title: '',
    message: '',
    confirmText: '确认',
    cancelText: '取消',
    type: 'warning',
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

  function onCancel() {
    state.visible = false
    state.reject?.('cancel')
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
