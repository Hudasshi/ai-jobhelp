<template>
  <div class="page">
    <div class="page-head">
      <div><h1>数据总览</h1><p>平台用户、诊断、投递与 AI 调用的实时聚合视图。</p></div>
      <el-radio-group v-model="days" @change="load">
        <el-radio-button :value="7">近 7 天</el-radio-button>
        <el-radio-button :value="30">近 30 天</el-radio-button>
      </el-radio-group>
    </div>

    <div class="grid grid-4 admin-stats">
      <div class="panel stat-box">
        <div class="stat-head"><span>用户总数</span><b>用</b></div>
        <strong>{{ stats.userTotal || 0 }}</strong>
        <p>活跃 {{ stats.activeUserCount || 0 }} 人</p>
      </div>
      <div class="panel stat-box">
        <div class="stat-head"><span>今日诊断</span><b>诊</b></div>
        <strong>{{ stats.todayDiagnosisCount || 0 }}</strong>
        <p>AI 辅助分析</p>
      </div>
      <div class="panel stat-box">
        <div class="stat-head"><span>今日 AI 调用</span><b>AI</b></div>
        <strong>{{ stats.todayAiCallCount || 0 }}</strong>
        <p>成功率 {{ rateText }}</p>
      </div>
      <div class="panel stat-box">
        <div class="stat-head"><span>近 7 日成本</span><b>¥</b></div>
        <strong>¥{{ Number(stats.sevenDayCost || 0).toFixed(4) }}</strong>
        <p>按 token 单价估算</p>
      </div>
    </div>

    <div class="grid admin-main">
      <section class="panel panel-pad trend-card">
        <div class="section-title"><div><h2>AI 调用趋势</h2><p>近 {{ days }} 个自然日按日聚合</p></div><span>总计 {{ trendTotal }} 次</span></div>
        <div class="chart">
          <div v-for="point in stats.aiCallTrend || []" :key="point.date" class="bar-column">
            <span class="bar-value">{{ point.count }}</span>
            <div class="bar-track"><div class="bar-fill" :style="{ height: barHeight(point.count) }"></div></div>
            <span class="bar-date">{{ point.date.slice(5) }}</span>
          </div>
        </div>
      </section>

      <section class="panel panel-pad distribution-card">
        <div class="section-title"><div><h2>投递分布</h2><p>全平台有效投递六态</p></div></div>
        <div class="distribution-list">
          <div v-for="item in distribution" :key="item.key">
            <div class="dist-head"><span>{{ item.label }}</span><strong>{{ item.value }}</strong></div>
            <div class="dist-track"><span :style="{ width: distPercent(item.value), background: item.color }"></span></div>
          </div>
        </div>
      </section>
    </div>

    <div class="grid grid-3 quick-links">
      <router-link to="/admin/users" class="panel quick-card"><span>用户运营</span><strong>用户与配额管理</strong><b>→</b></router-link>
      <router-link to="/admin/ai-logs" class="panel quick-card"><span>AI 治理</span><strong>查看调用元数据</strong><b>→</b></router-link>
      <router-link to="/admin/configs" class="panel quick-card"><span>运行参数</span><strong>系统配置热更新</strong><b>→</b></router-link>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../../api'

const days = ref(7)
const stats = ref({})
const distribution = computed(() => {
  const labels = { pending: '待投递', applied: '已投递', exam: '笔试', interview: '面试', offer: 'Offer', rejected: '已结束' }
  const colors = { pending: '#a4adc4', applied: '#3157d5', exam: '#e59024', interview: '#8d62d6', offer: '#12a67a', rejected: '#bcc2d1' }
  return Object.entries(stats.value.deliveryStatusDistribution || {}).map(([key, value]) => ({ key, value, label: labels[key], color: colors[key] }))
})
const trendTotal = computed(() => (stats.value.aiCallTrend || []).reduce((sum, item) => sum + item.count, 0))
const rateText = computed(() => stats.value.aiSuccessRate == null ? '暂无数据' : `${(stats.value.aiSuccessRate * 100).toFixed(1)}%`)
const maxCount = computed(() => Math.max(1, ...(stats.value.aiCallTrend || []).map((item) => item.count)))
const maxDistribution = computed(() => Math.max(1, ...Object.values(stats.value.deliveryStatusDistribution || {})))
const barHeight = (count) => `${Math.max(4, count / maxCount.value * 100)}%`
const distPercent = (value) => `${Math.max(2, value / maxDistribution.value * 100)}%`
async function load() { stats.value = await adminApi.stats(days.value) }
onMounted(load)
</script>

<style scoped>
.admin-stats { margin-bottom: 18px; }
.stat-box { padding: 20px; }
.stat-head { display: flex; justify-content: space-between; color: var(--muted); font-size: 11px; }
.stat-head b { width: 30px; height: 30px; border-radius: 9px; display: grid; place-items: center; background: var(--brand-soft); color: var(--brand); font-size: 9px; }
.stat-box > strong { display: block; font-size: 30px; margin: 17px 0 5px; letter-spacing: -1px; }
.stat-box p { margin: 0; color: var(--muted); font-size: 10px; }
.admin-main { grid-template-columns: 1.3fr .7fr; }
.section-title { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; margin-bottom: 24px; }
.section-title h2 { margin: 0 0 4px; font-size: 16px; }
.section-title p { margin: 0; color: var(--muted); font-size: 10px; }
.section-title > span { color: var(--brand); background: var(--brand-soft); border-radius: 7px; padding: 5px 8px; font-size: 10px; }
.chart { height: 260px; display: flex; gap: 7px; align-items: stretch; padding-top: 15px; }
.bar-column { flex: 1; display: flex; flex-direction: column; align-items: center; min-width: 0; }
.bar-value { font-size: 9px; color: var(--muted); height: 16px; }
.bar-track { flex: 1; width: min(28px, 70%); background: #f3f5fa; border-radius: 7px 7px 2px 2px; display: flex; align-items: flex-end; overflow: hidden; }
.bar-fill { width: 100%; background: linear-gradient(180deg, #6280f2, #3157d5); border-radius: 7px 7px 2px 2px; min-height: 4px; transition: .4s; }
.bar-date { font-size: 8px; color: var(--muted); margin-top: 8px; white-space: nowrap; }
.distribution-list { display: flex; flex-direction: column; gap: 17px; }
.dist-head { display: flex; justify-content: space-between; margin-bottom: 7px; font-size: 11px; }
.dist-head span { color: var(--muted); }
.dist-track { height: 7px; background: #f2f4f9; border-radius: 999px; overflow: hidden; }
.dist-track span { display: block; height: 100%; border-radius: 999px; }
.quick-links { margin-top: 18px; }
.quick-card { padding: 19px; display: flex; flex-direction: column; gap: 8px; transition: .2s; }
.quick-card:hover { transform: translateY(-2px); }
.quick-card span { color: var(--brand); font-size: 9px; font-weight: 700; }
.quick-card strong { font-size: 14px; }
.quick-card b { align-self: flex-end; color: #abb4c9; }
@media (max-width: 1000px) { .admin-main { grid-template-columns: 1fr; } }
</style>
