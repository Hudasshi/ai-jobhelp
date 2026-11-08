<template>
  <div class="page">
    <div class="page-head">
      <div><h1>AI 简历诊断</h1><p>对照目标 JD，从技能、项目、教育和表达四个维度找出最值得改的地方。</p></div>
      <div class="quota-note">每次诊断消耗 1 次 AI 配额</div>
    </div>

    <div class="diagnosis-layout">
      <section class="panel panel-pad create-panel">
        <div class="panel-title"><span class="step">01</span><div><h2>选择要诊断的简历</h2><p>提交时会冻结当前版本内容</p></div></div>
        <el-select v-model="form.resumeId" placeholder="选择简历版本" size="large" class="full">
          <el-option v-for="resume in resumes" :key="resume.id" :label="`${resume.name} · ${resume.targetPosition || '通用'}`" :value="resume.id" />
        </el-select>
        <div v-if="!resumes.length" class="inline-empty">暂无简历版本，<router-link to="/resumes">先去创建 →</router-link></div>

        <div class="panel-title second"><span class="step">02</span><div><h2>关联投递（可选）</h2><p>从投递发起会自动带出岗位上下文</p></div></div>
        <el-select v-model="form.deliveryId" clearable placeholder="不关联投递" size="large" class="full">
          <el-option v-for="delivery in deliveries" :key="delivery.id" :label="`${delivery.company} · ${delivery.position}`" :value="delivery.id" />
        </el-select>

        <div class="panel-title second"><span class="step">03</span><div><h2>粘贴目标 JD</h2><p>建议保留职责与任职要求，50~8000 字</p></div></div>
        <el-input v-model="form.jd" type="textarea" :rows="10" maxlength="8000" show-word-limit placeholder="把目标岗位的完整岗位描述粘贴到这里..." />
        <div class="submit-row">
          <span>{{ form.jd.length }} 字</span>
          <el-button type="primary" size="large" :loading="submitting" :disabled="!form.resumeId" @click="submit">开始 AI 诊断</el-button>
        </div>
      </section>

      <section class="history">
        <div class="history-head">
          <div><h2>诊断历史</h2><p>报告正文保留 180 天</p></div>
          <el-select v-model="statusFilter" clearable placeholder="全部状态" size="small" @change="loadPage">
            <el-option label="成功" value="success" /><el-option label="降级" value="degraded" /><el-option label="失败" value="failed" />
          </el-select>
        </div>
        <div v-if="records.length" class="history-list">
          <button v-for="record in records" :key="record.id" class="history-row panel" @click="openReport(record.id)">
            <div class="score-mini" :class="scoreClass(record.score)">{{ record.score ?? '—' }}</div>
            <div class="history-copy">
              <strong>{{ record.position || '未关联岗位' }}</strong>
              <span>{{ record.createdAt }} · {{ record.degraded ? '降级结果' : statusText(record.status) }}</span>
            </div>
            <span class="view-link">查看报告 →</span>
          </button>
        </div>
        <div v-else class="panel empty">还没有诊断记录，完成左侧表单开始第一次分析。</div>
        <div class="pager" v-if="total > pageSize">
          <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="loadPage" />
        </div>
      </section>
    </div>

    <el-drawer v-model="drawerVisible" size="min(900px, 96vw)" :with-header="false" destroy-on-close>
      <div v-if="reportLoading" class="report-loading"><el-skeleton :rows="8" animated /></div>
      <div v-else-if="report" class="report-page">
        <div class="report-head">
          <div>
            <span class="pill">诊断报告 · {{ report.createdAt }}</span>
            <h2>{{ report.position || '目标岗位诊断' }}</h2>
            <p>报告 ID #{{ report.id }} · 耗时 {{ report.costSeconds || 0 }} 秒 · {{ report.tokens || 0 }} tokens</p>
          </div>
          <div class="overall-score">
            <strong>{{ report.report?.score }}</strong><span>/ 100</span>
          </div>
        </div>

        <div class="dims">
          <div v-for="dim in report.report?.dims || []" :key="dim.key" class="dim-card">
            <div><span>{{ dim.name }}</span><strong>{{ dim.score }}</strong></div>
            <el-progress :percentage="dim.score" :stroke-width="7" :show-text="false" :color="dimColor(dim.key)" />
            <small>权重 {{ Math.round(dim.weight * 100) }}%</small>
          </div>
        </div>

        <div class="report-section-head">
          <div><h3>逐条改写建议</h3><p>采纳会记录在报告中，保存版本时可作为调整依据。</p></div>
          <span>已采纳 {{ adoptedCount }} / {{ report.report?.items?.length || 0 }}</span>
        </div>
        <div class="suggestion-list">
          <article v-for="item in report.report?.items || []" :key="item.entryId" class="suggestion-card" :class="{ adopted: item.adopted }">
            <div class="suggestion-top">
              <span class="entry-tag">经历 #{{ item.entryId }}</span>
              <el-switch v-model="item.adopted" active-text="已采纳" inactive-text="未采纳" @change="(value) => toggleAdopt(item, value)" />
            </div>
            <div class="original"><b>原表达</b><p>{{ item.original }}</p></div>
            <div class="rewrite"><b>建议改写</b><p>{{ item.rewrite }}</p></div>
            <div class="tips"><span><b>问题</b>{{ item.problem }}</span><span><b>建议</b>{{ item.suggestion }}</span></div>
          </article>
        </div>
        <div v-if="report.report?.skipped?.length" class="skipped-box">
          <strong>已跳过条目</strong>
          <span v-for="item in report.report.skipped" :key="item.entryId">#{{ item.entryId }} · {{ item.reason }}</span>
        </div>

        <div class="report-actions">
          <span v-if="adoptedCount === 0" class="adopt-hint">请先采纳至少一条改写建议</span>
          <el-button @click="drawerVisible = false">关闭</el-button>
          <el-button type="primary" :loading="savingResume" :disabled="adoptedCount === 0" @click="saveAsResume">基于本报告保存简历版本</el-button>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { deliveryApi, diagnosisApi, resumeApi, taskApi } from '../api'
