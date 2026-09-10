<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('cancel', distinguishClose ? 'close' : 'cancel')"
    :title="displayTitle"
    width="400px"
    :close-on-click-modal="false"
  >
    <div class="confirm-body">
      <el-icon v-if="type === 'warning'" :size="22" color="#FF9500">
        <WarningFilled />
      </el-icon>
      <el-icon v-else-if="type === 'danger'" :size="22" color="#FF3B30">
        <CircleCloseFilled />
      </el-icon>
      <el-icon v-else :size="22" color="#007AFF">
        <InfoFilled />
      </el-icon>
      <span class="confirm-message">{{ message }}</span>
    </div>
    <template #footer>
      <el-button @click="$emit('cancel', 'cancel')">{{ displayCancelText }}</el-button>
      <el-button :type="confirmButtonType" @click="$emit('confirm')">
        {{ displayConfirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { useI18n } from 'vue-i18n'
import { WarningFilled, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'

const { t } = useI18n()

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '' },
  cancelText: { type: String, default: '' },
  type: { type: String, default: 'warning' },
  /**
   * 是否区分「取消按钮」与「关闭（X / ESC / 遮罩）」：开启后前者 emit `'cancel'`、后者 emit `'close'`。
   * 默认关闭——两者都 emit `'cancel'`，调用方不必关心关闭方式。
   */
  distinguishClose: { type: Boolean, default: false }
})

defineEmits(['confirm', 'cancel'])

const displayTitle = computed(() => props.title || t('common.confirm'))
const displayConfirmText = computed(() => props.confirmText || t('common.confirm'))
const displayCancelText = computed(() => props.cancelText || t('common.cancel'))

const confirmButtonType = computed(() => {
  return props.type === 'danger' ? 'danger' : 'primary'
})
</script>

<style scoped>
.confirm-body {
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.confirm-message {
  font-size: 14px;
  color: var(--el-text-color-regular);
  line-height: 1.6;
  padding-top: 1px;
  /* 保留消息里的换行（块级差异预览等多行文案），单行文案渲染不变 */
  white-space: pre-line;
}
</style>
