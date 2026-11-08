<template>
  <div class="page dashboard">
    <section class="welcome-panel">
      <div>
        <span class="pill">求职进度中枢</span>
        <h1>{{ greeting }}，{{ summary.nickname || auth.nickname || '同学' }}</h1>
        <p>把今天要推进的事情变小：补一条经历、跑一次诊断，或者完成一场模拟面试。</p>
        <div class="welcome-actions">
          <el-button type="primary" size="large" @click="$router.push('/resumes')">生成定制简历</el-button>
          <el-button size="large" @click="$router.push('/deliveries')">查看投递看板</el-button>
        </div>
      </div>
      <div class="quota-ring">
        <div class="ring" :style="{ '--p': quotaPercent + '%' }">
          <div><strong>{{ summary.quota?.remaining ?? '—' }}</strong><span>今日剩余</span></div>
        </div>
        <p>已用 {{ summary.quota?.quotaUsed || 0 }} / {{ summary.quota?.quotaLimit || 0 }} 次</p>
      </div>
    </section>

    <section class="grid grid-4 stat-grid">
      <div v-for="item in statCards" :key="item.label" class="stat-card panel" @click="$router.push(item.path)">
        <div class="stat-top"><span>{{ item.eyebrow }}</span><b>{{ item.symbol }}</b></div>
        <strong>{{ item.value }}</strong>
        <p>{{ item.label }}</p>
      </div>
    </section>

    <section class="grid main-grid">
      <div class="panel panel-pad">
        <div class="section-head">
          <div><h2>最近任务</h2><p>AI 处理完成后会在这里留下记录</p></div>
          <router-link to="/diagnosis">查看诊断 →</router-link>
        </div>
        <div v-if="notices.length" class="notice-list">
          <button v-for="notice in notices" :key="notice.taskId" class="notice-row" @click="openNotice(notice)">
            <span class="task-dot" :class="notice.status.toLowerCase()"></span>
            <span class="notice-copy">
              <strong>{{ notice.title }}</strong>
              <small>{{ notice.createdAt }} · {{ taskStatus(notice.status) }}</small>
            </span>
            <span class="notice-right" :class="{ unread: !notice.readFlag }">
              {{ notice.status === 'FAIL' ? (notice.failReason || '失败') : notice.readFlag ? '已读' : '新通知' }}
            </span>
          </button>
        </div>
        <div v-else class="empty">还没有任务记录，先上传或生成一份简历吧。</div>
      </div>

      <div class="panel panel-pad">
        <div class="section-head">
          <div><h2>临近待办</h2><p>未来 24 小时与 3 天内</p></div>
          <router-link to="/deliveries">全部待办 →</router-link>
        </div>
        <div v-if="todos.length" class="todo-list">
          <div v-for="(todo, index) in todos" :key="`${todo.type}-${todo.recordId}-${index}`" class="todo-row">
            <span class="todo-type">{{ todoType(todo.type) }}</span>
            <div><strong>{{ todo.company }} · {{ todo.position }}</strong><small>{{ todo.time }}</small></div>
            <span class="todo-count">{{ countdown(todo.remainingSeconds) }}</span>
          </div>
        </div>
        <div v-else class="empty">近期待办为空，可以先完善投递计划。</div>
      </div>
    </section>

    <section class="grid grid-2 bottom-grid">
      <div class="panel panel-pad diagnosis-card">
        <div class="section-head"><div><h2>最近一次诊断</h2><p>用四维评分定位下一处提升</p></div></div>
        <template v-if="summary.latestDiagnosis">
          <div class="score-row">
            <div class="score">{{ summary.latestDiagnosis.score }}<span>分</span></div>
            <div><strong>{{ summary.latestDiagnosis.position || '未关联岗位' }}</strong><p>{{ summary.latestDiagnosis.createdAt }} · {{ summary.latestDiagnosis.status === 'degraded' ? '降级结果' : '诊断完成' }}</p></div>
          </div>
          <el-button class="wide-btn" @click="openDiagnosis">查看完整报告</el-button>
        </template>
        <div v-else class="empty">本月还没有诊断记录</div>
      </div>
      <div class="panel panel-pad flow-card">
        <div class="section-head"><div><h2>求职闭环</h2><p>每一步都会为下一步提供上下文</p></div></div>
        <div class="flow">
          <button @click="$router.push('/abilities')"><b>01</b><span>沉淀能力</span></button>
          <i>→</i>
          <button @click="$router.push('/resumes')"><b>02</b><span>生成简历</span></button>
          <i>→</i>
          <button @click="$router.push('/diagnosis')"><b>03</b><span>AI 诊断</span></button>
          <i>→</i>
          <button @click="$router.push('/interviews')"><b>04</b><span>面试演练</span></button>
        </div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { dashboardApi, taskApi } from '../api'
