<template>
  <div class="app-shell" :class="{ 'nav-open': mobileOpen }">
    <aside class="sidebar">
      <div class="logo-row">
        <div class="logo-mark">AI</div>
        <div><strong>求职助手</strong><span>Career Copilot</span></div>
      </div>
      <nav>
        <div class="nav-caption">求职工作台</div>
        <router-link v-for="item in userMenus" :key="item.path" :to="item.path" class="nav-item" @click="mobileOpen = false">
          <span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span>
        </router-link>
        <template v-if="auth.isAdmin">
          <div class="nav-caption admin-caption">系统管理</div>
          <router-link v-for="item in adminMenus" :key="item.path" :to="item.path" class="nav-item" @click="mobileOpen = false">
            <span class="nav-icon">{{ item.icon }}</span><span>{{ item.label }}</span>
          </router-link>
        </template>
      </nav>
      <div class="sidebar-foot">
        <div class="quota-mini" v-if="quota">
          <div><span>今日 AI 配额</span><strong>{{ quota.remaining }} / {{ quota.quotaLimit }}</strong></div>
          <el-progress :percentage="quotaPercent" :show-text="false" :stroke-width="6" />
        </div>
        <button class="logout-btn" @click="logout">退出登录</button>
      </div>
    </aside>

    <div class="mobile-mask" v-if="mobileOpen" @click="mobileOpen = false"></div>
    <main class="main">
      <header class="topbar">
        <button class="mobile-toggle" @click="mobileOpen = true">☰</button>
        <div class="crumb">
          <span>AI 求职助手</span>
          <b>/</b>
          <strong>{{ pageTitle }}</strong>
        </div>
        <div class="top-actions">
          <span class="date-chip">{{ today }}</span>
          <div class="user-chip">
            <span class="avatar">{{ (auth.nickname || 'U').slice(0, 1) }}</span>
            <div><strong>{{ auth.nickname || '用户' }}</strong><span>{{ auth.isAdmin ? '管理员' : '求职者' }}</span></div>
          </div>
        </div>
      </header>
      <div class="content"><router-view /></div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { dashboardApi } from '../api'
import { auth } from '../stores/auth'

const route = useRoute()
const router = useRouter()
const quota = ref(null)
const mobileOpen = ref(false)

const userMenus = [
  { path: '/dashboard', label: '工作台', icon: '首' },
  { path: '/abilities', label: '个人能力库', icon: '能' },
  { path: '/resumes', label: '简历工作台', icon: '历' },
  { path: '/diagnosis', label: 'AI 简历诊断', icon: '诊' },
  { path: '/deliveries', label: '投递看板', icon: '投' },
  { path: '/interviews', label: '模拟面试', icon: '面' },
  { path: '/profile', label: '个人中心', icon: '我' }
]
const adminMenus = [
  { path: '/admin', label: '数据总览', icon: '总' },
  { path: '/admin/users', label: '用户管理', icon: '用' },
  { path: '/admin/diagnoses', label: '诊断记录', icon: '诊' },
  { path: '/admin/ai-logs', label: 'AI 调用日志', icon: 'AI' },
  { path: '/admin/logs', label: '系统日志', icon: '志' },
  { path: '/admin/configs', label: '系统配置', icon: '配' }
]

const pageTitle = computed(() => [...userMenus, ...adminMenus].find((item) => route.path.startsWith(item.path))?.label || '工作台')
const quotaPercent = computed(() => {
  if (!quota.value || !quota.value.quotaLimit) return 0
  return Math.round((quota.value.quotaUsed / quota.value.quotaLimit) * 100)
})
const today = new Intl.DateTimeFormat('zh-CN', { month: 'long', day: 'numeric', weekday: 'short' }).format(new Date())

async function loadQuota() {
  try { quota.value = await dashboardApi.quota() } catch { /* 配额失败不阻塞导航 */ }
}
function logout() {
  auth.logout()
  router.push('/login')
}
onMounted(loadQuota)
</script>

