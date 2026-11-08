<template>
  <div class="register-shell">
    <div class="register-card panel">
      <div class="top-row">
        <div class="brand"><span>AI</span> 求职助手</div>
        <router-link to="/login">已有账号，去登录</router-link>
      </div>
      <div class="intro">
        <div class="pill">从今天开始沉淀</div>
        <h1>建立你的求职资产库</h1>
        <p>注册后先录入真实经历与技能，之后的简历生成、诊断和面试都会以此为基础。</p>
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top">
        <div class="grid grid-2">
          <el-form-item label="用户名" prop="username">
            <el-input v-model="form.username" size="large" placeholder="4~20位字母、数字或下划线" />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="form.nickname" size="large" placeholder="展示在工作台" />
          </el-form-item>
        </div>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model="form.email" size="large" placeholder="name@example.com" />
        </el-form-item>
        <div class="grid grid-2">
          <el-form-item label="密码" prop="password">
            <el-input v-model="form.password" type="password" show-password size="large" placeholder="6~32位" />
          </el-form-item>
          <el-form-item label="确认密码" prop="confirm">
            <el-input v-model="form.confirm" type="password" show-password size="large" placeholder="再次输入密码" />
          </el-form-item>
        </div>
        <el-button type="primary" size="large" class="submit-btn" :loading="loading" @click="submit">创建账号并进入</el-button>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi } from '../api'
import { auth } from '../stores/auth'

const router = useRouter()
const formRef = ref()
const loading = ref(false)
const form = reactive({ username: '', nickname: '', email: '', password: '', confirm: '' })
const rules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
    { pattern: /^[A-Za-z0-9_]{4,20}$/, message: '用户名为4~20位字母、数字或下划线', trigger: 'blur' }
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
    { min: 6, max: 32, message: '密码长度必须为6~32位', trigger: 'blur' }
  ],
  confirm: [{
    validator: (_, value, callback) => value === form.password ? callback() : callback(new Error('两次密码不一致')),
    trigger: 'blur'
  }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }]
}

async function submit() {
  await formRef.value.validate()
  loading.value = true
  try {
    const data = await authApi.register({
      username: form.username,
      password: form.password,
      nickname: form.nickname || null,
      email: form.email || null
    })
    auth.setSession(data)
    ElMessage.success('账号创建成功')
    router.push('/dashboard')
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.register-shell {
  min-height: 100vh; padding: 36px 20px; display: grid; place-items: center;
  background: radial-gradient(circle at 8% 8%, #dfe6ff, transparent 30%), radial-gradient(circle at 95% 92%, #d9f4ee, transparent 28%), #f5f7fc;
}
.register-card { width: min(760px, 100%); padding: 34px; }
.top-row { display: flex; align-items: center; justify-content: space-between; gap: 20px; }
.top-row .brand { display: flex; align-items: center; gap: 10px; font-weight: 800; }
.top-row .brand span { display: grid; place-items: center; width: 36px; height: 36px; border-radius: 11px; background: var(--brand); color: #fff; font-size: 12px; }
.top-row a { font-size: 13px; color: var(--brand); font-weight: 700; }
.intro { margin: 38px 0 30px; }
.intro h1 { margin: 15px 0 9px; font-size: 32px; letter-spacing: -1px; }
.intro p { margin: 0; color: var(--muted); line-height: 1.8; }
.submit-btn { width: 100%; height: 48px; border-radius: 12px; font-weight: 700; margin-top: 8px; }
@media (max-width: 650px) {
  .register-card { padding: 24px; }
  .grid-2 { grid-template-columns: 1fr; }
}
</style>
