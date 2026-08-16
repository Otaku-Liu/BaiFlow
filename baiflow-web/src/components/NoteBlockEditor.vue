<template>
  <div class="block-editor">
    <!-- 顶部工具栏（固定）：添加块 + 插入媒体 -->
    <div class="block-toolbar">
      <el-button size="small" @click="addBlockOf('p')">文本</el-button>
      <el-button size="small" @click="addBlockOf('h')">标题</el-button>
      <span class="toolbar-divider" />
      <input ref="imageInput" type="file" accept="image/*" style="display:none" @change="onImageSelected" />
      <el-button size="small" @click="openImagePicker"><el-icon><Picture /></el-icon> 图片</el-button>
      <input ref="audioInput" type="file" accept="audio/*" style="display:none" @change="onAudioSelected" />
      <el-button size="small" @click="openAudioPicker"><el-icon><Microphone /></el-icon> 音频</el-button>
    </div>

    <!-- 浮动格式栏：焦点在文本块时显示在该块上方偏左（mousedown.prevent 保持选中不丢失） -->
    <div v-show="showFormatBar" class="format-bar" :style="formatBarStyle">
      <el-button size="small" text @mousedown.prevent @click="applyInlineFormat('bold')"><b>B</b></el-button>
      <el-button size="small" text @mousedown.prevent @click="applyInlineFormat('italic')"><i>I</i></el-button>
      <el-button size="small" text @mousedown.prevent @click="applyInlineFormat('strikeThrough')"><s>S</s></el-button>
      <el-button size="small" text @mousedown.prevent @click="applyInlineFormat('underline')"><u>U</u></el-button>
    </div>

    <!-- 块列表（TransitionGroup：实时重排时块位移动画流畅） -->
    <div ref="listEl" class="block-list">
      <TransitionGroup tag="div" name="list" class="block-list-items">
      <div v-for="(b, idx) in modelValue" :key="b._id" class="block" :class="'block-' + b.type">
        <!-- 悬停横线 + ＋：在上方插入（点击＋选择类型） -->
        <div class="block-insert-line">
          <el-dropdown trigger="click" @command="(cmd) => insertAboveBlock(idx, cmd)">
            <span class="block-insert-plus" title="在上方插入">＋</span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="p">文本</el-dropdown-item>
                <el-dropdown-item command="h">标题</el-dropdown-item>
                <el-dropdown-item command="image" divided>图片</el-dropdown-item>
                <el-dropdown-item command="audio">音频</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        <!-- 拖动排序手柄（块左侧中间，悬停显示，宽度不随块类型变化） -->
        <span class="block-drag" draggable="true" @dragstart="onDragStart(idx, $event)" @dragend="clearDrag" title="拖动排序">⋮⋮⋮</span>
        <!-- 删除按钮（块右上角，悬停出现） -->
        <button class="block-del" title="删除" @click="remove(idx)">×</button>

        <div v-if="['p', 'h'].includes(b.type)" class="block-text-row">
          <el-dropdown trigger="click" @command="(cmd) => switchType(b, cmd)">
            <button class="block-type" title="切换块类型">{{ typeLabel(b) }}</button>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="p">文本</el-dropdown-item>
                <el-dropdown-item command="h">标题</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <EditableBlock
            :model-value="b.text"
            :idx="idx"
            :heading="b.type === 'h'"
            :placeholder="placeholder(b)"
            @update:model-value="(v) => onTextInput(idx, b, v)"
            @focus="onBlockFocus"
            @blur="onBlockBlur"
            @delete-block="() => onDeleteBlock(b)"
          />
        </div>
        <div v-else-if="b.type === 'image' || b.type === 'audio'" class="block-media-row">
          <div v-if="b.type === 'image'" class="block-media-wrap">
            <img :src="withToken(b.url)" :alt="b.alt" class="block-img" />
          </div>
          <div v-else class="block-media-wrap block-audio-wrap">
            <audio
              controls
              preload="metadata"
              :src="withToken(b.url)"
              class="block-audio"
              @error="onAudioError(b)"
            />
            <span v-if="b.audioFailed" class="audio-fallback">⚠ 音频无法加载</span>
          </div>
        </div>
      </div>
      </TransitionGroup>
      <!-- 列表末尾留白：拖到下方即插到末尾 -->
      <div class="block-drop-end"></div>
    </div>
  </div>
