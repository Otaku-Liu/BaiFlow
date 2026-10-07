<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="$emit('close')"
    :title="fileItem?.name || ''"
    class="baiflow-preview-drawer"
    :size="drawerSize"
    :resizable="resizableEnabled"
    direction="rtl"
    @resize="onDrawerResize"
    @resize-end="onDrawerResizeEnd"
    @close="handleClose"
  >
    <div class="preview-container" v-if="fileItem">
      <!-- 加载中 -->
      <div v-if="loading" class="preview-loading">
        <el-icon :size="32" class="is-loading"><Loading /></el-icon>
        <p>{{ $t('common.loading') }}</p>
      </div>

      <!-- 图片 -->
      <div v-else-if="category === 'image'" class="preview-image">
        <img :src="blobUrl" :alt="fileItem.name" />
      </div>

      <!-- 视频 -->
      <div v-else-if="category === 'video'" class="preview-video">
        <video
          ref="mediaRef"
          :src="blobUrl"
          controls
          playsinline
          @loadedmetadata="onMediaReady"
          @timeupdate="onTimeUpdate"
          @pause="onMediaPause"
          style="width:100%;max-height:var(--preview-h)"
        />
      </div>

      <!-- 音频 -->
      <div v-else-if="category === 'audio'" class="preview-audio">
        <div class="audio-artwork">
          <el-icon :size="64"><Headset /></el-icon>
        </div>
        <audio
          ref="mediaRef"
          :src="blobUrl"
          controls
          @loadedmetadata="onMediaReady"
          @timeupdate="onTimeUpdate"
          @pause="onMediaPause"
          style="width:100%;margin-top:24px"
        />
      </div>

      <!-- PDF -->
      <div v-else-if="category === 'pdf'" class="preview-pdf">
        <iframe :src="blobUrl" style="width:100%;height:var(--preview-h);border:none" />
      </div>

      <!-- Markdown：整块白底卡片 + 浅灰边框，左侧目录（文档无标题时目录不出现） -->
      <div v-else-if="category === 'markdown'" class="markdown-preview">
        <MarkdownToc
          :items="tocItems"
          :active-id="tocActiveId"
          :collapsed="tocCollapsed"
          @collapse="tocCollapsed = true"
          @expand="tocCollapsed = false"
          @jump="jumpToHeading"
        />
        <div
          ref="scrollRef"
          class="preview-markdown"
          @scroll="onScroll"
        >
          <div v-html="mdHtml" class="markdown-body" />
        </div>
      </div>

      <!-- 文本/代码 -->
      <div v-else-if="category === 'text'" class="preview-text" ref="scrollRef" @scroll="onScroll">
        <pre>{{ textContent }}</pre>
      </div>

      <!-- ZIP 目录树 -->
      <div v-else-if="category === 'zip'" class="preview-zip">
        <el-empty description="ZIP 预览开发中" />
      </div>

      <!-- 不支持 -->
      <div v-else class="preview-unsupported">
        <el-empty :description="t('preview.unsupported')">
          <el-button type="primary" @click="doDownload">{{ $t('common.download') }}</el-button>
        </el-empty>
      </div>
    </div>

    <template #footer>
      <div class="preview-footer">
        <span>{{ fileItem ? formatSize(fileItem.sizeBytes) : '' }}</span>
      </div>
    </template>
  </el-drawer>
</template>

<script setup>
import { ref, computed, watch, nextTick } from 'vue'
import { useI18n } from 'vue-i18n'
import { Loading, Headset } from '@element-plus/icons-vue'
import { Converter } from 'showdown'
import MarkdownToc from './MarkdownToc.vue'
import { fetchPreviewBlob, fetchPreviewContent } from '../api/files'
import { usePlaybackProgress } from '../composables/usePlaybackProgress'
import { mimeFromName, mimeCategory, progressTypeForCategory } from '../utils/mime'
import { formatSize } from '../utils/format'

const { t } = useI18n()

const props = defineProps({
  fileItem: { type: Object, default: null },
  visible: { type: Boolean, default: false }
})
const emit = defineEmits(['close'])