import { auth } from '../stores/auth'

const router = useRouter()
const summary = ref({})
const notices = ref([])
const todos = ref([])
const greeting = computed(() => {
  const hour = new Date().getHours()
  return hour < 11 ? '早上好' : hour < 14 ? '中午好' : hour < 18 ? '下午好' : '晚上好'
})
const quotaPercent = computed(() => {
  if (!summary.value.quota?.quotaLimit) return 0
  return Math.min(100, Math.round(summary.value.quota.quotaUsed / summary.value.quota.quotaLimit * 100))
})
const statCards = computed(() => [
  { label: '已确认能力', eyebrow: '事实资产', symbol: '能', value: summary.value.abilityCount || 0, path: '/abilities' },
  { label: '简历版本', eyebrow: '可投递', symbol: '历', value: summary.value.resumeCount || 0, path: '/resumes' },
  { label: '投递记录', eyebrow: '进行中', symbol: '投', value: summary.value.deliveryCount || 0, path: '/deliveries' },
  { label: '本月诊断', eyebrow: 'AI 分析', symbol: '诊', value: summary.value.monthlyDiagnosisCount || 0, path: '/diagnosis' }
])

function taskStatus(status) {
  return { QUEUED: '排队中', RUNNING: '处理中', SUCCESS: '已完成', FAIL: '失败' }[status] || status
}
function todoType(type) {
  return { exam: '笔试', interview: '面试', deadline: '截止' }[type] || type
}
function countdown(seconds) {
  if (seconds <= 0) return '即将开始'
  const hours = Math.floor(seconds / 3600)
  if (hours >= 24) return `${Math.floor(hours / 24)} 天后`
  if (hours > 0) return `${hours} 小时后`
  return `${Math.max(1, Math.floor(seconds / 60))} 分钟后`
}
async function openNotice(notice) {
  if (!notice.readFlag) {
    await taskApi.markRead(notice.taskId).catch(() => {})
    notice.readFlag = 1
  }
  if (notice.taskType === 'diagnose' && notice.resultId) {
    // 带上报告主键，诊断页才能直接定位到任务完成后的那份报告。
    router.push({ path: '/diagnosis', query: { reportId: notice.resultId } })
  }
  else if (notice.taskType === 'interview_gen' && notice.resultId) router.push(`/interviews/${notice.resultId}`)
  else router.push(notice.taskType === 'resume_parse' ? '/resumes' : '/resumes')
}
function openDiagnosis() {
  router.push({ path: '/diagnosis', query: { reportId: summary.value.latestDiagnosis.id } })
}
onMounted(async () => {
  const [summaryData, noticeData, todoData] = await Promise.all([
    dashboardApi.summary(),
    dashboardApi.notices(5),
    import('../api').then(({ deliveryApi }) => deliveryApi.todo())
  ])
  summary.value = summaryData
  notices.value = noticeData
  todos.value = todoData.slice(0, 4)
})
</script>