</template>

<script setup>
import { ref, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Picture, Microphone } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { uploadNoteMedia } from '../api/notes'
import { notifyError } from '../utils/notify'
import { htmlToMarkdown } from '../utils/noteBlocks'
import EditableBlock from './EditableBlock.vue'

const props = defineProps({
  /** 当前笔记的块列表（父组件持有，编辑器就地变更并 emit change） */
  modelValue: { type: Array, default: () => [] }
})
const emit = defineEmits(['change'])

const { t } = useI18n()
const authStore = useAuthStore()
const listEl = ref(null)
const imageInput = ref(null)
const audioInput = ref(null)

// 稳定块 ID（作为 v-for key，避免实时重排时按索引 key 导致 DOM 错乱/块消失）
let blockIdCounter = 1
function ensureBlockId(b) {
  if (b._id == null) {
    b._id = 'b' + (blockIdCounter++)
  }
  return b._id
}
watch(() => props.modelValue, (arr) => {
  ;(arr || []).forEach(ensureBlockId)
}, { immediate: true })

/** 媒体 URL 加会话 token（<img>/<audio> 无法带 Authorization 头，走 ?token= 兜底鉴权） */
function withToken(url) {
  if (!authStore.token) return url
  return url + (url.includes('?') ? '&' : '?') + 'token=' + encodeURIComponent(authStore.token)
}

function placeholder(b) {
  if (b.type === 'p') return t('notes.textPlaceholder')
  if (b.type === 'h') return t('notes.headingPlaceholder')
  return ''
}

function onTextInput(idx, b, val) {
  b.text = val
  emit('change')
}

// ---- 内联格式（B/I/U/S）：浮动格式栏跟随焦点文本块，execCommand 就地格式化 ----
const showFormatBar = ref(false)
const formatBarStyle = ref({})
let activeBlockEl = null

function onBlockFocus(e) {
  activeBlockEl = e.target
  positionFormatBar(e.target)
  showFormatBar.value = true
}

function onBlockBlur() {
  // 延时隐藏：点击格式栏按钮（mousedown.prevent）不触发 blur，此处兜底
  setTimeout(() => {
    if (activeBlockEl && document.activeElement !== activeBlockEl) {
      showFormatBar.value = false
    }
  }, 150)
}

/** 格式栏定位到聚焦块上方偏左 */
function positionFormatBar(ta) {
  const rect = ta.getBoundingClientRect()
  formatBarStyle.value = {
    position: 'fixed',
    left: Math.max(4, rect.left) + 'px',
    top: Math.max(4, rect.top - 34) + 'px'
  }
}

/** 对聚焦块当前选中文字应用格式（contenteditable execCommand；同步回 markdown 由 input 事件驱动） */
function applyInlineFormat(cmd) {
  const el = activeBlockEl
  if (!el) return
  el.focus()
  document.execCommand(cmd, false, null)
  // execCommand 触发 input → EditableBlock 已同步 b.text；此处再兜底同步一次（幂等）
  const idx = Number(el.dataset.idx)
  const b = props.modelValue[idx]
  if (b && b.text !== htmlToMarkdown(el.innerHTML)) {
    b.text = htmlToMarkdown(el.innerHTML)
    emit('change')
  }
}

function addBlockOf(cmd) {
  props.modelValue.push(blockFromCmd(cmd, ''))
  emit('change')
  focusLastText()
}

/** 追加媒体块（不再自动补文本块，文本由用户手动插入） */
function insertMediaBlock(block) {
  ensureBlockId(block)
  props.modelValue.push(block)
  emit('change')
}

/** 聚焦最后一个可编辑块（新文本块总是最后一个块） */
function focusLastText() {
  focusBlockText(props.modelValue.length - 1)
}

/** 聚焦指定块索引附近的可编辑块（跳过媒体块，向两边找最近的文本块） */
function focusBlockText(idx) {
  nextTick(() => {
    const n = props.modelValue.length
    for (let d = 0; d < n; d++) {
      for (const cand of [idx - d, idx + d]) {
        if (cand < 0 || cand >= n) continue
        const el = listEl.value?.querySelector(`.block-text[data-idx="${cand}"]`)
        if (el) {
          el.focus()
          return
        }
      }
    }
  })
}

