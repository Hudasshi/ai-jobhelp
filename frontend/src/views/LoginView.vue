<template>
  <div class="auth-shell">
    <section class="auth-hero">
      <div class="brand"><span class="brand-mark">AI</span> 求职助手</div>
      <div class="hero-copy">
        <div class="eyebrow">CAREER COPILOT</div>
        <h1>把求职过程，<br />变成一条清晰的成长路径。</h1>
        <p>沉淀真实经历，生成定制简历，定位能力差距，再用模拟面试把表达练到稳定。</p>
        <div class="hero-tags">
          <span>能力库</span><span>AI 诊断</span><span>投递看板</span><span>模拟面试</span>
        </div>
      </div>
      <div class="hero-metrics">
        <div><strong>4 维</strong><span>简历诊断</span></div>
        <div><strong>6 态</strong><span>投递追踪</span></div>
        <div><strong>1 套</strong><span>求职闭环</span></div>
      </div>
    </section>

    <section class="auth-form-side">
      <div class="login-card">
        <div class="form-head">
          <span class="mini-mark">AI</span>
          <h2>欢迎回来</h2>
          <p>登录后继续推进你的求职计划</p>
        </div>
        <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @keyup.enter="submit">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="请输入用户名" />
          </el-form-item>
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password size="large" placeholder="请输入密码" />
          </el-form-item>
          <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="submit">进入工作台</el-button>
        </el-form>
        <div class="demo-box">
          <span>演示账号</span>
          <button @click="fill('demo', '123456')">demo / 123456</button>
          <button @click="fill('admin', 'admin123')">admin / admin123</button>
          <button @click="fill('chenwei', '123456')">chenwei / 123456</button>
        </div>
        <div class="form-foot">还没有账号？<router-link to="/register">立即注册</router-link></div>
      </div>
    </section>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { auth } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: 'demo', password: '123456' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

function fill(username, password) {
  form.username = username
  form.password = password
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await authApi.login(form)
    auth.setSession(data)
    ElMessage.success(`欢迎回来，${data.nickname || form.username}`)
    const redirect = route.query.redirect
    router.push(redirect ? decodeURIComponent(String(redirect)) : (data.role === 'admin' ? '/admin' : '/dashboard'))
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.auth-shell { min-height: 100vh; display: grid; grid-template-columns: 1.15fr 0.85fr; background: #fff; }
.auth-hero {
  position: relative; overflow: hidden; padding: 48px 64px; color: #fff;
  background:
    radial-gradient(circle at 15% 15%, rgba(106, 139, 255, .55), transparent 34%),
    radial-gradient(circle at 90% 85%, rgba(80, 215, 188, .28), transparent 28%),
    linear-gradient(145deg, #16265d 0%, #2547c7 62%, #3157d5 100%);
  display: flex; flex-direction: column; justify-content: space-between;
}
.auth-hero::after {
  content: ""; position: absolute; width: 520px; height: 520px; right: -210px; top: 18%;
  border: 1px solid rgba(255,255,255,.18); border-radius: 50%; box-shadow: 0 0 0 80px rgba(255,255,255,.035), 0 0 0 160px rgba(255,255,255,.02);
}
.brand { display: flex; align-items: center; gap: 12px; font-size: 19px; font-weight: 800; letter-spacing: .5px; }
.brand-mark, .mini-mark {
  display: inline-grid; place-items: center; width: 38px; height: 38px; border-radius: 12px;
  color: #1f3ea9; background: #fff; font-size: 13px; font-weight: 900; letter-spacing: -.5px;
}
.hero-copy { position: relative; z-index: 1; max-width: 650px; }
.eyebrow { font-size: 12px; letter-spacing: 3px; opacity: .7; margin-bottom: 24px; }
.hero-copy h1 { margin: 0; font-size: clamp(38px, 4.8vw, 66px); line-height: 1.16; letter-spacing: -2px; }
.hero-copy p { max-width: 550px; margin: 26px 0 30px; font-size: 16px; line-height: 1.9; color: rgba(255,255,255,.76); }
.hero-tags { display: flex; flex-wrap: wrap; gap: 10px; }
.hero-tags span { border: 1px solid rgba(255,255,255,.22); background: rgba(255,255,255,.09); padding: 8px 14px; border-radius: 999px; font-size: 13px; }
.hero-metrics { display: flex; gap: 46px; position: relative; z-index: 1; }
.hero-metrics div { display: flex; flex-direction: column; gap: 5px; }
.hero-metrics strong { font-size: 28px; }
.hero-metrics span { color: rgba(255,255,255,.58); font-size: 12px; }
.auth-form-side { display: grid; place-items: center; padding: 40px; }
.login-card { width: min(420px, 100%); }
.form-head { margin-bottom: 32px; }
.form-head h2 { font-size: 32px; margin: 20px 0 8px; letter-spacing: -1px; }
.form-head p { margin: 0; color: var(--muted); }
.mini-mark { background: var(--brand); color: #fff; width: 42px; height: 42px; }
.submit-btn { width: 100%; height: 48px; margin-top: 7px; border-radius: 12px; font-weight: 700; }
.demo-box { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 22px; padding: 13px; background: #f7f8fc; border-radius: 12px; }
.demo-box span { width: 100%; color: var(--muted); font-size: 12px; }
.demo-box button { border: 0; background: #fff; color: var(--brand); font-size: 12px; padding: 6px 9px; border-radius: 7px; cursor: pointer; }
.form-foot { text-align: center; color: var(--muted); font-size: 13px; margin-top: 24px; }
.form-foot a { color: var(--brand); font-weight: 700; }
@media (max-width: 900px) {
  .auth-shell { grid-template-columns: 1fr; }
  .auth-hero { min-height: 360px; padding: 34px; }
  .hero-metrics { display: none; }
  .auth-form-side { padding: 34px 22px; }
}
</style>
