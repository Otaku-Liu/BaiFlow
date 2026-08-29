<template>
  <div class="users-view">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-input v-model="searchDisplayName" :placeholder="t('users.searchDisplayName')" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <el-select v-model="filterRole" :placeholder="t('users.filterRole')" clearable style="width: 120px">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="USER" value="USER" />
        </el-select>
        <el-select v-model="filterStatus" :placeholder="t('users.filterStatus')" clearable style="width: 120px">
          <el-option :label="t('users.status.normal')" value="NORMAL" />
          <el-option :label="t('users.status.disabled')" value="DISABLED" />
          <el-option :label="t('users.status.locked')" value="LOCKED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </div>
      <div class="action-row">
        <el-button type="primary" @click="showCreateDialog">{{ t('users.createUser') }}</el-button>
        <el-button v-if="authStore.isAdmin && selectedUserIds.length > 0" type="warning" :disabled="batchUpdating" @click="handleBatchStatus('DISABLED')">
          {{ t('users.batchDisable') }} ({{ selectedUserIds.length }})
        </el-button>
        <el-button v-if="authStore.isAdmin && selectedUserIds.length > 0" type="success" :disabled="batchUpdating" @click="handleBatchStatus('NORMAL')">
          {{ t('users.batchEnable') }} ({{ selectedUserIds.length }})
        </el-button>
        <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchDelete" :disabled="batchDeleting">
          {{ t('users.batchDelete') }} ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>

    <!-- 用户表格 -->
    <el-table :data="users" v-loading="loading" @selection-change="handleSelectionChange" stripe>
      <el-table-column type="selection" width="45" :selectable="isRowSelectable" />
      <el-table-column :label="t('users.avatar')" width="70">
        <template #default="{ row }">
          <el-avatar v-if="row.avatarUrl" :src="row.avatarUrl" :size="36" class="avatar-img" />
          <el-avatar v-else :size="36" class="avatar-fallback">{{ (row.displayName || row.username || '?')[0] }}</el-avatar>
        </template>
      </el-table-column>
      <el-table-column prop="username" :label="t('users.loginUsername')" min-width="120" />
      <el-table-column prop="displayName" :label="t('users.displayNameOpt')" min-width="120" />
      <el-table-column prop="role" :label="t('users.role')" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" :label="t('common.status')" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" :label="t('users.lastLogin')" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" :label="t('common.createdAt')" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column :label="t('common.actions')" width="330" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">{{ t('common.edit') }}</el-button>
          <el-button v-if="authStore.isAdmin && row.role === 'USER' && row.status !== 'DISABLED'" size="small" type="warning" @click="handleToggleStatus(row, 'DISABLED')">
            {{ t('users.disable') }}
          </el-button>
          <el-button v-if="authStore.isAdmin && row.role === 'USER' && row.status === 'DISABLED'" size="small" type="success" @click="handleToggleStatus(row, 'NORMAL')">
            {{ t('users.enable') }}
          </el-button>
          <el-button size="small" @click="showResetPwdDialog(row)">{{ t('users.resetPassword') }}</el-button>
          <el-button v-if="canDelete(row)" size="small" type="danger" @click="handleDelete(row)">{{ t('common.delete') }}</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-model:current-page="page" :page-size="size" :total="total"
        layout="total, prev, pager, next" @current-change="fetchUsers" />
    </div>

    <!-- 通用确认弹窗 -->
    <ConfirmDialog v-bind="bindings" @confirm="onConfirm" @cancel="onCancel" />

    <!-- 创建用户弹窗 -->
    <el-dialog v-model="createDialogVisible" :title="t('users.createDialogTitle')" width="420px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="auto" :key="locale">
        <el-form-item :label="t('users.loginUsername')" prop="username">
          <el-input v-model="createForm.username" :placeholder="t('users.loginUsername')" />
        </el-form-item>
        <el-form-item :label="t('users.initialPassword')" prop="password">
          <el-input v-model="createForm.password" type="password" :placeholder="t('users.initialPassword')" show-password />
        </el-form-item>
        <el-form-item :label="t('users.displayNameOpt')" prop="displayName">
          <el-input v-model="createForm.displayName" :placeholder="t('users.displayNameOpt')" />
        </el-form-item>
        <el-form-item :label="t('users.role')" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="USER" value="USER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">{{ t('common.create') }}</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户弹窗 -->
    <el-dialog v-model="editDialogVisible" :title="t('users.editDialogTitle')" width="420px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" label-width="auto" :key="locale">
        <el-form-item :label="t('users.loginUsername')">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item :label="t('users.displayNameOpt')">
          <el-input v-model="editForm.displayName" :placeholder="t('users.displayNameOpt')" />
        </el-form-item>
        <el-form-item :label="t('users.role')">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option label="USER" value="USER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item :label="t('common.status')">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option v-for="opt in statusOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
          </el-select>
          <div v-if="isEditingLocked" class="status-lock-hint">{{ t('users.lockedAutoHint') }}</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdate">{{ t('common.save') }}</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetPwdDialogVisible" :title="t('users.resetPwdDialogTitle')" width="400px" :close-on-click-modal="false">
      <el-form ref="resetPwdFormRef" :model="resetPwdForm" :rules="resetPwdRules" label-width="auto" :key="locale">
        <el-form-item :label="t('users.newPassword')" prop="newPassword">
          <el-input v-model="resetPwdForm.newPassword" type="password" :placeholder="t('users.newPasswordPlaceholder')" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">{{ t('common.cancel') }}</el-button>
        <el-button type="primary" :loading="resetting" @click="handleResetPassword">{{ t('users.confirmReset') }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { notifyRequestError } from '../utils/notify'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { listUsers, createUser, updateUser, batchDeleteUsers, batchUpdateUsersStatus, resetPassword } from '../api/users'
import { formatDateTime } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const authStore = useAuthStore()
const { t, locale } = useI18n()
const { confirm, bindings, onConfirm, onCancel } = useConfirmDialog()

// 列表状态
const loading = ref(false)
const users = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const selectedIds = ref([])
const filterRole = ref('')
const filterStatus = ref('')
const searchDisplayName = ref('')

// 创建弹窗
const createDialogVisible = ref(false)
const creating = ref(false)
const createFormRef = ref(null)
const createForm = reactive({ username: '', password: '', displayName: '', role: 'USER' })
const createRules = computed(() => ({
  username: [{ required: true, message: `${t('common.pleaseInput')}${t('users.loginUsername')}`, trigger: 'blur' }],
  password: [{ required: true, message: `${t('common.pleaseInput')}${t('users.initialPassword')}`, trigger: 'blur' }]
}))

// 编辑弹窗
const editDialogVisible = ref(false)
const updating = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ id: '', username: '', displayName: '', role: 'USER', status: 'NORMAL' })
/** 编辑中的用户是否处于自动锁定状态：可改为禁用，不允许设为正常 */
const isEditingLocked = computed(() => editForm.status === 'LOCKED')
/** 编辑弹窗状态下拉：锁定用户仅可在「保持锁定 / 禁用」间选择，正常/禁用用户仅可互切 */
const statusOptions = computed(() => {
  if (editForm.status === 'LOCKED') {
    return [
      { label: t('users.status.locked'), value: 'LOCKED' },
      { label: t('users.status.disabled'), value: 'DISABLED' }
    ]
  }
  return [
    { label: t('users.status.normal'), value: 'NORMAL' },
    { label: t('users.status.disabled'), value: 'DISABLED' }
  ]
})

