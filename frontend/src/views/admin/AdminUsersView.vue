<template>
  <div class="page">
    <div class="page-head">
      <div><h1>用户管理</h1><p>查看用户、调整每日配额与启停账号。管理员不能禁用自己。</p></div>
    </div>

    <div class="panel filter-bar">
      <el-input v-model="filters.keyword" clearable placeholder="用户名 / 昵称" @keyup.enter="search" />
      <el-select v-model="filters.status" clearable placeholder="全部状态">
        <el-option label="正常" value="normal" /><el-option label="已禁用" value="disabled" /><el-option label="已注销" value="deleted" />
      </el-select>
      <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="注册开始" end-placeholder="注册结束" />
      <el-button type="primary" @click="search">查询</el-button>
      <el-button @click="reset">重置</el-button>
    </div>

    <div class="panel table-panel">
      <el-table :data="records" v-loading="loading">
        <el-table-column label="用户" min-width="180">
          <template #default="{ row }">
            <div class="user-cell"><span class="avatar">{{ row.username.slice(0, 1).toUpperCase() }}</span><div><strong>{{ row.nickname || row.username }}</strong><small>@{{ row.username }}</small></div></div>
          </template>
        </el-table-column>
        <el-table-column prop="email" label="邮箱" min-width="180"><template #default="{ row }">{{ row.email || '—' }}</template></el-table-column>
        <el-table-column label="角色" width="90"><template #default="{ row }"><span class="role-tag" :class="row.role">{{ row.role === 'admin' ? '管理员' : '用户' }}</span></template></el-table-column>
        <el-table-column label="状态" width="100"><template #default="{ row }"><span class="status-tag" :class="row.displayStatus">{{ statusLabel(row.displayStatus) }}</span></template></el-table-column>
        <el-table-column label="今日配额" width="160">
          <template #default="{ row }"><span>{{ row.quotaUsed }} / {{ row.quotaLimit }}</span><el-progress :percentage="row.quotaLimit ? Math.round(row.quotaUsed / row.quotaLimit * 100) : 0" :show-text="false" :stroke-width="4" /></template>
        </el-table-column>
        <el-table-column prop="registeredAt" label="注册时间" width="165" />
        <el-table-column label="操作" width="220" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row.id)">详情</el-button>
            <el-button v-if="row.displayStatus !== 'deleted'" link type="primary" @click="openQuota(row)">配额</el-button>
            <el-button v-if="row.displayStatus !== 'deleted'" link :type="row.displayStatus === 'normal' ? 'danger' : 'success'" @click="toggleStatus(row)">
              {{ row.displayStatus === 'normal' ? '禁用' : '启用' }}
            </el-button>
          </template>
        </el-table-column>
      </el-table>
      <div class="table-footer">
        <span>共 {{ total }} 位用户</span>
        <el-pagination v-model:current-page="page" :page-size="size" :total="total" layout="prev, pager, next" @current-change="load" />
      </div>
    </div>

    <el-dialog v-model="quotaVisible" title="调整每日 AI 配额" width="min(420px, 92vw)">
      <div class="dialog-user">{{ currentUser?.nickname }} <span>@{{ currentUser?.username }}</span></div>
      <el-form label-position="top">
        <el-form-item label="每日配额覆盖值（0~999）">
          <el-input-number v-model="quotaValue" :min="0" :max="999" size="large" />
        </el-form-item>
      </el-form>
      <div class="dialog-tip">保存后会重置该用户今日已用次数，并递增配额代次。</div>
      <template #footer>
        <el-button @click="quotaVisible = false">取消</el-button>
        <el-button type="primary" :loading="savingQuota" @click="saveQuota">保存</el-button>
      </template>
    </el-dialog>

    <el-drawer v-model="detailVisible" size="min(620px, 96vw)" title="用户详情">
      <div v-if="detail" class="detail-body">
        <div class="detail-head">
          <span class="avatar big">{{ detail.username.slice(0, 1).toUpperCase() }}</span>
          <div><h2>{{ detail.nickname }}</h2><p>@{{ detail.username }} · {{ detail.role === 'admin' ? '管理员' : '普通用户' }}</p></div>
          <span class="status-tag" :class="detail.displayStatus">{{ statusLabel(detail.displayStatus) }}</span>
        </div>
        <div class="detail-grid">
          <div><span>邮箱</span><strong>{{ detail.email || '—' }}</strong></div>
          <div><span>注册时间</span><strong>{{ detail.registeredAt }}</strong></div>
          <div><span>最近登录</span><strong>{{ detail.lastLoginAt || '—' }}</strong></div>
          <div><span>今日配额</span><strong>{{ detail.quotaUsed }} / {{ detail.quotaLimit }}</strong></div>
        </div>
        <h3 class="recent-title">最近诊断</h3>
        <div v-if="detail.recentDiagnoses?.length" class="recent-list">
          <div v-for="item in detail.recentDiagnoses" :key="item.id">
            <span class="recent-score">{{ item.score ?? '—' }}</span>
            <div><strong>{{ item.position || '未关联岗位' }}</strong><small>{{ item.createdAt }} · {{ item.degraded ? '降级' : item.status }}</small></div>
          </div>
        </div>
        <div v-else class="empty">暂无诊断记录</div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { adminApi } from '../../api'

