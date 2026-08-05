<template>
  <div class="notes-view">
    <!-- 左侧列表 -->
    <aside class="notes-list">
      <div class="list-header">
        <el-input v-model="keyword" :placeholder="t('notes.searchPlaceholder')" clearable size="small" class="search-input">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-button type="primary" size="small" @click="newNote">
          <el-icon style="margin-right:4px"><Plus /></el-icon>{{ t('notes.newNote') }}
        </el-button>
      </div>
      <el-scrollbar class="list-scroll">
        <div v-loading="listLoading" class="list-items">
          <div
            v-for="n in notes" :key="n.id"
            class="note-item" :class="{ active: n.id === currentId }"
            @click="openNote(n)"
          >
            <div class="note-item-title">{{ n.title || t('notes.untitled') }}</div>
            <div class="note-item-time">{{ formatDateTime(n.updatedAt) }}</div>
          </div>
          <el-empty v-if="!listLoading && notes.length === 0" :description="t('notes.empty')" :image-size="60" />
        </div>
      </el-scrollbar>
    </aside>

    <!-- 编辑器（Vditor 容器始终渲染，保证 onMounted 时能初始化） -->
    <section class="note-editor">
      <div class="editor-header">
        <el-input v-model="title" :placeholder="t('notes.titlePlaceholder')" class="title-input" @input="onTitleInput" :disabled="!currentId && !isCreating" />
        <el-button size="small" type="danger" text :disabled="!currentId" @click="onDelete">{{ t('notes.delete') }}</el-button>
      </div>
      <!-- Vditor 编辑器：IR 即时渲染，编辑即见渲染效果 -->
      <div class="editor-body">
        <div ref="vditorEl" class="vditor-wrap" />
        <!-- 未选中时的空状态覆盖层（不卸载 Vditor 容器） -->
        <div v-if="!currentId && !isCreating" class="editor-empty">
          <el-empty :description="t('notes.selectHint')" :image-size="120" />
        </div>
      </div>
    </section>

    <ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />
  </div>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import Vditor from 'vditor'
import 'vditor/dist/index.css'
import { listNotes, createNote, getNote, updateNote, deleteNote } from '../api/notes'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import { useSse } from '../composables/useSse'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import { useNoteProgress } from '../composables/useNoteProgress'
import { formatDateTime } from '../utils/format'

const { t } = useI18n()
const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()

// ---- 列表状态 ----
const notes = ref([])
const listLoading = ref(false)
const keyword = ref('')
const currentId = ref(null)
const isCreating = ref(false)

// ---- 编辑状态 ----
const title = ref('')
const vditorEl = ref(null)
let vditor = null
let vditorReady = false
let pendingContent = null
let ownSaveAt = 0
let saveTimer = null
let searchTimer = null

const { maybeResume, saveFromScroll } = useNoteProgress(currentId, confirm)

/** Vditor 的滚动容器（阅读进度用，IR 模式在 .vditor-content / .vditor-ir） */
function getScrollEl() {
  if (!vditorEl.value) return null
  const content = vditorEl.value.querySelector('.vditor-content')
  if (content && content.scrollHeight > content.clientHeight) return content
  const ir = vditorEl.value.querySelector('.vditor-ir')
  if (ir && ir.scrollHeight > ir.clientHeight) return ir
  return content
}

// ---- 列表 ----
async function loadList() {
  listLoading.value = true
  try {
    const { data } = await listNotes({ page: 1, size: 100, keyword: keyword.value || undefined })
    notes.value = data?.data?.records || []
  } catch {
    notes.value = []
  } finally {
    listLoading.value = false
  }
}

function onSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  searchTimer = setTimeout(loadList, 300)
}

// ---- Vditor 编辑器 ----
function initVditor() {
  if (!vditorEl.value) return
  vditor = new Vditor(vditorEl.value, {
    mode: 'ir',                       // 即时渲染：编辑时直接看到渲染效果，且保留标准工具栏
    height: '100%',
    placeholder: t('notes.contentPlaceholder'),
    cache: { enable: false },         // 内容以服务端为准，不用本地缓存
    upload: { enable: false },        // 笔记为纯文本，禁图片上传
    counter: 0,
    toolbar: [
      'headings', 'bold', 'italic', 'strike', '|',
      'list', 'ordered-list', 'quote', '|',
      {
        name: 'code-block',
        tip: '代码块',
        className: 'right',
        icon: '<svg viewBox="0 0 24 24" width="16" height="16"><path fill="currentColor" d="M9.4 16.6L4.8 12l4.6-4.6L8 6l-6 6 6 6 1.4-1.4zm5.2 0l4.6-4.6-4.6-4.6L16 6l6 6-6 6-1.4-1.4z"/></svg>',
        click: () => vditor.insertValue('```\n\n```\n', true)
      },
      'inline-code', '|',
      'link', 'table', '|',
      'undo', 'redo', '|', 'preview', 'fullscreen'
    ],
    input: () => onContentInput(),
    after: () => {
      vditorReady = true
      if (pendingContent != null) {
        vditor.setValue(pendingContent)
        pendingContent = null
      }
      // 绑定滚动进度
      const content = vditorEl.value?.querySelector('.vditor-content')
      const ir = vditorEl.value?.querySelector('.vditor-ir')
      if (content) content.addEventListener('scroll', () => saveFromScroll(getScrollEl))
      if (ir) ir.addEventListener('scroll', () => saveFromScroll(getScrollEl))
    }
  })
}

