<template>
  <div class="files-view">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <!-- 管理员用户空间切换 -->
      <el-select v-if="authStore.isAdmin && viewUsers.length > 0" v-model="viewUserId"
        :placeholder="t('files.viewUserSpace')" clearable @change="onViewUserChange" style="width:180px">
        <el-option v-for="u in viewUsers" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
      </el-select>

      <!-- 面包屑导航 -->
      <div v-if="rootId" class="breadcrumb-wrapper">
        <el-button size="small" :disabled="!canGoUp" @click="goUp" style="margin-right:8px">↑ {{ t('files.upLevel') }}</el-button>
        <span class="breadcrumb-label">{{ t('files.currentPath') }}</span>
        <el-breadcrumb separator="/" class="breadcrumb">
          <el-breadcrumb-item>
            <el-link type="primary" @click="navigateTo(null)">{{ rootLabel }}</el-link>
          </el-breadcrumb-item>
          <el-breadcrumb-item v-for="(item, idx) in fileStore.breadcrumb" :key="item.id">
            <el-link type="primary" @click="navigateTo(item)">{{ item.name }}</el-link>
            <!-- 隐私文件夹标记 -->
            <el-tag v-if="item.privacyMode === 'PRIVATE'" size="small" type="warning" style="margin-left:4px">{{ t('files.privacy') }}</el-tag>
          </el-breadcrumb-item>
        </el-breadcrumb>
      </div>

      <div class="toolbar-actions">
        <el-button type="primary" @click="showUploadDialog = true" :disabled="!rootId">
          <el-icon><Upload /></el-icon> {{ t('files.uploadFile') }}
        </el-button>
        <el-button @click="showNewFolderDialog = true" :disabled="!rootId">
          <el-icon><FolderAdd /></el-icon> {{ t('files.newFolder') }}
        </el-button>
      </div>
    </div>

    <!-- 隐私密码验证弹窗 -->
    <el-dialog v-model="showPrivacyVerify" :title="t('files.privacyVerifyTitle')" width="400px" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        {{ t('files.privacyVerifyMsg') }}
      </el-alert>
      <el-form @submit.prevent="doVerifyPrivacy">
        <el-form-item :label="t('files.privacyPassword')">
          <el-input v-model="privacyPendingPassword" type="password" :placeholder="t('files.privacyPasswordPlaceholder')" show-password />
        </el-form-item>
        <div class="dialog-footer">
          <el-button @click="cancelPrivacyVerify">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="privacyVerifying">{{ t('files.verify') }}</el-button>
        </div>
      </el-form>
      <p v-if="privacyError" class="error-msg">{{ privacyError }}</p>
    </el-dialog>

    <!-- 文件列表表格 -->
    <el-table :data="fileStore.items" v-loading="loading" stripe style="margin-top:16px" @row-dblclick="onRowDblClick">
      <el-table-column :label="t('common.name')" min-width="280">
        <template #default="{ row }">
          <div class="name-cell">
            <el-icon :size="18">
              <Folder v-if="row.itemType === 'DIRECTORY'" color="#409EFF" />
              <Document v-else />
            </el-icon>
            <span style="margin-left:8px">{{ row.name }}</span>
            <el-tag v-if="row.privacyMode === 'PRIVATE'" size="small" type="warning" style="margin-left:6px">{{ t('files.privacy') }}</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column :label="t('common.size')" align="right">
        <template #default="{ row }">{{ row.itemType === 'DIRECTORY' ? '-' : formatSize(row.sizeBytes) }}</template>
      </el-table-column>
