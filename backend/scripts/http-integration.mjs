/**
 * 真实 HTTP 端到端联调脚本。
 *
 * 使用方式：
 *   1. 启动 MySQL、Redis 和后端（http://127.0.0.1:8080）；
 *   2. 在任意目录执行 `node backend/scripts/http-integration.mjs`；
 *   3. 脚本会创建唯一临时账号，走完注册、能力库、简历、诊断、投递、
 *      面试、管理端和注销流程，最后删除临时账号。
 *
 * 脚本只使用 Node.js 内置 fetch/FormData/Blob，不引入额外依赖。
 * 所有断言都以冻结的 HTTP 契约和业务状态机为准；发现偏差立即失败并打印
 * 可复现的步骤，不修改演示账号的数据。
 */

import { randomUUID } from 'node:crypto'
import { setTimeout as delay } from 'node:timers/promises'

const BASE_URL = process.env.API_BASE || 'http://127.0.0.1:8080'
const RUN_ID = Date.now().toString(36)
const TEMP_USERNAME = `it_${RUN_ID}`
const TEMP_PASSWORD = 'Test123456'
const NEW_PASSWORD = 'Test654321'

let checks = 0
let currentStage = '初始化'
let adminToken = ''
let adminId = null

function stage(name) {
  currentStage = name
  console.log(`\n[${name}]`)
}

function assert(condition, message) {
  if (!condition) {
    throw new Error(message)
  }
  checks++
}

function assertArray(value, message) {
  assert(Array.isArray(value), `${message}，实际类型=${typeof value}`)
}

function assertNoKeys(object, keys, message) {
  const leaked = keys.filter((key) => Object.prototype.hasOwnProperty.call(object, key))
  assert(leaked.length === 0, `${message}，泄露字段=${leaked.join(',')}`)
}

function localDateTime(hoursFromNow) {
  const date = new Date(Date.now() + hoursFromNow * 60 * 60 * 1000)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} `
    + `${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
}

