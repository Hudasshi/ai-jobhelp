<template>
  <div class="page">
    <div class="page-head">
      <div><h1>个人中心</h1><p>管理账号信息与今日 AI 配额；注销操作会清理个人业务数据，请谨慎使用。</p></div>
    </div>

    <div class="profile-layout">
      <aside class="profile-card panel">
        <div class="avatar-big">{{ (profile.nickname || profile.username || 'U').slice(0, 1) }}</div>
        <h2>{{ profile.nickname || '求职者' }}</h2>
        <p>@{{ profile.username }}</p>
        <span class="role-chip">{{ profile.role === 'admin' ? '管理员' : '普通用户' }}</span>
        <div class="identity-list">
          <div><span>邮箱</span><strong>{{ profile.email || '未填写' }}</strong></div>
          <div><span>注册时间</span><strong>{{ profile.createdAt || '—' }}</strong></div>
          <div><span>账号 ID</span><strong>#{{ profile.id }}</strong></div>
        </div>
      </aside>

      <div class="content-stack">
        <section class="panel panel-pad quota-card">
          <div class="card-title"><div><h3>今日 AI 配额</h3><p>每日 00:00 自动重置</p></div><span>{{ quota.remaining }} 次可用</span></div>
          <div class="quota-numbers"><strong>{{ quota.quotaUsed || 0 }}</strong><span>/ {{ quota.quotaLimit || 0 }} 次已使用</span></div>
          <el-progress :percentage="quotaPercent" :show-text="false" :stroke-width="9" :color="quotaPercent > 85 ? '#d9534f' : '#3157d5'" />
        </section>

        <section class="panel panel-pad">
          <div class="card-title"><div><h3>修改密码</h3><p>修改后当前登录仍有效，下次登录使用新密码</p></div></div>
          <el-form ref="passwordRef" :model="passwordForm" :rules="passwordRules" label-position="top" class="password-form">
            <el-form-item label="原密码" prop="oldPassword"><el-input v-model="passwordForm.oldPassword" type="password" show-password /></el-form-item>
            <el-form-item label="新密码" prop="newPassword"><el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6~32位" /></el-form-item>
            <el-form-item label="确认新密码" prop="confirm"><el-input v-model="passwordForm.confirm" type="password" show-password /></el-form-item>
            <el-button type="primary" :loading="changing" @click="changePassword">保存新密码</el-button>
          </el-form>
        </section>

        <section class="panel panel-pad danger-zone">
          <div class="card-title"><div><h3>注销并清理个人数据</h3><p>会注销账号并删除能力、简历、投递、面试等个人数据；诊断元数据按审计要求脱敏保留。</p></div></div>
          <el-button type="danger" plain @click="deleteDialog = true">申请注销账号</el-button>
        </section>
      </div>
    </div>

    <el-dialog v-model="deleteDialog" title="确认注销账号" width="min(480px, 94vw)">
      <div class="danger-message">
        <strong>此操作不可撤销</strong>
        <p>请输入当前用户名 <b>{{ profile.username }}</b> 进行确认。注销后同名账号不能再次注册。</p>
      </div>
      <el-input v-model="confirmUsername" :placeholder="profile.username" />
      <template #footer>
        <el-button @click="deleteDialog = false">取消</el-button>
        <el-button type="danger" :loading="deleting" :disabled="confirmUsername !== profile.username" @click="deleteAccount">确认注销</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { authApi, dashboardApi } from '../api'
import { auth } from '../stores/auth'