const mediaRef = ref(null)
const scrollRef = ref(null)
const textContent = ref('')
const mdHtml = ref('')
const blobUrl = ref('')
// 加载态：blobLoading（图片/音视频/PDF 拉 blob）与文本/Markdown 拉正文由 category 分派、互斥，共用一个
const loading = ref(false)

// ---- Markdown 目录 ----
const tocItems = ref([])        // [{ id, level, text }]
const tocActiveId = ref('')
const tocCollapsed = ref(false)
// 含标题的 DOM 元素（与 tocItems 同序）；只在跳转/高亮时读，不需要响应式
let headingEls = []

/** 高亮判定：标题顶边越过内容区顶边下方这么多像素即算「已进入该章节」 */
const ACTIVE_OFFSET = 16

// ---- 抽屉宽度（拖左边缘改宽，用 Element 内置 resizable）----
const DRAWER_WIDTH_KEY = 'baiflow_preview_drawer_width'
/** 下限 40% 屏宽。内置拖动只夹到 4px（源码 `clamp(..., 4, windowSize)`），下限由这里 + styles.css 的 min-width 一起兜 */
const DRAWER_MIN_RATIO = 0.4

function drawerMinPx() {
  return Math.round(window.innerWidth * DRAWER_MIN_RATIO)
}

/** 存的是 px：换到更小的窗口后重新夹一次，避免撑爆或窄到不可读；没存过则用默认 75% */
function readDrawerSize() {
  let saved = NaN
  try {
    saved = Number(localStorage.getItem(DRAWER_WIDTH_KEY))
  } catch {
    // 隐私模式等读不出来，忽略：走默认宽度
  }
  if (!Number.isFinite(saved) || saved <= 0) return '75%'
  return `${Math.min(Math.max(saved, drawerMinPx()), window.innerWidth)}px`
}

const drawerSize = ref(readDrawerSize())
const resizableEnabled = ref(true)

function saveDrawerWidth(px) {
  try {
    localStorage.setItem(DRAWER_WIDTH_KEY, String(px))
  } catch {
    // 写不进去就下次回默认，不值得打扰用户
  }
}

/**
 * 清掉 Element 内置拖动的内部起点。它只在 hasStartedDragging=false 时才重新读 offsetWidth
 * 当起点，而这个状态只由它 watch 的 [size, resizable] 变化来清 —— size 值没变时（反复触底）
 * 就得动 resizable，否则下次拖动会拿过期的起点，要往右拖回一大段抽屉才动。
 */
async function resetDrawerDragState() {
  resizableEnabled.value = false
  await nextTick()
  resizableEnabled.value = true
}

/**
 * 拖到下限：把 size 收回下限（Element 监听 size prop 后会就此结束本次拖动），
 * 再清一次拖动状态 —— 反复触底时 size 值不变、watcher 不跑，只靠它不够。
 * 拖出下限的最后一道兜底是 CSS min-width（styles.css），两者各管一件事。
 */
function onDrawerResize(_event, size) {
  const min = drawerMinPx()
  if (size >= min) return
  drawerSize.value = `${min}px`
  saveDrawerWidth(min)
  resetDrawerDragState()
}

function onDrawerResizeEnd(_event, size) {
  // 回写 size：一是持久化，二是让 prop 与真实宽度对齐 —— 拖动中推过下限时内置的内部值会低于
  // CSS 兜住的真实宽度，回写 prop 会清掉那份内部状态（Element 监听 size 后会重置拖动状态）
  const final = Math.max(Math.round(size), drawerMinPx())
  if (`${final}px` !== drawerSize.value) drawerSize.value = `${final}px`
  saveDrawerWidth(final)
}

// ---- MIME 判定 ----
const mime = computed(() => {
  return props.fileItem?.mimeType || mimeFromName(props.fileItem?.name) || 'application/octet-stream'
})
const category = computed(() => mimeCategory(mime.value))
const progressType = computed(() => progressTypeForCategory(category.value))

