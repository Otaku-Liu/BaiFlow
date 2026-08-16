<template>
  <div class="notes-view">
    <!-- 左侧列表 -->
    <aside class="notes-list">
      <div class="list-header">
        <el-input v-model="keyword" :placeholder="t('notes.searchPlaceholder')" clearable size="small" class="search-input" @input="onSearch">
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

    <!-- 编辑器 -->
    <section class="note-editor">
      <div class="editor-header">
        <el-input v-model="title" :placeholder="t('notes.titlePlaceholder')" class="title-input" @input="onTitleInput" :disabled="!currentId && !isCreating" />
        <el-button size="small" type="primary" text :disabled="!currentId && !isCreating" @click="saveNow">{{ t('common.save') }}</el-button>
        <el-button size="small" type="danger" text :disabled="!currentId" @click="onDelete">{{ t('notes.delete') }}</el-button>
      </div>
      <div class="editor-body">
        <!-- 所见即所得块编辑器（滚动容器在此，供阅读进度用） -->
        <div ref="scrollEl" class="editor-scroll" @scroll="onEditorScroll">
          <NoteBlockEditor v-model="blocks" @change="onBlockChange" />
        </div>
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
import { ElMessageBox } from 'element-plus'
import { Search, Plus } from '@element-plus/icons-vue'
import { listNotes, createNote, getNote, updateNote, deleteNote } from '../api/notes'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import NoteBlockEditor from '../components/NoteBlockEditor.vue'
import { useSse } from '../composables/useSse'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import { useNoteProgress } from '../composables/useNoteProgress'
import { markdownToBlocks, blocksToMarkdown } from '../utils/noteBlocks'
import { notifyError, notifySuccess } from '../utils/notify'
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
const blocks = ref([])        // 当前笔记的块列表
const scrollEl = ref(null)    // 编辑器滚动容器（阅读进度）
let dirty = false             // 有未保存改动（10s 自动保存 / 手动保存）
let noteUpdatedAt = null      // 当前打开笔记基于的 updatedAt（乐观并发）
let saveInterval = null
let searchTimer = null

const { maybeResume, saveFromScroll } = useNoteProgress(currentId)

/** 块编辑器滚动容器（阅读进度用，保存/恢复滚动位置） */
function getScrollEl() {
  return scrollEl.value
}

function onEditorScroll() {
  saveFromScroll(getScrollEl)
}

/** 把服务端 Markdown 解析为块列表；空内容自动补一个文本块（可直接输入，无需手动添加块） */
function setBlocksFromMd(md) {
  const parsed = markdownToBlocks(md || '')
  if (parsed.length === 0) {
    parsed.push({ type: 'p', text: '' })
  }
  blocks.value = parsed
}

/** 块编辑变更 → 标脏 */
function onBlockChange() {
  dirty = true
}

function onTitleInput() {
  dirty = true
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

// ---- 保存 ----
async function saveNow() {
  if (!currentId.value && !isCreating.value) return
  const body = { title: title.value, content: blocksToMarkdown(blocks.value) }
  try {
    if (isCreating.value) {
      const { data } = await createNote(body)
      const detail = data?.data
      if (data?.code !== 0 || !detail) {
        notifyError(data?.message || t('notes.saveFailed'))
        return
      }
      currentId.value = detail.id
      isCreating.value = false
      noteUpdatedAt = detail.updatedAt
    } else {
      const { data } = await updateNote(currentId.value, { ...body, baseUpdatedAt: noteUpdatedAt })
      if (data?.code !== 0) {
        if (data?.code === 40901) {
          handleConflict()
        } else {
          notifyError(data?.message || t('notes.saveFailed'))
        }
        return
      }
      noteUpdatedAt = data?.data?.updatedAt || noteUpdatedAt
    }
    dirty = false
    notifySuccess(t('notes.saved'))
    loadList()
  } catch (e) {
    notifyError(e.response?.data?.message || t('notes.saveFailed'))
  }
}

/** 乐观并发冲突：让用户选择「覆盖」（丢对方的改动）还是「重新加载」（丢本地改动） */
async function handleConflict() {
  let overwrite = false
  try {
    await ElMessageBox.confirm(t('notes.conflictMessage'), t('notes.conflictTitle'), {
      confirmButtonText: t('notes.conflictOverwrite'),
      cancelButtonText: t('notes.conflictReload'),
      distinguishCancelAndClose: true,
      type: 'warning'
    })
    overwrite = true
  } catch (action) {
    overwrite = false
    if (action === 'cancel') {
      await reloadOpenNote()
      return
    }
    return
  }
  noteUpdatedAt = null
  await saveNow()
}

/** 重新加载当前打开的笔记（放弃本地未保存改动） */
async function reloadOpenNote() {
  if (!currentId.value) return
  try {
    const { data } = await getNote(currentId.value)
    const detail = data?.data
    if (!detail) return
    title.value = detail.title || ''
    setBlocksFromMd(detail.content || '')
    noteUpdatedAt = detail.updatedAt
    dirty = false
  } catch (e) {
    notifyError(e.response?.data?.message || t('notes.loadFailed'))
  }
}

// ---- 新建 / 打开 ----
function newNote() {
  flushSave()
  currentId.value = null
  isCreating.value = true
  title.value = ''
  setBlocksFromMd('')
  dirty = false
  noteUpdatedAt = null
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
    setBlocksFromMd(detail.content || '')
    dirty = false
    noteUpdatedAt = detail.updatedAt
    await maybeResume(getScrollEl)
  } catch (e) {
    notifyError(e.response?.data?.message || t('notes.loadFailed'))
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
    setBlocksFromMd('')
    noteUpdatedAt = null
    loadList()
  } catch (e) {
    notifyError(e.response?.data?.message || t('notes.deleteFailed'))
  }
}

// ---- SSE 同步：设备/其他端保存时通知本浏览器 ----
useSse({
  NOTE_UPDATED: (e) => {
    let payload = {}
    try { payload = JSON.parse(e.data || '{}') } catch { /* ignore */ }
    console.log('[sse] NOTE_UPDATED', payload)
    loadList()
    const isOpenNote = payload.noteId === currentId.value
    // 防重复：同一 updatedAt 的重复事件（SSE 重连重放/重复发布）不重复重载
    if (isOpenNote && !isCreating.value && !dirty && payload.updatedAt !== noteUpdatedAt) {
      getNote(currentId.value).then(({ data }) => {
        const detail = data?.data
        if (detail) {
          title.value = detail.title || ''
          setBlocksFromMd(detail.content || '')
          noteUpdatedAt = detail.updatedAt
        }
      })
    }
  }
})

// ---- 生命周期 ----
function flushSave() {
  if (dirty) {
    saveNow()
  }
}

onMounted(() => {
  loadList()
  // 10 秒自动保存一次（仅在有未保存改动时）
  saveInterval = setInterval(() => {
    if (dirty) saveNow()
  }, 10000)
})

onBeforeUnmount(() => {
  if (saveInterval) clearInterval(saveInterval)
  flushSave()
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

.editor-body { flex: 1; display: flex; overflow: hidden; position: relative; }

.editor-scroll {
  flex: 1;
  overflow-y: auto;
  height: 100%;
}

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