<el-table-column :label="t('files.uploadTime')">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="300" fixed="right">
        <template #default="{ row }">
          <div class="action-btns">
            <el-button v-if="row.itemType === 'DIRECTORY'" type="primary" link size="small" @click="navigateTo(row)">
              {{ t('common.open') }}
            </el-button>
            <el-button v-if="row.itemType === 'FILE'" type="primary" link size="small" @click="showPreview(row)">
              {{ t('preview.preview') }}
            </el-button>
            <el-button v-if="row.itemType === 'FILE'" type="primary" link size="small" @click="doDownload(row)">
              {{ t('common.download') }}
            </el-button>
            <el-button type="warning" link size="small" @click="showRename(row)">{{ t('common.rename') }}</el-button>
            <el-button v-if="row.itemType === 'DIRECTORY' && row.privacyMode !== 'PRIVATE'"
              type="info" link size="small" @click="showSetPrivacy(row)">{{ t('files.setPrivacy') }}</el-button>
            <el-button v-if="row.privacyMode === 'PRIVATE'"
              type="info" link size="small" @click="doRemovePrivacy(row)">{{ t('files.removePrivacy') }}</el-button>
            <el-button type="danger" link size="small" @click="doDelete(row)">{{ t('common.delete') }}</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination v-if="fileStore.total > 0" style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="fileStore.page" :page-size="fileStore.size" :total="fileStore.total"
      layout="prev, pager, next" @current-change="loadFiles" />

    <!-- 空状态 -->
    <el-empty v-if="!loading && rootId && fileStore.items.length === 0" :description="t('files.emptyDirectory')" />

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" :title="t('files.uploadTitle')" width="450px">
      <el-upload ref="uploadRef" :auto-upload="false" :limit="5" drag
        :before-upload="() => false" :on-change="onFileSelected"
        :file-list="uploadFiles">
        <el-icon :size="48"><UploadFilled /></el-icon>
        <div>{{ t('files.dragOrClick') }}</div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doUpload" :loading="uploading">{{ t('common.upload') }}</el-button>
      </template>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="showNewFolderDialog" :title="t('files.newFolderTitle')" width="350px">
      <el-input v-model="newFolderName" :placeholder="t('files.newFolderPlaceholder')" @keyup.enter="doCreateFolder" />
      <template #footer>
        <el-button @click="showNewFolderDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doCreateFolder" :loading="creating">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重命名对话框 -->
    <el-dialog v-model="showRenameDialog" :title="t('files.renameTitle')" width="350px">
      <el-input v-model="renameName" :placeholder="t('files.renamePlaceholder')" @keyup.enter="doRename" />
      <template #footer>
        <el-button @click="showRenameDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doRename" :loading="renaming">{{ t('common.confirm') }}</el-button>
      </template>
    </el-dialog>

    <!-- 通用确认弹窗（替换 ElMessageBox） -->
    <ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />

    <!-- 设置隐私密码对话框 -->
    <el-dialog v-model="showSetPrivacyDialog" :title="t('files.setPrivacyTitle')" width="380px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
        {{ t('files.setPrivacyMsg') }}
      </el-alert>
      <el-input v-model="privacyPassword" type="password" :placeholder="t('files.setPrivacyPlaceholder')" show-password />
      <template #footer>
        <el-button @click="showSetPrivacyDialog = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" @click="doSetPrivacy" :loading="settingPrivacy">{{ t('files.confirmSetPrivacy') }}</el-button>
      </template>
    </el-dialog>

    <!-- 预览抽屉 -->
    <PreviewDrawer
      :file-item="previewFile"
      :visible="previewVisible"
      @close="previewVisible = false"
    />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Folder, Document, Upload, FolderAdd, UploadFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useFileStore } from '../stores/file'
import {
  listFiles, uploadFile, downloadFile, createFolder, renameFile, deleteFile,
  setPrivacy, removePrivacy, verifyPrivacy, listStorageRoots
} from '../api/files'
import { listUsers } from '../api/users'
import { formatDateTime, formatSize } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PreviewDrawer from '../components/PreviewDrawer.vue'
import { mimeCategory, canPreview } from '../utils/mime'

const authStore = useAuthStore()
const fileStore = useFileStore()
const { t } = useI18n()
const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()

/** 面包屑根节点显示名称：管理员视角为空时显示"根目录"，否则显示用户名或"我的文件" */
const rootLabel = computed(() => {
  if (!authStore.isAdmin) return t('files.myFiles')
  if (!viewUserId.value) return t('files.rootDirectory')
  if (viewUserId.value === authStore.user?.id) return t('files.myFiles')
  const u = viewUsers.value.find(u => u.id === viewUserId.value)
  return u ? (u.displayName || u.username) : t('files.myFiles')
})

