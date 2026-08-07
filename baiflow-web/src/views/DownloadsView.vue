<template>
  <div class="downloads-view">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> {{ t('downloads.newTask') }}
      </el-button>
      <el-select v-model="filterStatus" :placeholder="t('downloads.allStatus')" clearable @change="loadTasks" style="width:160px">
        <el-option :label="t('downloads.status.all')" value="" />
        <el-option :label="t('downloads.status.waiting')" value="WAITING" />
        <el-option :label="t('downloads.status.running')" value="RUNNING" />
        <el-option :label="t('downloads.status.paused')" value="PAUSED" />
        <el-option :label="t('downloads.status.completed')" value="COMPLETED" />
        <el-option :label="t('downloads.status.failed')" value="FAILED" />
      </el-select>
      <el-button :loading="loading" @click="loadTasks" style="margin-left:auto">
        <el-icon><Refresh /></el-icon> {{ t('common.refresh') }}
      </el-button>
    </div>

    <!-- 任务列表 -->
    <el-table :data="tasks" v-loading="loading" stripe style="margin-top:16px">
      <el-table-column :label="t('downloads.fileNameUrl')" min-width="220">
        <template #default="{ row }">
          <div>
            <div class="task-name">{{ row.fileName || row.sourceUrl }}</div>
            <div class="task-url">{{ row.sourceUrl }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('downloads.progress')" width="160">
        <template #default="{ row }">
          <div v-if="row.status === 'RUNNING' || row.status === 'PAUSED' || row.status === 'WAITING'">
            <el-progress :percentage="row.progress || 0" :status="row.status === 'FAILED' ? 'exception' : undefined" />
            <div class="speed-text" v-if="row.speedBytesPerSecond">
              {{ formatSpeed(row.speedBytesPerSecond) }}
            </div>
          </div>
          <div v-else class="size-text">
            {{ formatSize(row.totalBytes) }}
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.size')" width="100" align="right">
        <template #default="{ row }">{{ row.totalBytes ? formatSize(row.totalBytes) : '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('common.createdAt')" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="200" fixed="right">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button v-if="row.status === 'RUNNING'" type="warning" link size="small" @click="doPause(row)">
              {{ t('common.pause') }}
            </el-button>
            <el-button v-if="row.status === 'PAUSED'" type="success" link size="small" @click="doResume(row)">
              {{ t('common.resume') }}
            </el-button>
            <el-button type="danger" link size="small" @click="doDelete(row)">
              {{ t('common.delete') }}
            </el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination v-if="total > 0" style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="page" :page-size="size" :total="total"
      layout="prev, pager, next" @current-change="loadTasks" />

    <!-- 空状态 -->
    <el-empty v-if="!loading && tasks.length === 0" :description="t('downloads.noTasks')">
      <el-button type="primary" @click="showCreateDialog = true">{{ t('downloads.createFirstTask') }}</el-button>
    </el-empty>

    <!-- 通用确认弹窗 -->
    <ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />

    <!-- 创建下载任务对话框 -->
    <el-dialog v-model="showCreateDialog" :title="t('downloads.newTask')" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-position="top">
        <el-form-item :label="t('downloads.downloadUrl')" prop="sourceUrl">
          <el-input v-model="createForm.sourceUrl" :placeholder="t('downloads.downloadUrlPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('downloads.targetRoot')" prop="targetStorageRootId">
          <el-select v-model="createForm.targetStorageRootId" :placeholder="t('downloads.targetRootPlaceholder')" style="width:100%">
            <el-option v-for="r in roots" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('downloads.targetPath')">
          <el-input v-model="createForm.targetRelativePath" :placeholder="t('downloads.targetPathPlaceholder')" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doCreateDownload" :loading="creating">{{ t('downloads.startDownload') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createDownload, listDownloads, pauseDownload, resumeDownload, removeDownload
} from '../api/downloads'
import { listStorageRoots } from '../api/files'
import { formatDateTime, formatSize, formatSpeed } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const { t } = useI18n()
const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()

// ---- 状态 ----
const tasks = ref([])
const loading = ref(false)
const creating = ref(false)
const page = ref(1)
const size = ref(20)
const total = ref(0)
const filterStatus = ref('')
const showCreateDialog = ref(false)
const roots = ref([])
const createFormRef = ref(null)

const createForm = reactive({
  sourceUrl: '',
  targetStorageRootId: '',
  targetRelativePath: ''
})

const createRules = computed(() => ({
  sourceUrl: [{ required: true, message: t('downloads.urlRequired'), trigger: 'blur' }],
  targetStorageRootId: [{ required: true, message: t('downloads.rootRequired'), trigger: 'change' }]
}))

// ---- 初始化 ----
onMounted(async () => {
  loadTasks()
  try {
    const { data } = await listStorageRoots()
    if (data.code === 0) roots.value = data.data || []
  } catch { /* ignore */ }
})

// ---- 任务操作 ----
async function loadTasks() {
  loading.value = true
  try {
    const { data } = await listDownloads({
      status: filterStatus.value || undefined,
      page: page.value,
      size: size.value
    })
    if (data.code === 0) {
      tasks.value = data.data.records || []
      total.value = data.data.total || 0
    } else {
      ElMessage.error(data.message || t('downloads.loadFailed'))
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('downloads.requestFailed'))
  } finally {
    loading.value = false
  }
}

async function doCreateDownload() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createDownload(createForm)
    ElMessage.success(t('downloads.taskCreated'))
    showCreateDialog.value = false
    createForm.sourceUrl = ''
    createForm.targetRelativePath = ''
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('downloads.createTaskFailed'))
  } finally {
    creating.value = false
  }
}

async function doPause(row) {
  try {
    await pauseDownload(row.id)
    ElMessage.success(t('downloads.paused'))
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('downloads.pauseFailed'))
  }
}

async function doResume(row) {
  try {
    await resumeDownload(row.id)
    ElMessage.success(t('downloads.resumed'))
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('downloads.resumeFailed'))
  }
}

async function doDelete(row) {
  try {
    await confirm({
      title: t('downloads.deleteConfirmTitle'),
      message: t('downloads.deleteConfirmMsg'),
      confirmText: t('common.delete'),
      type: 'warning'
    })
    await removeDownload(row.id)
    ElMessage.success(t('downloads.status.deleted'))
    loadTasks()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('common.delete') + ' ' + t('downloads.loadFailed'))
  }
}

// ---- 格式化 ----
function statusType(status) {
  const map = { WAITING: 'info', RUNNING: '', PAUSED: 'warning', COMPLETED: 'success', FAILED: 'danger', DELETED: 'info' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = {
    WAITING: t('downloads.status.waiting'), RUNNING: t('downloads.status.running'),
    PAUSED: t('downloads.status.paused'), COMPLETED: t('downloads.status.completed'),
    FAILED: t('downloads.status.failed'), DELETED: t('downloads.status.deleted')
  }
  return map[status] || status
}

</script>

<style scoped>
.downloads-view { width: 100%; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
}

.task-name {
  font-weight: 500;
  color: var(--el-text-color-primary);
}

.task-url {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  max-width: 300px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-top: 2px;
}

.speed-text {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 4px;
  font-weight: 500;
}

.size-text {
  font-size: 13px;
  color: var(--el-text-color-regular);
}

.action-btns {
  display: flex;
  gap: 6px;
}
</style>
