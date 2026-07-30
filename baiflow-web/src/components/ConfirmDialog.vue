<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('cancel')"
    :title="title"
    width="400px"
    :close-on-click-modal="false"
    @close="$emit('cancel')"
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
      <el-button @click="$emit('cancel')">{{ cancelText }}</el-button>
      <el-button :type="confirmButtonType" @click="$emit('confirm')">
        {{ confirmText }}
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { computed } from 'vue'
import { WarningFilled, CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  title: { type: String, default: '确认' },
  message: { type: String, default: '' },
  confirmText: { type: String, default: '确认' },
  cancelText: { type: String, default: '取消' },
  type: { type: String, default: 'warning' }
})

defineEmits(['confirm', 'cancel'])

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
}
</style>