// 重置密码弹窗
const resetPwdDialogVisible = ref(false)
const resetting = ref(false)
const resetPwdFormRef = ref(null)
const resetPwdForm = reactive({ userId: '', newPassword: '' })
const resetPwdRules = computed(() => ({
  newPassword: [{ required: true, message: `${t('common.pleaseInput')}${t('users.newPassword')}`, trigger: 'blur' }]
}))

// 批量删除
const batchDeleting = ref(false)
// 批量禁用/启用
const batchUpdating = ref(false)
/** 选中项中仅 USER 角色用户（禁用/启用仅作用于 USER 角色） */
const selectedUserIds = computed(() => selectedIds.value.filter(id => {
  const row = users.value.find(u => u.id === id)
  return row && row.role === 'USER'
}))

function statusTagType(status) {
  return { 'NORMAL': 'success', 'DISABLED': 'warning', 'LOCKED': 'danger' }[status] || 'info'
}
function statusLabel(status) {
  const map = {
    NORMAL: t('users.status.normal'),
    DISABLED: t('users.status.disabled'),
    LOCKED: t('users.status.locked')
  }
  return map[status] || status
}

/** 当前用户或内置 admin 不可选/不可删 */
const BUILTIN_ADMIN = 'admin'
function isProtected(row) {
  return row.id === authStore.user?.id || row.username === BUILTIN_ADMIN
}
function isRowSelectable(row) {
  return !isProtected(row)
}
function canDelete(row) {
  return !isProtected(row)
}