// ---- 进度管理 ----
// 抽屉常驻挂载、fileItem 初始为 null：composable 在 setup 顶层创建一次，
// fileId/类型用 computed 响应文件切换，不在 watcher 里重建（useI18n 等须在 setup 顶层调用）
const progress = usePlaybackProgress(
  computed(() => props.fileItem?.id),
  computed(() => progressType.value || 'SECONDS')
)

async function onMediaReady() {
  if (!progress || !mediaRef.value) return
  await progress.promptResume((pos) => {
    if (mediaRef.value) mediaRef.value.currentTime = pos
  })
  progress.startAutoSave(() => mediaRef.value?.currentTime)
}

function onTimeUpdate() {
  // autoSave handles this
}

function onMediaPause() {
  if (progress && mediaRef.value) {
    progress.saveNow(mediaRef.value.currentTime)
  }
}

let scrollSaveTimer = null

function onScroll() {
  // 高亮要即时反馈，不等防抖
  if (category.value === 'markdown') updateActiveHeading()
  // 阅读进度：防抖 2s 保存。挂在模板 @scroll 上（声明式绑定），不像 addEventListener
  // 那样在重开同一文件时叠加出多个监听
  if (category.value !== 'text' && category.value !== 'markdown') return
  if (scrollSaveTimer) clearTimeout(scrollSaveTimer)
  scrollSaveTimer = setTimeout(() => {
    const el = scrollRef.value
    if (!el || !progress) return
    const max = el.scrollHeight - el.clientHeight
    if (max > 0) {
      // 回顶时 pct=0 也保存，用于清除历史进度
      progress.saveNow(Math.min(1, Math.max(0, el.scrollTop / max)))
    }
  }, 2000)
}

/**
 * 文本 / Markdown 共用的加载前半程：拉正文 → 写入 → 撤加载态 → 等 DOM 更新。
 * 撤加载态必须在 nextTick 之前：加载态下正文分支不渲染，scrollRef 为 null，
 * 调用方随后的续读与标题绑定会被整段跳过。
 */
async function loadContent(apply, applyFallback) {
  loading.value = true
  try {
    const { data } = await fetchPreviewContent(props.fileItem.id)
    apply(data)
    loading.value = false
    await nextTick()
  } catch {
    applyFallback()
    loading.value = false
  }
}

/** 恢复到进度里记的滚动百分比（文本 / Markdown 共用） */
async function restoreScrollPosition() {
  if (!progress || !scrollRef.value) return
  await progress.promptResume((pos) => {
    const el = scrollRef.value
    if (!el) return
    const max = el.scrollHeight - el.clientHeight
    if (max > 0) el.scrollTop = pos * max
  })
}

async function loadTextContent() {
  if (category.value !== 'text') return
  await loadContent(
    (data) => { textContent.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2) },
    () => { textContent.value = 'Failed to load content' }
  )
  await restoreScrollPosition()
}

// ---- Markdown 目录 ----
/**
 * 解析 Markdown：先在**离屏容器**里转成 HTML、给要进目录的标题赋好 id，再把目录与正文一次性写入。
 * 分两步（先写正文、nextTick 后再建目录）会让抽屉先画出满宽正文，随后目录栏插入把正文挤窄
 * —— 肉眼就是「正文先到、目录后弹」；一次写完两者同帧出现，没有空目录的中间态。
 *
 * 标题从渲染结果里取（而非正则扫 Markdown 源）才能认 setext 标题、且不会把围栏代码块里的
 * `#` 当标题；showdown 自带的标题 id 已用 noHeaderId 关掉——它对中文标题会生成空/退化的
 * id（实测 `# 一` → id=""、`# 二` → id="-1"），拿来做锚点直接失效。
 * 离屏容器只是 `createElement` + `innerHTML`，不插进文档：图片不会发起请求，脚本不会执行。
 */
function parseMarkdown(raw) {
  const converter = new Converter({ tables: true, strikethrough: true, tasklists: true, noHeaderId: true })
  const holder = document.createElement('div')
  holder.innerHTML = converter.makeHtml(raw)
  const items = []
  holder.querySelectorAll('h1, h2, h3').forEach((el) => {
    const text = el.textContent.trim()
    if (!text) return   // 空标题（如标题里只有图片）不进目录，与 Android 一致
    const id = `md-h-${items.length}`
    el.id = id
    items.push({ id, level: Number(el.tagName[1]), text })
  })
  tocItems.value = items
  tocActiveId.value = items.length ? items[0].id : ''
  mdHtml.value = holder.innerHTML
}

