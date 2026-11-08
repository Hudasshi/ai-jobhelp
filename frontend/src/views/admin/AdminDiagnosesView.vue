<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>诊断记录</h1>
        <p>跨用户查看诊断元数据、耗时与状态；管理员不查看诊断报告正文。</p>
      </div>
    </div>

    <div class="panel filter-bar">
      <el-input
        v-model="filters.userId"
        clearable
        inputmode="numeric"
        placeholder="用户 ID"
        @keyup.enter="search"
      />
      <el-select v-model="filters.status" clearable placeholder="全部状态">
        <el-option label="成功" value="success" />
        <el-option label="降级完成" value="degraded" />
        <el-option label="失败" value="failed" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="诊断开始"
        end-placeholder="诊断结束"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="panel table-panel">
      <el-table :data="records" v-loading="loading" row-key="id">
        <el-table-column prop="id" label="ID" width="70" />
        <el-table-column label="用户" min-width="140">
          <template #default="{ row }">
            <div class="user-cell">
              <strong>{{ row.username || '已注销用户' }}</strong>
              <small v-if="row.userId">#{{ row.userId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="目标岗位" min-width="190">
          <template #default="{ row }">{{ row.position || '未关联岗位' }}</template>
        </el-table-column>
        <el-table-column label="状态" width="120">
          <template #default="{ row }">
            <span class="status-tag" :class="row.status">{{ diagnosisStatus(row.status) }}</span>
          </template>
        </el-table-column>
        <el-table-column label="耗时" width="100">
          <template #default="{ row }">{{ row.costSeconds == null ? '—' : `${row.costSeconds}s` }}</template>
        </el-table-column>
        <el-table-column label="Tokens" width="105">
          <template #default="{ row }">{{ row.tokens == null ? '—' : row.tokens }}</template>
        </el-table-column>
        <el-table-column prop="createdAt" label="创建时间" width="170" />
        <el-table-column label="失败摘要" min-width="170">
          <template #default="{ row }">
            <el-tooltip v-if="row.failReason" :content="row.failReason" placement="top">
              <span class="fail-reason">{{ row.failReason }}</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span>共 {{ total }} 条诊断记录</span>
        <el-pagination
          v-model:current-page="page"
          :page-size="size"
          :total="total"
          layout="prev, pager, next"
          @current-change="load"
        />
      </div>
    </div>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { adminApi } from '../../api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const filters = reactive({
  userId: '',
  status: '',
  dateRange: []
})

const diagnosisStatus = (status) => ({
  success: '成功',
  degraded: '降级完成',
  failed: '失败'
}[status] || status)

/**
 * 页面统一使用后端分页协议 records/total/page/size。
 * userId 在空字符串时不能发送，否则类型转换会把“未筛选”误判为非法参数。
 */
async function load() {
  loading.value = true
  try {
    const data = await adminApi.diagnoses({
      userId: filters.userId === '' ? undefined : Number(filters.userId),
      status: filters.status || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
      page: page.value,
      size
    })
    records.value = data.records
    total.value = data.total
  } finally {
    loading.value = false
  }
}

function search() {
  page.value = 1
  load()
}

function reset() {
  Object.assign(filters, { userId: '', status: '', dateRange: [] })
  search()
}

onMounted(load)
</script>

<style scoped>
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 15px 17px; margin-bottom: 16px; }
.filter-bar .el-input { width: 125px; }
.filter-bar .el-select { width: 145px; }
.table-panel { padding: 5px 17px 13px; }
.user-cell { display: flex; flex-direction: column; gap: 3px; }
.user-cell strong { font-size: 12px; }
.user-cell small { color: var(--muted); font-size: 9px; }
.status-tag { padding: 4px 8px; border-radius: 6px; font-size: 9px; white-space: nowrap; }
.status-tag.success { background: #e9f8f3; color: var(--success); }
.status-tag.degraded { background: #fff5e8; color: #bd7217; }
.status-tag.failed { background: #fff0ef; color: var(--danger); }
.fail-reason { display: block; max-width: 220px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: var(--danger); font-size: 10px; }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: 13px 3px 3px; color: var(--muted); font-size: 10px; }
@media (max-width: 760px) {
  .filter-bar .el-input, .filter-bar .el-select, .filter-bar .el-date-editor { width: 100%; }
}
</style>
