<template>
  <div class="files-view">
    <!-- 顶部工具栏 -->
    <div class="toolbar">
      <!-- Storage Root 选择器 -->
      <el-select v-model="rootId" placeholder="选择存储根目录" @change="onRootChange" style="width:200px">
        <el-option v-for="r in roots" :key="r.id" :label="r.name" :value="r.id" />
      </el-select>

      <!-- 面包屑导航 -->
      <el-breadcrumb separator="/" v-if="rootId" class="breadcrumb">
        <el-breadcrumb-item>
          <el-link type="primary" @click="navigateTo(null)">根目录</el-link>
        </el-breadcrumb-item>
        <el-breadcrumb-item v-for="(item, idx) in fileStore.breadcrumb" :key="item.id">
          <el-link type="primary" @click="navigateTo(item)">{{ item.name }}</el-link>
          <!-- 隐私文件夹标记 -->
          <el-tag v-if="item.privacyMode === 'PRIVATE'" size="small" type="warning" style="margin-left:4px">隐私</el-tag>
        </el-breadcrumb-item>
      </el-breadcrumb>

      <div class="toolbar-actions">
        <el-button type="primary" @click="showUploadDialog = true" :disabled="!rootId">
          <el-icon><Upload /></el-icon> 上传文件
        </el-button>
        <el-button @click="showNewFolderDialog = true" :disabled="!rootId">
          <el-icon><FolderAdd /></el-icon> 新建文件夹
        </el-button>
      </div>
    </div>

    <!-- 隐私密码验证弹窗 -->
    <el-dialog v-model="showPrivacyVerify" title="隐私文件夹访问验证" width="400px" :close-on-click-modal="false" :close-on-press-escape="false">
      <el-alert type="warning" :closable="false" show-icon style="margin-bottom:16px">
        此文件夹受隐私保护，需要输入隐私密码才能访问。
      </el-alert>
      <el-form @submit.prevent="doVerifyPrivacy">
        <el-form-item label="隐私密码">
          <el-input v-model="privacyPendingPassword" type="password" placeholder="请输入隐私密码" show-password />
        </el-form-item>
        <div style="text-align:right">
          <el-button @click="cancelPrivacyVerify">取消</el-button>
          <el-button type="primary" native-type="submit" :loading="privacyVerifying">验证</el-button>
        </div>
      </el-form>
      <p v-if="privacyError" class="error-msg">{{ privacyError }}</p>
    </el-dialog>

    <!-- 文件列表表格 -->
    <el-table :data="fileStore.items" v-loading="loading" stripe style="margin-top:16px" @row-dblclick="onRowDblClick">
      <el-table-column label="名称" min-width="280">
        <template #default="{ row }">
          <div class="name-cell">
            <el-icon :size="18">
              <Folder v-if="row.itemType === 'DIRECTORY'" color="#409EFF" />
              <Document v-else />
            </el-icon>
            <span style="margin-left:8px">{{ row.name }}</span>
            <el-tag v-if="row.privacyMode === 'PRIVATE'" size="small" type="warning" style="margin-left:6px">隐私</el-tag>
          </div>
        </template>
      </el-table-column>
      <el-table-column label="大小" width="120" align="right">
        <template #default="{ row }">{{ row.itemType === 'DIRECTORY' ? '-' : formatSize(row.sizeBytes) }}</template>
      </el-table-column>
      <el-table-column label="类型" width="100">
        <template #default="{ row }">{{ row.itemType === 'DIRECTORY' ? '文件夹' : (row.mimeType || '文件') }}</template>
      </el-table-column>
      <el-table-column label="修改时间" width="180">
        <template #default="{ row }">{{ row.updatedAt || row.createdAt }}</template>
      </el-table-column>
      <el-table-column label="操作" width="300" fixed="right">
        <template #default="{ row }">
          <div class="action-btns">
            <!-- 进入文件夹 -->
            <el-button v-if="row.itemType === 'DIRECTORY'" type="primary" link size="small" @click="navigateTo(row)">
              打开
            </el-button>
            <!-- 下载文件 -->
            <el-button v-if="row.itemType === 'FILE'" type="primary" link size="small" @click="doDownload(row)">
              下载
            </el-button>
            <!-- 重命名 -->
            <el-button type="warning" link size="small" @click="showRename(row)">重命名</el-button>
            <!-- 隐私设置 / 取消 -->
            <el-button v-if="row.itemType === 'DIRECTORY' && row.privacyMode !== 'PRIVATE'"
              type="info" link size="small" @click="showSetPrivacy(row)">设置隐私</el-button>
            <el-button v-if="row.privacyMode === 'PRIVATE'"
              type="info" link size="small" @click="doRemovePrivacy(row)">取消隐私</el-button>
            <!-- 删除 -->
            <el-button type="danger" link size="small" @click="doDelete(row)">删除</el-button>
          </div>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <el-pagination v-if="fileStore.total > 0" style="margin-top:16px;justify-content:flex-end"
      v-model:current-page="fileStore.page" :page-size="fileStore.size" :total="fileStore.total"
      layout="prev, pager, next" @current-change="loadFiles" />

    <!-- 空状态 -->
    <el-empty v-if="!loading && rootId && fileStore.items.length === 0" description="此目录为空" />

    <!-- 上传对话框 -->
    <el-dialog v-model="showUploadDialog" title="上传文件" width="450px">
      <el-upload ref="uploadRef" :auto-upload="false" :limit="5" drag
        :before-upload="() => false" :on-change="onFileSelected"
        :file-list="uploadFiles">
        <el-icon :size="48"><UploadFilled /></el-icon>
        <div>拖拽文件到此处或点击选择</div>
      </el-upload>
      <template #footer>
        <el-button @click="showUploadDialog = false">取消</el-button>
        <el-button type="primary" @click="doUpload" :loading="uploading">上传</el-button>
      </template>
    </el-dialog>

    <!-- 新建文件夹对话框 -->
    <el-dialog v-model="showNewFolderDialog" title="新建文件夹" width="350px">
      <el-input v-model="newFolderName" placeholder="请输入文件夹名称" @keyup.enter="doCreateFolder" />
      <template #footer>
        <el-button @click="showNewFolderDialog = false">取消</el-button>
        <el-button type="primary" @click="doCreateFolder" :loading="creating">创建</el-button>
      </template>
    </el-dialog>

    <!-- 重命名对话框 -->
    <el-dialog v-model="showRenameDialog" title="重命名" width="350px">
      <el-input v-model="renameName" placeholder="请输入新名称" @keyup.enter="doRename" />
      <template #footer>
        <el-button @click="showRenameDialog = false">取消</el-button>
        <el-button type="primary" @click="doRename" :loading="renaming">确认</el-button>
      </template>
    </el-dialog>

    <!-- 设置隐私密码对话框 -->
    <el-dialog v-model="showSetPrivacyDialog" title="设置隐私密码" width="380px">
      <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
        设置后任何人访问此文件夹都需要输入此密码，包括管理员。
      </el-alert>
      <el-input v-model="privacyPassword" type="password" placeholder="请输入隐私密码（至少4位）" show-password />
      <template #footer>
        <el-button @click="showSetPrivacyDialog = false">取消</el-button>
        <el-button type="primary" @click="doSetPrivacy" :loading="settingPrivacy">确认设置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Folder, Document, Upload, FolderAdd, UploadFilled } from '@element-plus/icons-vue'