import { pollTask } from '../utils/poll'

const route = useRoute()
const router = useRouter()
const resumes = ref([])
const deliveries = ref([])
const records = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 6
const statusFilter = ref('')
const submitting = ref(false)
const drawerVisible = ref(false)
const reportLoading = ref(false)
const report = ref(null)
const savingResume = ref(false)
const form = reactive({ resumeId: null, deliveryId: null, jd: '' })
const adoptedCount = computed(() => (report.value?.report?.items || []).filter((item) => item.adopted).length)

const statusText = (status) => ({ success: '成功', degraded: '降级完成', failed: '失败' }[status] || status)
const scoreClass = (score) => score >= 80 ? 'good' : score >= 65 ? 'mid' : 'low'
const dimColor = (key) => ({ skill: '#3157d5', project: '#12a67a', education: '#e59024', expression: '#8d62d6' }[key] || '#3157d5')

async function loadOptions() {
  const [resumeData, deliveryData] = await Promise.all([
    resumeApi.list({ page: 1, size: 100 }),
    deliveryApi.list()
  ])
  resumes.value = resumeData.records.filter((item) => item.status === 'ready')
  deliveries.value = deliveryData.records
  if (!form.resumeId && resumes.value.length) form.resumeId = resumes.value[0].id
}
async function loadPage() {
  const data = await diagnosisApi.page({ status: statusFilter.value || undefined, page: page.value, size: pageSize })
  records.value = data.records
  total.value = data.total
}
async function submit() {
  if (!form.resumeId) return ElMessage.warning('请选择简历版本')
  if (form.jd.trim().length < 50) return ElMessage.warning('JD 至少需要 50 字')
  submitting.value = true
  try {
    const { taskId } = await diagnosisApi.create({ resumeId: form.resumeId, deliveryId: form.deliveryId || null, jd: form.jd.trim() })
    const task = await pollTask(taskApi, taskId)
    if (task.status !== 'SUCCESS') throw new Error(task.failReason || '诊断失败')
    ElMessage.success('诊断完成')
    await loadPage()
    await openReport(task.resultId)
  } catch (error) {
    if (error.message) ElMessage.error(error.message)
  } finally { submitting.value = false }
}
async function openReport(id) {
  drawerVisible.value = true
  reportLoading.value = true
  try { report.value = await diagnosisApi.get(id) }
  finally { reportLoading.value = false }
}
async function toggleAdopt(item, value) {
  try {
    await diagnosisApi.adopt(report.value.id, item.entryId, value)
    ElMessage.success(value ? '已采纳该建议' : '已取消采纳')
  } catch {
    item.adopted = !value
  }
}
async function saveAsResume() {
  try {
    const { value: name } = await ElMessageBox.prompt('请输入新版本名称', '保存诊断版本', {
      inputValue: `${report.value.position || '诊断'} · 优化版`,
      inputValidator: (value) => Boolean(value?.trim()) || '版本名称不能为空'
    })
    savingResume.value = true
    await resumeApi.save({
      name: name.trim(),
      targetPosition: report.value.position || null,
      sourceType: 'diagnose',
      diagnoseId: report.value.id
    })
    ElMessage.success('已保存到简历工作台')
  } catch (error) {
    if (error !== 'cancel' && error?.message) ElMessage.error(error.message)
  } finally { savingResume.value = false }
}