/** 空块 + Backspace → 删除本块并聚焦邻近文本块（含最上方首块） */
function onDeleteBlock(b) {
  const idx = props.modelValue.indexOf(b)
  if (idx < 0) return
  props.modelValue.splice(idx, 1)
  if (props.modelValue.length === 0) {
    props.modelValue.push({ type: 'p', text: '' })
  }
  emit('change')
  focusBlockText(Math.min(idx, props.modelValue.length - 1))
}

/** 媒体「在上方插入」时的目标索引（null = 追加到末尾） */
let pendingInsertIdx = null

/** 在指定块上方插入：文本类直接插，图片/音频打开选择器后插到该位置 */
function insertAboveBlock(idx, cmd) {
  if (cmd === 'image') {
    pendingInsertIdx = idx
    imageInput.value?.click()
    return
  }
  if (cmd === 'audio') {
    pendingInsertIdx = idx
    audioInput.value?.click()
    return
  }
  props.modelValue.splice(idx, 0, blockFromCmd(cmd, ''))
  emit('change')
  focusBlockText(idx)
}

/** 块类型 → 新块（cmd 同 addBlockOf，text 为初始内容；带稳定 _id 作 key） */
function blockFromCmd(cmd, text) {
  const b = (() => {
    if (cmd === 'h') return { type: 'h', level: 1, text }
    return { type: 'p', text }
  })()
  ensureBlockId(b)
  return b
}

/** 块类型标识文案（标题不再区分 H1/H2/H3，统一「标题」） */
function typeLabel(b) {
  return b.type === 'h' ? '标题' : '文本'
}

/** 音频加载失败：标记并在块上显示回退文案 */
function onAudioError(b) {
  b.audioFailed = true
  console.error('[note-media] audio load error, url=', b.url, 'tokenized=', withToken(b.url))
}

// ---- 拖动排序（实时预览重排：源块移除、下方补位，悬停目标即预览落位） ----
let dragBlock = null      // 正在拖动的块数据（拖动期间从数组中取出）
let dragFromIdx = null    // 源块原位置（取消时还原）
let dropped = false       // 是否已放下（放下提交，否则还原）
let dragClone = null      // 跟随鼠标的块浮层
let dragSourceEl = null   // 源块元素（拖动时淡出）

function onDragStart(idx, e) {
  dragFromIdx = idx
  dragBlock = props.modelValue[idx]
  dropped = false
  props.modelValue.splice(idx, 1)   // 源块消失，下方块补位
  // 隐藏原生拖影，用自定义浮层跟随鼠标
  const transparent = new Image()
  transparent.src = 'data:image/gif;base64,R0lGODlhAQABAIAAAAUEBA==' // 1x1 透明
  e.dataTransfer.setDragImage(transparent, 0, 0)
  const blockEl = e.target.closest('.block')
  if (blockEl) {
    dragSourceEl = blockEl
    blockEl.classList.add('dragging')
    dragClone = blockEl.cloneNode(true)
    dragClone.classList.add('block-drag-clone')
    const rect = blockEl.getBoundingClientRect()
    dragClone.style.width = rect.width + 'px'
    dragClone.style.left = rect.left + 'px'   // X 固定（源块位置），只跟 Y
    dragClone.style.top = (e.clientY - 16) + 'px'
    document.body.appendChild(dragClone)
  }
  // 文档级监听：整个浏览器范围内拖动都能预览落位
  document.addEventListener('dragover', onDocDragOver, true)
  document.addEventListener('drop', onDocDrop, true)
}

function onDocDragOver(e) {
  e.preventDefault()   // 允许在任意位置 drop
  moveClone(e)
  onGlobalDragOver(e)
}

function onDocDrop(e) {
  e.preventDefault()
  onDrop()
}

function moveClone(e) {
  if (dragClone) {
    dragClone.style.top = (e.clientY - 16) + 'px'   // 只跟随 Y 轴移动
  }
}

function removeClone() {
  if (dragSourceEl) {
    dragSourceEl.classList.remove('dragging')
    dragSourceEl = null
  }
  if (dragClone) {
    dragClone.remove()
    dragClone = null
  }
}

