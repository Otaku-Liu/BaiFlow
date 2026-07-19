<template>
  <div class="shares-view">
    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Share /></el-icon> 创建分享链接
      </el-button>
      <el-select v-model="filterStatus" clearable @change="loadShares" placeholder="状态筛选" style="width:140px">
        <el-option label="全部" value="" />
        <el-option label="有效" value="ACTIVE" />
        <el-option label="已过期" value="EXPIRED" />
        <el-option label="已撤销" value="REVOKED" />
      </el-select>
      <el-button :loading="loading" @click="loadShares" style="margin-left:auto">刷新</el-button>
    </div>

    <el-table :data="shares" v-loading="loading" stripe style="margin-top:16px">
      <el-table-column label="分享目标 ID" prop="targetFileItemId" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.shareType === 'FOLDER' ? '文件夹' : '文件' }}</template>
      </el-table-column>
      <el-table-column label="访问模式" width="100">
        <template #default="{ row }">{{ row.accessMode === 'DOWNLOAD' ? '可下载' : '仅浏览' }}</template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="访问/上限" width="100">
        <template #default="{ row }">{{ row.viewCount }}{{ row.maxViews > 0 ? '/' + row.maxViews : '' }}</template>
      </el-table-column>
      <el-table-column label="下载/上限" width="100">
        <template #default="{ row }">{{ row.downloadCount }}{{ row.maxDownloads > 0 ? '/' + row.maxDownloads : '' }}</template>
      </el-table-column>
      <el-table-column label="过期时间" width="160">
        <template #default="{ row }">{{ row.expiresAt || '永不过期' }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="160" prop="createdAt" />
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ACTIVE'" type="danger" link size="small" @click="doRevoke(row)">撤销</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total>0" style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="page" :page-size="size" :total="total" layout="prev,pager,next" @current-change="loadShares" />
    <el-empty v-if="!loading && shares.length===0" description="没有分享链接" />

    <!-- 创建分享对话框 -->
    <el-dialog v-model="showCreateDialog" title="创建分享链接" width="500px">
      <el-form :model="createForm" ref="createFormRef" label-position="top">
        <el-form-item label="目标文件/文件夹 ID" prop="targetFileItemId" required>
          <el-input v-model="createForm.targetFileItemId" placeholder="输入文件或文件夹的 ID" />
        </el-form-item>
        <el-form-item label="分享类型" required>
          <el-radio-group v-model="createForm.shareType">
            <el-radio value="FILE">文件</el-radio>
            <el-radio value="FOLDER">文件夹</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="访问模式" required>
          <el-radio-group v-model="createForm.accessMode">
            <el-radio value="VIEW">仅浏览</el-radio>
            <el-radio value="DOWNLOAD">可下载</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="提取码（可选）">
          <el-input v-model="createForm.extractionCode" placeholder="留空则不设提取码" />
        </el-form-item>
        <el-form-item label="过期时间（可选）">
          <el-date-picker v-model="createForm.expiresAt" type="datetime" placeholder="永不过期" style="width:100%" />
        </el-form-item>
        <el-form-item label="最大访问次数（0=不限）">
          <el-input-number v-model="createForm.maxViews" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item label="最大下载次数（0=不限）">
          <el-input-number v-model="createForm.maxDownloads" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <div v-if="shareResult" class="share-result">
        <el-alert type="success" :closable="false" show-icon>
          <p>分享链接已创建！</p>
          <p class="share-token">{{ shareResult }}</p>
          <el-button size="small" @click="copyShareUrl">复制链接</el-button>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="showCreateDialog = false; shareResult = ''">关闭</el-button>
        <el-button type="primary" @click="doCreateShare" :loading="creating">创建分享</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Share } from '@element-plus/icons-vue'
import { createShare, listShares, revokeShare, buildShareUrl } from '../api/shares'
import { useAuthStore } from '../stores/auth'

const authStore = useAuthStore()
const shares = ref([]); const loading = ref(false); const creating = ref(false)
const page = ref(1); const size = ref(20); const total = ref(0)
const filterStatus = ref(''); const showCreateDialog = ref(false)
const shareResult = ref('')

const createForm = reactive({
  targetFileItemId: '', shareType: 'FILE', accessMode: 'VIEW',
  extractionCode: '', expiresAt: null, maxViews: 0, maxDownloads: 0
})

onMounted(() => loadShares())

async function loadShares() {
  loading.value = true
  try {
    const { data } = await listShares({ status: filterStatus.value||undefined, page: page.value, size: size.value })
    if (data.code==='OK') { shares.value = data.data.records||[]; total.value = data.data.total||0 }
  } catch(e) { ElMessage.error('加载失败') } finally { loading.value = false }
}

async function doCreateShare() {
  if (!createForm.targetFileItemId) { ElMessage.warning('请输入目标文件/文件夹 ID'); return }
  creating.value = true
  try {
    const expiresIso = createForm.expiresAt ? new Date(createForm.expiresAt).toISOString() : null
    const { data } = await createShare({
      targetFileItemId: createForm.targetFileItemId, shareType: createForm.shareType,
      accessMode: createForm.accessMode, expiresAt: expiresIso,
      maxViews: createForm.maxViews, maxDownloads: createForm.maxDownloads,
      extractionCode: createForm.extractionCode || null
    })
    if (data.code==='OK' && data.data.token) {
      const url = buildShareUrl(data.data.token)
      shareResult.value = url
      loadShares()
    } else {
      ElMessage.error(data.message||'创建失败')
    }
  } catch(e) { ElMessage.error('创建失败') } finally { creating.value = false }
}

async function doRevoke(row) {
  try {
    await ElMessageBox.confirm('确定撤销此分享链接？','确认撤销',{type:'warning',confirmButtonText:'撤销',cancelButtonText:'取消'})
    await revokeShare(row.id)
    ElMessage.success('已撤销'); loadShares()
  } catch(e) { if(e!=='cancel') ElMessage.error('撤销失败') }
}

function copyShareUrl() {
  navigator.clipboard.writeText(shareResult.value).then(()=>ElMessage.success('已复制到剪贴板'))
}

function statusType(s) { return {ACTIVE:'success',EXPIRED:'info',REVOKED:'danger'}[s]||'info' }
function statusLabel(s) { return {ACTIVE:'有效',EXPIRED:'已过期',REVOKED:'已撤销'}[s]||s }
</script>

<style scoped>
.shares-view { max-width:1200px }
.toolbar { display:flex; align-items:center; gap:12px }
.share-result { margin-top:16px }
.share-token { font-family:monospace; word-break:break-all; font-size:12px; background:var(--el-fill-color-light); padding:8px; border-radius:4px }
</style>
