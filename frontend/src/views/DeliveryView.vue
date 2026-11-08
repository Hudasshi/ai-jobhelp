<template>
  <div class="page">
    <div class="page-head">
      <div><h1>投递看板</h1><p>用六个状态看清每一份投递现在走到哪一步，按规则推进，避免状态倒退和遗漏。</p></div>
      <el-button type="primary" size="large" @click="openCreate">＋ 新增投递</el-button>
    </div>

    <div class="summary-strip">
      <div class="summary-total panel">
        <span>总投递</span><strong>{{ stats.deliveryTotal || 0 }}</strong>
        <small>面试转化率 {{ conversionText }}</small>
      </div>
      <div v-for="status in statuses" :key="status.value" class="summary-pill" :class="status.value" @click="filterStatus = filterStatus === status.value ? '' : status.value">
        <span>{{ status.label }}</span><strong>{{ stats.statusCounts?.[status.value] || 0 }}</strong>
      </div>
    </div>

    <div class="delivery-layout">
      <section class="kanban" :class="{ filtered: filterStatus }">
        <div v-for="column in visibleColumns" :key="column.value" class="kanban-column">
          <div class="column-head">
            <div><span class="status-dot" :class="column.value"></span><strong>{{ column.label }}</strong></div>
            <span>{{ grouped[column.value]?.length || 0 }}</span>
          </div>
          <div class="column-body">
            <article v-for="record in grouped[column.value] || []" :key="record.id" class="delivery-card panel">
              <div class="card-head">
                <span class="company-initial">{{ record.company?.slice(0, 1) || 'C' }}</span>
                <div class="company-copy"><strong>{{ record.company }}</strong><span>{{ record.position }}</span></div>
                <el-dropdown trigger="click">
                  <button class="more-btn">···</button>
                  <template #dropdown>
                    <el-dropdown-menu>
                      <el-dropdown-item @click="openEdit(record)">编辑投递</el-dropdown-item>
                      <el-dropdown-item @click="startInterview(record)">发起模拟面试</el-dropdown-item>
                      <el-dropdown-item divided @click="remove(record)">删除记录</el-dropdown-item>
                    </el-dropdown-menu>
                  </template>
                </el-dropdown>
              </div>
              <div class="meta-grid">
                <span><b>投递</b>{{ shortDate(record.deliveryDate) }}</span>
                <span><b>截止</b>{{ shortDate(record.deadline) }}</span>
                <span v-if="record.examTime"><b>笔试</b>{{ record.examTime }}</span>
                <span v-if="record.interviewTime"><b>面试</b>{{ record.interviewTime }}</span>
              </div>
              <p v-if="record.skillReq" class="skill-line">{{ record.skillReq }}</p>
              <div v-if="record.interviewSummary" class="interview-badge">
                面试进度 {{ record.interviewSummary.progressIndex }}/{{ record.interviewSummary.totalQuestions }}
              </div>
              <div class="card-actions">
                <button v-for="next in nextStatuses[record.status] || []" :key="next" @click="advance(record, next)">
                  {{ transitionLabel(next) }}
                </button>
                <span v-if="!nextStatuses[record.status]?.length" class="terminal">终态</span>
              </div>
            </article>
            <div v-if="!(grouped[column.value] || []).length" class="column-empty">暂无记录</div>
          </div>
        </div>
      </section>

      <aside class="todo-panel panel">
        <div class="todo-head"><div><h2>临近待办</h2><p>自动从投递时间派生</p></div><span>{{ todos.length }}</span></div>
        <div v-if="todos.length" class="todo-list">
          <div v-for="(todo, index) in todos" :key="`${todo.recordId}-${todo.type}-${index}`" class="todo-item">
            <span class="todo-icon">{{ { exam: '笔', interview: '面', deadline: '止' }[todo.type] }}</span>
            <div><strong>{{ todo.company }} · {{ todo.position }}</strong><span>{{ todo.time }} · {{ countdown(todo.remainingSeconds) }}</span></div>
          </div>
        </div>
        <div v-else class="empty small-empty">近 3 天没有新待办</div>
        <div class="todo-legend"><span>笔试/面试：24 小时内</span><span>截止：今天起 3 天内</span></div>
      </aside>
    </div>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑投递' : '新增投递'" width="min(760px, 95vw)" destroy-on-close>
      <div class="parse-banner">
        <div><strong>从 JD 自动提取</strong><span>粘贴岗位描述，生成公司、岗位、技能与截止日期</span></div>
        <el-button size="small" :loading="parsing" @click="parseJd">智能解析</el-button>
      </div>
      <el-input v-model="form.jdRaw" type="textarea" :rows="5" maxlength="8000" show-word-limit placeholder="粘贴完整 JD（可选）..." class="jd-area" />
      <el-form :model="form" label-position="top" class="delivery-form">
        <div class="grid grid-2">
          <el-form-item label="公司名称" required><el-input v-model="form.company" placeholder="例如：字节跳动" /></el-form-item>
          <el-form-item label="岗位名称" required><el-input v-model="form.position" placeholder="例如：Java 后端开发工程师" /></el-form-item>
        </div>
        <div class="grid grid-3">
          <el-form-item label="投递日期" required>
            <el-date-picker v-model="form.deliveryDate" type="date" value-format="YYYY-MM-DD" placeholder="选择日期" class="full" />
          </el-form-item>
          <el-form-item label="投递截止">
            <el-date-picker v-model="form.deadline" type="date" value-format="YYYY-MM-DD" placeholder="可选" class="full" />
          </el-form-item>
          <el-form-item label="当前状态">
            <el-select v-model="form.status" :disabled="Boolean(form.id)">
              <el-option v-for="item in statuses" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </el-form-item>
        </div>
        <div class="grid grid-2">
          <el-form-item label="笔试时间">
            <el-date-picker v-model="form.examTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="可选" class="full" />
          </el-form-item>
          <el-form-item label="面试时间">
            <el-date-picker v-model="form.interviewTime" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="可选" class="full" />
          </el-form-item>
        </div>
        <el-form-item label="技能要求"><el-input v-model="form.skillReq" maxlength="500" placeholder="例如：Java, MySQL, Redis" /></el-form-item>
        <el-form-item label="备注"><el-input v-model="form.remark" type="textarea" :rows="2" maxlength="500" placeholder="内推码、联系人、跟进计划等" /></el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存投递</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deliveryApi } from '../api'
