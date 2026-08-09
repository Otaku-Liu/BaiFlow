<template>
  <div class="app-shell">
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <button class="sidebar-toggle" @click="toggleSidebar" :aria-label="sidebarOpen ? '收起侧边栏' : '展开侧边栏'">
            <el-icon :size="20"><Fold v-if="sidebarOpen" /><Expand v-else /></el-icon>
          </button>
          <h3>BaiFlow</h3>
        </div>
        <div class="header-right">
          <el-dropdown class="locale-switcher" @command="handleLocaleChange">
            <span class="locale-trigger">
              {{ locale === 'zh-CN' ? '中文' : 'English' }}
              <el-icon class="locale-arrow"><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
                <el-dropdown-item command="en">English</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
          <span class="user-info" @click="profileDialogVisible = true">
            {{ authStore.user?.displayName || authStore.user?.username }}
          </span>
          <el-button type="danger" text @click="handleLogout">{{ t('common.logout') }}</el-button>
        </div>
      </el-header>
      <el-container class="body-container">
        <!-- 移动端遮罩 -->
        <div
          class="sidebar-overlay"
          :class="{ visible: sidebarOpen }"
          @click="closeSidebar"
        />

        <!-- 侧边栏 -->
        <el-aside class="app-aside" :class="{ open: sidebarOpen }">
          <el-menu :default-active="activeMenu" @select="handleMenuSelect">
            <el-menu-item index="files">
              <el-icon><FolderOpened /></el-icon>
              <span>{{ t('menu.files') }}</span>
            </el-menu-item>
            <el-menu-item index="notes">
              <el-icon><Memo /></el-icon>
              <span>{{ t('menu.notes') }}</span>
            </el-menu-item>
            <el-menu-item index="shares">
              <el-icon><Share /></el-icon>
              <span>{{ t('menu.shares') }}</span>
            </el-menu-item>
            <el-menu-item v-if="authStore.isAdmin" index="users">
              <el-icon><User /></el-icon>
              <span>{{ t('menu.users') }}</span>
            </el-menu-item>
            <el-sub-menu v-if="authStore.isAdmin" index="logs">
              <template #title>
                <el-icon><Document /></el-icon>
                <span>{{ t('menu.logs') }}</span>
              </template>
              <el-menu-item index="login-logs">
                <span>{{ t('menu.loginLogs') }}</span>
              </el-menu-item>
            </el-sub-menu>
          </el-menu>
        </el-aside>

        <!-- 主内容区 -->
        <el-main class="app-main">
          <transition name="view-fade" mode="out-in">
            <FilesView v-if="activeMenu === 'files'" key="files" />
            <NotesView v-else-if="activeMenu === 'notes'" key="notes" />
            <SharesView v-else-if="activeMenu === 'shares'" key="shares" />
            <UsersView v-else-if="activeMenu === 'users'" key="users" />
            <LoginLogsView v-else-if="activeMenu === 'login-logs'" key="login-logs" />
          </transition>
        </el-main>
      </el-container>
    </el-container>

    <!-- 个人资料弹窗 -->
    <el-dialog v-model="profileDialogVisible" title="个人资料" width="440px" :close-on-click-modal="true">
      <el-form label-width="auto" :key="locale">
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

      <!-- 操作区：保存资料 / 修改密码（置于登录设备上方） -->
      <div class="profile-actions">
        <el-button type="primary" @click="handleSaveProfile">保存资料</el-button>
        <el-button link type="primary" @click="openPasswordDialog">修改密码</el-button>
      </div>

      <el-divider />
      <!-- 登录设备管理 -->
      <div class="session-section">
        <div class="session-title">登录设备</div>
        <div v-if="sessions.length === 0" class="session-empty">暂无登录设备</div>
        <div v-for="s in sessions" :key="s.id" class="session-row">
          <div class="session-info">
            <div class="session-name">
              {{ s.deviceName || (s.deviceType === 'ANDROID' ? 'Android 设备' : 'Web 浏览器') }}
              <el-tag v-if="s.current" size="small" type="success" style="margin-left:6px">当前</el-tag>
            </div>
            <div class="session-meta">
              {{ s.deviceType === 'ANDROID' ? 'App' : 'Web' }} · {{ s.ip || '—' }} ·
              {{ formatDateTime(s.lastUsedAt) }}
            </div>
          </div>
          <el-button v-if="!s.current" link type="danger" size="small" @click="handleRevokeSession(s)">强制下线</el-button>
        </div>
      </div>

      <template #footer>
        <el-button @click="closeProfileDialog">取消</el-button>
      </template>
    </el-dialog>

    <!-- 修改密码弹窗 -->
    <el-dialog v-model="passwordDialogVisible" title="修改密码" width="400px" align-center :close-on-click-modal="true">
      <el-form label-width="auto" :key="locale">
        <el-form-item label="旧密码">
          <el-input v-model="oldPassword" type="password" placeholder="输入旧密码" show-password />
        </el-form-item>
        <el-form-item label="新密码">
          <el-input v-model="newPassword" type="password" placeholder="输入新密码" show-password />
        </el-form-item>
        <el-form-item label="确认新密码">
          <el-input v-model="confirmPassword" type="password" placeholder="再次输入新密码" show-password />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="passwordDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleChangePassword">确定修改</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { FolderOpened, Share, User, Fold, Expand, Document, ArrowDown, Memo } from '@element-plus/icons-vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { updateProfile, uploadAvatar, changePassword, listSessions, revokeSession } from '../api/auth'
