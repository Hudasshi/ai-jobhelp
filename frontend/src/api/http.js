import axios from 'axios'
import { ElMessage } from 'element-plus'
import { auth } from '../stores/auth'

/**
 * 统一 Axios 实例。
 *
 * 后端业务失败使用 HTTP 200 + code!=0，因此响应拦截器必须检查 code，
 * 不能只依赖 HTTP 状态码。401/403/500 仍由 HTTP 状态表达。
 */
const http = axios.create({
  baseURL: '/api',
  timeout: 90000
})

http.interceptors.request.use((config) => {
  if (auth.token) {
    config.headers.Authorization = `Bearer ${auth.token}`
  }
  return config
})

http.interceptors.response.use(
  (response) => {
    const payload = response.data
    if (payload && payload.code === 0) {
      return payload.data
    }
    const error = new Error(payload?.msg || '请求失败')
    error.code = payload?.code
    error.reason = payload?.reason
    error.data = payload?.data
    // 业务错误统一提示，但调用方仍可 catch 后做局部交互。
    ElMessage.error(error.message)
    return Promise.reject(error)
  },
  (error) => {
    if (error.response?.status === 401) {
      auth.logout()
      ElMessage.warning('登录已失效，请重新登录')
      if (!location.pathname.startsWith('/login')) {
        location.href = '/login'
      }
    } else if (error.response?.status === 403) {
      ElMessage.error(error.response.data?.msg || '无权执行该操作')
    } else {
      ElMessage.error(error.response?.data?.msg || '网络异常，请稍后重试')
    }
    return Promise.reject(error)
  }
)

export default http