function setVditorContent(md) {
  if (vditorReady && vditor) {
    vditor.setValue(md || '')
  } else {
    pendingContent = md || ''
  }
}

// ---- 保存 ----
async function saveNow() {
  if (!currentId.value && !isCreating.value) return
  // 记录本次保存时间：SSE 收到自己写入的回声事件时（短窗口内）不重载
  ownSaveAt = Date.now()
  const body = { title: title.value, content: vditor?.getValue() || '' }
  try {
    if (isCreating.value) {
      const { data } = await createNote(body)
      const detail = data?.data
      if (detail) {
        currentId.value = detail.id
        isCreating.value = false
      }
    } else {
      await updateNote(currentId.value, body)
    }
    loadList()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('notes.saveFailed'))
  }
}

function onContentInput() {
  if (saveTimer) clearTimeout(saveTimer)
  saveTimer = setTimeout(saveNow, 1500)
}

function onTitleInput() {
  onContentInput()
}

// ---- 新建 / 打开 ----
function newNote() {
  flushSave()
  currentId.value = null
  isCreating.value = true
  title.value = ''
  setVditorContent('')
}

async function openNote(note) {
  flushSave()
  isCreating.value = false
  currentId.value = note.id
  title.value = ''
  try {
    const { data } = await getNote(note.id)
    const detail = data?.data
    if (!detail) return
    title.value = detail.title || ''
    setVditorContent(detail.content || '')
    await maybeResume(getScrollEl)
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('notes.loadFailed'))
  }
}

// ---- 删除 ----
async function onDelete() {
  if (!currentId.value) return
  try {
    await confirm({
      title: t('notes.delete'),
      message: t('notes.deleteConfirm'),
      confirmText: t('common.delete'),
      type: 'warning'
    })
  } catch { return }
  try {
    await deleteNote(currentId.value)
    currentId.value = null
    title.value = ''
    setVditorContent('')
    loadList()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('notes.deleteFailed'))
  }
}

// ---- SSE 同步 ----
useSse({
  NOTE_UPDATED: (e) => {
    let payload = {}
    try { payload = JSON.parse(e.data || '{}') } catch { /* ignore */ }
    loadList()
    // 自己保存的回声事件（保存后 2s 窗口内到达）不重载正文，避免打断输入
    const isOwnEcho = payload.noteId === currentId.value && (Date.now() - ownSaveAt) < 2000
    if (!isOwnEcho && payload.noteId === currentId.value && !isCreating.value) {
      getNote(currentId.value).then(({ data }) => {
        const detail = data?.data
        if (detail) {
          title.value = detail.title || ''
          setVditorContent(detail.content || '')
          ElMessage.info(t('notes.synced'))
        }
      })
    }
  }
})

// ---- 生命周期 ----
function flushSave() {
  if (saveTimer) {
    clearTimeout(saveTimer)
    saveTimer = null
    saveNow()
  }
}

onMounted(() => {
  loadList()
  initVditor()
})

onBeforeUnmount(() => {
  flushSave()
  vditor?.destroy()
})
</script>

<style scoped>
.notes-view {
  display: flex;
  height: calc(100vh - 100px);
  gap: 16px;
}

/* ---- 左侧列表 ---- */
.notes-list {
  width: 280px;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  padding: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
}

.list-header {
  display: flex;
  gap: 8px;
  margin-bottom: 8px;
}

.search-input { flex: 1; }

.list-scroll { flex: 1; }

.list-items { padding: 4px 0; }

.note-item {
  padding: 8px 10px;
  border-radius: 8px;
  cursor: pointer;
  transition: background-color 0.15s ease;
  margin-bottom: 2px;
}

.note-item:hover { background: rgba(0, 0, 0, 0.04); }

.note-item.active {
  background: rgba(0, 122, 255, 0.1);
}

.note-item-title {
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.note-item-time {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}

/* ---- 编辑器 ---- */
.note-editor {
  position: relative;
  flex: 1;
  display: flex;
  flex-direction: column;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 1px 3px rgba(0, 0, 0, 0.04);
  overflow: hidden;
}

.editor-header {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color-light);
}

.title-input { flex: 1; }
.title-input :deep(.el-input__inner) { font-size: 16px; font-weight: 600; }

.editor-body { flex: 1; display: flex; overflow: hidden; }

.vditor-wrap { flex: 1; }

.editor-empty {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fff;
  z-index: 3;
}
</style>
