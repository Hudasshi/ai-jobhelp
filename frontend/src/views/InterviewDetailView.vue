<template>
  <div class="page interview-page" v-if="interview">
    <div class="page-head">
      <div>
        <button class="back-link" @click="$router.push('/interviews')">← 返回面试列表</button>
        <h1>模拟面试 #{{ interview.id }}</h1>
        <p>投递 #{{ interview.deliveryId }} · {{ interviewStatus }} · 创建于 {{ interview.createdAt }}</p>
      </div>
      <div class="head-actions">
        <el-button :disabled="readOnly" :loading="reviewing" @click="generateReview">生成复盘</el-button>
        <el-button type="primary" :disabled="interview.archived" @click="archive">归档面试</el-button>
      </div>
    </div>

    <div v-if="interview.contentExpired" class="expired-banner">本场面试正文已超过 180 天保留期并被清理。</div>

    <div class="interview-layout">
      <section class="question-column">
        <article v-for="(question, index) in interview.questions" :key="question.index" class="question-card panel" :class="{ active: activeIndex === index, answered: answerOf(index) }">
          <button class="question-head" @click="selectQuestion(index)">
            <span class="question-no">{{ String(index + 1).padStart(2, '0') }}</span>
            <div><span class="category">{{ categoryLabel(question.category) }}</span><h3>{{ question.question }}</h3></div>
            <span class="question-state">{{ answerOf(index) ? '已完成' : index === interview.progressIndex ? '当前题' : '未作答' }}</span>
          </button>
          <div v-if="answerOf(index)" class="answer-block">
            <div class="user-answer"><b>你的回答</b><p>{{ answerOf(index).answer }}</p></div>
            <div class="feedback-grid">
              <div class="feedback good"><b>做得好的地方</b><p>{{ answerOf(index).feedback?.good || '暂无' }}</p></div>
              <div class="feedback improve"><b>可以改进</b><p>{{ answerOf(index).feedback?.bad || '暂无' }}</p></div>
            </div>
            <details class="sample-answer"><summary>查看参考表达</summary><p>{{ answerOf(index).feedback?.sample || '暂无' }}</p></details>
          </div>
          <div v-if="activeIndex === index && !readOnly && index <= interview.progressIndex" class="answer-editor">
            <div class="editor-head"><span>{{ answerOf(index) ? '修改并重新点评' : '输入你的回答' }}</span><span>{{ draft.length }} / 2000</span></div>
            <el-input v-model="draft" type="textarea" :rows="6" maxlength="2000" placeholder="建议用 STAR：情境、任务、行动、结果..." />
            <div class="editor-actions">
              <el-button type="primary" :loading="submitting" @click="submitAnswer(index)">提交并获取 AI 点评</el-button>
              <span>每次新提交消耗 1 次 AI 配额</span>
            </div>
          </div>
        </article>
        <div v-if="!interview.questions?.length" class="panel empty">题目尚未生成完成，请稍后刷新。</div>
      </section>

      <aside class="side">
        <div class="panel panel-pad progress-panel">
          <span class="pill">本场进度</span>
          <div class="big-progress">{{ interview.progressIndex }}<span>/ {{ interview.questions?.length || 0 }}</span></div>
          <el-progress :percentage="progressPercent" :show-text="false" :stroke-width="8" />
          <p>{{ interview.progressIndex >= (interview.questions?.length || 0) ? '所有题目已完成，可以生成复盘。' : '完成当前题后可继续下一题。' }}</p>
        </div>
        <div class="panel panel-pad status-panel">
          <h3>面试状态</h3>
          <div class="status-line"><span>当前状态</span><strong>{{ interviewStatus }}</strong></div>
          <div class="status-line"><span>归档</span><strong>{{ interview.archived ? '已归档' : '未归档' }}</strong></div>
          <div class="status-line"><span>归档时间</span><strong>{{ interview.archiveTime || '—' }}</strong></div>
        </div>
        <div v-if="interview.reviewText" class="panel panel-pad review-panel">
          <div class="review-head"><h3>面试复盘</h3><button @click="showReview = !showReview">{{ showReview ? '收起' : '展开' }}</button></div>
          <div v-if="showReview" class="markdown-box review-text">{{ interview.reviewText }}</div>
        </div>
      </aside>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { interviewApi } from '../api'

const route = useRoute()
const interview = ref(null)
const activeIndex = ref(0)
const draft = ref('')
const submitting = ref(false)
const reviewing = ref(false)
const showReview = ref(true)

const interviewStatus = computed(() => ({ created: '待开始', answering: '作答中', reviewed: '已复盘', archived: '已归档' }[interview.value?.status] || interview.value?.status))
const readOnly = computed(() => interview.value?.archived || interview.value?.status === 'reviewed')
const progressPercent = computed(() => {
  const total = interview.value?.questions?.length || 0
  return total ? Math.round(interview.value.progressIndex / total * 100) : 0
})
function answerOf(index) {
  return interview.value?.answers?.find((item) => item.index === index)
}
function categoryLabel(category) {
  return { technical_deep_dive: '技术深挖', fundamentals: '基础知识', behavioral: '行为面试' }[category] || category
}
/**
 * 切换题目时必须同时切换输入框草稿。
 * 只改变 activeIndex 会让草稿停留在上一题，用户可能把答案误提交到另一道题。
 */
