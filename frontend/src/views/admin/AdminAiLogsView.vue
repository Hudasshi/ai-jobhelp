<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>AI 调用日志</h1>
        <p>查看模型调用元数据、Token、耗时和估算成本；Prompt 与模型回答不进入管理端。</p>
      </div>
    </div>

    <div class="panel filter-bar">
      <el-select v-model="filters.scene" clearable placeholder="全部场景">
        <el-option v-for="item in scenes" :key="item.value" :label="item.label" :value="item.value" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="全部状态">
        <el-option label="成功" value="success" />
        <el-option label="失败" value="fail" />
      </el-select>
      <el-date-picker
        v-model="filters.dateRange"
        type="daterange"
        value-format="YYYY-MM-DD"
        start-placeholder="调用开始"
        end-placeholder="调用结束"
      />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="panel table-panel">
      <el-table :data="records" v-loading="loading" row-key="id">
        <el-table-column prop="createdAt" label="调用时间" width="170" />
        <el-table-column label="用户" min-width="130">
          <template #default="{ row }">
            <div class="user-cell">
              <strong>{{ row.username || '系统/已注销' }}</strong>
              <small v-if="row.userId">#{{ row.userId }}</small>
            </div>
          </template>
        </el-table-column>
        <el-table-column label="场景" width="125">
          <template #default="{ row }"><span class="scene-tag">{{ sceneLabel(row.scene) }}</span></template>
        </el-table-column>
        <el-table-column prop="model" label="模型" min-width="130" />
        <el-table-column label="输入 / 输出" width="115">
          <template #default="{ row }">{{ row.promptTokens ?? 0 }} / {{ row.completionTokens ?? 0 }}</template>
        </el-table-column>
        <el-table-column label="耗时" width="90">
          <template #default="{ row }">{{ formatDuration(row.durationMs) }}</template>
        </el-table-column>
        <el-table-column label="状态" width="115">
          <template #default="{ row }">
            <span class="status-tag" :class="row.status">
              {{ row.status === 'success' ? (row.degraded ? '成功·降级' : '成功') : '失败' }}
            </span>
          </template>
        </el-table-column>
        <el-table-column label="估算成本" width="115">
          <template #default="{ row }">¥{{ Number(row.costEstimate || 0).toFixed(6) }}</template>
        </el-table-column>
      </el-table>

      <div class="table-footer">
        <span>共 {{ total }} 次外部模型尝试</span>
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

const scenes = [
  { value: 'resume_parse', label: '简历解析' },
  { value: 'resume_generate', label: '简历生成' },
  { value: 'diagnose', label: '简历诊断' },
  { value: 'jd_parse', label: 'JD 解析' },
  { value: 'interview_gen', label: '面试出题' },
  { value: 'interview_feedback', label: '面试点评' }
]

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const filters = reactive({ scene: '', status: '', dateRange: [] })

const sceneLabel = (value) => scenes.find((item) => item.value === value)?.label || value

/**
 * 毫秒对运营人员不直观，这里只做展示格式转换，不改变接口原始值。
 */
function formatDuration(ms) {
  if (ms == null) return '—'
  if (ms < 1000) return `${ms} ms`
  return `${(ms / 1000).toFixed(1)} s`
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.aiLogs({
      scene: filters.scene || undefined,
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
  Object.assign(filters, { scene: '', status: '', dateRange: [] })
  search()
}

onMounted(load)
</script>

<style scoped>
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 15px 17px; margin-bottom: 16px; }
.filter-bar .el-select { width: 150px; }
.table-panel { padding: 5px 17px 13px; }
.user-cell { display: flex; flex-direction: column; gap: 3px; }
.user-cell strong { font-size: 12px; }
.user-cell small { color: var(--muted); font-size: 9px; }
.scene-tag { padding: 4px 8px; border-radius: 6px; background: var(--brand-soft); color: var(--brand); font-size: 9px; white-space: nowrap; }
.status-tag { padding: 4px 8px; border-radius: 6px; font-size: 9px; white-space: nowrap; }
.status-tag.success { background: #e9f8f3; color: var(--success); }
.status-tag.fail { background: #fff0ef; color: var(--danger); }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: 13px 3px 3px; color: var(--muted); font-size: 10px; }
@media (max-width: 760px) {
  .filter-bar .el-select, .filter-bar .el-date-editor { width: 100%; }
}
</style>