let pendingTarget = null   // 待应用的预览落位（{type:'block',idx} | {type:'end'}）
let rafId = null           // 合并 dragover 高频事件，每帧只重排一次（避免长距离拖动卡顿）

/** 全局拖放（整个编辑器范围）：按光标 Y 轴判定插入位置，实时重排预览 */
function onGlobalDragOver(e) {
  autoScroll(e.clientY)
  if (dragBlock == null) return
  const blockEls = listEl.value?.querySelectorAll('.block')
  let targetIdx = blockEls ? blockEls.length : 0   // 默认末尾
  if (blockEls) {
    for (let i = 0; i < blockEls.length; i++) {
      const r = blockEls[i].getBoundingClientRect()
      if (e.clientY < r.top + r.height / 2) {
        targetIdx = i   // 插到该块前
        break
      }
      targetIdx = i + 1 // 否则插到该块后（下一个块前）
    }
  }
  pendingTarget = { type: 'block', idx: targetIdx }
  schedulePreview()
}

function schedulePreview() {
  if (rafId != null) return
  rafId = requestAnimationFrame(() => {
    rafId = null
    applyPreview()
  })
}

function applyPreview() {
  if (dragBlock == null || pendingTarget == null) return
  const t = pendingTarget
  pendingTarget = null
  const arr = props.modelValue
  const cur = arr.indexOf(dragBlock)
  // dragstart 已把 dragBlock 移出数组、首次落位前 cur 为 -1：
  // 此时不能 splice(-1,1)（会把最后一个块删掉），直接插入即可
  if (cur < 0) {
    if (t.type === 'end') {
      arr.push(dragBlock)
    } else {
      arr.splice(t.idx, 0, dragBlock)
    }
    return
  }
  if (t.type === 'end') {
    if (cur !== arr.length - 1) {
      arr.splice(cur, 1)
      arr.push(dragBlock)
    }
    return
  }
  if (cur === t.idx) return   // 已在目标前
  arr.splice(cur, 1)
  let insertAt = t.idx
  if (cur < t.idx) insertAt -= 1   // 移除后目标左移
  arr.splice(insertAt, 0, dragBlock)
}

function onDrop() {
  if (dragBlock == null) {
    clearDrag()
    return
  }
  // 提交前先应用最后挂起的预览落位
  if (rafId != null) {
    cancelAnimationFrame(rafId)
    rafId = null
    applyPreview()
  }
  dropped = true
  emit('change')
  clearDrag()
}


/** 拖动靠近滚动区上下边缘时自动滚动 */
function autoScroll(clientY) {
  const scroller = listEl.value?.closest('.editor-scroll')
  if (!scroller) return
  const r = scroller.getBoundingClientRect()
  const edge = 48
  if (clientY < r.top + edge) {
    scroller.scrollTop -= 14
  } else if (clientY > r.bottom - edge) {
    scroller.scrollTop += 14
  }
}

/** 拖动结束/取消：移除文档级监听，取消挂起的预览，未放下则还原顺序 */
function clearDrag() {
  document.removeEventListener('dragover', onDocDragOver, true)
  document.removeEventListener('drop', onDocDrop, true)
  if (rafId != null) {
    cancelAnimationFrame(rafId)
    rafId = null
  }
  pendingTarget = null
  if (!dropped && dragBlock != null) {
    const cur = props.modelValue.indexOf(dragBlock)
    if (cur >= 0) props.modelValue.splice(cur, 1)
    if (dragFromIdx != null && dragFromIdx <= props.modelValue.length) {
      props.modelValue.splice(dragFromIdx, 0, dragBlock)
    }
  }
  dragBlock = null
  dragFromIdx = null
  dropped = false
  removeClone()
}

/** 点击类型标识 → 切换块类型（保留当前文本） */
function switchType(b, cmd) {
  const text = b.text || ''
  const nb = blockFromCmd(cmd, text)
  Object.keys(b).forEach((k) => delete b[k])
  Object.assign(b, nb)
  emit('change')
}

