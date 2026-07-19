<template>
  <div class="downloads-view">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Plus /></el-icon> 新建下载任务
      </el-button>
      <el-select v-model="filterStatus" placeholder="全部状态" clearable @change="loadTasks" style="width:160px">
        <el-option label="全部" value="" />
        <el-option label="等待中" value="WAITING" />
        <el-option label="下载中" value="RUNNING" />
        <el-option label="已暂停" value="PAUSED" />
        <el-option label="已完成" value="COMPLETED" />
        <el-option label="失败" value="FAILED" />
      </el-select>
      <el-button :loading="loading" @click="loadTasks" style="margin-left:auto">
        <el-icon><Refresh /></el-icon> 刷新
      </el-button>
    </div>

    <!-- 任务列表 -->
    <el-table :data="tasks" v-loading="loading" stripe style="margin-top:16px">
      <el-table-column label="文件名/URL" min-width="220">
        <template #default="{ row }">
          <div>
            <div class="task-name">{{ row.fileName || row.sourceUrl }}</div>
            <div class="task-url">{{ row.sourceUrl }}</div>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="进度" width="160">
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
      <el-table-column label="大小" width="100" align="right">
        <template #default="{ row }">{{ row.totalBytes ? formatSize(row.totalBytes) : '-' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160">
        <template #default="{ row }">{{ row.createdAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="200" fixed="right">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button v-if="row.status === 'RUNNING'" type="warning" link size="small" @click="doPause(row)">
              暂停
            </el-button>
            <el-button v-if="row.status === 'PAUSED'" type="success" link size="small" @click="doResume(row)">
              恢复
            </el-button>
            <el-button type="danger" link size="small" @click="doDelete(row)">
              删除
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
    <el-empty v-if="!loading && tasks.length === 0" description="没有下载任务">
      <el-button type="primary" @click="showCreateDialog = true">创建你的第一个下载任务</el-button>
    </el-empty>

    <!-- 创建下载任务对话框 -->
    <el-dialog v-model="showCreateDialog" title="新建下载任务" width="500px">
      <el-form :model="createForm" :rules="createRules" ref="createFormRef" label-position="top">
        <el-form-item label="下载 URL" prop="sourceUrl">
          <el-input v-model="createForm.sourceUrl" placeholder="https://example.com/file.zip 或磁力链接" />
        </el-form-item>
        <el-form-item label="目标存储根目录" prop="targetStorageRootId">
          <el-select v-model="createForm.targetStorageRootId" placeholder="选择存储根目录" style="width:100%">
            <el-option v-for="r in roots" :key="r.id" :label="r.name" :value="r.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="存放子路径（可选）">
          <el-input v-model="createForm.targetRelativePath" placeholder="留空则放根目录，如 downloads/" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="showCreateDialog = false">取消</el-button>
        <el-button type="primary" @click="doCreateDownload" :loading="creating">开始下载</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Refresh } from '@element-plus/icons-vue'
import {
  createDownload, listDownloads, pauseDownload, resumeDownload, removeDownload
} from '../api/downloads'
import { listStorageRoots } from '../api/files'

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

const createRules = {
  sourceUrl: [{ required: true, message: '请输入下载 URL', trigger: 'blur' }],
  targetStorageRootId: [{ required: true, message: '请选择存储根目录', trigger: 'change' }]
}

// ---- 初始化 ----
onMounted(async () => {
  loadTasks()
  try {
    const { data } = await listStorageRoots()
    if (data.code === 'OK') roots.value = data.data || []
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
    if (data.code === 'OK') {
      tasks.value = data.data.records || []
      total.value = data.data.total || 0
    } else {
      ElMessage.error(data.message || '加载失败')
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '请求失败')
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
    ElMessage.success('下载任务已创建')
    showCreateDialog.value = false
    createForm.sourceUrl = ''
    createForm.targetRelativePath = ''
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建下载任务失败')
  } finally {
    creating.value = false
  }
}

async function doPause(row) {
  try {
    await pauseDownload(row.id)
    ElMessage.success('已暂停')
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '暂停失败')
  }
}

async function doResume(row) {
  try {
    await resumeDownload(row.id)
    ElMessage.success('已恢复')
    loadTasks()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '恢复失败')
  }
}

async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除此下载任务吗？`, '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    await removeDownload(row.id)
    ElMessage.success('已删除')
    loadTasks()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

// ---- 格式化 ----
function statusType(status) {
  const map = { WAITING: 'info', RUNNING: '', PAUSED: 'warning', COMPLETED: 'success', FAILED: 'danger', DELETED: 'info' }
  return map[status] || 'info'
}

function statusLabel(status) {
  const map = { WAITING: '等待中', RUNNING: '下载中', PAUSED: '已暂停', COMPLETED: '已完成', FAILED: '失败', DELETED: '已删除' }
  return map[status] || status
}

function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0, size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}

function formatSpeed(bytes) {
  return formatSize(bytes) + '/s'
}
</script>

<style scoped>
.downloads-view { max-width: 1200px; }
.toolbar { display: flex; align-items: center; gap: 12px; }
.task-name { font-weight: 500; }
.task-url { font-size: 12px; color: var(--el-text-color-secondary); max-width: 300px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.speed-text { font-size: 12px; color: var(--el-text-color-secondary); margin-top: 2px; }
.size-text { font-size: 13px; color: var(--el-text-color-regular); }
.action-btns { display: flex; gap: 4px; }
</style>