/** 正文渲染进 DOM 后取回标题元素（与 tocItems 同序），供跳转与滚动高亮使用 */
function bindHeadingEls() {
  const root = scrollRef.value
  if (!root) return
  headingEls = tocItems.value.map((item) => root.querySelector(`#${item.id}`)).filter(Boolean)
}

/**
 * 滚动高亮当前章节：从当前项出发只比较相邻标题，每帧 O(1) 次 rect 读取；
 * 不预存偏移量，正文图片撑开高度后也能自愈。
 */
function updateActiveHeading() {
  const root = scrollRef.value
  if (!root || !headingEls.length) return
  // 滚到底时最后一个标题可能永远到不了阈值线，兜底高亮末尾
  if (root.scrollTop + root.clientHeight >= root.scrollHeight - 2) {
    tocActiveId.value = tocItems.value[tocItems.value.length - 1].id
    return
  }
  const line = root.getBoundingClientRect().top + ACTIVE_OFFSET
  let i = headingEls.findIndex((el) => el.id === tocActiveId.value)
  if (i < 0) i = 0
  while (i + 1 < headingEls.length && headingEls[i + 1].getBoundingClientRect().top <= line) i++
  while (i > 0 && headingEls[i].getBoundingClientRect().top > line) i--
  tocActiveId.value = tocItems.value[i].id
}

/** 目录跳转：按标题相对滚动容器的偏移置 scrollTop（scrollIntoView 会连带滚动祖先/抽屉） */
function jumpToHeading(id) {
  const root = scrollRef.value
  const el = headingEls.find((h) => h.id === id)
  if (!root || !el) return
  const offset = el.getBoundingClientRect().top - root.getBoundingClientRect().top + root.scrollTop
  root.scrollTop = Math.max(0, offset - 8)
  tocActiveId.value = id
}

/** 清空目录状态 —— 四份状态（项 / 当前高亮 / 折叠 / 标题元素引用）必须一起清，否则串到下一个文件 */
function resetToc() {
  tocItems.value = []
  tocActiveId.value = ''
  tocCollapsed.value = false   // 展开状态不记忆：每次打开都展开
  headingEls = []
}

// ---- Markdown 加载 ----
async function loadMarkdown() {
  if (category.value !== 'markdown') return
  resetToc()
  await loadContent(
    (data) => parseMarkdown(typeof data === 'string' ? data : ''),
    () => { mdHtml.value = '<p>Failed to load content</p>' }
  )
  bindHeadingEls()
  await restoreScrollPosition()
}

// ---- Blob 加载 ----
async function loadBlob() {
  if (!props.fileItem) return
  const cat = category.value
  if (!['image', 'video', 'audio', 'pdf'].includes(cat)) return
  loading.value = true
  try {
    blobUrl.value = await fetchPreviewBlob(props.fileItem.id)
  } catch {
    blobUrl.value = ''
  } finally {
    loading.value = false
  }
}

// ---- 关闭处理 ----
function handleClose() {
  if (progress) {
    if (mediaRef.value) progress.saveNow(mediaRef.value.currentTime)
    progress.stopAutoSave()
  }
  // 掐掉还没到点的滚动进度保存，避免关抽屉/换文件后补一次陈旧的上报
  if (scrollSaveTimer) {
    clearTimeout(scrollSaveTimer)
    scrollSaveTimer = null
  }
  // 清理 Object URL
  if (blobUrl.value) {
    URL.revokeObjectURL(blobUrl.value)
    blobUrl.value = ''
  }
  emit('close')
}

function doDownload() {
  if (!props.fileItem) return
  const a = document.createElement('a')
  a.href = blobUrl.value || ''
  a.download = props.fileItem.name
  a.click()
}

