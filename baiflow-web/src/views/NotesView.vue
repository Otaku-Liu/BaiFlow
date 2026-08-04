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

    <!-- 编辑器（Milkdown 容器始终渲染，保证创建时存在） -->
    <section class="note-editor">
      <div class="editor-header">
        <el-input v-model="title" :placeholder="t('notes.titlePlaceholder')" class="title-input" @input="onTitleInput" :disabled="!currentId && !isCreating" />
        <el-button size="small" type="danger" text :disabled="!currentId" @click="onDelete">{{ t('notes.delete') }}</el-button>
      </div>
      <!-- 格式工具栏（Milkdown 无内置按钮，自建命令触发） -->
      <div v-if="currentId || isCreating" class="editor-toolbar">
        <el-button size="small" text title="标题 H2" @click="runCommand(wrapInHeadingCommand.key, 2)">H</el-button>
        <el-button size="small" text title="加粗" @click="runCommand(toggleStrongCommand.key)"><b>B</b></el-button>
        <el-button size="small" text title="斜体" @click="runCommand(toggleEmphasisCommand.key)"><i>I</i></el-button>
        <el-button size="small" text title="行内代码" @click="runCommand(toggleInlineCodeCommand.key)">`code`</el-button>
        <el-button size="small" text title="代码块" @click="runCommand(createCodeBlockCommand.key)">```</el-button>
        <el-button size="small" text title="无序列表" @click="runCommand(wrapInBulletListCommand.key)">• 列表</el-button>
        <el-button size="small" text title="有序列表" @click="runCommand(wrapInOrderedListCommand.key)">1. 列表</el-button>
        <el-button size="small" text title="引用" @click="runCommand(wrapInBlockquoteCommand.key)">引用</el-button>
        <el-button size="small" text title="分割线" @click="runCommand(insertHrCommand.key)">—</el-button>
      </div>
      <!-- Milkdown 编辑器：WYSIWYG 所见即所得 -->
      <div class="editor-body">
        <div ref="mdEl" class="md-wrap" />
        <!-- 未选中时的空状态覆盖层（不卸载编辑器容器） -->
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
import { Editor, rootCtx, defaultValueCtx, config, commandsCtx } from '@milkdown/kit/core'
import { commonmark, toggleStrongCommand, toggleEmphasisCommand, toggleInlineCodeCommand,
  wrapInHeadingCommand, wrapInBulletListCommand, wrapInOrderedListCommand,
  wrapInBlockquoteCommand, insertHrCommand, createCodeBlockCommand } from '@milkdown/kit/preset/commonmark'
import { history } from '@milkdown/kit/plugin/history'
import { listener, listenerCtx } from '@milkdown/kit/plugin/listener'
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
const content = ref('')       // 当前 Markdown（由编辑器监听回调更新）
const mdEl = ref(null)
let editor = null
let editorSeq = 0
let ownSaveAt = 0
let saveTimer = null
let searchTimer = null

const { maybeResume, saveFromScroll } = useNoteProgress(currentId, confirm)

/** Milkdown 编辑器滚动容器（阅读进度用） */
function getScrollEl() {
  return mdEl.value?.querySelector('.milkdown') || mdEl.value
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

// ---- Milkdown 编辑器 ----
/** 销毁旧编辑器并用指定内容创建新编辑器（每次切笔记重建，简单可靠） */
async function createEditor(md) {
  const seq = ++editorSeq
  if (editor) { editor.destroy(); editor = null }
  const created = await Editor.make()
    .config((ctx) => {
      ctx.set(rootCtx, mdEl.value)
      ctx.set(defaultValueCtx, md || '')
    })
    .use(commonmark)
    .use(history)
    .use(listener)
    // 监听器注册放在 listener 插件之后，保证 listenerCtx 已注入
    .use(config((ctx) => {
      ctx.get(listenerCtx).markdownUpdated((_ctx, markdown) => {
        content.value = markdown
        onContentInput()
      })
    }))
    .create()
  // 若期间又被重建，销毁过期实例
  if (seq !== editorSeq) { created.destroy(); return }
  editor = created
  attachScroll()
}

function setEditorContent(md) {
  content.value = md || ''
  return createEditor(md || '')
}

function attachScroll() {
  const el = getScrollEl()
  if (el) el.addEventListener('scroll', () => saveFromScroll(getScrollEl))
}

/** 触发 Milkdown 命令（工具栏按钮）：如 wrapInHeadingCommand.key + 级别 */
function runCommand(cmdKey, payload) {
  if (!editor) return
  editor.action((ctx) => {
    ctx.get(commandsCtx).call(cmdKey, payload)
  })
}

// ---- 保存 ----
async function saveNow() {
  if (!currentId.value && !isCreating.value) return
  // 记录本次保存时间：SSE 收到自己写入的回声事件时（短窗口内）不重载
  ownSaveAt = Date.now()
  const body = { title: title.value, content: content.value }
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
  setEditorContent('')
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
    await setEditorContent(detail.content || '')
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
    setEditorContent('')
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
          setEditorContent(detail.content || '')
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
  createEditor('')
})

onBeforeUnmount(() => {
  flushSave()
  editor?.destroy()
  editor = null
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

.editor-toolbar {
  display: flex;
  align-items: center;
  gap: 2px;
  padding: 4px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
  flex-wrap: wrap;
}
.editor-toolbar .el-button { font-size: 13px; font-weight: 500; }

.editor-body { flex: 1; display: flex; overflow: hidden; }

.md-wrap {
  flex: 1;
  overflow: hidden;
}
/* Milkdown 编辑器：铺满并内部滚动（阅读进度滚动源）+ 基础排版（自绘，未用主题包） */
.md-wrap :deep(.milkdown) {
  height: 100%;
  overflow-y: auto;
  padding: 16px 24px;
  font-size: 15px;
  line-height: 1.7;
  color: #1d1d1f;
  outline: none;
}
.md-wrap :deep(.milkdown .ProseMirror) { outline: none; }
.md-wrap :deep(.milkdown h1) { font-size: 1.6em; border-bottom: 1px solid #e5e5ea; padding-bottom: 8px; margin: 24px 0 16px; }
.md-wrap :deep(.milkdown h2) { font-size: 1.3em; border-bottom: 1px solid #e5e5ea; padding-bottom: 6px; margin: 20px 0 12px; }
.md-wrap :deep(.milkdown h3) { font-size: 1.1em; margin: 16px 0 8px; }
.md-wrap :deep(.milkdown p) { margin: 0 0 12px; }
.md-wrap :deep(.milkdown code) { background: #f5f5f7; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; font-family: "SF Mono", "JetBrains Mono", monospace; }
.md-wrap :deep(.milkdown pre) { background: #f5f5f7; padding: 16px; border-radius: 8px; overflow: auto; }
.md-wrap :deep(.milkdown pre code) { background: none; padding: 0; }
.md-wrap :deep(.milkdown blockquote) { border-left: 3px solid #007AFF; padding-left: 14px; color: #86868b; margin: 12px 0; }
.md-wrap :deep(.milkdown ul), .md-wrap :deep(.milkdown ol) { padding-left: 24px; margin: 8px 0; }
.md-wrap :deep(.milkdown li) { margin: 4px 0; }
.md-wrap :deep(.milkdown table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.md-wrap :deep(.milkdown th), .md-wrap :deep(.milkdown td) { border: 1px solid #e5e5ea; padding: 8px 12px; text-align: left; }
.md-wrap :deep(.milkdown th) { background: #fafafa; font-weight: 600; }
.md-wrap :deep(.milkdown img) { max-width: 100%; }
.md-wrap :deep(.milkdown a) { color: #007AFF; }

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
