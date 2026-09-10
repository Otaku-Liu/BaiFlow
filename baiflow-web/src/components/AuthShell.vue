<template>
  <div class="auth-wrapper">
    <el-dropdown class="auth-locale" @command="handleLocaleChange">
      <span class="auth-locale-trigger">
        {{ locale === 'zh-CN' ? '中文' : 'English' }}
        <el-icon class="auth-locale-arrow"><ArrowDown /></el-icon>
      </span>
      <template #dropdown>
        <el-dropdown-menu>
          <el-dropdown-item command="zh-CN">中文</el-dropdown-item>
          <el-dropdown-item command="en">English</el-dropdown-item>
        </el-dropdown-menu>
      </template>
    </el-dropdown>

    <el-card class="auth-card" shadow="hover">
      <template #header>
        <img src="/brand/logo-icon.png" class="auth-logo" alt="BaiFlow" />
        <h2 style="margin:0;text-align:center;font-size:24px;font-weight:600;letter-spacing:-0.02em">BaiFlow</h2>
        <p style="margin:6px 0 0;text-align:center;font-size:13px;color:var(--el-text-color-secondary)">{{ subtitle }}</p>
      </template>
      <slot />
    </el-card>
  </div>
</template>

<script setup>
import { useI18n } from 'vue-i18n'
import { ArrowDown } from '@element-plus/icons-vue'

defineProps({
  /** 副标题（卡片标题下方一行），由使用方传入自己的 i18n 文案 */
  subtitle: { type: String, default: '' }
})

const { locale } = useI18n()

/** 语言切换：登录页与初始化向导共用，写入 localStorage.baiflow_locale（与主界面顶部一致） */
function handleLocaleChange(lang) {
  locale.value = lang
  localStorage.setItem('baiflow_locale', lang)
}
</script>

<style scoped>
/* 登录页 / 首次初始化向导共用的外壳（居中卡片 + 右上语言切换 + 品牌头）。
   表单本体由使用方通过默认插槽放入；下方 :deep() 规则作用于插槽内容
   （插槽内容渲染在本组件的卡片内，:deep 的前缀能匹配到卡片元素）。 */
.auth-wrapper {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100vh;
  background: linear-gradient(180deg, #f5f5f7 0%, #ececf1 100%);
}

.auth-locale {
  position: absolute;
  top: 16px;
  right: 20px;
}

.auth-locale-trigger {
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

.auth-locale-trigger:hover {
  background: rgba(0, 0, 0, 0.04);
  color: var(--el-text-color-primary);
}

/* 悬浮/聚焦不显示默认黑色轮廓框 */
.auth-locale-trigger,
.auth-locale-trigger:hover,
.auth-locale-trigger:focus,
.auth-locale-trigger:focus-visible {
  outline: none;
  box-shadow: none;
}

.auth-locale-arrow {
  font-size: 12px;
}

.auth-card {
  width: 400px;
  border: none;
  border-radius: 16px;
  box-shadow: 0 4px 24px rgba(0, 0, 0, 0.08), 0 1px 4px rgba(0, 0, 0, 0.04);
}

.auth-card :deep(.el-card__header) {
  padding: 28px 24px 0;
  border-bottom: none;
}

.auth-card :deep(.el-card__body) {
  padding: 24px;
}

.auth-logo {
  display: block;
  width: 72px;
  height: 72px;
  margin: 0 auto 16px;
  border-radius: 18px;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.12);
}

.auth-card h2 {
  font-size: 24px;
  font-weight: 600;
  letter-spacing: -0.02em;
  color: var(--el-text-color-primary);
}

/* ---- 以下规则作用于插槽里的表单（两个页面共用同一套表单外观） ---- */
.auth-wrapper :deep(.el-form-item__label) {
  font-weight: 500;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-transform: none;
  letter-spacing: 0;
}

.auth-wrapper :deep(.el-button--primary) {
  height: 44px;
  font-size: 15px;
  font-weight: 600;
  border-radius: 10px;
  letter-spacing: 0.02em;
}

.auth-wrapper :deep(.error-msg) {
  color: var(--el-color-danger);
  text-align: center;
  margin-top: 16px;
  font-size: 13px;
  font-weight: 500;
}
</style>