async function fetchUsers() {
  loading.value = true
  try {
    const res = await listUsers({
      page: page.value, size: size.value,
      role: filterRole.value || undefined,
      status: filterStatus.value || undefined,
      displayName: searchDisplayName.value || undefined
    })
    users.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (e) {
    notifyRequestError(e, t('users.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchUsers()
}

function handleReset() {
  searchDisplayName.value = ''
  filterRole.value = ''
  filterStatus.value = ''
  page.value = 1
  fetchUsers()
}

function handleSelectionChange(selection) {
  // 过滤掉当前用户和内置 admin，防止意外选中
  selectedIds.value = selection.filter(r => !isProtected(r)).map(r => r.id)
}

function showCreateDialog() {
  createForm.username = ''
  createForm.password = ''
  createForm.displayName = ''
  createForm.role = 'USER'
  createDialogVisible.value = true
}

async function handleCreate() {
  const valid = await createFormRef.value?.validate().catch(() => false)
  if (!valid) return
  creating.value = true
  try {
    await createUser(createForm)
    ElMessage.success(t('users.userCreated'))
    createDialogVisible.value = false
    fetchUsers()
  } catch (e) {
    notifyRequestError(e, t('users.createFailed'))
  } finally {
    creating.value = false
  }
}

function showEditDialog(row) {
  editForm.id = row.id
  editForm.username = row.username
  editForm.displayName = row.displayName || ''
  editForm.role = row.role
  editForm.status = row.status
  editDialogVisible.value = true
}

async function handleUpdate() {
  updating.value = true
  try {
    // LOCKED 仅由登录失败自动锁定维护，手动编辑锁定用户时跳过 status 字段，避免误提交
    const payload = { displayName: editForm.displayName, role: editForm.role }
    if (editForm.status !== 'LOCKED') payload.status = editForm.status
    await updateUser(editForm.id, payload)
    ElMessage.success(t('users.userUpdated'))
    editDialogVisible.value = false
    fetchUsers()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('users.updateFailed'))
  } finally {
    updating.value = false
  }
}

function showResetPwdDialog(row) {
  resetPwdForm.userId = row.id
  resetPwdForm.newPassword = ''
  resetPwdDialogVisible.value = true
}

async function handleResetPassword() {
  const valid = await resetPwdFormRef.value?.validate().catch(() => false)
  if (!valid) return
  resetting.value = true
  try {
    await resetPassword(resetPwdForm.userId, resetPwdForm.newPassword)
    ElMessage.success(t('users.passwordReset'))
    resetPwdDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.message || t('users.resetFailed'))
  } finally {
    resetting.value = false
  }
}

async function handleDelete(row) {
  try {
    await confirm({
      title: t('users.deleteConfirmTitle'),
      message: t('users.deleteConfirmMsg', { name: row.displayName || row.username }),
      confirmText: t('common.delete'),
      type: 'warning'
    })
    await batchDeleteUsers(row.id)
    ElMessage.success(t('users.deleted'))
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') notifyRequestError(e, t('users.deleteFailed'))
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await confirm({
      title: t('users.batchDeleteTitle'),
      message: t('users.batchDeleteMsg', { count: selectedIds.value.length }),
      confirmText: t('common.delete'),
      type: 'warning'
    })
    batchDeleting.value = true
    await batchDeleteUsers(selectedIds.value.join(','))
    ElMessage.success(t('users.batchDeleted', { count: selectedIds.value.length }))
    selectedIds.value = []
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('users.batchDeleteFailed'))
  } finally {
    batchDeleting.value = false
  }
}

/** 单个用户禁用/启用（仅 USER 角色；禁用锁定用户时后端会清除其 Redis 锁键） */
async function handleToggleStatus(row, status) {
  const isDisable = status === 'DISABLED'
  try {
    await confirm({
      title: t(isDisable ? 'users.disableConfirmTitle' : 'users.enableConfirmTitle'),
      message: t(isDisable ? 'users.disableConfirmMsg' : 'users.enableConfirmMsg', { name: row.displayName || row.username }),
      confirmText: t(isDisable ? 'users.disable' : 'users.enable'),
      type: isDisable ? 'danger' : 'warning'
    })
    await updateUser(row.id, { status })
    ElMessage.success(t(isDisable ? 'users.disabled' : 'users.enabled'))
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('users.statusUpdateFailed'))
  }
}

/** 批量禁用/启用（仅作用于选中的 USER 角色用户） */
async function handleBatchStatus(status) {
  const ids = selectedUserIds.value
  if (ids.length === 0) return
  const isDisable = status === 'DISABLED'
  try {
    await confirm({
      title: t(isDisable ? 'users.batchDisableConfirmTitle' : 'users.batchEnableConfirmTitle'),
      message: t(isDisable ? 'users.batchDisableConfirmMsg' : 'users.batchEnableConfirmMsg', { count: ids.length }),
      confirmText: t(isDisable ? 'users.disable' : 'users.enable'),
      type: isDisable ? 'danger' : 'warning'
    })
    batchUpdating.value = true
    await batchUpdateUsersStatus(ids.join(','), status)
    ElMessage.success(t(isDisable ? 'users.batchDisabled' : 'users.batchEnabled', { count: ids.length }))
    selectedIds.value = []
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || t('users.statusUpdateFailed'))
  } finally {
    batchUpdating.value = false
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.users-view { min-height: 400px; }

/* 头像：有图透明底，无头像浅灰底 + 白字首字（与 HomeView 一致） */
.avatar-img {
  --el-avatar-bg-color: transparent;
}
.avatar-fallback {
  --el-avatar-bg-color: #c0c4cc;
  --el-avatar-text-color: #ffffff;
}

.filter-bar { margin-bottom: 16px; }

.filter-row {
  display: flex;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
  flex-wrap: wrap;
}

.action-row {
  display: flex;
  gap: 10px;
  align-items: center;
}

.pagination-wrap {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

.status-lock-hint {
  margin-top: 4px;
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
</style>
