import { createRouter, createWebHistory } from 'vue-router'
import { auth } from '../stores/auth'

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { public: true } },
  { path: '/register', component: () => import('../views/RegisterView.vue'), meta: { public: true } },
  {
    path: '/',
    component: () => import('../layouts/MainLayout.vue'),
    children: [
      { path: '', redirect: '/dashboard' },
      { path: 'dashboard', component: () => import('../views/DashboardView.vue') },
      { path: 'abilities', component: () => import('../views/AbilityView.vue') },
      { path: 'resumes', component: () => import('../views/ResumeView.vue') },
      { path: 'diagnosis', component: () => import('../views/DiagnosisView.vue') },
      { path: 'deliveries', component: () => import('../views/DeliveryView.vue') },
      { path: 'interviews', component: () => import('../views/InterviewListView.vue') },
      { path: 'interviews/:id', component: () => import('../views/InterviewDetailView.vue') },
      { path: 'profile', component: () => import('../views/ProfileView.vue') },
      { path: 'admin', component: () => import('../views/admin/AdminStatsView.vue'), meta: { admin: true } },
      { path: 'admin/users', component: () => import('../views/admin/AdminUsersView.vue'), meta: { admin: true } },
      { path: 'admin/diagnoses', component: () => import('../views/admin/AdminDiagnosesView.vue'), meta: { admin: true } },
      { path: 'admin/ai-logs', component: () => import('../views/admin/AdminAiLogsView.vue'), meta: { admin: true } },
      { path: 'admin/logs', component: () => import('../views/admin/AdminSystemLogsView.vue'), meta: { admin: true } },
      { path: 'admin/configs', component: () => import('../views/admin/AdminConfigsView.vue'), meta: { admin: true } }
    ]
  },
  { path: '/:pathMatch(.*)*', redirect: '/dashboard' }
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 })
})

/**
 * 路由守卫同时处理“未登录”和“普通用户访问管理端”两类场景。
 * 后端仍有独立鉴权；这里只负责前端体验，不能替代服务端权限校验。
 */
router.beforeEach((to) => {
  if (to.meta.public) {
    if (auth.token && (to.path === '/login' || to.path === '/register')) {
      return auth.isAdmin ? '/admin' : '/dashboard'
    }
    return true
  }
  if (!auth.token) {
    return `/login?redirect=${encodeURIComponent(to.fullPath)}`
  }
  if (to.meta.admin && !auth.isAdmin) {
    return '/dashboard'
  }
  return true
})

export default router
