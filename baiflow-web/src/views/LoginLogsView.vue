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
          style="width: 380px"
        />
        <el-select v-model="filterStatus" :placeholder="t('loginLog.filterStatus')" clearable style="width: 120px">
          <el-option :label="t('loginLog.success')" value="LOGIN_SUCCESS" />
          <el-option :label="t('loginLog.failed')" value="LOGIN_FAILED" />
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
      <el-table-column :label="t('loginLog.loginResult')" width="90">
        <template #default="{ row }">
          <el-tag :type="row.action === 'LOGIN_SUCCESS' ? 'success' : 'danger'" size="small">
            {{ row.action === 'LOGIN_SUCCESS' ? t('loginLog.success') : t('loginLog.failed') }}
          </el-tag>
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
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { useI18n } from 'vue-i18n'
import { getLoginLogs } from '../api/logs'
import { formatDateTime } from '../utils/format'

const { t } = useI18n()

// 列表状态
const loading = ref(false)
const logs = ref([])
const page = ref(1)
const size = ref(20)
const total = ref(0)
const searchUsername = ref('')
const filterStatus = ref('')
const dateRange = ref(null)

/** 返回今日的日期时间范围（yyyy-MM-dd HH:mm:ss），00:00:00 ~ 23:59:59 */
function todayRange() {
  const now = new Date()
  const pad = (n) => String(n).padStart(2, '0')
  const d = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
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
    ElMessage.error(t('loginLog.loadFailed'))
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
  dateRange.value = todayRange()
  fetchLogs()
})
</script>

<style scoped>
.login-logs-view {
  /* .baiflow-data-table 已提供 filter-bar / filter-row / pagination-wrap 的外围布局 */
}
</style>
