<template>
  <div class="users-view">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-input v-model="searchDisplayName" placeholder="搜索展示名" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <el-select v-model="filterRole" placeholder="角色" clearable style="width: 120px">
          <el-option label="ADMIN" value="ADMIN" />
          <el-option label="USER" value="USER" />
        </el-select>
        <el-select v-model="filterStatus" placeholder="状态" clearable style="width: 120px">
          <el-option label="正常" value="ACTIVE" />
          <el-option label="已禁用" value="DISABLED" />
          <el-option label="已锁定" value="LOCKED" />
        </el-select>
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="handleReset">重置</el-button>
      </div>
      <div class="action-row">
        <el-button type="primary" @click="showCreateDialog">创建用户</el-button>
        <el-button v-if="selectedIds.length > 0" type="danger" @click="handleBatchDelete" :disabled="batchDeleting">
          批量删除 ({{ selectedIds.length }})
        </el-button>
      </div>
    </div>

    <!-- 用户表格 -->
    <el-table :data="users" v-loading="loading" @selection-change="handleSelectionChange" stripe>
      <el-table-column type="selection" width="45" :selectable="isRowSelectable" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="displayName" label="展示名" min-width="120" />
      <el-table-column prop="role" label="角色" width="100">
        <template #default="{ row }">
          <el-tag :type="row.role === 'ADMIN' ? 'danger' : 'info'" size="small">{{ row.role }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="statusTagType(row.status)" size="small">{{ statusLabel(row.status) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="lastLoginAt" label="最后登录" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.lastLoginAt) }}</template>
      </el-table-column>
      <el-table-column prop="createdAt" label="创建时间" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="260" fixed="right">
        <template #default="{ row }">
          <el-button size="small" @click="showEditDialog(row)">编辑</el-button>
          <el-button size="small" @click="showResetPwdDialog(row)">重置密码</el-button>
          <el-button v-if="canDelete(row)" size="small" type="danger" @click="handleDelete(row)">删除</el-button>
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
    <el-dialog v-model="createDialogVisible" title="创建用户" width="420px" :close-on-click-modal="false">
      <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="80px">
        <el-form-item label="用户名" prop="username">
          <el-input v-model="createForm.username" placeholder="登录用户名" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="createForm.password" type="password" placeholder="初始密码" show-password />
        </el-form-item>
        <el-form-item label="展示名" prop="displayName">
          <el-input v-model="createForm.displayName" placeholder="显示名称（可选）" />
        </el-form-item>
        <el-form-item label="角色" prop="role">
          <el-select v-model="createForm.role" style="width: 100%">
            <el-option label="USER" value="USER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreate">创建</el-button>
      </template>
    </el-dialog>

    <!-- 编辑用户弹窗 -->
    <el-dialog v-model="editDialogVisible" title="编辑用户" width="420px" :close-on-click-modal="false">
      <el-form ref="editFormRef" :model="editForm" label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="editForm.username" disabled />
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="editForm.displayName" placeholder="显示名称" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="editForm.role" style="width: 100%">
            <el-option label="USER" value="USER" />
            <el-option label="ADMIN" value="ADMIN" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="editForm.status" style="width: 100%">
            <el-option label="正常" value="ACTIVE" />
            <el-option label="已禁用" value="DISABLED" />
            <el-option label="已锁定" value="LOCKED" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="updating" @click="handleUpdate">保存</el-button>
      </template>
    </el-dialog>

    <!-- 重置密码弹窗 -->
    <el-dialog v-model="resetPwdDialogVisible" title="重置密码" width="400px" :close-on-click-modal="false">
      <el-form ref="resetPwdFormRef" :model="resetPwdForm" :rules="resetPwdRules" label-width="80px">
        <el-form-item label="新密码" prop="newPassword">
          <el-input v-model="resetPwdForm.newPassword" type="password" placeholder="输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="resetPwdDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="resetting" @click="handleResetPassword">确认重置</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '../stores/auth'
import { listUsers, createUser, updateUser, batchDeleteUsers, resetPassword } from '../api/users'
import { formatDateTime } from '../utils/format'
import { useConfirmDialog } from '../composables/useConfirmDialog'
import ConfirmDialog from '../components/ConfirmDialog.vue'

const authStore = useAuthStore()
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
const createRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

// 编辑弹窗
const editDialogVisible = ref(false)
const updating = ref(false)
const editFormRef = ref(null)
const editForm = reactive({ id: '', username: '', displayName: '', role: 'USER', status: 'ACTIVE' })

// 重置密码弹窗
const resetPwdDialogVisible = ref(false)
const resetting = ref(false)
const resetPwdFormRef = ref(null)
const resetPwdForm = reactive({ userId: '', newPassword: '' })
const resetPwdRules = {
  newPassword: [{ required: true, message: '请输入新密码', trigger: 'blur' }]
}

// 批量删除
const batchDeleting = ref(false)

function statusTagType(status) {
  return { ACTIVE: 'success', DISABLED: 'warning', LOCKED: 'danger' }[status] || 'info'
}
function statusLabel(status) {
  return { ACTIVE: '正常', DISABLED: '已禁用', LOCKED: '已锁定' }[status] || status
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
    ElMessage.error('加载用户列表失败')
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
    ElMessage.success('用户创建成功')
    createDialogVisible.value = false
    fetchUsers()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '创建失败')
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
    await updateUser(editForm.id, { displayName: editForm.displayName, role: editForm.role, status: editForm.status })
    ElMessage.success('用户信息已更新')
    editDialogVisible.value = false
    fetchUsers()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '更新失败')
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
    ElMessage.success('密码已重置')
    resetPwdDialogVisible.value = false
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '重置失败')
  } finally {
    resetting.value = false
  }
}

async function handleDelete(row) {
  try {
    await confirm({
      title: '确认删除',
      message: `确定要删除用户 "${row.displayName || row.username}" 吗？其拥有的文件将被永久删除，下载和分享记录将保留。`,
      confirmText: '删除',
      type: 'warning'
    })
    await batchDeleteUsers(row.id)
    ElMessage.success('用户已删除')
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '删除失败')
  }
}

async function handleBatchDelete() {
  if (selectedIds.value.length === 0) return
  try {
    await confirm({
      title: '批量删除确认',
      message: `确定要删除选中的 ${selectedIds.value.length} 个用户吗？其拥有的文件将被永久删除。`,
      confirmText: '删除',
      type: 'warning'
    })
    batchDeleting.value = true
    await batchDeleteUsers(selectedIds.value.join(','))
    ElMessage.success(`已删除 ${selectedIds.value.length} 个用户`)
    selectedIds.value = []
    fetchUsers()
  } catch (e) {
    if (e !== 'cancel') ElMessage.error(e.response?.data?.message || '批量删除失败')
  } finally {
    batchDeleting.value = false
  }
}

onMounted(fetchUsers)
</script>

<style scoped>
.users-view { min-height: 400px; }

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
</style>
