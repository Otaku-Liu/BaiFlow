<template>
  <div class="shares-view">
    <div class="toolbar">
      <el-button type="primary" @click="showCreateDialog = true">
        <el-icon><Share /></el-icon> {{ t('shares.createLink') }}
      </el-button>
      <el-select v-model="filterStatus" clearable @change="loadShares" :placeholder="t('shares.statusFilter')" style="width:140px">
        <el-option :label="t('shares.status.all')" value="" />
        <el-option :label="t('shares.status.active')" value="ACTIVE" />
        <el-option :label="t('shares.status.expired')" value="EXPIRED" />
        <el-option :label="t('shares.status.revoked')" value="REVOKED" />
      </el-select>
      <el-button :loading="loading" @click="loadShares" style="margin-left:auto">{{ t('common.refresh') }}</el-button>
    </div>

    <el-table :data="shares" v-loading="loading" stripe style="margin-top:16px">
      <el-table-column :label="t('shares.shareTargetId')" prop="targetFileItemId" min-width="180" />
      <el-table-column :label="t('common.type')" width="100">
        <template #default="{ row }">{{ row.shareType === 'FOLDER' ? t('common.folder') : t('common.file') }}</template>
      </el-table-column>
      <el-table-column :label="t('shares.accessMode')" width="100">
        <template #default="{ row }">{{ row.accessMode === 'DOWNLOAD' ? t('shares.downloadable') : t('shares.viewable') }}</template>
      </el-table-column>
      <el-table-column :label="t('common.status')" width="80">
        <template #default="{ row }">
          <el-tag :type="statusType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column :label="t('shares.viewLimit')" width="100">
        <template #default="{ row }">{{ row.viewCount }}{{ row.maxViews > 0 ? '/' + row.maxViews : '' }}</template>
      </el-table-column>
      <el-table-column :label="t('shares.downloadLimit')" width="100">
        <template #default="{ row }">{{ row.downloadCount }}{{ row.maxDownloads > 0 ? '/' + row.maxDownloads : '' }}</template>
      </el-table-column>
      <el-table-column :label="t('shares.expireTime')" width="160">
        <template #default="{ row }">{{ row.expiresAt ? formatDateTime(row.expiresAt) : t('common.never') }}</template>
      </el-table-column>
      <el-table-column :label="t('common.createdAt')" width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="180" fixed="right">
        <template #default="{ row }">
          <el-button v-if="row.status === 'ACTIVE'" type="danger" link size="small" @click="doRevoke(row)">{{ t('common.revoke') }}</el-button>
          <el-button v-if="authStore.isAdmin" link size="small" @click="showAnalytics(row)">{{ t('common.analyze') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination v-if="total>0" style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="page" :page-size="size" :total="total" layout="prev,pager,next" @current-change="loadShares" />
    <el-empty v-if="!loading && shares.length===0" :description="t('shares.noShares')" />

    <!-- 创建分享对话框 -->
    <el-dialog v-model="showCreateDialog" :title="t('shares.createDialogTitle')" width="500px">
      <el-form :model="createForm" ref="createFormRef" label-position="top">
        <el-form-item :label="t('shares.targetFileId')" prop="targetFileItemId" required>
          <el-input v-model="createForm.targetFileItemId" :placeholder="t('shares.targetFileIdPlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('shares.shareType')" required>
          <el-radio-group v-model="createForm.shareType">
            <el-radio value="FILE">{{ t('common.file') }}</el-radio>
            <el-radio value="FOLDER">{{ t('common.folder') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('shares.accessMode')" required>
          <el-radio-group v-model="createForm.accessMode">
            <el-radio value="VIEW">{{ t('shares.viewable') }}</el-radio>
            <el-radio value="DOWNLOAD">{{ t('shares.downloadable') }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item :label="t('shares.extractionCode')">
          <el-input v-model="createForm.extractionCode" :placeholder="t('shares.extractionCodePlaceholder')" />
        </el-form-item>
        <el-form-item :label="t('shares.expireTimeOpt')">
          <el-date-picker v-model="createForm.expiresAt" type="datetime" :placeholder="t('shares.expireTimePlaceholder')" style="width:100%" />
        </el-form-item>
        <el-form-item :label="t('shares.maxViews')">
          <el-input-number v-model="createForm.maxViews" :min="0" :max="9999" />
        </el-form-item>
        <el-form-item :label="t('shares.maxDownloads')">
          <el-input-number v-model="createForm.maxDownloads" :min="0" :max="9999" />
        </el-form-item>
      </el-form>
      <div v-if="shareResult" class="share-result">
        <el-alert type="success" :closable="false" show-icon>
          <p>{{ t('shares.shareCreated') }}</p>
          <p class="share-token">{{ shareResult }}</p>
          <el-button size="small" @click="copyShareUrl">{{ t('shares.copyLink') }}</el-button>
        </el-alert>
      </div>
      <template #footer>
        <el-button @click="showCreateDialog = false; shareResult = ''">{{ t('common.close') }}</el-button>
        <el-button type="primary" @click="doCreateShare" :loading="creating">{{ t('shares.createShare') }}</el-button>
      </template>
    </el-dialog>

    <!-- 通用确认弹窗 -->
    <ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />

    <!-- 分享分析抽屉 -->
    <el-drawer v-model="analyticsVisible" :title="t('shares.analyticsTitle')" size="600px">
      <el-table :data="analyticsLogs" v-loading="analyticsLoading" stripe>
        <el-table-column prop="action" :label="t('common.actions')" width="120">
          <template #default="{ row }">
            <el-tag :type="row.success ? 'success' : 'danger'" size="small">{{ row.action }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" :label="t('common.ipAddress')" width="140" />
        <el-table-column prop="userAgent" :label="t('common.userAgent')" min-width="180" show-overflow-tooltip />
        <el-table-column prop="failureReason" :label="t('shares.failureReason')" min-width="120" show-overflow-tooltip />
        <el-table-column prop="createdAt" :label="t('common.time')" width="160">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
      </el-table>
      <el-empty v-if="!analyticsLoading && analyticsLogs.length === 0" :description="t('shares.noAccessRecords')" />
    </el-drawer>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { ElMessage } from 'element-plus'
import { Share } from '@element-plus/icons-vue'
import { createShare, listShares, revokeShare, buildShareUrl, getShareAnalytics } from '../api/shares'
import { useAuthStore } from '../stores/auth'
import { formatDateTime } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const authStore = useAuthStore()
const { t } = useI18n()
const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()
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
  } catch(e) { ElMessage.error(t('shares.loadFailed')) } finally { loading.value = false }
}

async function doCreateShare() {
  if (!createForm.targetFileItemId) { ElMessage.warning(t('shares.inputTargetId')); return }
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
      ElMessage.error(data.message||t('shares.createFailed'))
    }
  } catch(e) { ElMessage.error(t('shares.createFailed')) } finally { creating.value = false }
}

async function doRevoke(row) {
  try {
    await confirm({ title: t('shares.revokeConfirmTitle'), message: t('shares.revokeConfirmMsg'), confirmText: t('common.revoke'), type: 'warning' })
    await revokeShare(row.id)
    ElMessage.success(t('shares.revoked')); loadShares()
  } catch(e) { if(e!=='cancel') ElMessage.error(t('shares.revokeFailed')) }
}

function copyShareUrl() {
  navigator.clipboard.writeText(shareResult.value).then(()=>ElMessage.success(t('shares.copied')))
}

// 分析相关
const analyticsVisible = ref(false)
const analyticsLoading = ref(false)
const analyticsLogs = ref([])

async function showAnalytics(row) {
  analyticsVisible.value = true
  analyticsLoading.value = true
  analyticsLogs.value = []
  try {
    const { data } = await getShareAnalytics(row.id)
    if (data.code === 'OK') {
      analyticsLogs.value = data.data?.records || []
    }
  } catch (e) {
    ElMessage.error(t('shares.analyticsFailed'))
  } finally {
    analyticsLoading.value = false
  }
}

function statusType(s) { return {ACTIVE:'success',EXPIRED:'info',REVOKED:'danger'}[s]||'info' }
function statusLabel(s) { return {ACTIVE:t('shares.status.active'),EXPIRED:t('shares.status.expired'),REVOKED:t('shares.status.revoked')}[s]||s }
</script>

<style scoped>
.shares-view { max-width: 1200px; }

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  padding-bottom: 12px;
}

.share-result { margin-top: 16px; }

.share-token {
  font-family: "SF Mono", "JetBrains Mono", "Fira Code", monospace;
  word-break: break-all;
  font-size: 12px;
  background: var(--el-fill-color-light);
  padding: 10px;
  border-radius: var(--baiflow-radius-md);
  color: var(--el-text-color-primary);
}
</style>