const records = ref([])
const total = ref(0)
const page = ref(1)
const size = 10
const loading = ref(false)
const filters = reactive({ keyword: '', status: '', dateRange: [] })
const quotaVisible = ref(false)
const currentUser = ref(null)
const quotaValue = ref(20)
const savingQuota = ref(false)
const detailVisible = ref(false)
const detail = ref(null)
const statusLabel = (status) => ({ normal: '正常', disabled: '已禁用', deleted: '已注销' }[status] || status)

async function load() {
  loading.value = true
  try {
    const data = await adminApi.users({
      keyword: filters.keyword || undefined,
      status: filters.status || undefined,
      startDate: filters.dateRange?.[0] || undefined,
      endDate: filters.dateRange?.[1] || undefined,
      page: page.value,
      size
    })
    records.value = data.records
    total.value = data.total
  } finally { loading.value = false }
}
function search() { page.value = 1; load() }
function reset() { Object.assign(filters, { keyword: '', status: '', dateRange: [] }); search() }
function openQuota(row) {
  currentUser.value = row
  quotaValue.value = row.quotaLimit
  quotaVisible.value = true
}
async function saveQuota() {
  savingQuota.value = true
  try {
    await adminApi.quota(currentUser.value.id, quotaValue.value)
    ElMessage.success('配额已更新')
    quotaVisible.value = false
    await load()
  } finally { savingQuota.value = false }
}
async function toggleStatus(row) {
  const next = row.displayStatus === 'normal' ? 0 : 1
  await ElMessageBox.confirm(
    next === 0 ? `禁用“${row.nickname || row.username}”？其已签发 Token 将在下一次请求立即失效。` : `启用账号“${row.nickname || row.username}”？`,
    next === 0 ? '禁用账号' : '启用账号',
    { type: next === 0 ? 'warning' : 'info' }
  )
  await adminApi.status(row.id, next)
  ElMessage.success(next === 0 ? '账号已禁用' : '账号已启用')
  await load()
}
async function openDetail(id) {
  detailVisible.value = true
  detail.value = await adminApi.user(id)
}
onMounted(load)
</script>

<style scoped>
.filter-bar { display: flex; flex-wrap: wrap; gap: 10px; padding: 15px 17px; margin-bottom: 16px; }
.filter-bar .el-input { width: 210px; }
.filter-bar .el-select { width: 145px; }
.table-panel { padding: 5px 17px 13px; }
.user-cell { display: flex; align-items: center; gap: 9px; }
.avatar { width: 32px; height: 32px; border-radius: 9px; display: grid; place-items: center; background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 800; }
.avatar.big { width: 55px; height: 55px; border-radius: 15px; font-size: 20px; }
.user-cell > div { display: flex; flex-direction: column; gap: 2px; }
.user-cell strong { font-size: 12px; }
.user-cell small { color: var(--muted); font-size: 9px; }
.role-tag, .status-tag { padding: 4px 8px; border-radius: 6px; background: #f1f3f9; color: var(--muted); font-size: 9px; }
.role-tag.admin { background: #f3edff; color: #8059c7; }
.status-tag.normal { background: #e9f8f3; color: var(--success); }
.status-tag.disabled { background: #fff2e7; color: #bf7417; }
.status-tag.deleted { background: #f0f1f5; color: #969daf; }
.table-footer { display: flex; justify-content: space-between; align-items: center; padding: 13px 3px 3px; color: var(--muted); font-size: 10px; }
.dialog-user { font-size: 14px; font-weight: 700; margin-bottom: 17px; }
.dialog-user span { color: var(--muted); font-size: 10px; font-weight: 400; }
.dialog-tip { color: var(--muted); font-size: 10px; background: #f6f8fd; padding: 10px; border-radius: 8px; }
.detail-body { padding: 0 8px; }
.detail-head { display: flex; align-items: center; gap: 13px; padding-bottom: 20px; border-bottom: 1px solid var(--line); }
.detail-head > div { flex: 1; }
.detail-head h2 { margin: 0 0 4px; font-size: 20px; }
.detail-head p { margin: 0; color: var(--muted); font-size: 10px; }
.detail-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 11px; margin: 18px 0; }
.detail-grid div { background: #f8faff; border-radius: 10px; padding: 12px; display: flex; flex-direction: column; gap: 4px; }
.detail-grid span { color: var(--muted); font-size: 9px; }
.detail-grid strong { font-size: 11px; }
.recent-title { font-size: 14px; margin: 25px 0 11px; }
.recent-list { display: flex; flex-direction: column; gap: 8px; }
.recent-list > div { display: flex; align-items: center; gap: 10px; border: 1px solid var(--line); border-radius: 10px; padding: 10px; }
.recent-score { width: 37px; height: 37px; border-radius: 9px; display: grid; place-items: center; background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 800; }
.recent-list div div { display: flex; flex-direction: column; gap: 3px; }
.recent-list strong { font-size: 11px; }
.recent-list small { color: var(--muted); font-size: 9px; }
@media (max-width: 760px) { .filter-bar .el-input, .filter-bar .el-select, .filter-bar .el-date-editor { width: 100%; } .detail-grid { grid-template-columns: 1fr; } }
</style>
