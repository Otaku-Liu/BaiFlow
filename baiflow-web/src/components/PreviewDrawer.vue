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

      <!-- 文本/代码 -->
      <div v-else-if="category === 'text'" class="preview-text" ref="scrollRef" @scroll="onScroll">
        <pre>{{ textContent }}</pre>
      </div>

      <!-- XLSX -->
      <div v-else-if="category === 'xlsx'" class="preview-xlsx">
        <el-table :data="xlsxData" border stripe max-height="70vh" style="width:100%">
          <el-table-column
            v-for="(col, ci) in xlsxCols"
            :key="ci"
            :label="col"
            :prop="col"
            min-width="120"
            show-overflow-tooltip
          />
        </el-table>
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
import http from '../api/http'
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
const xlsxCols = ref([])
const xlsxData = ref([])
const blobUrl = ref('')
const blobLoading = ref(false)

// ---- MIME 判定 ----
const mime = computed(() => {
  return props.fileItem?.mimeType || mimeFromName(props.fileItem?.name) || 'application/octet-stream'
})
const category = computed(() => mimeCategory(mime.value))
const progressType = computed(() => progressTypeForCategory(category.value))

// ---- 进度管理 ----
let progress = null
if (props.fileItem) {
  progress = usePlaybackProgress(props.fileItem.id, progressType.value || 'SECONDS')
}

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
          scrollRef.value.scrollTop = pos * scrollRef.value.scrollHeight
        }
      })
      // 滚动进度：防抖 2 秒保存
      let scrollTimer = null
      scrollRef.value.addEventListener('scroll', () => {
        if (scrollTimer) clearTimeout(scrollTimer)
        scrollTimer = setTimeout(() => {
          if (scrollRef.value) {
            const pct = scrollRef.value.scrollTop / scrollRef.value.scrollHeight
            if (pct > 0) progress.saveNow(pct)
          }
        }, 2000)
      })
    }
  } catch {
    textContent.value = 'Failed to load content'
  }
}

// ---- XLSX 加载 ----
async function loadXlsx() {
  if (category.value !== 'xlsx') return
  try {
    const XLSX = await import('xlsx')
    // 通过 Axios 获取 blob，再转 ArrayBuffer
    const { data } = await fetchPreviewContent(props.fileItem.id)
    const blobResp = await http.get(`/files/${props.fileItem.id}/preview`, { responseType: 'blob' })
    const buf = await blobResp.data.arrayBuffer()
    const wb = XLSX.read(buf, { type: 'array' })
    const sheetName = wb.SheetNames[0]
    const sheet = wb.Sheets[sheetName]
    const json = XLSX.utils.sheet_to_json(sheet, { header: 1 })
    if (json.length > 0) {
      xlsxCols.value = json[0].map((_, i) => `col_${i}`)
      xlsxData.value = json.slice(1, 501).map(row => {
        const obj = {}
        row.forEach((cell, i) => { obj[`col_${i}`] = cell ?? '' })
        return obj
      })
    }
  } catch {
    xlsxData.value = []
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
  xlsxCols.value = []
  xlsxData.value = []
  blobUrl.value = ''
  blobLoading.value = false
  if (['image', 'video', 'audio', 'pdf'].includes(category.value)) {
    await loadBlob()
  }
  if (category.value === 'text') await loadTextContent()
  if (category.value === 'xlsx') await loadXlsx()
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

.preview-text {
  max-height: 75vh; overflow: auto;
  background: var(--el-fill-color-lighter); border-radius: 8px; padding: 16px;
}
.preview-text pre {
  margin: 0; font-size: 13px; line-height: 1.6;
  white-space: pre-wrap; word-break: break-word;
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
}

.preview-xlsx { overflow: auto; }

.preview-unsupported { padding-top: 60px; text-align: center; }

.preview-footer {
  display: flex; align-items: center; gap: 16px;
  font-size: 13px; color: var(--el-text-color-secondary);
}
</style>
