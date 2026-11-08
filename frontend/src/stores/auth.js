import { reactive } from 'vue'

/**
 * 轻量认证状态。
 *
 * 项目只有 token、昵称和角色三个全局会话字段，不需要引入 Pinia；
 * localStorage 用于刷新页面后恢复登录，退出时同步清理。
 */
export const auth = reactive({
  token: localStorage.getItem('jobhelper_token') || '',
  nickname: localStorage.getItem('jobhelper_nickname') || '',
  role: localStorage.getItem('jobhelper_role') || '',

  setSession(data) {
    this.token = data.token
    this.nickname = data.nickname || ''
    this.role = data.role || 'user'
    localStorage.setItem('jobhelper_token', this.token)
    localStorage.setItem('jobhelper_nickname', this.nickname)
    localStorage.setItem('jobhelper_role', this.role)
  },

  logout() {
    this.token = ''
    this.nickname = ''
    this.role = ''
    localStorage.removeItem('jobhelper_token')
    localStorage.removeItem('jobhelper_nickname')
    localStorage.removeItem('jobhelper_role')
  },

  get isAdmin() {
    return this.role === 'admin'
  }
})
