<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>系统日志</h1>
        <p>查看登录、配额调整、账号启停、清理与熔断审计；remark 仅保存脱敏元数据。</p>
      </div>
    </div>

    <div class="panel filter-bar">
      <el-select v-model="filters.action" clearable placeholder="全部动作">
        <el-option v-for="item in actions" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.result" clearable placeholder="全部结果">
        <el-option label="成功" :value="1" />
        <el-option label="失败" :value="0" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="操作开始"
        end-placeholder="操作结束"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="panel table-panel">
      <el-table :data="records" v-loading="loading" row-key="id">
        <el-table-column prop="createdAt" label="操作时间" width="170" />
        <el-table-column label="操作者" min-width="135">
          <template #default="{ row }">
            <div class="user-cell">
              <strong>{{ row.username || '匿名/系统' }}</strong>
              <small v-if="row.userId">#{{ row.userId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="动作" min-width="135">
          <template #default="{ row }"><span class="action-tag">{{ actionLabel(row.action) }}</span></template>
        </el-table-column>
        <el-table-column label="结果" width="95">
          <template #default="{ row }">
            <span class="result-tag" :class="{ success: row.success, fail: !row.success }">
              {{ row.success ? '成功' : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="IP" width="135">
          <template #default="{ row }">{{ row.ip || '—' }}</template>
        </el-table-column>
        <el-table-column label="脱敏备注" min-width="280">
          <template #default="{ row }">
            <el-tooltip v-if="row.remark" :content="row.remark" placement="top">
              <span class="remark-text">{{ row.remark }}</span>
            </el-tooltip>
            <span v-else class="muted">—</span>
          </template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span>共 {{ total }} 条审计记录</span>
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

// 九个动作值与冻结契约及 system_log.action 白名单保持一致。
const actions = [
  { value: 'login', label: '登录成功' },
  { value: 'login_fail', label: '登录失败' },
  { value: 'quota_adjust', label: '配额调整' },
  { value: 'delete_all', label: '注销清空数据' },
  { value: 'user_disable', label: '禁用账号' },
  { value: 'user_enable', label: '启用账号' },
  { value: 'file_clean', label: '文件清理' },
  { value: 'log_clean', label: '日志清理' },
  { value: 'circuit_break', label: 'AI 服务熔断' }
]

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const filters = reactive({ action: '', result: '', dateRange: [] })

const actionLabel = (value) => actions.find((item) => item.value === value)?.label || value

/**
 * 接口筛选字段是 result（0/1），返回行字段却是布尔 success。
 * 这里是契约明确规定的字段映射，不能把 success=false 直接当查询参数发送。
 */
async function load() {
  loading.value = true
  try {
    const data = await adminApi.logs({
      action: filters.action || undefined,
      result: filters.result === '' ? undefined : filters.result,
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
  Object.assign(filters, { action: '', result: '', dateRange: [] })
  search()
}

onMounted(load)
</script>

<style scoped>
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 15px 17px; margin-bottom: 16px; }
.filter-bar .el-select { width: 155px; }
.table-panel { padding: 5px 17px 13px; }
.user-cell { display: flex; flex-direction: column; gap: 3px; }
.user-cell strong { font-size: 12px; }
.user-cell small { color: var(--muted); font-size: 9px; }
.action-tag { padding: 4px 8px; border-radius: 6px; background: #f3edff; color: #8059c7; font-size: 9px; white-space: nowrap; }
.result-tag { padding: 4px 8px; border-radius: 6px; font-size: 9px; }
.result-tag.success { background: #e9f8f3; color: var(--success); }
.result-tag.fail { background: #fff0ef; color: var(--danger); }
.remark-text { display: block; max-width: 430px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; color: #55617b; font-size: 10px; }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: 13px 3px 3px; color: var(--muted); font-size: 10px; }
@media (max-width: 760px) {
  .filter-bar .el-select, .filter-bar .el-date-editor { width: 100%; }
}
</style>
