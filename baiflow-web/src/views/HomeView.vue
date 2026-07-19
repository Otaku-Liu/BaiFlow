<template>
  <div class="app-shell">
    <el-container>
      <!-- 顶部导航 -->
      <el-header class="app-header">
        <div class="header-left">
          <h3>BaiFlow</h3>
        </div>
        <div class="header-right">
          <span class="user-info">{{ authStore.user?.displayName || authStore.user?.username }}</span>
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
          </el-menu>
        </el-aside>
        <!-- 主内容区 -->
        <el-main class="app-main">
          <FilesView v-if="activeMenu === 'files'" />
          <DownloadsView v-if="activeMenu === 'downloads'" />
          <SharesView v-if="activeMenu === 'shares'" />
        </el-main>
      </el-container>
    </el-container>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { FolderOpened, Download, Share } from '@element-plus/icons-vue'
import { useAuthStore } from '../stores/auth'
import FilesView from './FilesView.vue'
import DownloadsView from './DownloadsView.vue'
import SharesView from './SharesView.vue'

const router = useRouter()
const authStore = useAuthStore()
const activeMenu = ref('files')

function handleMenuSelect(index) {
  activeMenu.value = index
}

function handleLogout() {
  authStore.clearSession()
  router.push('/login')
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