// ---- 状态 ----
const roots = ref([])
const rootId = ref('')
const viewUsers = ref([])
const viewUserId = ref('')
const loading = ref(false)
const showUploadDialog = ref(false)
const showNewFolderDialog = ref(false)
const showRenameDialog = ref(false)
const showSetPrivacyDialog = ref(false)
const showPrivacyVerify = ref(false)
const uploadFiles = ref([])
const uploading = ref(false)
const creating = ref(false)
const renaming = ref(false)
const settingPrivacy = ref(false)
const privacyVerifying = ref(false)
const newFolderName = ref('')
const renameName = ref('')
const renameTarget = ref(null)
const privacyTarget = ref(null)
const privacyPassword = ref('')
const privacyPendingPassword = ref('')
const privacyPendingFolderId = ref(null)
const privacyPendingCallback = ref(null)
const privacyError = ref('')

// ---- 预览 ----
const previewVisible = ref(false)
const previewFile = ref(null)

function showPreview(row) {
  previewFile.value = row
  previewVisible.value = true
}

function onRowDblClick(row) {
  if (row.itemType === 'DIRECTORY') {
    navigateTo(row)
  } else if (canPreview(row.mimeType)) {
    showPreview(row)
  }
}

// ---- 初始化 ----
onMounted(async () => {
  // 并行加载存储根目录和管理员用户列表
  const [rootsResult, usersResult] = await Promise.allSettled([
    listStorageRoots(),
    authStore.isAdmin ? listUsers({ page: 1, size: 100 }) : Promise.resolve(null)
  ])

  // 处理存储根目录
  if (rootsResult.status === 'fulfilled') {
    const { data } = rootsResult.value
    if (data.code === 0 && data.data?.length > 0) {
      roots.value = data.data
      rootId.value = data.data[0].id
      fileStore.setCurrentRoot(rootId.value)
    } else {
      console.warn('没有可用的存储根目录，上传和新建文件夹按钮将保持禁用')
    }
  } else {
    console.error('获取存储根目录失败:', rootsResult.reason?.response?.status,
      rootsResult.reason?.response?.data?.message || rootsResult.reason?.message)
  }

  // 管理员加载用户列表（用于空间切换），默认选中当前登录用户
  if (authStore.isAdmin && usersResult.status === 'fulfilled' && usersResult.value) {
    const res = usersResult.value
    if (res.data.code === 0) {
      viewUsers.value = res.data.data?.records || []
      // 默认选中当前登录用户，避免展示所有用户的文件
      if (authStore.user?.id) {
        viewUserId.value = authStore.user.id
      }
    }
  }

  // 两边都就绪后再加载文件列表，确保 viewUserId 已设置
  if (rootId.value) {
    loadFiles()
  }
})

// ---- 管理员用户空间切换 ----
function onViewUserChange(id) {
  viewUserId.value = id || ''
  fileStore.breadcrumb = []
  fileStore.page = 1
  loadFiles()
}

// ---- 文件操作 ----
async function loadFiles() {
  if (!rootId.value) return
  loading.value = true
  try {
    const folderId = fileStore.currentFolderId
    const token = fileStore.getPrivacyToken(folderId)
    const params = {
      storageRootId: rootId.value,
      parentId: folderId || undefined,
      page: fileStore.page,
      size: fileStore.size
    }
    // 管理员选择查看其他用户空间时传递 viewUserId
    if (authStore.isAdmin && viewUserId.value) {
      params.viewUserId = viewUserId.value
    }
    const { data } = await listFiles(params, token)
    if (data.code === 0) {
      fileStore.items = data.data.records || []
      fileStore.total = data.data.total || 0
    } else {
      handleApiError(data)
    }
  } catch (e) {
    handleHttpError(e)
  } finally {
    loading.value = false
  }
}

/** 导航到指定目录 */
function navigateTo(item) {
  if (item == null) {
    // 导航到根目录
    fileStore.breadcrumb = []
  } else {
    // 截断面包屑到此项
    const idx = fileStore.breadcrumb.findIndex(b => b.id === item.id)
    if (idx >= 0) {
      fileStore.breadcrumb = fileStore.breadcrumb.slice(0, idx + 1)
    } else {
      fileStore.breadcrumb.push(item)
    }
  }
  fileStore.page = 1
  loadFiles()
}

/** 根目录时不可返回上一级（按钮置灰） */
const canGoUp = computed(() => fileStore.breadcrumb.length > 0)

/** 返回上一级 */
function goUp() {
  const b = fileStore.breadcrumb
  if (b.length === 0) return
  if (b.length === 1) {
    navigateTo(null)          // 上一级 = 根目录
  } else {
    navigateTo(b[b.length - 2]) // 上一级 = 面包屑倒数第二项
  }
}