function selectQuestion(index) {
  activeIndex.value = index
  draft.value = answerOf(index)?.answer || ''
}
async function load() {
  interview.value = await interviewApi.get(route.params.id)
  activeIndex.value = Math.min(interview.value.progressIndex, Math.max(0, (interview.value.questions?.length || 1) - 1))
  draft.value = answerOf(activeIndex.value)?.answer || ''
}
async function submitAnswer(index) {
  if (readOnly.value) return ElMessage.warning('本场面试已复盘或归档，不能继续作答')
  submitting.value = true
  try {
    const result = await interviewApi.answer(interview.value.id, {
      index,
      answer: draft.value,
      requestId: crypto.randomUUID()
    })
    ElMessage.success('点评已生成')
    await load()
    selectQuestion(Math.min(result.progressIndex, interview.value.questions.length - 1))
  } finally { submitting.value = false }
}
async function generateReview() {
  reviewing.value = true
  try {
    await interviewApi.review(interview.value.id)
    ElMessage.success('复盘已生成')
    await load()
  } finally { reviewing.value = false }
}
async function archive() {
  await ElMessageBox.confirm('归档后本场面试将变为只读，确定继续吗？', '归档面试', { type: 'warning' })
  await interviewApi.archive(interview.value.id)
  ElMessage.success('已归档')
  await load()
}
onMounted(load)
</script>

<style scoped>
.back-link { border: 0; background: transparent; color: var(--muted); font-size: 11px; cursor: pointer; padding: 0; margin-bottom: 10px; }
.head-actions { display: flex; gap: 8px; }
.interview-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 18px; align-items: start; }
.question-column { display: flex; flex-direction: column; gap: 12px; }
.question-card { padding: 0; overflow: hidden; }
.question-card.active { border-color: #aebdff; box-shadow: 0 12px 30px rgba(49, 87, 213, .1); }
.question-card.answered { border-left: 3px solid var(--success); }
.question-head { width: 100%; border: 0; background: #fff; display: flex; align-items: flex-start; gap: 13px; padding: 18px 20px; text-align: left; cursor: pointer; }
.question-no { flex: none; width: 35px; height: 35px; border-radius: 10px; display: grid; place-items: center; background: #f1f4fc; color: var(--brand); font-size: 12px; font-weight: 800; }
.question-head > div { flex: 1; }
.category { color: var(--brand); font-size: 9px; font-weight: 700; }
.question-head h3 { margin: 6px 0 0; font-size: 14px; line-height: 1.6; }
.question-state { font-size: 9px; color: var(--muted); white-space: nowrap; margin-top: 5px; }
.answer-block { padding: 0 20px 18px 68px; }
.user-answer { background: #f8faff; border-radius: 10px; padding: 12px 14px; margin-bottom: 10px; }
.user-answer b, .feedback b { color: var(--muted); font-size: 9px; }
.user-answer p, .feedback p { margin: 6px 0 0; font-size: 11px; line-height: 1.7; color: #35405a; }
.feedback-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; }
.feedback { border-radius: 10px; padding: 11px 13px; }
.feedback.good { background: #f1fbf8; }
.feedback.good b { color: var(--success); }
.feedback.improve { background: #fff8ef; }
.feedback.improve b { color: #bc7419; }
.sample-answer { margin-top: 9px; border: 1px dashed #dfe4ef; border-radius: 9px; padding: 10px 12px; font-size: 10px; color: #55617b; }
.sample-answer summary { cursor: pointer; color: var(--brand); }
.sample-answer p { line-height: 1.7; }
.answer-editor { margin: 0 20px 20px 68px; border-top: 1px solid var(--line); padding-top: 15px; }
.editor-head { display: flex; justify-content: space-between; margin-bottom: 8px; font-size: 10px; color: var(--muted); }
.editor-actions { display: flex; align-items: center; gap: 12px; margin-top: 10px; }
.editor-actions span { color: var(--muted); font-size: 9px; }
.side { display: flex; flex-direction: column; gap: 14px; }
.big-progress { font-size: 42px; font-weight: 900; letter-spacing: -2px; margin: 15px 0 8px; }
.big-progress span { font-size: 14px; color: var(--muted); margin-left: 4px; font-weight: 500; }
.progress-panel p { font-size: 10px; color: var(--muted); line-height: 1.6; margin: 12px 0 0; }
.status-panel h3, .review-panel h3 { margin: 0 0 12px; font-size: 14px; }
.status-line { display: flex; justify-content: space-between; gap: 10px; padding: 9px 0; border-bottom: 1px solid #f0f2f8; font-size: 10px; }
.status-line:last-child { border-bottom: 0; }
.status-line span { color: var(--muted); }
.review-panel { max-height: 500px; overflow: auto; }
.review-head { display: flex; justify-content: space-between; }
.review-head button { border: 0; background: transparent; color: var(--brand); font-size: 10px; cursor: pointer; }
.review-text { font-size: 11px; line-height: 1.9; }
.expired-banner { background: #fff4e8; color: #a86916; border-radius: 10px; padding: 12px; margin-bottom: 16px; font-size: 12px; }
@media (max-width: 1050px) { .interview-layout { grid-template-columns: 1fr; } .side { order: -1; display: grid; grid-template-columns: 1fr 1fr; } .review-panel { grid-column: 1 / -1; } }
@media (max-width: 700px) { .answer-block, .answer-editor { padding-left: 20px; margin-left: 20px; } .feedback-grid, .side { grid-template-columns: 1fr; } .question-head { padding: 15px; } .question-state { display: none; } }
</style>