function localDate(daysFromNow) {
  const date = new Date(Date.now() + daysFromNow * 24 * 60 * 60 * 1000)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

async function api(method, path, options = {}) {
  const {
    token,
    body,
    form,
    http = 200,
    code = 0,
    label = `${method} ${path}`
  } = options
  const headers = {}
  if (token) {
    headers.Authorization = `Bearer ${token}`
  }

  let payload
  if (form) {
    payload = form
  } else if (body !== undefined) {
    headers['Content-Type'] = 'application/json; charset=utf-8'
    payload = JSON.stringify(body)
  }

  const response = await fetch(`${BASE_URL}${path}`, {
    method,
    headers,
    body: payload
  })
  const text = await response.text()
  let json = null
  try {
    json = text ? JSON.parse(text) : null
  } catch {
    throw new Error(`${label}: 响应不是 JSON，HTTP=${response.status}，body=${text}`)
  }

  if (response.status !== http) {
    throw new Error(
      `${label}: HTTP ${response.status}，期望 ${http}，code=${json?.code}，msg=${json?.msg}`
    )
  }
  if (json?.code !== code) {
    throw new Error(
      `${label}: 业务码 ${json?.code}，期望 ${code}，msg=${json?.msg}，data=${JSON.stringify(json?.data)}`
    )
  }
  checks++
  return json.data
}

async function waitTask(token, taskId, label) {
  assert(Number.isInteger(taskId) && taskId > 0, `${label}: taskId 必须为正整数`)
  for (let attempt = 0; attempt < 80; attempt++) {
    const task = await api('GET', `/api/task/${taskId}`, { token })
    if (task.status === 'SUCCESS') {
      return task
    }
    if (task.status === 'FAIL') {
      throw new Error(`${label}: 异步任务失败，原因=${task.failReason || '未知'}`)
    }
    await delay(200)
  }
  throw new Error(`${label}: 异步任务超时`)
}

function formWithTextFile(name, content) {
  const form = new FormData()
  form.append('file', new Blob([content], { type: 'text/plain;charset=utf-8' }), name)
  return form
}

async function login(username, password, options = {}) {
  return api('POST', '/api/auth/login', {
    body: { username, password },
    ...options
  })
}

async function run() {
  console.log(`HTTP 联调目标: ${BASE_URL}`)
  console.log(`临时账号: ${TEMP_USERNAME}`)
  console.log(`后端健康检查: ${await api('GET', '/ping') ? '通过' : '通过'}`)

  stage('管理端基线')
  const adminLogin = await login('admin', 'admin123')
  adminToken = adminLogin.token
  assert(adminLogin.role === 'admin', 'admin 登录角色应为 admin')
  const adminProfile = await api('GET', '/api/user/profile', { token: adminToken })
  adminId = adminProfile.id
  assert(adminId > 0, '管理员 ID 必须为正整数')

  let configs = await api('GET', '/api/admin/configs', { token: adminToken })
  assertArray(configs.items, '系统配置 items 必须为数组')
  assert(configs.items.length === 13, `系统配置必须固定 13 项，实际 ${configs.items.length}`)
  const defaultQuota = configs.items.find((item) => item.configKey === 'quota.daily.default')
  assert(defaultQuota, '必须存在 quota.daily.default')
  if (defaultQuota.configValue !== '50') {
    // 用户最终拍板为 50；开发库若仍是旧种子值，联调先校正到冻结基线。
    configs = await api('PUT', '/api/admin/configs', {
      token: adminToken,
      body: { items: [{ configKey: 'quota.daily.default', configValue: '50' }] }
    })
  }
  assert(
    configs.items.find((item) => item.configKey === 'quota.daily.default')?.configValue === '50',
    'quota.daily.default 必须为 50'
  )
  const duplicateConfig = await api('PUT', '/api/admin/configs', {
    token: adminToken,
    body: {
      items: [
        { configKey: 'quota.daily.default', configValue: '50' },
        { configKey: 'quota.daily.default', configValue: '50' }
      ]
    },
    code: 400
  })
  assert(duplicateConfig === null, '重复配置键应返回 code=400 且 data=null')

  stage('注册、登录与配额')
  const registered = await api('POST', '/api/auth/register', {
    body: {
      username: TEMP_USERNAME,
      password: TEMP_PASSWORD,
      nickname: '联调临时用户',
      email: `${TEMP_USERNAME}@example.com`
    }
  })
  let token = registered.token
  assert(token, '注册必须返回 token')
  assert(registered.role === 'user', '注册角色必须为 user')
  const profile = await api('GET', '/api/user/profile', { token })
  const userId = profile.id
  assert(userId > 0, 'profile.id 必须为正整数')
  assert(profile.username === TEMP_USERNAME, 'profile.username 应与注册值一致')
  assertNoKeys(profile, ['password'], '个人资料不得返回密码')
  const loggedIn = await login(TEMP_USERNAME, TEMP_PASSWORD)
  token = loggedIn.token
  const quota = await api('GET', '/api/quota/remaining', { token })
  assert(quota.quotaLimit === 50, `新用户默认配额应为 50，实际 ${quota.quotaLimit}`)
  assert(quota.quotaUsed === 0, `新用户已用配额应为 0，实际 ${quota.quotaUsed}`)
  assert(quota.remaining === 50, `新用户剩余配额应为 50，实际 ${quota.remaining}`)

  stage('能力库 CRUD、确认与启停')
  const fieldSpec = await api('GET', '/api/ability/field-spec', { token })
  assertArray(fieldSpec.entryTypes, 'field-spec.entryTypes 必须为数组')
  assert(fieldSpec.entryTypes.length === 6, `能力字段规格必须为六类，实际 ${fieldSpec.entryTypes.length}`)

  const project = await api('POST', '/api/ability', {
    token,
    body: {
      entryType: 'project',
      title: '招聘数据平台 · 后端负责人',
      detail: {
        name: '招聘数据平台',
        role: '后端负责人',
        tech: 'Spring Boot、MySQL、Redis',
        period: '2026.01-2026.06',
        description: '负责投递与诊断接口设计，支撑 3000 QPS，接口耗时下降 40%。',
        result: '核心接口 P95 从 480ms 降至 180ms。'
      },
      sortOrder: 1
    }
  })
  assert(project.id > 0, '新增能力条目应返回正整数 ID')
  assert(project.status === 'confirmed', '手工新增能力条目应直接为 confirmed')
  assert(project.enabled === 1, '手工新增能力条目应默认启用')

  const skill = await api('POST', '/api/ability', {
    token,
    body: {
      entryType: 'skill',
      title: 'Java 后端开发',
      detail: {
        level: '熟练',
        years: 3
      },
      sortOrder: 2
    }
  })
  const abilityDetail = await api('GET', `/api/ability/${project.id}`, { token })
  assert(abilityDetail.title === project.title, '能力详情标题应与新增值一致')
  const updatedProject = await api('PUT', '/api/ability', {
    token,
    body: {
      id: project.id,
      entryType: 'project',
      title: '招聘数据平台 · 后端核心开发',
      detail: {
        ...project.detail,
        description: '负责投递与诊断接口设计，支撑 3200 QPS，接口耗时下降 45%。'
      },
      sortOrder: 1
    }
  })
  assert(updatedProject.title.includes('核心开发'), '能力条目编辑应生效')
  const abilityPage = await api(
    'GET',
    '/api/ability?status=confirmed&enabled=1&page=1&size=100',
    { token }
  )
  assert(abilityPage.total >= 2, '确认且启用的能力条目至少应有 2 条')

  await api('PUT', `/api/ability/${project.id}/enabled`, {
    token,
    body: { enabled: 0 }
  })
  const disabledProject = await api('GET', `/api/ability/${project.id}`, { token })
  assert(disabledProject.enabled === 0, '停用能力条目后 enabled 应为 0')
  await api('PUT', `/api/ability/${project.id}/enabled`, {
    token,
    body: { enabled: 1 }
  })
  // 已确认条目的重放确认按契约是成功 no-op。
  await api('PUT', `/api/ability/${project.id}/confirm`, { token })

  const disposableAbility = await api('POST', '/api/ability', {
    token,
    body: {
      entryType: 'other',
      title: '联调待删除条目',
      detail: { description: '删除幂等测试。' }
    }
  })
  await api('DELETE', `/api/ability/${disposableAbility.id}`, { token })
  await api('DELETE', `/api/ability/${disposableAbility.id}`, { token })

  stage('简历生成、版本保存与上传解析')
  const generateTask = await api('POST', '/api/resume/generate', {
    token,
    body: {
      entryIds: [project.id, skill.id],
      jdText: 'Java 后端开发，要求熟悉 Spring Boot、MySQL、Redis 和分布式系统。'
    }
  })
  const generatedTask = await waitTask(token, generateTask.taskId, '生成简历草稿')
  assert(generatedTask.taskType === 'resume_generate', '任务类型应为 resume_generate')
  assertArray(generatedTask.resultData?.items, '生成草稿必须返回 items')
  assert(generatedTask.resultData.items.length >= 2, '生成草稿至少应包含两条能力')

  const generatedResume = await api('POST', '/api/resume', {
    token,
    body: {
      name: '联调生成简历',
      targetPosition: 'Java 后端开发工程师',
      sourceType: 'generate',
      draftTaskId: generateTask.taskId
    }
  })
  const generatedResumeId = generatedResume.id
  assert(generatedResume.status === 'ready', '保存后的生成简历状态应为 ready')
  assertArray(generatedResume.items, '保存后的简历必须返回 items')
  assert(generatedResume.items.length >= 2, '保存后的简历至少应包含两条能力')
  assertNoKeys(generatedResume, ['rawText', 'sourceSnapshot'], '简历响应不得返回原文内部字段')

  const resumeList = await api('GET', '/api/resume/list?page=1&size=100', { token })
  assert(resumeList.total >= 1, '简历列表至少应包含一条记录')
  const generatedResumeDetail = await api('GET', `/api/resume/${generatedResumeId}`, { token })
  assert(generatedResumeDetail.id === generatedResumeId, '简历详情 ID 应一致')

  const uploadText = [
    '教育经历',
    '贵州大学 计算机科学与技术 本科 2022-2026 GPA 3.8',
    '',
    '项目经历',
    'AI 求职助手 后端负责人 使用 Spring Boot、MySQL、Redis 支撑 3000 QPS',
    '',
    '技能',
    '熟悉 Java、Spring Boot、MySQL、Redis、Docker'
  ].join('\n')
  const parseTask = await api('POST', '/api/resume/parse', {
    token,
    form: formWithTextFile('integration-resume.txt', uploadText)
  })
  const parsedTask = await waitTask(token, parseTask.taskId, '上传并解析简历')
  assert(parsedTask.taskType === 'resume_parse', '任务类型应为 resume_parse')
  assert(parsedTask.resultData?.inserted >= 1, `上传解析应至少写入 1 条能力，实际 ${parsedTask.resultData?.inserted}`)
  const uploadResumeId = parsedTask.resultId
  assert(uploadResumeId > 0, '上传解析任务应返回 carrier resumeId')
  const uploadResume = await api('GET', `/api/resume/${uploadResumeId}`, { token })
  assert(uploadResume.status === 'ready', '上传解析成功后载体状态应为 ready')
  assert(uploadResume.sourceType === 'upload', '上传解析载体 sourceType 应为 upload')

  const pendingPage = await api(
    'GET',
    '/api/ability?status=pending&page=1&size=100',
    { token }
  )
  assert(pendingPage.total >= 1, '上传解析后应存在待确认能力')
  for (const entry of pendingPage.records) {
    await api('PUT', `/api/ability/${entry.id}/confirm`, { token })
  }
  const confirmedPage = await api(
    'GET',
    '/api/ability?status=confirmed&enabled=1&page=1&size=100',
    { token }
  )
  const allConfirmedIds = confirmedPage.records.map((entry) => entry.id)
  const uploadedResume = await api('POST', '/api/resume', {
    token,
    body: {
      name: '联调上传合成简历',
      targetPosition: 'Java 后端开发工程师',
      sourceType: 'upload',
      entryIds: allConfirmedIds,
      sourceResumeId: uploadResumeId
    }
  })
  assert(uploadedResume.items.length === allConfirmedIds.length,
    '上传合成版本应包含全部 confirmed+enabled 能力')

  const disposableResume = await api('POST', '/api/resume', {
    token,
    body: {
      name: '联调待删除简历',
      targetPosition: 'Java 后端开发工程师',
      sourceType: 'generate',
      draftTaskId: generateTask.taskId
    }
  })
  await api('DELETE', `/api/resume/${disposableResume.id}`, { token })
  await api('DELETE', `/api/resume/${disposableResume.id}`, { token })

  stage('简历诊断与采纳合成')
  const jd = [
    '岗位：Java 后端开发工程师',
    '公司：贵州星河科技有限公司',
    '岗位职责：负责招聘业务后端服务、简历诊断和异步任务平台建设。',
    '任职要求：熟悉 Java、Spring Boot、MySQL、Redis、Docker，具备高并发系统设计经验。',
    '工作地点：贵阳。'
  ].join('\n')
  const diagnoseTask = await api('POST', '/api/diagnose', {
    token,
    body: { resumeId: generatedResumeId, jd }
  })
  const diagnosedTask = await waitTask(token, diagnoseTask.taskId, '生成诊断报告')
  assert(diagnosedTask.taskType === 'diagnose', '任务类型应为 diagnose')
  const diagnosisId = diagnosedTask.resultId
  assert(diagnosisId > 0, '诊断任务应返回 reportId')
  const diagnosis = await api('GET', `/api/diagnose/${diagnosisId}`, { token })
  assert(diagnosis.status === 'success' || diagnosis.status === 'degraded',
    `诊断状态应成功或降级，实际 ${diagnosis.status}`)
  assertNoKeys(diagnosis.report, ['sourceSnapshot'], '诊断报告不得泄露内部 sourceSnapshot')
  assertArray(diagnosis.report.dims, '诊断报告 dims 必须为数组')
  assert(diagnosis.report.dims.length === 4, `诊断必须固定四维，实际 ${diagnosis.report.dims.length}`)
  assertArray(diagnosis.report.items, '诊断报告 items 必须为数组')
  assert(diagnosis.report.items.length >= 1, '诊断至少应生成一条建议')
  const adoptable = diagnosis.report.items.find((item) => item.verdict === 'rewritten')
  assert(adoptable, '诊断报告应包含可采纳的改写建议')
  const adoption = await api(
    'PUT',
    `/api/diagnose/${diagnosisId}/items/${adoptable.entryId}/adopt`,
    { token, body: { adopted: true } }
  )
  assert(adoption.adopted === true, '采纳状态应为 true')
  assert(adoption.adoptedCount >= 1, '已采纳数量至少应为 1')

  const diagnosedResume = await api('POST', '/api/resume', {
    token,
    body: {
      name: '联调诊断优化简历',
      targetPosition: 'Java 后端开发工程师',
      sourceType: 'diagnose',
      diagnoseId: diagnosisId
    }
  })
  assert(diagnosedResume.sourceType === 'diagnose', '诊断合成简历来源应为 diagnose')
  assert(diagnosedResume.items.length === diagnosis.report.items.length,
    '诊断合成简历应保留完整快照骨架')
  const diagnosisPage = await api('GET', '/api/diagnose/page?page=1&size=10', { token })
  assert(diagnosisPage.total >= 1, '本人诊断历史至少应有一条')

  stage('投递看板、JD 解析与状态机')
  const deliveryBase = {
    company: '贵州星河科技有限公司',
    position: 'Java 后端开发工程师',
    jdRaw: jd,
    deliveryDate: localDate(0),
    examTime: localDateTime(2),
    interviewTime: localDateTime(26),
    skillReq: 'Java、Spring Boot、MySQL',
    deadline: localDate(2),
    remark: '联调投递记录'
  }
  const delivery = await api('POST', '/api/delivery', { token, body: deliveryBase })
  const deliveryId = delivery.id
  assert(delivery.status === 'pending', '投递默认状态应为 pending')
  assertArray(delivery.statusHistory, '投递响应必须包含 statusHistory')
  assert(delivery.statusHistory.length === 1, '新建投递应有一条初始历史')
  assertNoKeys(delivery, ['userId', 'jobTargetId', 'deleted'], '投递响应不得泄露内部归属字段')

  // status 字段省略表示保持当前状态，不追加历史。
  const editedDelivery = await api('PUT', '/api/delivery', {
    token,
    body: {
      id: deliveryId,
      ...deliveryBase,
      remark: '联调投递记录（编辑）'
    }
  })
  assert(editedDelivery.status === 'pending', '编辑省略 status 时应保持 pending')
  assert(editedDelivery.statusHistory.length === 1, '编辑同状态不应追加历史')
  const nullStatus = await api('PUT', '/api/delivery', {
    token,
    body: { id: deliveryId, ...deliveryBase, status: null },
    code: 400
  })
  assert(nullStatus === null, '编辑时 status=null 应返回 code=400')
  await api('PUT', `/api/delivery/${deliveryId}/status`, {
    token,
    body: { status: 'interview' },
    code: 400
  })

  for (const status of ['applied', 'exam', 'interview', 'exam', 'interview', 'offer']) {
    await api('PUT', `/api/delivery/${deliveryId}/status`, {
      token,
      body: { status }
    })
  }
  const offerDelivery = await api('GET', `/api/delivery/${deliveryId}`, { token })
  assert(offerDelivery.status === 'offer', '状态链最终应为 offer')
  assert(offerDelivery.statusHistory.length === 7, '六次成功迁移加初始历史应共 7 条')
  await api('PUT', `/api/delivery/${deliveryId}/status`, {
    token,
    body: { status: 'rejected' },
    code: 400
  })

  const quotaBeforeJd = await api('GET', '/api/quota/remaining', { token })
  const parsedJd = await api('POST', '/api/delivery/parse-jd', {
    token,
    body: { jdRaw: jd }
  })
  assert(parsedJd.jobTargetId > 0, 'JD 解析应返回 jobTargetId')
  assert(parsedJd.company.includes('星河'), 'JD 解析应提取公司名')
  assert(parsedJd.position.includes('Java'), 'JD 解析应提取岗位名')
  assert(String(parsedJd.skills).includes('Java'), 'JD 解析应提取 Java 技能')
  const quotaAfterJd = await api('GET', '/api/quota/remaining', { token })
  assert(quotaAfterJd.quotaUsed === quotaBeforeJd.quotaUsed + 1,
    'JD 解析应精确扣减一次配额')

  const interviewDelivery = await api('POST', '/api/delivery', {
    token,
    body: {
      ...deliveryBase,
      position: 'Java 后端开发工程师（面试）',
      status: 'applied',
      remark: '联调面试投递'
    }
  })
  const todo = await api('GET', '/api/delivery/todo', { token })
  assertArray(todo, '待办接口必须返回数组')
  assert(
    todo.some((item) => item.recordId === interviewDelivery.id && item.type === 'exam'),
    '两小时后的笔试应进入待办'
  )

  const disposableDelivery = await api('POST', '/api/delivery', {
    token,
    body: {
      ...deliveryBase,
      position: '联调待删除岗位',
      jdRaw: null,
      remark: '删除幂等测试'
    }
  })
  await api('DELETE', `/api/delivery/${disposableDelivery.id}`, { token })
  await api('DELETE', `/api/delivery/${disposableDelivery.id}`, { token })

  const demoToken = (await login('demo', '123456')).token
  await api('GET', `/api/delivery/${interviewDelivery.id}`, {
    token: demoToken,
    http: 403,
    code: 403
  })

  stage('模拟面试幂等与归档')
  const interviewTask = await api('POST', '/api/interview/start', {
    token,
    body: { deliveryId: interviewDelivery.id, focus: 'technical_deep_dive' }
  })
  const interviewGen = await waitTask(token, interviewTask.taskId, '生成面试题')
  assert(interviewGen.taskType === 'interview_gen', '任务类型应为 interview_gen')
  const interviewId = interviewGen.resultId
  const interview = await api('GET', `/api/interview/${interviewId}`, { token })
  assertArray(interview.questions, '面试详情 questions 必须为数组')
  assert(interview.questions.length >= 5, '面试题至少应为 5 道')
  assert(Array.isArray(interview.answers) && interview.answers.length === 0,
    '新面试答案列表应为空')

  const answerBody = {
    index: 0,
    answer: '我在项目中负责异步任务编排，使用 Redis 做幂等控制，并通过压测把 P95 从 480ms 降到 180ms。',
    requestId: randomUUID()
  }
  const quotaBeforeAnswer = await api('GET', '/api/quota/remaining', { token })
  const answerResult = await api('POST', `/api/interview/${interviewId}/answer`, {
    token,
    body: answerBody
  })
  assert(answerResult.progressIndex === 1, '首题提交后进度应为 1')
  assert(answerResult.feedback?.good, '逐题点评必须包含 good')
  const quotaAfterAnswer = await api('GET', '/api/quota/remaining', { token })
  assert(quotaAfterAnswer.quotaUsed === quotaBeforeAnswer.quotaUsed + 1,
    '首次提交答案应扣一次配额')
  const replay = await api('POST', `/api/interview/${interviewId}/answer`, {
    token,
    body: answerBody
  })
  assert(
    JSON.stringify(replay) === JSON.stringify(answerResult),
    '同一 requestId 与相同载荷应回放相同结果'
  )
  const quotaAfterReplay = await api('GET', '/api/quota/remaining', { token })
  assert(quotaAfterReplay.quotaUsed === quotaAfterAnswer.quotaUsed,
    '幂等重放不得二次扣配额')
  await api('POST', `/api/interview/${interviewId}/answer`, {
    token,
    body: { ...answerBody, answer: `${answerBody.answer} 额外内容` },
    code: 409
  })
  const quotaAfterConflict = await api('GET', '/api/quota/remaining', { token })
  assert(quotaAfterConflict.quotaUsed === quotaAfterAnswer.quotaUsed,
    'requestId 冲突不得扣配额')

  const review = await api('POST', `/api/interview/${interviewId}/review`, { token })
  assert(review.reviewText.includes('模拟面试复盘'), '复盘文本应包含标题')
  await api('POST', `/api/interview/${interviewId}/answer`, {
    token,
    body: { index: 1, answer: '继续作答', requestId: randomUUID() },
    code: 400
  })
  await api('PUT', `/api/interview/${interviewId}/archive`, { token })
  const archivedInterview = await api('GET', `/api/interview/${interviewId}`, { token })
  assert(archivedInterview.archived === true, '归档后 archived 必须为 true')
  const interviewPage = await api('GET', '/api/interview/list?page=1&size=10', { token })
  assert(interviewPage.total >= 1, '面试列表至少应有一条')

  stage('管理端统计、用户与安全返回白名单')
  const stats = await api('GET', '/api/admin/stats?days=7', { token: adminToken })
  assert(stats.userTotal >= 3, '用户总数应至少包含演示账号与临时账号')
  assertArray(stats.aiCallTrend, 'AI 调用趋势必须为数组')
  assert(stats.aiCallTrend.length === 7, '7 天趋势必须恰好有 7 项')
  assert(Array.isArray(stats.aiCallTrend), 'AI 调用趋势格式错误')
  const userPage = await api(
    'GET',
    `/api/admin/users?keyword=${encodeURIComponent(TEMP_USERNAME)}&page=1&size=10`,
    { token: adminToken }
  )
  assert(userPage.total === 1, '管理端按用户名应只命中临时账号')
  const adminUser = userPage.records[0]
  assert(adminUser.id === userId, '管理端用户 ID 应与 profile 一致')
  assertNoKeys(adminUser, ['password', 'quotaOverride'], '管理端用户列表不得泄露密码或覆盖值原文')
  const adminUserDetail = await api(`GET`, `/api/admin/users/${userId}`, { token: adminToken })
  assert(adminUserDetail.username === TEMP_USERNAME, '管理端用户详情用户名应一致')

  const adjustedQuota = await api('PUT', `/api/admin/users/${userId}/quota`, {
    token: adminToken,
    body: { override: 55 }
  })
  assert(adjustedQuota.quotaLimit === 55, '管理员配额覆盖后限额应为 55')
  assert(adjustedQuota.remaining === 55, '调整配额后剩余额度应重置为 55')

  await api('PUT', `/api/admin/users/${userId}/status`, {
    token: adminToken,
    body: { status: 0 }
  })
  await login(TEMP_USERNAME, TEMP_PASSWORD, { http: 403, code: 403 })
  await api('GET', '/api/user/profile', {
    token,
    http: 403,
    code: 403
  })
  await api('PUT', `/api/admin/users/${userId}/status`, {
    token: adminToken,
    body: { status: 1 }
  })

  const diagnosisAdminPage = await api(
    'GET',
    `/api/admin/diagnoses?userId=${userId}&page=1&size=10`,
    { token: adminToken }
  )
  assert(diagnosisAdminPage.total >= 1, '管理端应能查到临时用户诊断')
  assertNoKeys(
    diagnosisAdminPage.records[0],
    ['report', 'reportJson', 'sourceSnapshot', 'jd', 'contentJson'],
    '管理端诊断列表不得返回报告正文'
  )
  const aiLogPage = await api(
    'GET',
    `/api/admin/ai-logs?status=success&page=1&size=10`,
    { token: adminToken }
  )
  assert(aiLogPage.total >= 1, 'AI 调用日志至少应有一条成功记录')
  assertNoKeys(
    aiLogPage.records[0],
    ['prompt', 'response', 'inputSnapshot', 'resultJson'],
    'AI 日志不得返回 Prompt 或模型正文'
  )
  const disableLogPage = await api(
    'GET',
    '/api/admin/logs?action=user_disable&page=1&size=10',
    { token: adminToken }
  )
  assert(disableLogPage.total >= 1, '禁用账号应写入 user_disable 审计日志')
  const enableLogPage = await api(
    'GET',
    '/api/admin/logs?action=user_enable&page=1&size=10',
    { token: adminToken }
  )
  assert(enableLogPage.total >= 1, '启用账号应写入 user_enable 审计日志')
  await api('GET', '/api/admin/stats', { token, http: 403, code: 403 })

  stage('改密、注销与令牌失效')
  await api('PUT', '/api/user/password', {
    token,
    body: { oldPassword: 'wrong-password', newPassword: NEW_PASSWORD },
    code: 1
  })
  await api('PUT', '/api/user/password', {
    token,
    body: { oldPassword: TEMP_PASSWORD, newPassword: NEW_PASSWORD }
  })
  await login(TEMP_USERNAME, TEMP_PASSWORD, { code: 1 })
  const relogin = await login(TEMP_USERNAME, NEW_PASSWORD)
  token = relogin.token
  await api('DELETE', '/api/user/data', {
    token,
    body: { username: 'wrong-name' },
    code: 400
  })
  const purged = await api('DELETE', '/api/user/data', {
    token,
    body: { username: TEMP_USERNAME }
  })
  assert(purged.deleted === true, '注销成功应返回 deleted=true')
  await api('GET', '/api/user/profile', {
    token,
    http: 403,
    code: 403
  })
  const deletedUser = await api(`GET`, `/api/admin/users/${userId}`, { token: adminToken })
  assert(deletedUser.displayStatus === 'deleted', '注销后管理端显示状态应为 deleted')

  console.log(`\n全部 HTTP 联调通过：${checks} 项断言。`)
}

run().catch((error) => {
  console.error(`\n联调失败（阶段：${currentStage}）`)
  console.error(error?.stack || error)
  process.exitCode = 1
})
