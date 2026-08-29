<template>
  <div class="baiflow-data-table login-logs-view">
    <!-- 筛选栏 -->
    <div class="filter-bar">
      <div class="filter-row">
        <el-input v-model="searchUsername" :placeholder="t('loginLog.searchUsername')" clearable style="width: 200px" @keyup.enter="handleSearch" />
        <el-date-picker
          v-model="dateRange"
          type="datetimerange"
          range-separator="—"
          :start-placeholder="t('loginLog.startDate')"
          :end-placeholder="t('loginLog.endDate')"
          format="YYYY-MM-DD HH:mm:ss"
          value-format="YYYY-MM-DD HH:mm:ss"
          :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
          clearable
          style="width: 380px"
        />
        <el-select v-model="filterStatus" :placeholder="t('loginLog.filterStatus')" clearable style="width: 150px">
          <el-option v-for="opt in actionOptions" :key="opt.value" :label="opt.label" :value="opt.value" />
        </el-select>
        <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
        <el-button @click="handleReset">{{ t('common.reset') }}</el-button>
      </div>
    </div>

    <!-- 登录日志表格 -->
    <el-table :data="logs" v-loading="loading" stripe :empty-text="t('loginLog.noLogs')">
      <el-table-column prop="username" :label="t('loginLog.username')" min-width="120" />
      <el-table-column :label="t('loginLog.displayName')" min-width="100">
        <template #default="{ row }">
          {{ row.displayName || '-' }}
        </template>
      </el-table-column>
      <el-table-column :label="t('loginLog.actionLabel')" width="110">
        <template #default="{ row }">
          <el-tag :type="actionTagType(row.action)" size="small">{{ actionLabel(row.action) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" :label="t('common.ipAddress')" min-width="140" />
      <el-table-column prop="userAgent" :label="t('common.userAgent')" min-width="200" show-overflow-tooltip />
      <el-table-column prop="detail" :label="t('common.detail')" min-width="140" show-overflow-tooltip />
      <el-table-column :label="t('loginLog.loginTime')" min-width="160">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <!-- 分页 -->
    <div class="pagination-wrap">
      <el-pagination
        v-if="total > 0"
        v-model:current-page="page"
        v-model:page-size="size"
        :page-sizes="[10, 20, 50, 100]"
        :total="total"
        layout="total, sizes, prev, pager, next"
        @current-change="fetchLogs"
        @size-change="handleSizeChange"
      />
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { getLoginLogs } from '../api/logs'
import { formatDateTime } from '../utils/format'
import { notifyRequestError } from '../utils/notify'

const { t } = useI18n()

/** 登录与会话操作的类型选项（用于筛选下拉） */
const actionOptions = computed(() => [
  { value: 'LOGIN_SUCCESS', label: t('loginLog.action.loginSuccess') },
  { value: 'LOGIN_FAILED', label: t('loginLog.action.loginFailed') },
  { value: 'LOGOUT', label: t('loginLog.action.logout') },
  { value: 'FORCE_LOGOUT', label: t('loginLog.action.forceLogout') },
  { value: 'PASSWORD_CHANGED', label: t('loginLog.action.passwordChanged') },
  { value: 'ACCOUNT_LOCKED', label: t('loginLog.action.accountLocked') },
  { value: 'ACCOUNT_UNLOCKED', label: t('loginLog.action.accountUnlocked') }
])

/** 审计动作 → 展示文案 */
function actionLabel(action) {
  const map = {
    LOGIN_SUCCESS: t('loginLog.action.loginSuccess'),
    LOGIN_FAILED: t('loginLog.action.loginFailed'),
    LOGOUT: t('loginLog.action.logout'),
    FORCE_LOGOUT: t('loginLog.action.forceLogout'),
    PASSWORD_CHANGED: t('loginLog.action.passwordChanged'),
    ACCOUNT_LOCKED: t('loginLog.action.accountLocked'),
    ACCOUNT_UNLOCKED: t('loginLog.action.accountUnlocked')
  }
  return map[action] || action
}

/** 审计动作 → 标签颜色 */
function actionTagType(action) {
  return {
    LOGIN_SUCCESS: 'success',
    LOGIN_FAILED: 'danger',
    LOGOUT: 'info',
    FORCE_LOGOUT: 'warning',
    PASSWORD_CHANGED: 'warning',
    ACCOUNT_LOCKED: 'danger',
    ACCOUNT_UNLOCKED: 'success'
  }[action] || 'info'
}

// 列表状态
const loading = ref(false)
const logs = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const searchUsername = ref('')
const filterStatus = ref('')
const dateRange = ref(null)

/** 返回今天的日期时间范围（yyyy-MM-dd HH:mm:ss），固定按 UTC+8（Asia/Shanghai）计算，与服务端时区一致 */
function todayRange() {
  const pad = (n) => String(n).padStart(2, '0')
  const cn = new Date(Date.now() + 8 * 3600 * 1000) // 转到 UTC+8 再取 UTC 分量拼日期
  const d = `${cn.getUTCFullYear()}-${pad(cn.getUTCMonth() + 1)}-${pad(cn.getUTCDate())}`
  return [`${d} 00:00:00`, `${d} 23:59:59`]
}

async function fetchLogs() {
  loading.value = true
  try {
    const params = {
      page: page.value,
      size: size.value,
      username: searchUsername.value || undefined,
      status: filterStatus.value || undefined,
      startDate: dateRange.value?.[0] || undefined,
      endDate: dateRange.value?.[1] || undefined
    }
    const res = await getLoginLogs(params)
    logs.value = res.data.data?.records || []
    total.value = res.data.data?.total || 0
  } catch (e) {
    notifyRequestError(e, t('loginLog.loadFailed'))
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  fetchLogs()
}

function handleReset() {
  searchUsername.value = ''
  filterStatus.value = ''
  dateRange.value = todayRange()
  page.value = 1
  fetchLogs()
}

function handleSizeChange() {
  page.value = 1
  fetchLogs()
}

onMounted(() => {
  // 默认查当天（UTC+8）；日期框可清空查看全部历史
  dateRange.value = todayRange()
  fetchLogs()
})
</script>

<style scoped>
.login-logs-view {
  /* .baiflow-data-table 已提供 filter-bar / filter-row / pagination-wrap 的外围布局 */
}
</style>