onMounted(async () => {
  await Promise.all([loadOptions(), loadPage()])
  const reportId = Number(route.query.reportId)
  if (reportId) openReport(reportId)
  else if (records.value.length) {
    // 工作台进入时只展示历史列表，不自动打开，避免打断用户发起新诊断。
  }
  if (route.query.reportId) router.replace({ path: '/diagnosis' })
})
</script>

<style scoped>
.quota-note { padding: 9px 13px; border-radius: 10px; background: var(--brand-soft); color: var(--brand); font-size: 11px; }
.diagnosis-layout { display: grid; grid-template-columns: minmax(0, 1.1fr) minmax(350px, .7fr); gap: 20px; align-items: start; }
.create-panel { padding: 26px; }
.panel-title { display: flex; gap: 12px; align-items: flex-start; margin-bottom: 15px; }
.panel-title.second { margin-top: 27px; }
.step { flex: none; display: grid; place-items: center; width: 29px; height: 29px; border-radius: 9px; background: var(--brand-soft); color: var(--brand); font-size: 10px; font-weight: 800; }
.panel-title h2 { margin: 0 0 4px; font-size: 15px; }
.panel-title p { margin: 0; color: var(--muted); font-size: 10px; }
.full { width: 100%; }
.inline-empty { color: var(--muted); font-size: 11px; margin-top: 8px; }
.inline-empty a { color: var(--brand); }
.submit-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 16px; }
.submit-row span { color: var(--muted); font-size: 11px; }
.history { min-width: 0; }
.history-head { display: flex; align-items: flex-end; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.history-head h2 { margin: 0 0 4px; font-size: 18px; }
.history-head p { margin: 0; color: var(--muted); font-size: 10px; }
.history-list { display: flex; flex-direction: column; gap: 11px; }
.history-row { padding: 15px; border: 1px solid var(--line); display: flex; align-items: center; gap: 12px; text-align: left; cursor: pointer; transition: .18s; }
.history-row:hover { border-color: #bdcaff; transform: translateX(2px); }
.score-mini { width: 48px; height: 48px; border-radius: 12px; display: grid; place-items: center; font-size: 18px; font-weight: 900; background: #f1f3f9; color: var(--muted); }
.score-mini.good { background: #e9f8f3; color: var(--success); }
.score-mini.mid { background: #fff4e8; color: #bd7317; }
.score-mini.low { background: #fdeeee; color: var(--danger); }
.history-copy { flex: 1; display: flex; flex-direction: column; gap: 5px; min-width: 0; }
.history-copy strong { font-size: 13px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.history-copy span { color: var(--muted); font-size: 10px; }
.view-link { color: var(--brand); font-size: 10px; font-weight: 700; white-space: nowrap; }
.pager { display: flex; justify-content: center; margin-top: 18px; }
.report-loading { padding: 30px; }
.report-page { padding: 32px 36px 40px; background: #fff; min-height: 100%; }
.report-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 20px; padding-bottom: 25px; border-bottom: 1px solid var(--line); }
.report-head h2 { margin: 15px 0 7px; font-size: 26px; }
.report-head p { margin: 0; color: var(--muted); font-size: 11px; }
.overall-score { display: flex; align-items: baseline; color: var(--brand); }
.overall-score strong { font-size: 52px; letter-spacing: -3px; }
.overall-score span { font-size: 13px; color: var(--muted); }
.dims { display: grid; grid-template-columns: repeat(4, 1fr); gap: 12px; margin: 22px 0; }
.dim-card { background: #f8faff; border: 1px solid #edf0f8; border-radius: 12px; padding: 14px; }
.dim-card > div { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.dim-card span { font-size: 11px; color: var(--muted); }
.dim-card strong { font-size: 18px; }
.dim-card small { display: block; color: var(--muted); font-size: 9px; margin-top: 7px; }
.report-section-head { display: flex; justify-content: space-between; align-items: flex-end; margin: 30px 0 14px; }
.report-section-head h3 { margin: 0 0 4px; font-size: 18px; }
.report-section-head p { margin: 0; color: var(--muted); font-size: 10px; }
.report-section-head > span { color: var(--muted); font-size: 11px; }
.suggestion-list { display: flex; flex-direction: column; gap: 14px; }
.suggestion-card { border: 1px solid var(--line); border-radius: 14px; padding: 16px; transition: .2s; }
.suggestion-card.adopted { border-color: #b6e6d8; background: #fbfffd; }
.suggestion-top { display: flex; justify-content: space-between; align-items: center; margin-bottom: 12px; }
.entry-tag { font-size: 10px; color: var(--muted); background: #f1f3f9; padding: 4px 8px; border-radius: 6px; }
.original, .rewrite { border-radius: 9px; padding: 11px 13px; margin-bottom: 9px; }
.original { background: #fafafa; }
.rewrite { background: #f2f6ff; }
.original b, .rewrite b { font-size: 10px; color: var(--muted); display: block; margin-bottom: 5px; }
.rewrite b { color: var(--brand); }
.original p, .rewrite p { margin: 0; font-size: 12px; line-height: 1.7; color: #38425a; }
.tips { display: grid; grid-template-columns: 1fr 1fr; gap: 10px; }
.tips span { font-size: 10px; color: var(--muted); line-height: 1.6; }
.tips b { display: block; color: #566178; margin-bottom: 3px; }
.skipped-box { margin-top: 15px; border: 1px dashed #e1c696; background: #fffbf4; border-radius: 10px; padding: 12px; display: flex; gap: 12px; flex-wrap: wrap; font-size: 10px; color: #987133; }
.report-actions { display: flex; justify-content: flex-end; gap: 9px; border-top: 1px solid var(--line); margin-top: 26px; padding-top: 20px; }
.adopt-hint { align-self: center; color: #bd7317; font-size: 10px; margin-right: auto; }
@media (max-width: 1100px) { .diagnosis-layout { grid-template-columns: 1fr; } }
@media (max-width: 720px) { .dims, .tips { grid-template-columns: 1fr 1fr; } .report-page { padding: 22px 18px; } .report-head { flex-direction: column; } }
</style>