// ---- 打开时加载 ----
watch(() => props.visible, async (v) => {
  if (!v || !props.fileItem) return
  textContent.value = ''
  blobUrl.value = ''
  loading.value = false
  if (['image', 'video', 'audio', 'pdf'].includes(category.value)) {
    await loadBlob()
  }
  if (category.value === 'markdown') await loadMarkdown()
  if (category.value === 'text') await loadTextContent()
})
</script>

<style scoped>
.preview-container {
  min-height: 300px;
  /* 预览区高度（各处共用这一个值）。抽屉头 + 尾 + 内边距固定占约 165px，
     取其上的 180px 做余量，让预览跟着窗口一起变高（1080 高的屏 75vh→900px），
     同时用 max() 兜住短窗口：绝不会比原来的 75vh 更矮。 */
  --preview-h: max(75vh, calc(100vh - 180px));
}

.preview-loading {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding-top: 80px; color: var(--el-text-color-secondary); gap: 12px;
}

.preview-image { text-align: center; }
.preview-image img { max-width: 100%; max-height: var(--preview-h); object-fit: contain; }

.preview-video { text-align: center; }

.preview-audio { text-align: center; padding-top: 40px; }
.audio-artwork {
  width: 160px; height: 160px; margin: 0 auto;
  background: var(--el-fill-color-light); border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: var(--el-text-color-secondary);
}

.preview-pdf { min-height: var(--preview-h); }

/* Markdown：整块白底卡片 + 浅灰边框。白卡片落在同样白色的抽屉里，边框是唯一的内容区
   边界；其余预览类型不加边框（图/视频/音频自带深色底或插图板，PDF 框内是浏览器阅读器）。
   overflow:hidden 负责把目录栏/正文的直角裁进圆角里 —— 两片拉手都落在卡片内部，不受影响 */
.markdown-preview {
  position: relative;
  display: flex;
  max-height: var(--preview-h);
  background: #fff;
  border: 1px solid var(--el-border-color);
  border-radius: 8px;
  overflow: hidden;
}

.preview-markdown { flex: 1; min-width: 0; overflow: auto; padding: 24px; }

.markdown-body { font-size: 14px; line-height: 1.7; color: #1d1d1f; }
.markdown-body :deep(h1) { font-size: 1.6em; border-bottom: 1px solid #e5e5ea; padding-bottom: 8px; margin: 24px 0 16px; }
.markdown-body :deep(h2) { font-size: 1.3em; border-bottom: 1px solid #e5e5ea; padding-bottom: 6px; margin: 20px 0 12px; }
.markdown-body :deep(h3) { font-size: 1.1em; margin: 16px 0 8px; }
.markdown-body :deep(p) { margin: 0 0 12px; }
.markdown-body :deep(code) { background: #f5f5f7; padding: 2px 6px; border-radius: 4px; font-size: 0.9em; }
.markdown-body :deep(pre) { background: #f5f5f7; padding: 16px; border-radius: 8px; overflow: auto; }
.markdown-body :deep(pre code) { background: none; padding: 0; }
.markdown-body :deep(blockquote) { border-left: 3px solid #007AFF; padding-left: 14px; color: #86868b; margin: 12px 0; }
.markdown-body :deep(ul), .markdown-body :deep(ol) { padding-left: 24px; margin: 8px 0; }
.markdown-body :deep(li) { margin: 4px 0; }
.markdown-body :deep(table) { border-collapse: collapse; width: 100%; margin: 12px 0; }
.markdown-body :deep(th), .markdown-body :deep(td) { border: 1px solid #e5e5ea; padding: 8px 12px; text-align: left; }
.markdown-body :deep(th) { background: #fafafa; font-weight: 600; }
.markdown-body :deep(img) { max-width: 100%; }
.markdown-body :deep(a) { color: #007AFF; }

.preview-text {
  max-height: var(--preview-h); overflow: auto;
  background: var(--el-fill-color-lighter); border-radius: 8px; padding: 16px;
}
.preview-text pre {
  margin: 0; font-size: 13px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
}

.preview-unsupported { padding-top: 60px; text-align: center; }

.preview-footer {
  display: flex; align-items: center; gap: 16px;
  font-size: 13px; color: var(--el-text-color-secondary);
}
</style>