/** 上传 */
function onFileSelected(file) {
  uploadFiles.value.push(file)
}

async function doUpload() {
  const pending = uploadFiles.value.filter(f => f.raw)
  if (pending.length === 0) { ElMessage.warning(t('files.selectFile')); return }
  uploading.value = true
  try {
    const token = fileStore.currentPrivacyToken
    for (const f of pending) {
      const { data } = await uploadFile({
        storageRootId: rootId.value,
        parentId: fileStore.currentFolderId,
        file: f.raw,
        viewUserId: authStore.isAdmin && viewUserId.value ? viewUserId.value : undefined
      }, token)
      if (data.code !== 0) {
        ElMessage.error(data.message || t('files.uploadFailed'))
        uploading.value = false
        return
      }
    }
    ElMessage.success(t('files.uploadSuccess'))
    showUploadDialog.value = false
    uploadFiles.value = []
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('files.uploadFailed'))
  } finally {
    uploading.value = false
  }
}

/** 下载 */
async function doDownload(row) {
  try {
    const token = fileStore.getPrivacyToken(fileStore.currentFolderId)
    await checkParentPrivacy(row.id, token, async (effectiveToken) => {
      const resp = await downloadFile(row.id, effectiveToken)
      const blob = resp.data
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = row.name; a.click()
      URL.revokeObjectURL(url)
    })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || t('files.operationFailed'))
  }
}

/** 新建文件夹 */
async function doCreateFolder() {
  if (!newFolderName.value.trim()) { ElMessage.warning(t('files.inputFolderName')); return }
  creating.value = true
  try {
    const token = fileStore.currentPrivacyToken
    const { data } = await createFolder({
      storageRootId: rootId.value,
      parentId: fileStore.currentFolderId,
      name: newFolderName.value.trim(),
      viewUserId: authStore.isAdmin && viewUserId.value ? viewUserId.value : undefined
    }, token)
    if (data.code === 0) {
      ElMessage.success(t('files.folderCreated'))
      showNewFolderDialog.value = false
      newFolderName.value = ''
      loadFiles()
    } else {
      ElMessage.error(data.message || t('files.createFailed'))
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('files.createFailed'))
  } finally {
    creating.value = false
  }
}

/** 重命名 */
function showRename(row) {
  renameTarget.value = row
  renameName.value = row.name
  showRenameDialog.value = true
}

async function doRename() {
  if (!renameName.value.trim()) { ElMessage.warning(t('files.inputNewName')); return }
  renaming.value = true
  try {
    const token = fileStore.currentPrivacyToken
    await renameFile(renameTarget.value.id, renameName.value.trim(), token)
    ElMessage.success(t('files.renameSuccess'))
    showRenameDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('files.renameFailed'))
  } finally {
    renaming.value = false
  }
}

/** 删除 */
async function doDelete(row) {
  try {
    await confirm({
      title: t('files.deleteConfirmTitle'),
      message: t('files.deleteConfirmMsg', { name: row.name }),
      confirmText: t('common.delete'),
      type: 'warning'
    })
    const token = fileStore.currentPrivacyToken
    await deleteFile(row.id, token)
    ElMessage.success(t('files.deleted'))
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('files.deleteFailed'))
  }
}

/** 设置隐私密码 */
function showSetPrivacy(row) {
  privacyTarget.value = row
  privacyPassword.value = ''
  showSetPrivacyDialog.value = true
}

async function doSetPrivacy() {
  if (!privacyPassword.value || privacyPassword.value.length < 4) {
    ElMessage.warning(t('files.privacyPwdMinLength')); return
  }
  settingPrivacy.value = true
  try {
    await setPrivacy(privacyTarget.value.id, privacyPassword.value)
    ElMessage.success(t('files.privacySet'))
    showSetPrivacyDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('files.privacySetFailed'))
  } finally {
    settingPrivacy.value = false
  }
}

/** 取消隐私保护 */
async function doRemovePrivacy(row) {
  try {
    await confirm({
      title: t('files.removePrivacyTitle'),
      message: t('files.removePrivacyMsg', { name: row.name }),
      confirmText: t('common.confirm'),
      type: 'warning'
    })
    await removePrivacy(row.id)
    // 清除本地存储的访问令牌
    fileStore.clearPrivacyToken(row.id)
    ElMessage.success(t('files.privacyRemoved'))
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('files.operationFailed'))
  }
}