async function onImageSelected(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const { data } = await uploadNoteMedia(file, 'IMAGE')
    if (data?.code !== 0 || !data?.data?.url) {
      console.error('[note-media] upload failed:', data)
      notifyError(data?.message || '上传失败')
      return
    }
    const block = { type: 'image', url: data.data.url, alt: file.name }
    insertOrAppend(block)
  } catch (err) {
    console.error('[note-media] upload exception:', err)
    notifyError(err.response?.data?.message || '上传失败')
  }
}

async function onAudioSelected(e) {
  const file = e.target.files?.[0]
  e.target.value = ''
  if (!file) return
  try {
    const { data } = await uploadNoteMedia(file, 'AUDIO')
    if (data?.code !== 0 || !data?.data?.url) {
      console.error('[note-media] upload failed:', data)
      notifyError(data?.message || '上传失败')
      return
    }
    const url = data.data.url + (data.data.url.includes('?') ? '&' : '?') + 'mediaType=audio'
    insertOrAppend({ type: 'audio', url, duration: 0 })
  } catch (err) {
    console.error('[note-media] upload exception:', err)
    notifyError(err.response?.data?.message || '上传失败')
  }
}

/** 媒体块：pendingInsertIdx 非空 → 插到该位置；否则追加到末尾（并补空文本块） */
function insertOrAppend(block) {
  ensureBlockId(block)
  if (pendingInsertIdx != null) {
    const idx = pendingInsertIdx
    pendingInsertIdx = null
    props.modelValue.splice(idx, 0, block)
    emit('change')
  } else {
    insertMediaBlock(block)
  }
}

function openImagePicker() {
  pendingInsertIdx = null
  imageInput.value?.click()
}

function openAudioPicker() {
  pendingInsertIdx = null
  audioInput.value?.click()
}

function remove(idx) {
  props.modelValue.splice(idx, 1)
  emit('change')
}
</script>

<style scoped>
.block-editor {
  /* 左侧留出拖动柄空间 */
  padding: 16px 24px 16px 40px;
}

.block-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-bottom: 12px;
  align-items: center;
}

.toolbar-divider {
  width: 1px;
  height: 20px;
  background: var(--el-border-color-light);
  margin: 0 4px;
}

/* 浮动格式栏（焦点文本块上方偏左） */
.format-bar {
  position: fixed;
  display: flex;
  gap: 2px;
  padding: 2px 4px;
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: 8px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.12);
  z-index: 50;
}

.block-list {
  /* 块列表容器（聚焦定位用） */
}

.block {
  position: relative;
  background: #fafafa;
  border: 1px solid #f0f0f3;
  border-radius: 8px;
  padding: 12px 12px 8px 12px;
  margin-bottom: 8px;
}
.block:last-child {
  margin-bottom: 0;
}

/* TransitionGroup：实时重排时块位移动画 */
.list-move {
  transition: transform 0.2s ease;
}
.block-list-items {
  display: block;
}

/* 块右上角删除按钮（悬停出现；半透明不挡内容，悬停按钮变实心红） */
.block-del {
  position: absolute;
  top: 3px;
  right: 3px;
  width: 18px;
  height: 18px;
  border-radius: 50%;
  border: none;
  background: rgba(0, 0, 0, 0.55);
  color: #fff;
  font-size: 12px;
  line-height: 1;
  cursor: pointer;
  opacity: 0;
  transition: opacity 0.15s;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.block:hover .block-del { opacity: 0.35; }
.block-del:hover { opacity: 1; }

/* 列表末尾拖放区（拖到这里吸附到最下方） */
.block-drop-end {
  position: relative;
  min-height: 28px;
}

/* 拖动时跟随鼠标的块浮层 */
.block-drag-clone {
  position: fixed;
  width: auto;
  min-width: 140px;
  max-width: 80vw;
  background: #fff;
  border: 1px solid #e5e5ea;
  border-radius: 8px;
  padding: 8px 12px;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.18);
  pointer-events: none;
  opacity: 0.92;
  z-index: 9999;
  overflow: hidden;
}
.block-drag-clone .block-drag,
.block-drag-clone .block-del,
.block-drag-clone .block-insert-line,
.block-drag-clone .block-drop-end,
.block-drag-clone .audio-fallback { display: none; }