<style scoped>
.dashboard { display: flex; flex-direction: column; gap: 20px; }
.welcome-panel {
  position: relative; overflow: hidden; display: flex; justify-content: space-between; align-items: center; gap: 30px;
  padding: 34px 38px; border-radius: 22px; color: #fff;
  background: radial-gradient(circle at 90% 20%, rgba(109, 230, 205, .28), transparent 25%), linear-gradient(125deg, #172a66, #3157d5 72%);
  box-shadow: 0 22px 60px rgba(27, 58, 165, .24);
}
.welcome-panel::after { content: ""; position: absolute; width: 260px; height: 260px; right: 12%; top: -160px; border: 1px solid rgba(255,255,255,.15); border-radius: 50%; box-shadow: 0 0 0 55px rgba(255,255,255,.03); }
.welcome-panel > div { position: relative; z-index: 1; }
.welcome-panel .pill { background: rgba(255,255,255,.12); color: #dfe7ff; }
.welcome-panel h1 { font-size: 32px; margin: 18px 0 10px; letter-spacing: -1px; }
.welcome-panel p { margin: 0; color: rgba(255,255,255,.7); max-width: 650px; line-height: 1.8; }
.welcome-actions { display: flex; gap: 10px; margin-top: 24px; }
.welcome-actions :deep(.el-button:not(.el-button--primary)) { background: rgba(255,255,255,.08); border-color: rgba(255,255,255,.25); color: #fff; }
.quota-ring { text-align: center; min-width: 165px; }
.ring { width: 122px; height: 122px; border-radius: 50%; display: grid; place-items: center; margin: 0 auto 10px; background: conic-gradient(#69dec9 var(--p), rgba(255,255,255,.15) 0); position: relative; }
.ring::before { content: ""; position: absolute; inset: 9px; border-radius: 50%; background: #2747b0; }
.ring > div { position: relative; z-index: 1; display: flex; flex-direction: column; }
.ring strong { font-size: 28px; }
.ring span { font-size: 10px; color: rgba(255,255,255,.65); }
.quota-ring p { font-size: 11px; color: rgba(255,255,255,.55); }
.stat-card { padding: 20px; cursor: pointer; transition: .2s; }
.stat-card:hover { transform: translateY(-3px); box-shadow: 0 22px 45px rgba(36, 51, 104, .13); }
.stat-top { display: flex; justify-content: space-between; align-items: center; color: var(--muted); font-size: 11px; }
.stat-top b { width: 31px; height: 31px; border-radius: 9px; display: grid; place-items: center; color: var(--brand); background: var(--brand-soft); font-size: 11px; }
.stat-card > strong { display: block; font-size: 34px; margin: 17px 0 2px; letter-spacing: -1px; }
.stat-card p { margin: 0; color: var(--muted); font-size: 12px; }
.main-grid { grid-template-columns: minmax(0, 1.35fr) minmax(340px, .65fr); }
.section-head { display: flex; justify-content: space-between; gap: 16px; align-items: flex-start; margin-bottom: 18px; }
.section-head h2 { margin: 0 0 5px; font-size: 17px; }
.section-head p { margin: 0; color: var(--muted); font-size: 11px; }
.section-head a { color: var(--brand); font-size: 12px; font-weight: 700; }
.notice-row { width: 100%; border: 0; background: transparent; display: flex; align-items: center; gap: 12px; padding: 12px 5px; border-bottom: 1px solid #f0f2f8; cursor: pointer; text-align: left; }
.notice-row:last-child { border-bottom: 0; }
.task-dot { width: 8px; height: 8px; border-radius: 50%; background: var(--brand); flex: none; }
.task-dot.fail { background: var(--danger); }
.task-dot.queued, .task-dot.running { background: var(--warning); }
.notice-copy { flex: 1; display: flex; flex-direction: column; gap: 4px; }
.notice-copy strong { font-size: 13px; }
.notice-copy small { color: var(--muted); font-size: 10px; }
.notice-right { font-size: 10px; color: var(--muted); }
.notice-right.unread { color: var(--brand); font-weight: 700; }
.todo-row { display: flex; align-items: center; gap: 11px; padding: 11px 0; border-bottom: 1px solid #f0f2f8; }
.todo-row:last-child { border-bottom: 0; }
.todo-type { padding: 4px 7px; border-radius: 6px; background: #fff4e7; color: #c17413; font-size: 10px; font-weight: 700; }
.todo-row div { flex: 1; display: flex; flex-direction: column; gap: 3px; }
.todo-row strong { font-size: 12px; }
.todo-row small { color: var(--muted); font-size: 10px; }
.todo-count { font-size: 10px; color: #d6683f; }
.bottom-grid { margin-bottom: 0; }
.score-row { display: flex; align-items: center; gap: 18px; padding: 10px 0 20px; }
.score { width: 74px; height: 74px; border-radius: 50%; display: flex; align-items: baseline; justify-content: center; padding-top: 19px; background: var(--brand-soft); color: var(--brand); font-size: 28px; font-weight: 900; }
.score span { font-size: 10px; margin-left: 2px; }
.score-row strong { font-size: 14px; }
.score-row p { margin: 6px 0 0; color: var(--muted); font-size: 11px; }
.wide-btn { width: 100%; }
.flow { display: flex; align-items: center; gap: 8px; }
.flow button { flex: 1; border: 1px solid var(--line); background: #f9faff; border-radius: 12px; padding: 13px 8px; cursor: pointer; display: flex; flex-direction: column; align-items: center; gap: 7px; }
.flow b { color: var(--brand); font-size: 11px; }
.flow span { font-size: 11px; color: var(--ink); }
.flow i { color: #b8c0d6; font-style: normal; }
@media (max-width: 1050px) {
  .main-grid { grid-template-columns: 1fr; }
  .welcome-panel { align-items: flex-start; }
}
@media (max-width: 640px) {
  .welcome-panel { padding: 25px; }
  .welcome-panel h1 { font-size: 25px; }
  .quota-ring { display: none; }
  .flow { overflow-x: auto; }
  .flow button { min-width: 85px; }
}
</style>