const router = useRouter()
const profile = ref({})
const quota = ref({})
const passwordRef = ref()
const changing = ref(false)
const deleting = ref(false)
const deleteDialog = ref(false)
const confirmUsername = ref('')
const passwordForm = reactive({ oldPassword: '', newPassword: '', confirm: '' })
const passwordRules = {
  oldPassword: [{ required: true, message: '请输入原密码', trigger: 'blur' }],
  newPassword: [{ required: true, min: 6, max: 32, message: '新密码长度必须为6~32位', trigger: 'blur' }],
  confirm: [{ validator: (_, value, callback) => value === passwordForm.newPassword ? callback() : callback(new Error('两次密码不一致')), trigger: 'blur' }]
}
const quotaPercent = computed(() => {
  if (!quota.value.quotaLimit) return 0
  return Math.min(100, Math.round(quota.value.quotaUsed / quota.value.quotaLimit * 100))
})
async function changePassword() {
  await passwordRef.value.validate()
  changing.value = true
  try {
    await authApi.changePassword({ oldPassword: passwordForm.oldPassword, newPassword: passwordForm.newPassword })
    ElMessage.success('密码已修改')
    Object.assign(passwordForm, { oldPassword: '', newPassword: '', confirm: '' })
    passwordRef.value.resetFields()
  } finally { changing.value = false }
}
async function deleteAccount() {
  deleting.value = true
  try {
    await authApi.deleteData({ username: confirmUsername.value })
    auth.logout()
    ElMessage.success('账号已注销')
    router.push('/login')
  } finally { deleting.value = false }
}
onMounted(async () => {
  ;[profile.value, quota.value] = await Promise.all([authApi.profile(), dashboardApi.quota()])
})
</script>

<style scoped>
.profile-layout { display: grid; grid-template-columns: 300px minmax(0, 1fr); gap: 20px; align-items: start; }
.profile-card { padding: 30px; text-align: center; }
.avatar-big { width: 82px; height: 82px; border-radius: 25px; margin: 0 auto 17px; display: grid; place-items: center; color: #fff; font-size: 33px; font-weight: 800; background: linear-gradient(135deg, #3157d5, #50cbb3); box-shadow: 0 14px 28px rgba(49, 87, 213, .24); }
.profile-card h2 { margin: 0 0 5px; font-size: 21px; }
.profile-card > p { margin: 0 0 12px; color: var(--muted); font-size: 12px; }
.role-chip { display: inline-block; background: var(--brand-soft); color: var(--brand); border-radius: 999px; padding: 4px 10px; font-size: 10px; font-weight: 700; }
.identity-list { margin-top: 25px; border-top: 1px solid var(--line); padding-top: 7px; text-align: left; }
.identity-list div { display: flex; justify-content: space-between; gap: 12px; padding: 11px 0; border-bottom: 1px solid #f2f4f9; font-size: 10px; }
.identity-list div:last-child { border-bottom: 0; }
.identity-list span { color: var(--muted); }
.identity-list strong { text-align: right; font-weight: 600; }
.content-stack { display: flex; flex-direction: column; gap: 18px; }
.card-title { display: flex; justify-content: space-between; gap: 15px; align-items: flex-start; margin-bottom: 18px; }
.card-title h3 { margin: 0 0 5px; font-size: 16px; }
.card-title p { margin: 0; color: var(--muted); font-size: 10px; line-height: 1.6; }
.card-title > span { color: var(--brand); background: var(--brand-soft); border-radius: 7px; padding: 5px 8px; font-size: 10px; }
.quota-numbers { display: flex; align-items: baseline; gap: 8px; margin-bottom: 10px; }
.quota-numbers strong { font-size: 38px; letter-spacing: -2px; }
.quota-numbers span { color: var(--muted); font-size: 11px; }
.password-form { max-width: 440px; }
.danger-zone { border-color: #f1d8d7; background: #fffdfd; }
.danger-zone .card-title h3 { color: #b94a46; }
.danger-message { background: #fff4f3; border-radius: 10px; padding: 13px 15px; margin-bottom: 14px; }
.danger-message strong { color: #b94a46; font-size: 12px; }
.danger-message p { color: #705c5b; font-size: 11px; line-height: 1.7; margin: 7px 0 0; }
@media (max-width: 850px) { .profile-layout { grid-template-columns: 1fr; } .profile-card { text-align: left; } .avatar-big { margin-left: 0; } }
</style>