<style scoped>
.app-shell { min-height: 100vh; display: flex; }
.sidebar {
  position: fixed; inset: 0 auto 0 0; width: 248px; z-index: 30; padding: 24px 16px 18px;
  background: #111a33; color: #fff; display: flex; flex-direction: column;
}
.logo-row { display: flex; align-items: center; gap: 12px; padding: 0 8px 25px; }
.logo-mark { width: 42px; height: 42px; border-radius: 13px; display: grid; place-items: center; font-weight: 900; font-size: 13px; background: linear-gradient(135deg, #5d7dff, #41c7ae); }
.logo-row div:last-child { display: flex; flex-direction: column; gap: 3px; }
.logo-row strong { font-size: 16px; letter-spacing: .5px; }
.logo-row span { font-size: 10px; color: #8490b4; letter-spacing: 1px; }
nav { flex: 1; overflow: auto; }
.nav-caption { color: #667293; font-size: 10px; letter-spacing: 1.6px; padding: 12px 11px 8px; }
.admin-caption { margin-top: 14px; border-top: 1px solid #25304c; padding-top: 20px; }
.nav-item { display: flex; align-items: center; gap: 11px; padding: 10px 11px; margin-bottom: 3px; border-radius: 10px; color: #aab4d1; font-size: 13px; transition: .18s; }
.nav-item:hover { color: #fff; background: #1b2745; }
.nav-item.router-link-active { color: #fff; background: linear-gradient(90deg, #3157d5, #355fd8); box-shadow: 0 8px 18px rgba(28, 61, 177, .3); }
.nav-icon { display: grid; place-items: center; width: 25px; height: 25px; border-radius: 7px; background: rgba(255,255,255,.08); font-size: 10px; font-weight: 800; }
.sidebar-foot { padding: 14px 4px 0; border-top: 1px solid #25304c; }
.quota-mini { background: #1a2543; padding: 12px; border-radius: 11px; margin-bottom: 11px; }
.quota-mini > div { display: flex; justify-content: space-between; gap: 8px; font-size: 10px; margin-bottom: 8px; color: #8f9bbb; }
.quota-mini strong { color: #dce4ff; }
.logout-btn { border: 0; background: transparent; color: #7f8aac; width: 100%; padding: 9px; cursor: pointer; font-size: 12px; }
.logout-btn:hover { color: #fff; }
.main { flex: 1; min-width: 0; margin-left: 248px; }
.topbar { height: 70px; display: flex; align-items: center; padding: 0 30px; gap: 18px; background: rgba(255,255,255,.87); border-bottom: 1px solid var(--line); backdrop-filter: blur(14px); position: sticky; top: 0; z-index: 20; }
.crumb { display: flex; align-items: center; gap: 9px; font-size: 13px; color: var(--muted); }
.crumb b { font-weight: 400; color: #c1c6d6; }
.crumb strong { color: var(--ink); }
.top-actions { margin-left: auto; display: flex; align-items: center; gap: 16px; }
.date-chip { font-size: 12px; color: var(--muted); }
.user-chip { display: flex; align-items: center; gap: 9px; padding-left: 16px; border-left: 1px solid var(--line); }
.user-chip > div { display: flex; flex-direction: column; gap: 2px; }
.user-chip strong { font-size: 12px; }
.user-chip span { font-size: 10px; color: var(--muted); }
.avatar { display: grid !important; place-items: center; width: 34px; height: 34px; border-radius: 10px; background: var(--brand-soft); color: var(--brand) !important; font-weight: 800; }
.content { padding: 28px 30px 46px; }
.mobile-toggle { display: none; border: 0; background: transparent; font-size: 21px; cursor: pointer; }
.mobile-mask { display: none; }
@media (max-width: 900px) {
  .sidebar { transform: translateX(-100%); transition: .25s; }
  .nav-open .sidebar { transform: translateX(0); }
  .main { margin-left: 0; }
  .mobile-toggle { display: inline-block; }
  .mobile-mask { display: block; position: fixed; inset: 0; z-index: 25; background: rgba(12, 19, 38, .45); }
  .content { padding: 22px 16px 40px; }
  .topbar { padding: 0 16px; }
  .date-chip { display: none; }
}
</style>