import { nextStatuses, shortDate, statusLabels } from '../utils/format'

const router = useRouter()
const statuses = [
  { value: 'pending', label: '待投递' }, { value: 'applied', label: '已投递' },
  { value: 'exam', label: '笔试' }, { value: 'interview', label: '面试' },
  { value: 'offer', label: 'Offer' }, { value: 'rejected', label: '已结束' }
]
const records = ref([])
const stats = ref({})
const todos = ref([])
const dialogVisible = ref(false)
const saving = ref(false)
const parsing = ref(false)
const filterStatus = ref('')
const form = reactive(blankForm())

const grouped = computed(() => {
  const result = {}
  statuses.forEach((status) => { result[status.value] = [] })
  records.value.forEach((record) => (result[record.status] ||= []).push(record))
  return result
})
const visibleColumns = computed(() => filterStatus.value ? statuses.filter((item) => item.value === filterStatus.value) : statuses)
const conversionText = computed(() => stats.value.interviewConversionRate == null ? '暂无' : `${stats.value.interviewConversionRate}%`)

function blankForm() {
  return {
    id: null, company: '', position: '', jdRaw: '', deliveryDate: new Date().toISOString().slice(0, 10),
    status: 'pending', examTime: null, interviewTime: null, skillReq: '', deadline: null, remark: ''
  }
}
function assignForm(source) {
  Object.assign(form, blankForm(), source)
}
async function load() {
  const [boardData, todoData] = await Promise.all([deliveryApi.list(), deliveryApi.todo()])
  records.value = boardData.records
  stats.value = boardData.stats
  todos.value = todoData
}
function openCreate() { assignForm({}); dialogVisible.value = true }
function openEdit(record) {
  assignForm({ ...record, jdRaw: record.jdRaw || '' })
  dialogVisible.value = true
}
async function parseJd() {
  if (!form.jdRaw.trim()) return ElMessage.warning('请先粘贴 JD')
  parsing.value = true
  try {
    const data = await deliveryApi.parseJd(form.jdRaw.trim())
    form.company = data.company || form.company
    form.position = data.position || form.position
    form.skillReq = data.skills || form.skillReq
    form.deadline = data.deadline || form.deadline
    ElMessage.success('JD 已解析，可继续修改')
  } finally { parsing.value = false }
}
async function save() {
  if (!form.company.trim() || !form.position.trim()) return ElMessage.warning('公司和岗位不能为空')
  if (!form.deliveryDate) return ElMessage.warning('请选择投递日期')
  saving.value = true
  try {
    const payload = {
      company: form.company.trim(), position: form.position.trim(), jdRaw: form.jdRaw || null,
      deliveryDate: form.deliveryDate, status: form.status, examTime: form.examTime || null,
      interviewTime: form.interviewTime || null, skillReq: form.skillReq || null,
      deadline: form.deadline || null, remark: form.remark || null
    }
    if (form.id) await deliveryApi.update({ ...payload, id: form.id })
    else await deliveryApi.create(payload)
    ElMessage.success(form.id ? '投递已更新' : '投递已新增')
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function advance(record, status) {
  if (status === 'rejected') {
    await ElMessageBox.confirm('把这份投递标记为已结束？终态不能恢复到其他状态。', '确认结束', { type: 'warning' })
  }
  await deliveryApi.status(record.id, status)
  ElMessage.success(`已更新为${statusLabels[status]}`)
  await load()
}
function transitionLabel(status) {
  return {
    applied: '标记已投递', exam: '进入笔试', interview: '进入面试', offer: '收到 Offer', rejected: '结束'
  }[status] || statusLabels[status]
}
async function remove(record) {
  await ElMessageBox.confirm(`删除“${record.company} · ${record.position}”的投递记录？`, '删除投递', { type: 'warning' })
  await deliveryApi.remove(record.id)
  ElMessage.success('已删除')
  await load()
}
function startInterview(record) {
  router.push({ path: '/interviews', query: { deliveryId: record.id } })
}
function countdown(seconds) {
  if (seconds <= 0) return '已到时间'
  const hours = Math.floor(seconds / 3600)
  if (hours >= 24) return `${Math.floor(hours / 24)} 天后`
  if (hours > 0) return `${hours} 小时后`
  return `${Math.max(1, Math.floor(seconds / 60))} 分钟后`
}
onMounted(load)
</script>

<style scoped>
.summary-strip { display: flex; gap: 10px; margin-bottom: 18px; overflow-x: auto; padding-bottom: 3px; }
.summary-total { padding: 13px 18px; display: flex; align-items: center; gap: 14px; min-width: 235px; }
.summary-total span { color: var(--muted); font-size: 11px; }
.summary-total strong { font-size: 25px; }
.summary-total small { margin-left: auto; color: var(--muted); font-size: 9px; }
.summary-pill { min-width: 105px; cursor: pointer; padding: 13px 15px; border: 1px solid var(--line); border-radius: 13px; background: #fff; display: flex; align-items: center; justify-content: space-between; transition: .18s; }
.summary-pill:hover { border-color: #b7c5f8; }
.summary-pill span { color: var(--muted); font-size: 10px; }
.summary-pill strong { font-size: 19px; }
.delivery-layout { display: grid; grid-template-columns: minmax(0, 1fr) 290px; gap: 18px; align-items: start; }
.kanban { display: grid; grid-template-columns: repeat(6, minmax(205px, 1fr)); gap: 12px; overflow-x: auto; padding-bottom: 8px; }
.kanban.filtered { grid-template-columns: minmax(250px, 1fr); max-width: 440px; }
.kanban-column { background: #eef1f8; border-radius: 14px; min-height: 470px; padding: 9px; }
.column-head { display: flex; justify-content: space-between; align-items: center; padding: 6px 6px 12px; }
.column-head > div { display: flex; align-items: center; gap: 7px; }
.column-head strong { font-size: 12px; }
.column-head > span { font-size: 10px; color: var(--muted); background: #fff; border-radius: 999px; padding: 3px 7px; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #8b96b3; }
.status-dot.applied { background: #5a7be8; } .status-dot.exam { background: #e59a2c; }
.status-dot.interview { background: #8d62d6; } .status-dot.offer { background: var(--success); } .status-dot.rejected { background: #9fa5b4; }
.column-body { display: flex; flex-direction: column; gap: 9px; }
.delivery-card { padding: 13px; box-shadow: none; border-radius: 11px; }
.card-head { display: flex; align-items: flex-start; gap: 8px; }
.company-initial { flex: none; width: 30px; height: 30px; border-radius: 8px; display: grid; place-items: center; background: var(--brand-soft); color: var(--brand); font-size: 12px; font-weight: 800; }
.company-copy { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 3px; }
.company-copy strong { font-size: 12px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.company-copy span { color: var(--muted); font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.more-btn { border: 0; background: transparent; color: var(--muted); cursor: pointer; padding: 0 2px; }
.meta-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 7px; margin: 12px 0; }
.meta-grid span { display: flex; flex-direction: column; gap: 2px; color: #5e6981; font-size: 9px; }
.meta-grid b { color: #9aa2b5; font-size: 8px; font-weight: 600; }
.skill-line { margin: 0 0 10px; padding: 7px 8px; border-radius: 7px; background: #f5f7fb; color: #667086; font-size: 9px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.interview-badge { border-radius: 7px; background: #f2edff; color: #7352b2; padding: 6px 8px; font-size: 9px; margin-bottom: 9px; }
.card-actions { display: flex; flex-wrap: wrap; gap: 5px; border-top: 1px solid #f0f2f7; padding-top: 9px; }
.card-actions button { border: 0; background: #f1f4fd; color: var(--brand); border-radius: 6px; padding: 5px 7px; font-size: 8px; cursor: pointer; }
.card-actions button:hover { background: var(--brand); color: #fff; }
.terminal { color: var(--muted); font-size: 9px; padding: 5px 0; }
.column-empty { border: 1px dashed #d9deea; border-radius: 10px; padding: 20px 8px; text-align: center; color: #a2aabc; font-size: 9px; }
.todo-panel { border-radius: 15px; padding: 17px; }
.todo-head { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 11px; }
.todo-head h2 { margin: 0 0 4px; font-size: 15px; }
.todo-head p { margin: 0; font-size: 9px; color: var(--muted); }
.todo-head > span { background: var(--brand-soft); color: var(--brand); padding: 3px 7px; border-radius: 999px; font-size: 9px; }
.todo-item { display: flex; gap: 9px; padding: 10px 0; border-bottom: 1px solid #f0f2f8; }
.todo-icon { flex: none; width: 27px; height: 27px; display: grid; place-items: center; border-radius: 8px; background: #fff4e7; color: #bd751b; font-size: 9px; font-weight: 700; }
.todo-item div { display: flex; flex-direction: column; gap: 3px; }
.todo-item strong { font-size: 10px; line-height: 1.4; }
.todo-item span { color: var(--muted); font-size: 8px; }
.small-empty { padding: 25px 8px; font-size: 10px; }
.todo-legend { padding-top: 12px; display: flex; flex-direction: column; gap: 4px; color: #9aa2b4; font-size: 8px; }
.parse-banner { display: flex; justify-content: space-between; align-items: center; gap: 12px; background: var(--brand-soft); padding: 11px 13px; border-radius: 10px; margin-bottom: 11px; }
.parse-banner div { display: flex; flex-direction: column; gap: 3px; }
.parse-banner strong { font-size: 11px; color: var(--brand); }
.parse-banner span { font-size: 9px; color: #6978aa; }
.jd-area { margin-bottom: 18px; }
.full { width: 100%; }
@media (max-width: 1200px) { .delivery-layout { grid-template-columns: 1fr; } .todo-panel { order: -1; } }
@media (max-width: 720px) { .kanban { grid-template-columns: repeat(6, 240px); } }
</style>
