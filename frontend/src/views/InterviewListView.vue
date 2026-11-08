<template>
  <div class="page">
    <div class="page-head">
      <div><h1>模拟面试</h1><p>根据真实投递与 JD 出题，逐题作答、获得点评，再生成结构化复盘。</p></div>
      <el-button type="primary" size="large" @click="openStart()">开始一场面试</el-button>
    </div>

    <div class="interview-hero">
      <div>
        <span class="pill">面试练习闭环</span>
        <h2>把“知道”练成“说得清楚”</h2>
        <p>建议先完成投递记录，再针对该岗位发起模拟面试；每场 5~6 题，逐题点评。</p>
      </div>
      <div class="hero-steps">
        <span><b>1</b>选择投递</span><i>→</i>
        <span><b>2</b>逐题作答</span><i>→</i>
        <span><b>3</b>生成复盘</span><i>→</i>
        <span><b>4</b>归档</span>
      </div>
    </div>

    <div class="interview-grid" v-if="records.length">
      <article v-for="record in records" :key="record.id" class="interview-card panel" @click="$router.push(`/interviews/${record.id}`)">
        <div class="card-top">
          <span class="status-chip" :class="record.status">{{ interviewStatus(record.status) }}</span>
          <span class="time">{{ record.createdAt }}</span>
        </div>
        <h3>投递 #{{ record.deliveryId }} · 模拟面试</h3>
        <div class="progress-row">
          <div class="progress-copy"><span>完成进度</span><strong>{{ record.progressIndex }} / {{ record.totalQuestions }}</strong></div>
          <el-progress :percentage="record.totalQuestions ? Math.round(record.progressIndex / record.totalQuestions * 100) : 0" :show-text="false" :stroke-width="7" />
        </div>
        <div class="card-foot">
          <span>{{ record.archived ? '已归档，只读' : record.progressIndex ? '可继续作答' : '等待开始' }}</span>
          <b>进入面试 →</b>
        </div>
      </article>
    </div>
    <div v-else class="panel empty">还没有模拟面试。选择一条投递记录开始第一次练习。</div>
    <div class="pager" v-if="total > pageSize">
      <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="startVisible" title="开始模拟面试" width="min(540px, 94vw)">
      <div class="start-tip">系统会根据该投递关联的岗位 JD 和能力库生成题目，本次会消耗 1 次 AI 配额。</div>
      <el-form label-position="top">
        <el-form-item label="选择投递记录" required>
          <el-select v-model="startForm.deliveryId" placeholder="请选择" class="full">
            <el-option v-for="delivery in deliveries" :key="delivery.id" :label="`${delivery.company} · ${delivery.position}`" :value="delivery.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="面试侧重">
          <el-radio-group v-model="startForm.focus">
            <el-radio-button value="comprehensive">综合面试</el-radio-button>
            <el-radio-button value="technical_deep_dive">技术深挖</el-radio-button>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="startVisible = false">取消</el-button>
        <el-button type="primary" :loading="starting" @click="start">开始出题</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { deliveryApi, interviewApi, taskApi } from '../api'
import { pollTask } from '../utils/poll'

const route = useRoute()
const router = useRouter()
const records = ref([])
const deliveries = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 9
const startVisible = ref(false)
const starting = ref(false)
const startForm = reactive({ deliveryId: null, focus: 'comprehensive' })

const interviewStatus = (status) => ({ created: '待开始', answering: '作答中', reviewed: '已复盘', archived: '已归档' }[status] || status)

async function load() {
  const data = await interviewApi.list({ page: page.value, size: pageSize })
  records.value = data.records
  total.value = data.total
}
async function openStart(deliveryId) {
  const data = await deliveryApi.list()
  deliveries.value = data.records
  startForm.deliveryId = deliveryId || Number(route.query.deliveryId) || deliveries.value[0]?.id || null
  startVisible.value = true
}
async function start() {
  if (!startForm.deliveryId) return ElMessage.warning('请选择投递记录')
  starting.value = true
  try {
    const { taskId } = await interviewApi.start(startForm)
    const task = await pollTask(taskApi, taskId)
    if (task.status !== 'SUCCESS') throw new Error(task.failReason || '出题失败')
    ElMessage.success('题目已生成')
    startVisible.value = false
    router.push(`/interviews/${task.resultId}`)
  } catch (error) {
    if (error.message) ElMessage.error(error.message)
  } finally { starting.value = false }
}
onMounted(async () => {
  await load()
  if (route.query.deliveryId) openStart()
})
</script>

<style scoped>
.interview-hero { display: flex; justify-content: space-between; align-items: center; gap: 24px; background: linear-gradient(120deg, #fff, #eef3ff); border: 1px solid #dfe6fb; border-radius: 18px; padding: 25px 28px; margin-bottom: 20px; }
.interview-hero h2 { margin: 14px 0 7px; font-size: 24px; }
.interview-hero p { margin: 0; color: var(--muted); font-size: 12px; }
.hero-steps { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.hero-steps span { display: flex; flex-direction: column; align-items: center; gap: 5px; font-size: 10px; color: var(--muted); }
.hero-steps b { width: 27px; height: 27px; border-radius: 50%; background: #fff; color: var(--brand); display: grid; place-items: center; font-size: 10px; border: 1px solid #dce4ff; }
.hero-steps i { color: #bec7df; font-style: normal; }
.interview-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 17px; }
.interview-card { padding: 20px; cursor: pointer; transition: .2s; }
.interview-card:hover { transform: translateY(-3px); box-shadow: 0 22px 45px rgba(36, 51, 104, .13); }
.card-top { display: flex; justify-content: space-between; gap: 8px; align-items: center; }
.status-chip { font-size: 10px; padding: 4px 8px; border-radius: 6px; background: #f0f2f8; color: var(--muted); }
.status-chip.answering { background: #fff3e4; color: #b97117; }
.status-chip.reviewed, .status-chip.archived { background: #e9f8f3; color: var(--success); }
.time { color: #a0a8ba; font-size: 9px; }
.interview-card h3 { font-size: 16px; margin: 17px 0; }
.progress-copy { display: flex; justify-content: space-between; margin-bottom: 8px; }
.progress-copy span { color: var(--muted); font-size: 10px; }
.progress-copy strong { font-size: 11px; }
.card-foot { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid #f0f2f8; padding-top: 14px; margin-top: 18px; }
.card-foot span { color: var(--muted); font-size: 10px; }
.card-foot b { color: var(--brand); font-size: 10px; }
.pager { display: flex; justify-content: center; margin-top: 22px; }
.start-tip { background: #f5f8ff; border-radius: 10px; padding: 12px 14px; color: #6475a1; font-size: 11px; line-height: 1.7; margin-bottom: 17px; }
.full { width: 100%; }
@media (max-width: 1150px) { .interview-grid { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 760px) { .interview-grid { grid-template-columns: 1fr; } .interview-hero { flex-direction: column; align-items: flex-start; } }
</style>
