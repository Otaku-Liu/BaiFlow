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
            <img v-if="row.itemType === 'DIRECTORY'" :src="folderIconPath" class="file-type-icon" alt="" />
            <img v-else :src="fileIconPath(row.name, row.mimeType)" class="file-type-icon" alt="" />
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
      <el-table-column :label="t('files.downloadCount')" width="110" align="right">
        <template #default="{ row }">
          <el-button v-if="row.itemType === 'FILE'" link type="primary" size="small" @click="showDownloadDetails(row)">
            {{ row.downloadCount ?? 0 }}
          </el-button>
          <span v-else>-</span>
        </template>
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
            <!-- 根级主目录与隐私空间不可重命名/删除 -->
            <el-button v-if="row.parentId !== null && row.privacyMode !== 'PRIVATE'" type="warning" link size="small" @click="showRename(row)">{{ t('common.rename') }}</el-button>
            <el-button v-if="row.parentId !== null && row.privacyMode !== 'PRIVATE'" type="danger" link size="small" @click="doDelete(row)">{{ t('common.delete') }}</el-button>
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

    <!-- 下载详情弹窗 -->
    <el-dialog v-model="downloadDetailsVisible" :title="t('files.downloadDetails')" width="640px">
      <div class="download-detail-header">
        <span class="download-detail-name">{{ downloadDetailsFileName }}</span>
        <span class="download-detail-count">{{ t('files.downloadCount') }}：{{ downloadDetailsTotal }}</span>
      </div>
      <el-table :data="downloadRecords" v-loading="downloadDetailsLoading" size="small" max-height="360">
        <el-table-column :label="t('files.downloadTime')" min-width="150">
          <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column :label="t('files.downloadSource')" width="110">
          <template #default="{ row }">
            <el-tag :type="row.source === 'CLIENT' ? 'primary' : 'warning'" size="small">
              {{ row.source === 'CLIENT' ? t('files.sourceClient') : t('files.sourceShare') }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column :label="t('files.downloader')" min-width="130">
          <template #default="{ row }">
            {{ row.source === 'CLIENT' ? (row.downloaderUsername || '-') : (t('files.sourceShare') + (row.shareId ? ' · ' + row.shareId : '')) }}
          </template>
        </el-table-column>
        <el-table-column prop="ipAddress" :label="t('common.ipAddress')" min-width="120" />
      </el-table>
      <template #footer>
        <el-button @click="downloadDetailsVisible = false">{{ t('common.close') }}</el-button>
      </template>
    </el-dialog>

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

    <!-- 隐私空间首次设置密码弹窗 -->
    <el-dialog v-model="showPrivacySetup" :title="t('files.privacySetupTitle')" width="380px" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        {{ t('files.privacySetupMsg') }}
      </el-alert>
      <el-form @submit.prevent="doSetupPrivacy">
        <el-form-item :label="t('files.privacyPassword')">
          <el-input v-model="privacySetupPassword" type="password" :placeholder="t('files.privacySetupPlaceholder')" show-password />
        </el-form-item>
        <div class="dialog-footer">
          <el-button @click="cancelPrivacySetup">{{ t('common.cancel') }}</el-button>
          <el-button type="primary" native-type="submit" :loading="settingPrivacy">{{ t('files.confirmSetPrivacy') }}</el-button>
        </div>
      </el-form>
      <p v-if="privacySetupError" class="error-msg">{{ privacySetupError }}</p>
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
import { ref, computed, watch, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { Upload, FolderAdd, UploadFilled } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { useFileStore } from '../stores/file'
import {
  listFiles, uploadFile, downloadFile, createFolder, renameFile, deleteFile,
  setPrivacy, verifyPrivacy, listStorageRoots, getFileDownloads
} from '../api/files'
import { listUsers } from '../api/users'
import { formatDateTime, formatSize } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'
import PreviewDrawer from '../components/PreviewDrawer.vue'
import { mimeCategory, canPreview, fileIconPath, folderIconPath } from '../utils/mime'

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

// ---- 浏览路径持久化：刷新浏览器后保持当前目录（localStorage，沿用 baiflow_ 前缀惯例）----
const FILE_PATH_KEY = 'baiflow_file_breadcrumb'

function persistFilePath() {
  try {
    localStorage.setItem(FILE_PATH_KEY, JSON.stringify({
      rootId: rootId.value,
      breadcrumb: fileStore.breadcrumb,
      viewUserId: viewUserId.value || ''
    }))
  } catch { /* localStorage 不可用时忽略，不影响浏览 */ }
}

function restoreFilePath() {
  try {
    const raw = localStorage.getItem(FILE_PATH_KEY)
    return raw ? JSON.parse(raw) : null
  } catch { return null }
}

// 路径（根/面包屑/管理员查看的用户空间）变化即落盘，覆盖 navigateTo/goUp/换用户空间 等所有入口
watch(() => [fileStore.currentRootId, fileStore.breadcrumb, viewUserId.value], persistFilePath, { deep: true })

const loading = ref(false)
const showUploadDialog = ref(false)
const showNewFolderDialog = ref(false)
const showRenameDialog = ref(false)
const showPrivacyVerify = ref(false)
const showPrivacySetup = ref(false)
const uploadFiles = ref([])
const uploading = ref(false)
const creating = ref(false)
const renaming = ref(false)
const settingPrivacy = ref(false)
const privacyVerifying = ref(false)
const newFolderName = ref('')
const renameName = ref('')
const renameTarget = ref(null)
const privacySetupPassword = ref('')
const privacySetupError = ref('')
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
    // 每次进入隐私空间都要验证密码：清掉缓存令牌，让后端返回 40105/40107 触发弹窗
    if (row.privacyMode === 'PRIVATE') {
      fileStore.clearPrivacyToken(row.id)
    }
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

  // 恢复刷新前浏览的路径：存储根一致才恢复（面包屑 + 管理员查看的用户空间）
  const saved = restoreFilePath()
  if (saved && saved.rootId === rootId.value) {
    fileStore.breadcrumb = Array.isArray(saved.breadcrumb) ? saved.breadcrumb : []
    if (saved.viewUserId && authStore.isAdmin) {
      viewUserId.value = saved.viewUserId
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
    const token = fileStore.currentPrivacyToken
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

// 下载详情弹窗
const downloadDetailsVisible = ref(false)
const downloadDetailsLoading = ref(false)
const downloadDetailsFileName = ref('')
const downloadDetailsTotal = ref(0)
const downloadRecords = ref([])

/** 查看文件的下载记录（CLIENT + SHARE 来源、下载人、时间） */
async function showDownloadDetails(row) {
  downloadDetailsFileName.value = row.name
  downloadDetailsTotal.value = row.downloadCount ?? 0
  downloadDetailsVisible.value = true
  downloadDetailsLoading.value = true
  try {
    const res = await getFileDownloads(row.id, 1, 20)
    downloadRecords.value = res.data.data?.records || []
    downloadDetailsTotal.value = res.data.data?.total ?? downloadDetailsTotal.value
  } catch (e) {
    ElMessage.error(t('files.downloadDetailsLoadFailed'))
  } finally {
    downloadDetailsLoading.value = false
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

/** 隐私空间首次访问：设置密码（40107 触发）；设密后立即用同一密码换取令牌进入 */
function cancelPrivacySetup() {
  showPrivacySetup.value = false
  privacyPendingCallback.value = null
}

async function doSetupPrivacy() {
  if (!privacySetupPassword.value || privacySetupPassword.value.length < 4) {
    ElMessage.warning(t('files.privacyPwdMinLength')); return
  }
  settingPrivacy.value = true
  privacySetupError.value = ''
  try {
    const folderId = privacyPendingFolderId.value
    const setResp = await setPrivacy(folderId, privacySetupPassword.value)
    if (setResp.data?.code !== 0) {
      privacySetupError.value = setResp.data?.message || t('files.privacySetFailed')
      return
    }
    // 设密后立即验证换取访问令牌，直接进入隐私空间
    const { data } = await verifyPrivacy(folderId, privacySetupPassword.value)
    if (data.code === 0) {
      fileStore.savePrivacyToken(folderId, data.data.accessToken)
      ElMessage.success(t('files.privacySet'))
      showPrivacySetup.value = false
      if (privacyPendingCallback.value) {
        privacyPendingCallback.value()
        privacyPendingCallback.value = null
      }
    } else {
      privacySetupError.value = data.message || t('files.privacySetFailed')
    }
  } catch (e) {
    privacySetupError.value = e.response?.data?.message || t('files.privacySetFailed')
  } finally {
    settingPrivacy.value = false
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
  try {
    await callback(currentToken)
  } catch (e) {
    const code = e.response?.data?.code
    if (isPrivacyCode(code)) {
      // 需要隐私密码——弹出验证/首次设置对话框
      privacyPendingFolderId.value = fileItemId
      privacyPendingCallback.value = () => {
        const newToken = fileStore.getPrivacyToken(privacyPendingFolderId.value)
        callback(newToken).catch(err => {
          ElMessage.error(err.response?.data?.message || t('files.operationFailed'))
        })
      }
      showPrivacyDialog(code, e.response?.data?.message || '')
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
const isPrivacyCode = (code) => code === 40105 || code === 40106 || code === 40107

/** 打开隐私弹窗：40107 首次设置密码，其余输入密码验证 */
function showPrivacyDialog(code, message) {
  if (code === 40107) {
    privacySetupPassword.value = ''
    privacySetupError.value = message || ''
    showPrivacySetup.value = true
  } else {
    privacyPendingPassword.value = ''
    privacyError.value = message || ''
    showPrivacyVerify.value = true
  }
}

/**
 * 打开隐私密码弹窗并绑定当前目录的重试：必须带上当前目录 ID
 * （此前缺失导致 verifyPrivacy(null) 报「文件项不存在」）。
 */
function openPrivacyDialog(code, message) {
  privacyPendingFolderId.value = fileStore.currentFolderId
  privacyPendingCallback.value = () => loadFiles()
  showPrivacyDialog(code, message)
}

function handleApiError(data) {
  if (isPrivacyCode(data.code)) {
    openPrivacyDialog(data.code, data.message || '')
  } else {
    ElMessage.error(data.message || t('files.operationFailed'))
  }
}

function handleHttpError(e) {
  const code = e.response?.data?.code
  if (isPrivacyCode(code)) {
    openPrivacyDialog(code, e.response?.data?.message || '')
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

/* 文件类型图标（200x200 PNG，缩小展示） */
.file-type-icon {
  width: 18px;
  height: 18px;
  object-fit: contain;
  flex-shrink: 0;
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
