<template>
  <div class="app-shell">
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <h3>BaiFlow</h3>
        </div>
        <div class="header-right">
          <span class="user-info" style="cursor:pointer" @click="profileDialogVisible = true">
            {{ authStore.user?.displayName || authStore.user?.username }}
          </span>
          <el-button type="danger" text @click="handleLogout">退出</el-button>
        </div>
      </el-header>
      <el-container>
        <!-- 侧边栏 -->
        <el-aside width="200px" class="app-aside">
          <el-menu :default-active="activeMenu" @select="handleMenuSelect">
            <el-menu-item index="files">
              <el-icon><FolderOpened /></el-icon>
              <span>文件中心</span>
            </el-menu-item>
            <el-menu-item index="downloads">
              <el-icon><Download /></el-icon>
              <span>下载中心</span>
            </el-menu-item>
            <el-menu-item index="shares">
              <el-icon><Share /></el-icon>
              <span>分享管理</span>
            </el-menu-item>
            <el-menu-item v-if="authStore.isAdmin" index="users">
              <el-icon><User /></el-icon>
              <span>用户管理</span>
            </el-menu-item>
          </el-menu>
        </el-aside>
        <!-- 主内容区 -->
        <el-main class="app-main">
          <FilesView v-if="activeMenu === 'files'" />
          <DownloadsView v-if="activeMenu === 'downloads'" />
          <SharesView v-if="activeMenu === 'shares'" />
          <UsersView v-if="activeMenu === 'users'" />
        </el-main>
      </el-container>
    </el-container>

    <!-- 个人资料弹窗 -->
    <el-dialog v-model="profileDialogVisible" title="个人资料" width="440px" :close-on-click-modal="false">
      <el-form label-width="80px">
        <el-form-item label="用户名">
          <el-input :model-value="authStore.user?.username" disabled />
        </el-form-item>
        <el-form-item label="展示名">
          <el-input v-model="profileDisplayName" placeholder="展示名称" />
        </el-form-item>
        <el-form-item label="头像">
          <div class="avatar-section">
            <el-avatar v-if="authStore.user?.avatarUrl" :src="authStore.user.avatarUrl" :size="64" />
            <el-avatar v-else :size="64">{{ (authStore.user?.displayName || authStore.user?.username || '?')[0] }}</el-avatar>
            <el-upload
              :show-file-list="false"
              :before-upload="handleAvatarUpload"
              accept=".jpg,.jpeg,.png,.gif,.webp"
              style="margin-left:12px"
            >
              <el-button size="small">更换头像</el-button>
            </el-upload>
          </div>
        </el-form-item>
      </el-form>
      <el-divider />
      <el-form label-width="80px">
        <el-form-item label="旧密码">
          <el-input v-model="oldPassword" type="password" placeholder="输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" placeholder="输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="profileDialogVisible = false">取消</el-button>
        <el-button @click="handleSaveProfile">保存资料</el-button>
        <el-button type="primary" @click="handleChangePassword">修改密码</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FolderOpened, Download, Share, User } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import { updateProfile, uploadAvatar, changePassword } from '../api/auth'
import FilesView from './FilesView.vue'
import DownloadsView from './DownloadsView.vue'
import SharesView from './SharesView.vue'
import UsersView from './UsersView.vue'

const router = useRouter()
const authStore = useAuthStore()
const activeMenu = ref('files')

// 个人资料弹窗
const profileDialogVisible = ref(false)
const profileDisplayName = ref('')
const oldPassword = ref('')
const newPassword = ref('')

// 打开弹窗时预填当前展示名
watch(profileDialogVisible, (v) => {
  if (v) {
    profileDisplayName.value = authStore.user?.displayName || ''
    oldPassword.value = ''
    newPassword.value = ''
  }
})

function handleMenuSelect(index) {
  activeMenu.value = index
}

function handleLogout() {
  authStore.clearSession()
  router.push('/login')
}

async function handleSaveProfile() {
  try {
    const res = await updateProfile(profileDisplayName.value)
    ElMessage.success('资料已更新')
    // 更新本地 store
    if (authStore.user) {
      authStore.user.displayName = res.data?.displayName || profileDisplayName.value
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '保存失败')
  }
}

async function handleAvatarUpload(file) {
  if (file.size > 1024 * 1024) {
    ElMessage.error('头像文件不能超过 1MB')
    return false
  }
  try {
    const res = await uploadAvatar(file)
    ElMessage.success('头像已更新')
    if (authStore.user) {
      authStore.user.avatarUrl = res.data?.avatarUrl || ''
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '头像上传失败')
  }
  return false // 阻止 el-upload 默认上传
}

async function handleChangePassword() {
  if (!oldPassword.value || !newPassword.value) {
    ElMessage.warning('请输入旧密码和新密码')
    return
  }
  try {
    await changePassword(oldPassword.value, newPassword.value)
    ElMessage.success('密码已修改，请重新登录')
    oldPassword.value = ''
    newPassword.value = ''
    profileDialogVisible.value = false
    authStore.clearSession()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '密码修改失败')
  }
}
</script>

<style scoped>
.app-shell { min-height: 100vh; background: var(--el-bg-color-page); }
.app-header {
  display: flex; align-items: center; justify-content: space-between;
  background: #fff; border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 20px; height: 56px;
}
.header-left h3 { margin: 0; }
.header-right { display: flex; align-items: center; gap: 12px; }
.user-info { color: var(--el-text-color-secondary); font-size: 14px; }
.app-aside { background: #fff; border-right: 1px solid var(--el-border-color-light); }
.app-main { padding: 20px; }
</style>