import { formatDateTime } from '../utils/format'
import FilesView from './FilesView.vue'
import NotesView from './NotesView.vue'
import SharesView from './SharesView.vue'
import UsersView from './UsersView.vue'
import LoginLogsView from './LoginLogsView.vue'

const router = useRouter()
const authStore = useAuthStore()
const { t, locale } = useI18n()
const activeMenu = ref('files')

// ---- 响应式侧边栏 ----
const sidebarOpen = ref(false)
const isMobile = ref(false)

function checkMobile() {
  isMobile.value = window.innerWidth < 768
  if (!isMobile.value) {
    sidebarOpen.value = false  // 桌面端始终不显示 overlay
  }
}

function toggleSidebar() {
  sidebarOpen.value = !sidebarOpen.value
}

function closeSidebar() {
  sidebarOpen.value = false
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})

// 个人资料弹窗
const profileDialogVisible = ref(false)
const profileDisplayName = ref('')
const sessions = ref([])

// 修改密码弹窗
const passwordDialogVisible = ref(false)
const oldPassword = ref('')
const newPassword = ref('')
const confirmPassword = ref('')

watch(profileDialogVisible, (v) => {
  if (v) {
    profileDisplayName.value = authStore.user?.displayName || ''
    loadSessions()
  }
})

/** 显式关闭个人资料弹窗（取消 / X 均走此路径） */
function closeProfileDialog() {
  profileDialogVisible.value = false
}

/** 打开修改密码弹窗（每次重置密码字段） */
function openPasswordDialog() {
  oldPassword.value = ''
  newPassword.value = ''
  confirmPassword.value = ''
  passwordDialogVisible.value = true
}

/** 加载当前用户的登录设备列表（res.data 是 ApiResponse 包装，真正列表在 res.data.data） */
async function loadSessions() {
  try {
    const res = await listSessions()
    sessions.value = res.data?.data || []
  } catch (e) {
    sessions.value = []
  }
}

/** 强制下线某设备（会话） */
async function handleRevokeSession(s) {
  try {
    await revokeSession(s.id)
    ElMessage.success('已强制下线')
    loadSessions()
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '操作失败')
  }
}

function handleMenuSelect(index) {
  activeMenu.value = index
  // 移动端选择菜单后关闭侧边栏
  if (isMobile.value) {
    closeSidebar()
  }
}

function handleLogout() {
  authStore.clearSession()
  router.push('/login')
}

function handleLocaleChange(lang) {
  locale.value = lang
  localStorage.setItem('baiflow_locale', lang)
}

async function handleSaveProfile() {
  try {
    const res = await updateProfile(profileDisplayName.value)
    ElMessage.success('资料已更新')
    if (authStore.user) {
      authStore.user.displayName = res.data?.data?.displayName || profileDisplayName.value
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
      authStore.user.avatarUrl = res.data?.data?.avatarUrl || ''
    }
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '头像上传失败')
  }
  return false
}

async function handleChangePassword() {
  if (!oldPassword.value || !newPassword.value || !confirmPassword.value) {
    ElMessage.warning('请输入旧密码、新密码和确认新密码')
    return
  }
  if (newPassword.value !== confirmPassword.value) {
    ElMessage.warning('两次输入的新密码不一致')
    return
  }
  try {
    await changePassword(oldPassword.value, newPassword.value)
    ElMessage.success('密码已修改，请重新登录')
    passwordDialogVisible.value = false
    profileDialogVisible.value = false
    authStore.clearSession()
    router.push('/login')
  } catch (e) {
    ElMessage.error(e.response?.data?.message || '密码修改失败')
  }
}
</script>

<style scoped>
.app-shell {
  min-height: 100vh;
  background: var(--el-bg-color-page);
}

/* ---- 顶栏 ---- */
.app-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: rgba(255, 255, 255, 0.85);
  backdrop-filter: blur(12px);
  -webkit-backdrop-filter: blur(12px);
  border-bottom: 1px solid var(--el-border-color-light);
  padding: 0 24px;
  height: 52px;
  position: relative;
  z-index: 100;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.header-left h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
  letter-spacing: -0.01em;
  color: var(--el-text-color-primary);
}

/* 侧边栏切换按钮 */
.sidebar-toggle {
  display: none;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  padding: 0;
  border: none;
  border-radius: 8px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.sidebar-toggle:hover {
  background: rgba(0, 0, 0, 0.06);
  color: var(--el-text-color-primary);
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.locale-switcher {
  margin-right: 4px;
}

.locale-trigger {
  display: flex;
  align-items: center;
  gap: 4px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  font-weight: 500;
  cursor: pointer;
  padding: 4px 8px;
  border-radius: 6px;
  transition: background-color 0.15s ease, color 0.15s ease;
}

.locale-trigger:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--el-text-color-primary);
}