import { useFileStore } from '../stores/file'
import {
  listFiles, uploadFile, downloadFile, createFolder, renameFile, deleteFile,
  setPrivacy, removePrivacy, verifyPrivacy, listStorageRoots
} from '../api/files'

const fileStore = useFileStore()

// ---- 状态 ----
const roots = ref([])
const rootId = ref('')
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

// ---- 初始化 ----
onMounted(async () => {
  try {
    const { data } = await listStorageRoots()
    if (data.code === 'OK') roots.value = data.data || []
  } catch { /* 管理员权限不足或没有存储根目录 */ }
})

// ---- 文件操作 ----
function onRootChange(id) {
  fileStore.setCurrentRoot(id)
  loadFiles()
}

async function loadFiles() {
  if (!rootId.value) return
  loading.value = true
  try {
    const folderId = fileStore.currentFolderId
    // 获取该文件夹（如有）的隐私令牌
    const token = fileStore.getPrivacyToken(folderId)
    const { data } = await listFiles({
      storageRootId: rootId.value,
      parentId: folderId,
      page: fileStore.page,
      size: fileStore.size
    }, token)
    if (data.code === 'OK') {
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

/** 双击行进入文件夹 */
function onRowDblClick(row) {
  if (row.itemType === 'DIRECTORY') navigateTo(row)
}

/** 上传 */
function onFileSelected(file) {
  uploadFiles.value.push(file)
}

async function doUpload() {
  const pending = uploadFiles.value.filter(f => f.raw)
  if (pending.length === 0) { ElMessage.warning('请选择文件'); return }
  uploading.value = true
  try {
    const token = fileStore.currentPrivacyToken
    for (const f of pending) {
      await uploadFile({
        storageRootId: rootId.value,
        parentId: fileStore.currentFolderId,
        file: f.raw
      }, token)
    }
    ElMessage.success('上传成功')
    showUploadDialog.value = false
    uploadFiles.value = []
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

/** 下载 */
async function doDownload(row) {
  try {
    const token = fileStore.getPrivacyToken(fileStore.currentFolderId)
    // 检查父链隐私
    await checkParentPrivacy(row.id, token, async (effectiveToken) => {
      const resp = await downloadFile(row.id, effectiveToken)
      const blob = resp.data
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url; a.download = row.name; a.click()
      URL.revokeObjectURL(url)
    })
  } catch (e) {
    ElMessage.error(e.response?.data?.message || e.message || '下载失败')
  }
}

/** 新建文件夹 */
async function doCreateFolder() {
  if (!newFolderName.value.trim()) { ElMessage.warning('请输入文件夹名称'); return }
  creating.value = true
  try {
    const token = fileStore.currentPrivacyToken
    await createFolder({
      storageRootId: rootId.value,
      parentId: fileStore.currentFolderId,
      name: newFolderName.value.trim()
    }, token)
    ElMessage.success('文件夹创建成功')
    showNewFolderDialog.value = false
    newFolderName.value = ''
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建失败')
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
  if (!renameName.value.trim()) { ElMessage.warning('请输入新名称'); return }
  renaming.value = true
  try {
    const token = fileStore.currentPrivacyToken
    await renameFile(renameTarget.value.id, renameName.value.trim(), token)
    ElMessage.success('重命名成功')
    showRenameDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '重命名失败')
  } finally {
    renaming.value = false
  }
}

/** 删除 */
async function doDelete(row) {
  try {
    await ElMessageBox.confirm(`确定要删除 "${row.name}" 吗？此操作不可撤销。`, '确认删除', {
      type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消'
    })
    const token = fileStore.currentPrivacyToken
    await deleteFile(row.id, token)
    ElMessage.success('已删除')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '删除失败')
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
    ElMessage.warning('隐私密码至少需要4位'); return
  }
  settingPrivacy.value = true
  try {
    await setPrivacy(privacyTarget.value.id, privacyPassword.value)
    ElMessage.success('隐私密码已设置')
    showSetPrivacyDialog.value = false
    loadFiles()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '设置失败')
  } finally {
    settingPrivacy.value = false
  }
}

/** 取消隐私保护 */
async function doRemovePrivacy(row) {
  try {
    await ElMessageBox.confirm(`确定要取消 "${row.name}" 的隐私保护吗？`, '确认', {
      type: 'warning', confirmButtonText: '确认', cancelButtonText: '取消'
    })
    await removePrivacy(row.id)
    // 清除本地存储的访问令牌
    fileStore.clearPrivacyToken(row.id)
    ElMessage.success('隐私保护已取消')
    loadFiles()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '操作失败')
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
  // 如果后端返回 PRIVATE_PASSWORD_REQUIRED，则弹出验证
  try {
    await callback(currentToken)
  } catch (e) {
    const code = e.response?.data?.code
    if (code === 'PRIVATE_PASSWORD_REQUIRED' || code === 'PRIVATE_PASSWORD_INVALID') {
      // 需要验证隐私密码——弹出验证对话框
      privacyPendingFolderId.value = fileItemId
      privacyPendingCallback.value = () => {
        const newToken = fileStore.getPrivacyToken(privacyPendingFolderId.value)
        callback(newToken).catch(err => {
          ElMessage.error(err.response?.data?.message || '操作失败')
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
  if (!privacyPendingPassword.value) { privacyError.value = '请输入隐私密码'; return }
  privacyVerifying.value = true
  privacyError.value = ''
  try {
    const { data } = await verifyPrivacy(privacyPendingFolderId.value, privacyPendingPassword.value)
    if (data.code === 'OK') {
      // 保存访问令牌
      fileStore.savePrivacyToken(privacyPendingFolderId.value, data.data.accessToken)
      ElMessage.success('验证成功')
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
  if (data.code === 'PRIVATE_PASSWORD_REQUIRED' || data.code === 'PRIVATE_PASSWORD_INVALID') {
    showPrivacyVerify.value = true
    privacyPendingPassword.value = ''
    privacyError.value = data.message || ''
    privacyPendingCallback.value = () => loadFiles()
  } else {
    ElMessage.error(data.message || '操作失败')
  }
}

function handleHttpError(e) {
  const code = e.response?.data?.code
  if (code === 'PRIVATE_PASSWORD_REQUIRED' || code === 'PRIVATE_PASSWORD_INVALID') {
    showPrivacyVerify.value = true
    privacyPendingPassword.value = ''
    privacyError.value = e.response?.data?.message || ''
    privacyPendingCallback.value = () => loadFiles()
  } else {
    ElMessage.error(e.response?.data?.message || '请求失败')
  }
}

/** 文件大小格式化 */
function formatSize(bytes) {
  if (!bytes) return '0 B'
  const units = ['B', 'KB', 'MB', 'GB', 'TB']
  let i = 0
  let size = bytes
  while (size >= 1024 && i < units.length - 1) { size /= 1024; i++ }
  return size.toFixed(i === 0 ? 0 : 1) + ' ' + units[i]
}
</script>

<style scoped>
.files-view { max-width: 1200px; }
.toolbar { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.breadcrumb { flex: 1; }
.toolbar-actions { display: flex; gap: 8px; }
.name-cell { display: flex; align-items: center; }
.action-btns { display: flex; gap: 4px; flex-wrap: wrap; }
.error-msg { color: var(--el-color-danger); margin-top: 12px; font-size: 13px; }
</style>