// ---- 隐私访问验证 ----

/**
 * 在操作前校验父目录链上是否有隐私文件夹。
 * 如果有且当前无有效令牌，则弹出验证弹窗。
 * @param {string} fileItemId - 操作目标文件项 ID
 * @param {string|null} currentToken - 当前目录的令牌
 * @param {Function} callback - 验证通过后执行的回调，传入有效令牌
 */
async function checkParentPrivacy(fileItemId, currentToken, callback) {
  // 简化处理：使用当前目录令牌尝试访问
  // 如果后端返回 40105（PRIVATE_PASSWORD_REQUIRED），则弹出验证
  try {
    await callback(currentToken)
  } catch (e) {
    const code = e.response?.data?.code
    if (code === 40105 || code === 40106) {
      // 需要验证隐私密码——弹出验证对话框
      privacyPendingFolderId.value = fileItemId
      privacyPendingCallback.value = () => {
        const newToken = fileStore.getPrivacyToken(privacyPendingFolderId.value)
        callback(newToken).catch(err => {
          ElMessage.error(err.response?.data?.message || t('files.operationFailed'))
        })
      }
      privacyPendingPassword.value = ''
      privacyError.value = ''
      showPrivacyVerify.value = true
    } else {
      ElMessage.error(e.response?.data?.message || '操作失败')
    }
  }
}

/** 验证隐私密码 */
async function doVerifyPrivacy() {
  if (!privacyPendingPassword.value) { privacyError.value = t('files.privacyPwdRequired'); return }
  privacyVerifying.value = true
  privacyError.value = ''
  try {
    const { data } = await verifyPrivacy(privacyPendingFolderId.value, privacyPendingPassword.value)
    if (data.code === 0) {
      // 保存访问令牌
      fileStore.savePrivacyToken(privacyPendingFolderId.value, data.data.accessToken)
      ElMessage.success(t('files.verifySuccess'))
      showPrivacyVerify.value = false
      // 重试之前失败的操作
      if (privacyPendingCallback.value) {
        privacyPendingCallback.value()
        privacyPendingCallback.value = null
      }
    } else {
      privacyError.value = data.message || '验证失败'
    }
  } catch (e) {
    privacyError.value = e.response?.data?.message || '验证失败'
  } finally {
    privacyVerifying.value = false
  }
}

function cancelPrivacyVerify() {
  showPrivacyVerify.value = false
  privacyPendingCallback.value = null
}

// ---- 错误处理 ----
function handleApiError(data) {
  if (data.code === 40105 || data.code === 40106) {
    showPrivacyVerify.value = true
    privacyPendingPassword.value = ''
    privacyError.value = data.message || ''
    privacyPendingCallback.value = () => loadFiles()
  } else {
    ElMessage.error(data.message || t('files.operationFailed'))
  }
}

function handleHttpError(e) {
  const code = e.response?.data?.code
  if (code === 40105 || code === 40106) {
    showPrivacyVerify.value = true
    privacyPendingPassword.value = ''
    privacyError.value = e.response?.data?.message || ''
    privacyPendingCallback.value = () => loadFiles()
  } else {
    ElMessage.error(e.response?.data?.message || t('files.requestFailed'))
  }
}

</script>

<style scoped>
.files-view { width: 100%; }

/* 表格单元格：除名称列外不换行 */
.files-view :deep(.el-table__body td) {
  white-space: nowrap;
}
.files-view :deep(.el-table__body td:first-child) {
  white-space: normal;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  padding-bottom: 12px;
}

.breadcrumb-wrapper {
  display: flex;
  align-items: center;
  gap: 6px;
  flex: 1;
}

.breadcrumb-label {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  white-space: nowrap;
  user-select: none;
}

.breadcrumb {
  font-size: 14px;
}

.breadcrumb :deep(.el-breadcrumb__inner) {
  font-weight: 500;
}

.toolbar-actions {
  display: flex;
  gap: 10px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
}

.name-cell {
  display: flex;
  align-items: center;
  font-weight: 500;
}

.action-btns {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
}

.error-msg {
  color: var(--el-color-danger);
  margin-top: 12px;
  font-size: 13px;
}
</style>