/* 拖动排序手柄（块左侧中间，绝对定位，加长一倍，不随块类型/标题高度变化） */
.block-drag {
  position: absolute;
  left: -32px;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  width: 26px;
  text-align: center;
  color: #c7c7cc;
  font-size: 14px;
  letter-spacing: -1px;
  padding: 2px 0 0;
  cursor: grab;
  user-select: none;
  line-height: 1.7;
  opacity: 0;
  transition: opacity 0.15s;
}
.block:hover .block-drag { opacity: 1; color: #86868b; }

/* 拖动中的源块淡出（仿佛被拿起） */
.block.dragging { opacity: 0.35; }

/* 媒体行（拖动柄 + 媒体内容） */
.block-media-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

.block-text-row {
  display: flex;
  align-items: flex-start;
  gap: 6px;
}

/* 块类型标识按钮（左侧，固定宽对齐，点击切换类型） */
.block-type {
  width: 40px;
  flex-shrink: 0;
  border: none;
  background: transparent;
  color: #c7c7cc;
  font-size: 12px;
  padding: 4px 3px 0 0;
  text-align: left;
  cursor: pointer;
  line-height: 1.7;
}
.block:hover .block-type { color: #86868b; }
.block-type:hover { color: #007AFF; }

/* 所见即所得可编辑块（contenteditable）：自动随内容增高，空块占位提示 */
.block-text {
  flex: 1;
  width: auto;
  min-height: 1.7em;          /* 空块仍可点击聚焦 */
  border: none;
  outline: none;
  font-size: 15px;
  line-height: 1.7;
  color: #1d1d1f;
  background: transparent;
  font-family: inherit;
  padding: 2px 0;
  word-break: break-word;
  white-space: normal;
}
.block-text:focus { outline: none; }
.block-text.is-empty::before {
  content: attr(data-placeholder);
  color: #c7c7cc;
  pointer-events: none;
}

.block-h {
  font-weight: 700;
  color: #111;
}
.block-h.block-text { font-size: 1.3em; }

/* 块内渲染的行内结构（列表/引用/代码）与预览观感一致 */
.block-text :deep(ul), .block-text :deep(ol) { margin: 4px 0; padding-left: 24px; }
.block-text :deep(blockquote) {
  margin: 4px 0;
  padding: 2px 0 2px 12px;
  border-left: 3px solid #c7c7cc;
  color: #86868b;
}
.block-text :deep(pre) {
  background: #f5f5f7;
  border-radius: 8px;
  padding: 12px;
  overflow-x: auto;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
  font-size: 13px;
  margin: 4px 0;
}
.block-text :deep(code) {
  background: rgba(0, 0, 0, 0.05);
  border-radius: 4px;
  padding: 1px 4px;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
  font-size: 0.92em;
}
.block-text :deep(pre code) { background: none; padding: 0; }

.block-code {
  background: #f5f5f7;
  border-radius: 8px;
  padding: 12px;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
  font-size: 13px;
}

.block-media-wrap {
  position: relative;
  display: inline-block;
  margin: 4px 0;
}
.block-img {
  max-width: 60%;
  height: auto;
  border-radius: 8px;
  display: block;
}
.block-audio-wrap {
  flex: 1;
  min-width: 0;
  padding: 2px 0;
}
.block-audio {
  display: block;
  width: 100%;
}
.audio-fallback {
  display: block;
  color: #86868b;
  font-size: 13px;
  margin-top: 4px;
}

/* 悬停「＋」：在上方插入，点击＋选类型（灰色横线已移除；抬高并留上下边距；
   pointer-events:none 让透明区域不拦截点击，只有「＋」本身可点） */
.block-insert-line {
  position: absolute;
  top: -17px;
  left: 50%;
  transform: translateX(-50%);
  width: 52px;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: center;
  opacity: 0;
  transition: opacity 0.15s;
  z-index: 3;
  pointer-events: none;
}
.block:hover .block-insert-line { opacity: 1; }
.block-insert-plus {
  position: relative;
  width: 14px;
  height: 14px;
  border-radius: 50%;
  background: #fff;
  border: 1px solid #c7c7cc;
  color: #007AFF;
  font-size: 11px;
  line-height: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  pointer-events: auto;
}

.block-empty {
  color: #86868b;
  font-size: 14px;
  padding: 20px 0;
}
</style>
