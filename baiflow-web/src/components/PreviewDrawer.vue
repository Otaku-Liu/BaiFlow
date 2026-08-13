<template>
  <el-drawer
    :model-value="visible"
    @update:model-value="$emit('close')"
    :title="fileItem?.name || ''"
    size="75%"
    direction="rtl"
    @close="handleClose"
  >
    <div class="preview-container" v-if="fileItem">
      <!-- 加载中 -->
      <div v-if="blobLoading" class="preview-loading">
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
          style="width:100%;max-height:70vh"
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
        <iframe :src="blobUrl" style="width:100%;height:75vh;border:none" />
      </div>

      <!-- Markdown -->
      <div v-else-if="category === 'markdown'" class="preview-markdown" ref="scrollRef" @scroll="onScroll">
        <div v-html="mdHtml" class="markdown-body" />
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
const blobLoading = ref(false)

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

function onScroll() {
  // debounced save handled by composable
}

// ---- 文本加载 ----
async function loadTextContent() {
  if (category.value !== 'text') return
  try {
    const { data } = await fetchPreviewContent(props.fileItem.id)
    textContent.value = typeof data === 'string' ? data : JSON.stringify(data, null, 2)
    await nextTick()
    if (progress && scrollRef.value) {
      await progress.promptResume((pos) => {
        if (scrollRef.value) {
          const max = scrollRef.value.scrollHeight - scrollRef.value.clientHeight
          if (max > 0) scrollRef.value.scrollTop = pos * max
        }
      })
      // 滚动进度：防抖 2 秒保存
      let scrollTimer = null
      scrollRef.value.addEventListener('scroll', () => {
        if (scrollTimer) clearTimeout(scrollTimer)
        scrollTimer = setTimeout(() => {
          if (scrollRef.value) {
            const max = scrollRef.value.scrollHeight - scrollRef.value.clientHeight
            if (max > 0) {
              const pct = Math.min(1, Math.max(0, scrollRef.value.scrollTop / max))
              // 回顶时 pct=0 也保存，用于清除历史进度
              progress.saveNow(pct)
            }
          }
        }, 2000)
      })
    }
  } catch {
    textContent.value = 'Failed to load content'
  }
}

// ---- Markdown 加载 ----
async function loadMarkdown() {
  if (category.value !== 'markdown') return
  try {
    const { data } = await fetchPreviewContent(props.fileItem.id)
    const raw = typeof data === 'string' ? data : ''
    const converter = new Converter({ tables: true, strikethrough: true, tasklists: true })
    mdHtml.value = converter.makeHtml(raw)
    await nextTick()
    if (progress && scrollRef.value) {
      await progress.promptResume((pos) => {
        if (scrollRef.value) {
          const max = scrollRef.value.scrollHeight - scrollRef.value.clientHeight
          if (max > 0) scrollRef.value.scrollTop = pos * max
        }
      })
      let scrollTimer = null
      scrollRef.value.addEventListener('scroll', () => {
        if (scrollTimer) clearTimeout(scrollTimer)
        scrollTimer = setTimeout(() => {
          if (scrollRef.value) {
            const max = scrollRef.value.scrollHeight - scrollRef.value.clientHeight
            if (max > 0) {
              const pct = Math.min(1, Math.max(0, scrollRef.value.scrollTop / max))
              // 回顶时 pct=0 也保存，用于清除历史进度
              progress.saveNow(pct)
            }
          }
        }, 2000)
      })
    }
  } catch {
    mdHtml.value = '<p>Failed to load content</p>'
  }
}

// ---- Blob 加载 ----
async function loadBlob() {
  if (!props.fileItem) return
  const cat = category.value
  if (!['image', 'video', 'audio', 'pdf'].includes(cat)) return
  blobLoading.value = true
  try {
    blobUrl.value = await fetchPreviewBlob(props.fileItem.id)
  } catch {
    blobUrl.value = ''
  } finally {
    blobLoading.value = false
  }
}

// ---- 关闭处理 ----
function handleClose() {
  if (progress) {
    if (mediaRef.value) progress.saveNow(mediaRef.value.currentTime)
    progress.stopAutoSave()
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
  blobLoading.value = false
  if (['image', 'video', 'audio', 'pdf'].includes(category.value)) {
    await loadBlob()
  }
  if (category.value === 'markdown') await loadMarkdown()
  if (category.value === 'text') await loadTextContent()
})
</script>

<style scoped>
.preview-container { min-height: 300px; }

.preview-loading {
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  padding-top: 80px; color: var(--el-text-color-secondary); gap: 12px;
}

.preview-image { text-align: center; }
.preview-image img { max-width: 100%; max-height: 75vh; object-fit: contain; }

.preview-video { text-align: center; }

.preview-audio { text-align: center; padding-top: 40px; }
.audio-artwork {
  width: 160px; height: 160px; margin: 0 auto;
  background: var(--el-fill-color-light); border-radius: 12px;
  display: flex; align-items: center; justify-content: center;
  color: var(--el-text-color-secondary);
}

.preview-pdf { min-height: 75vh; }

.preview-markdown {
  max-height: 75vh; overflow: auto;
  padding: 24px; background: #fff; border-radius: 8px;
}

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
  max-height: 75vh; overflow: auto;
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
