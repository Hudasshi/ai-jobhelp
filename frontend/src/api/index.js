import http from './http'

/**
 * 按业务模块封装接口，页面只关心方法名和业务参数。
 * 路径去掉 /api 前缀，因为 Axios baseURL 已配置为 /api。
 */
export const authApi = {
  login: (data) => http.post('/auth/login', data),
  register: (data) => http.post('/auth/register', data),
  profile: () => http.get('/user/profile'),
  changePassword: (data) => http.put('/user/password', data),
  deleteData: (data) => http.delete('/user/data', { data })
}

export const dashboardApi = {
  summary: () => http.get('/dashboard/summary'),
  notices: (limit = 5) => http.get('/dashboard/notices', { params: { limit } }),
  quota: () => http.get('/quota/remaining')
}

export const abilityApi = {
  fieldSpec: () => http.get('/ability/field-spec'),
  list: (params) => http.get('/ability', { params }),
  get: (id) => http.get(`/ability/${id}`),
  create: (data) => http.post('/ability', data),
  update: (data) => http.put('/ability', data),
  remove: (id) => http.delete(`/ability/${id}`),
  enabled: (id, enabled) => http.put(`/ability/${id}/enabled`, { enabled }),
  confirm: (id) => http.put(`/ability/${id}/confirm`),
  reject: (id) => http.put(`/ability/${id}/reject`)
}

export const resumeApi = {
  list: (params) => http.get('/resume/list', { params }),
  get: (id) => http.get(`/resume/${id}`),
  upload: (formData) => http.post('/resume/parse', formData),
  generate: (data) => http.post('/resume/generate', data),
  save: (data) => http.post('/resume', data),
  remove: (id) => http.delete(`/resume/${id}`)
}

export const taskApi = {
  get: (id) => http.get(`/task/${id}`),
  markRead: (id) => http.put(`/task/${id}/read`)
}

export const diagnosisApi = {
  create: (data) => http.post('/diagnose', data),
  page: (params) => http.get('/diagnose/page', { params }),
  get: (id) => http.get(`/diagnose/${id}`),
  adopt: (id, entryId, adopted) =>
    http.put(`/diagnose/${id}/items/${entryId}/adopt`, { adopted })
}

export const deliveryApi = {
  list: () => http.get('/delivery/list'),
  todo: () => http.get('/delivery/todo'),
  get: (id) => http.get(`/delivery/${id}`),
  create: (data) => http.post('/delivery', data),
  update: (data) => http.put('/delivery', data),
  remove: (id) => http.delete(`/delivery/${id}`),
  parseJd: (jdRaw) => http.post('/delivery/parse-jd', { jdRaw }),
  status: (id, status) => http.put(`/delivery/${id}/status`, { status })
}

export const interviewApi = {
  start: (data) => http.post('/interview/start', data),
  list: (params) => http.get('/interview/list', { params }),
  get: (id) => http.get(`/interview/${id}`),
  answer: (id, data) => http.post(`/interview/${id}/answer`, data),
  review: (id) => http.post(`/interview/${id}/review`),
  archive: (id) => http.put(`/interview/${id}/archive`)
}

export const adminApi = {
  stats: (days = 7) => http.get('/admin/stats', { params: { days } }),
  users: (params) => http.get('/admin/users', { params }),
  user: (id) => http.get(`/admin/users/${id}`),
  quota: (id, override) => http.put(`/admin/users/${id}/quota`, { override }),
  status: (id, status) => http.put(`/admin/users/${id}/status`, { status }),
  diagnoses: (params) => http.get('/admin/diagnoses', { params }),
  aiLogs: (params) => http.get('/admin/ai-logs', { params }),
  logs: (params) => http.get('/admin/logs', { params }),
  configs: () => http.get('/admin/configs'),
  updateConfigs: (items) => http.put('/admin/configs', { items })
}
