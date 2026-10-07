<template>
  <template v-if="items.length">
    <!-- 收起态：贴预览框左边缘，长边（左边缘）落在框上、向右收窄，箭头指进正文＝把目录拉出来 -->
    <button
      v-if="collapsed"
      type="button"
      class="toc-tab toc-tab-collapsed"
      :title="t('preview.tocExpand')"
      :aria-label="t('preview.tocExpand')"
      @click="$emit('expand')"
    >
      <svg class="toc-tab-chevron" viewBox="0 0 24 24" aria-hidden="true">
        <path :d="CHEVRON_RIGHT" fill="none" stroke="currentColor" stroke-width="2.2"
              stroke-linecap="round" stroke-linejoin="round" />
      </svg>
    </button>

    <!-- 展开态：左侧常驻目录栏（宽＝卡片 25%，自身不可拖；要调正文/目录比例就拖整个预览抽屉） -->
    <aside v-else class="toc-panel">
      <div class="toc-head">
        <span class="toc-title">{{ t('preview.tocTitle') }}</span>
      </div>
      <nav class="toc-list">
        <a
          v-for="item in items"
          :key="item.id"
          :href="`#${item.id}`"
          :title="item.text"
          :class="['toc-item', `toc-level-${item.level}`, { 'is-active': item.id === activeId }]"
          @click.prevent="$emit('jump', item.id)"
        >{{ item.text }}</a>
      </nav>

      <!-- 收起拉手：同一片形状改贴分隔线右侧，箭头指回目录＝收回去 -->
      <button
        type="button"
        class="toc-tab toc-tab-expanded"
        :title="t('preview.tocCollapse')"
        :aria-label="t('preview.tocCollapse')"
        @click="$emit('collapse')"
      >
        <svg class="toc-tab-chevron" viewBox="0 0 24 24" aria-hidden="true">
          <path :d="CHEVRON_LEFT" fill="none" stroke="currentColor" stroke-width="2.2"
                stroke-linecap="round" stroke-linejoin="round" />
        </svg>
      </button>
    </aside>
  </template>
</template>

<script setup>
import { useI18n } from 'vue-i18n'

const { t } = useI18n()

defineProps({
  // 标题项：[{ id, level: 1|2|3, text }]，由 PreviewDrawer 渲染后从 DOM 提取
  items: { type: Array, default: () => [] },
  // 当前所在章节的 id（滚动高亮）
  activeId: { type: String, default: '' },
  collapsed: { type: Boolean, default: false }
})
defineEmits(['collapse', 'expand', 'jump'])

// 手画 chevron（Element Plus 没有单箭头：ArrowRight 带箭杆、DArrow 是双箭头）。
// 两态箭头相反，与「展开/收起」对应：收起态指进正文（拉出），展开态指回目录（收回）
const CHEVRON_LEFT = 'M15 5l-7 7 7 7'
const CHEVRON_RIGHT = 'M9 5l7 7-7 7'
</script>

<style scoped>
/* 常驻目录栏：白底 + 右侧分隔线（整块预览卡片由 PreviewDrawer 画边框）。
   宽度恒为卡片的 25%，下限 160px —— 抽屉拖窄时 25% 会小到没法看，这是唯一的破例；
   自己不可拖：调正文/目录比例的动作收敛到预览抽屉的左边缘（见 PreviewDrawer） */
.toc-panel {
  position: relative;
  width: 25%;
  min-width: 160px;
  flex: none;
  display: flex; flex-direction: column;
  border-right: 1px solid var(--el-border-color);
}

.toc-head {
  padding: 14px 8px 8px 16px;
  font-size: 12px; font-weight: 600; letter-spacing: 0.02em;
  color: var(--el-text-color-secondary);
}

.toc-list { flex: 1; overflow: auto; padding: 0 0 12px; }

.toc-item {
  display: block;
  padding: 5px 12px 5px 16px;
  font-size: 13px; line-height: 1.5;
  color: var(--el-text-color-regular);
  text-decoration: none;
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
  cursor: pointer;
  transition: background-color 0.15s ease, color 0.15s ease;
}
.toc-level-2 { padding-left: 28px; }
.toc-level-3 { padding-left: 40px; }
.toc-item:hover { color: #007AFF; background: rgba(0, 122, 255, 0.06); }
.toc-item.is-active {
  color: #007AFF; background: rgba(0, 122, 255, 0.08);
  font-weight: 500;
}

/* 展开/收起共用的拉手：形状与朝向两态完全相同，只是改贴哪条边。
   - 默认是看得见的灰底（透明白底在白卡片上等于没有），悬停加深一档；
   - 梯形用 clip-path 画：左边满高（长边）、右边收到 40%（短边），即「左长右短」。
     用 clip-path 而不是 border-radius，因为要的是梯形收窄，不是圆头。 */
.toc-tab {
  position: absolute; top: 50%; z-index: 2;
  transform: translateY(-50%);
  display: flex; align-items: center; justify-content: center;
  width: 14px; height: 60px;
  padding: 0; border: none;
  background: var(--el-border-color);
  color: var(--el-text-color-secondary);
  cursor: pointer;
  clip-path: polygon(0 0, 100% 30%, 100% 70%, 0 100%);
  transition: background-color 0.15s ease;
}
.toc-tab:hover { background: var(--el-border-color-dark); }
.toc-tab-chevron { width: 12px; height: 12px; }

/* 收起态：贴预览卡片左边缘（14px 落在正文 24px 左内边距里，不压字） */
.toc-tab-collapsed { left: 0; }

/* 展开态：贴分隔线右侧，长边落在分隔线上（分隔线右边依次是拉手 14px、正文内边距 24px） */
.toc-tab-expanded { right: -14px; }
</style>