.locale-arrow {
  font-size: 12px;
  transition: transform 0.15s ease;
}

.user-info {
  color: var(--el-text-color-secondary);
  font-size: 14px;
  font-weight: 500;
  cursor: pointer;
  transition: color 0.15s ease;
}

.user-info:hover {
  color: var(--el-text-color-primary);
}

/* ---- Body 布局 ---- */
.body-container {
  position: relative;
}

/* ---- 侧边栏: iPad 分栏风格 (方案 1) ---- */
.app-aside {
  background: #f2f2f7;
  border-right: none;
  padding: 12px 8px;
  width: 220px;
  flex-shrink: 0;
  transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1);
}

/* 菜单项覆写 */
.app-aside :deep(.el-menu) {
  border-right: none;
  background: transparent;
}

.app-aside :deep(.el-menu-item) {
  border-radius: 8px;
  margin-bottom: 2px;
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  padding-left: 16px !important;
  transition: background-color 0.15s ease;
}

.app-aside :deep(.el-menu-item:hover) {
  background-color: rgba(0, 0, 0, 0.04);
}

.app-aside :deep(.el-menu-item.is-active) {
  background-color: rgba(0, 122, 255, 0.1);
  color: #007AFF;
  font-weight: 600;
}

.app-aside :deep(.el-menu-item .el-icon) {
  font-size: 18px;
  margin-right: 10px;
}

/* 子菜单覆写 */
.app-aside :deep(.el-sub-menu) {
  margin-bottom: 2px;
}

.app-aside :deep(.el-sub-menu__title) {
  border-radius: 8px;
  height: 40px;
  line-height: 40px;
  font-size: 14px;
  font-weight: 500;
  color: var(--el-text-color-primary);
  padding-left: 16px !important;
  transition: background-color 0.15s ease;
}

.app-aside :deep(.el-sub-menu__title:hover) {
  background-color: rgba(0, 0, 0, 0.04);
}

.app-aside :deep(.el-sub-menu.is-active .el-sub-menu__title) {
  color: #007AFF;
  font-weight: 600;
}

.app-aside :deep(.el-sub-menu .el-menu) {
  padding-left: 8px;
}

.app-aside :deep(.el-sub-menu .el-menu-item) {
  padding-left: 24px !important;
  font-size: 13px;
  height: 36px;
  line-height: 36px;
}

/* ---- 移动端遮罩 ---- */
.sidebar-overlay {
  display: none;
  position: fixed;
  inset: 0;
  background: rgba(0, 0, 0, 0.3);
  z-index: 90;
  opacity: 0;
  transition: opacity 0.3s ease;
  pointer-events: none;
}

.sidebar-overlay.visible {
  opacity: 1;
  pointer-events: auto;
}

/* ---- 主内容区 ---- */
.app-main {
  padding: 24px;
  background: #f5f5f7;
  min-height: calc(100vh - 52px);
  flex: 1;
  overflow-x: auto;
}

/* ---- 内容区过渡 ---- */
.view-fade-enter-active,
.view-fade-leave-active {
  transition: opacity 0.15s ease;
}

.view-fade-enter-from,
.view-fade-leave-to {
  opacity: 0;
}

/* ---- 个人资料弹窗 ---- */
.avatar-section {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* ================================================================
   响应式: 平板 / 手机
   ================================================================ */
@media (max-width: 1023px) {
  .app-aside {
    width: 240px;  /* 移动端略宽，方便触摸 */
  }
}

@media (max-width: 767px) {
  .sidebar-toggle {
    display: flex;
  }

  .app-header {
    padding: 0 16px;
  }

  .header-right .user-info {
    display: none;  /* 移动端隐藏用户名文字 */
  }

  .sidebar-overlay {
    display: block;
  }

  .app-aside {
    position: fixed;
    top: 52px;
    left: 0;
    bottom: 0;
    z-index: 95;
    width: 260px;
    transform: translateX(-100%);
    box-shadow: 0 0 0 0 transparent;
    transition: transform 0.3s cubic-bezier(0.4, 0, 0.2, 1),
                box-shadow 0.3s ease;
  }

  .app-aside.open {
    transform: translateX(0);
    box-shadow: 4px 0 20px rgba(0, 0, 0, 0.1);
  }

  .app-main {
    padding: 16px;
  }
}

/* 个人资料弹窗 · 操作区（保存资料 / 修改密码） */
.profile-actions {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 4px;
}

/* 个人资料弹窗 · 登录设备 */
.session-title {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  margin-bottom: 8px;
}
.session-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 4px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.session-row:last-child {
  border-bottom: none;
}
.session-name {
  font-size: 14px;
  color: var(--el-text-color-primary);
}
.session-meta {
  font-size: 12px;
  color: var(--el-text-color-secondary);
  margin-top: 2px;
}
.session-empty {
  font-size: 13px;
  color: var(--el-text-color-secondary);
  padding: 8px 0;
}
</style>
