<template>
  <div class="records-view">
    <div class="filter-bar">
      <el-date-picker v-model="dateRange" type="daterange"
        :start-placeholder="t('records.startDate')" :end-placeholder="t('records.endDate')"
        value-format="YYYY-MM-DD" clearable style="width: 260px" @change="handleSearch" />
      <el-input v-model="fileName" :placeholder="t('records.fileName')" clearable
        style="width: 200px" @keyup.enter="handleSearch" @clear="handleSearch" />
      <el-select v-model="source" :placeholder="t('records.source')" clearable
        style="width: 130px" @change="handleSearch">
        <el-option v-for="s in sourceOptions" :key="s" :label="s" :value="s" />
      </el-select>
      <el-select v-if="authStore.isAdmin" v-model="userId" :placeholder="t('records.allUsers')" clearable
        style="width: 160px" @change="handleSearch">
        <el-option v-for="u in users" :key="u.id" :label="u.displayName || u.username" :value="u.id" />
      </el-select>
      <el-button type="primary" @click="handleSearch">{{ t('common.search') }}</el-button>
    </div>

    <el-table :data="records" v-loading="loading" stripe>
      <el-table-column prop="fileName" :label="t('records.fileName')" min-width="220" show-overflow-tooltip />
      <el-table-column v-if="authStore.isAdmin" :label="t('records.user')" width="150">
        <template #default="{ row }">{{ row.uploaderUsername || row.downloaderUsername || '-' }}</template>
      </el-table-column>
      <el-table-column :label="t('records.source')" width="110">
        <template #default="{ row }">
          <el-tag :type="sourceTagType(row.source)" size="small">{{ row.source }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ipAddress" :label="t('records.ip')" width="150" />
      <el-table-column :label="t('records.time')" min-width="170">
        <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
      </el-table-column>
    </el-table>

    <el-pagination
      class="records-pagination"
      v-model:current-page="page" v-model:page-size="size"
      :total="total" :page-sizes="[10, 20, 50]" layout="total, sizes, prev, pager, next, jumper"
      @current-change="load" @size-change="handleSizeChange" />
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useI18n } from 'vue-i18n'
import { useAuthStore } from '../stores/auth'
import { listUploadRecords, listDownloadRecords } from '../api/records'
import { listUsers } from '../api/users'
import { formatDateTime } from '../utils/format'

const props = defineProps({
  /** 'upload' | 'download' */
  type: { type: String, default: 'upload' }
})

const authStore = useAuthStore()
const { t } = useI18n()

const isUpload = computed(() => props.type === 'upload')

// 默认查看当天
const now = new Date()
const pad = n => String(n).padStart(2, '0')
const today = `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
const dateRange = ref([today, today])

const fileName = ref('')
const source = ref('')
const userId = ref('')
const users = ref([])
const records = ref([])
const total = ref(0)
const page = ref(1)
const size = ref(20)
const loading = ref(false)

/** 来源选项随类型：上传 WEB/ANDROID，下载 CLIENT/SHARE */
const sourceOptions = computed(() => (isUpload.value ? ['WEB', 'ANDROID'] : ['CLIENT', 'SHARE']))

async function load() {
  loading.value = true
  try {
    const params = {
      start: dateRange.value?.[0] || '',
      end: dateRange.value?.[1] || '',
      fileName: fileName.value,
      source: source.value,
      userId: authStore.isAdmin ? userId.value : '',
      page: page.value,
      size: size.value
    }
    const res = isUpload.value
      ? await listUploadRecords(params)
      : await listDownloadRecords(params)
    records.value = res.data?.data?.records || []
    total.value = res.data?.data?.total || 0
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  page.value = 1
  load()
}

function handleSizeChange() {
  page.value = 1
  load()
}

function sourceTagType(s) {
  if (s === 'ANDROID' || s === 'CLIENT') return 'success'
  if (s === 'SHARE') return 'warning'
  return 'info'
}

onMounted(async () => {
  if (authStore.isAdmin) {
    const res = await listUsers({ page: 1, size: 100 })
    users.value = res.data?.data?.records || []
  }
  load()
})
</script>

<style scoped>
.filter-bar {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.records-pagination {
  margin-top: 12px;
  justify-content: flex-end;
}
</style>
