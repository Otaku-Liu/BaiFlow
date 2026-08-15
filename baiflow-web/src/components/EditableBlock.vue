<template>
  <div
    ref="el"
    class="block-text"
    :class="{ 'block-h': heading, 'is-empty': isEmpty }"
    :data-idx="idx"
    :data-placeholder="placeholder"
    contenteditable="true"
    @input="onInput"
    @focus="onFocus"
    @blur="onBlur"
    @keydown="onKeydown"
  ></div>
</template>

<script setup>
import { ref, watch, onMounted } from 'vue'
import { inlineToHtml, htmlToMarkdown } from '../utils/noteBlocks'

/**
 * 所见即所得文本块 — contenteditable 渲染行内 markdown 的最终效果，编辑即预览。
 * 输入/失焦时用 htmlToMarkdown 把 innerHTML 转回 markdown 回写 b.text；
 * 挂载与外部内容变化时用 inlineToHtml 重新渲染（输入自同步期间不重渲染，避免光标跳动）。
 */
const props = defineProps({
  modelValue: { type: String, default: '' },
  placeholder: { type: String, default: '' },
  heading: { type: Boolean, default: false },
  idx: { type: Number, default: -1 }
})
const emit = defineEmits(['update:modelValue', 'focus', 'blur', 'deleteBlock'])

const el = ref(null)
const isEmpty = ref(true)

function refreshPlaceholder() {
  isEmpty.value = !el.value?.textContent?.trim()
}

/** 用渲染后的 HTML 填充（挂载/外部内容变化时） */
function renderHtml() {
  if (!el.value) return
  el.value.innerHTML = inlineToHtml(props.modelValue || '')
  refreshPlaceholder()
}

onMounted(renderHtml)

// 外部 modelValue 变化（切笔记/重载）：当前 DOM 与目标不一致才重渲染（输入自同步时跳过）
watch(() => props.modelValue, (v) => {
  if (!el.value) return
  if (htmlToMarkdown(el.value.innerHTML) === (v || '')) return
  renderHtml()
})

function onInput() {
  const md = htmlToMarkdown(el.value.innerHTML)
  if (md !== props.modelValue) {
    emit('update:modelValue', md)
  }
  refreshPlaceholder()
}

function onFocus(e) {
  emit('focus', e)
}

function onBlur(e) {
  onInput()   // 失焦前做最终同步（防止某次输入未触发 input 事件）
  emit('blur', e)
}

function onKeydown(e) {
  // 软换行：Enter / Shift+Enter 都只换行（插入 <br>），不新建块
  if (e.key === 'Enter') {
    e.preventDefault()
    document.execCommand('insertLineBreak', false, null)
    return
  }
  // 空块 + Backspace → 删除本块
  if (e.key === 'Backspace' && !el.value.textContent.trim()) {
    e.preventDefault()
    emit('deleteBlock')
  }
}
</script>
