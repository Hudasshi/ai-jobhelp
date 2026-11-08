/**
 * 前端真实浏览器巡检脚本。
 *
 * 不引入 Playwright/Puppeteer：直接使用本机 Chrome 的 DevTools Protocol，
 * 因此只需要 Node 内置模块。脚本会真实操作登录表单，依次加载用户端和管理端
 * 页面，并收集页面标题、浏览器异常和失败的 /api 请求。
 *
 * 运行前要求：
 * 1. 后端 http://127.0.0.1:8080 已启动；
 * 2. 前端 http://127.0.0.1:5173 已启动；
 * 3. 本机存在 Chrome。
 */
import { spawn } from 'node:child_process'
import { existsSync } from 'node:fs'
import { mkdtemp, rm } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join } from 'node:path'

const FRONTEND = 'http://127.0.0.1:5173'
const BACKEND = 'http://127.0.0.1:8080'
const DEBUG_PORT = 9333
const CHROME_CANDIDATES = [
  'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe',
  'C:\\Program Files (x86)\\Microsoft\\Edge\\Application\\msedge.exe'
]

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

function findChrome() {
  const executable = CHROME_CANDIDATES.find((item) => existsSync(item))
  if (!executable) throw new Error('未找到 Chrome 或 Edge，无法执行页面巡检')
  return executable
}

async function waitForDebugPort(timeoutMs = 15000) {
  const deadline = Date.now() + timeoutMs
  while (Date.now() < deadline) {
    try {
      const response = await fetch(`http://127.0.0.1:${DEBUG_PORT}/json/version`)
      if (response.ok) return
    } catch {
      // 浏览器启动需要一点时间，端口尚未监听时继续等待。
    }
    await sleep(200)
  }
  throw new Error('Chrome 调试端口启动超时')
}

/**
 * 极简 CDP 客户端：每个消息带自增 id，响应按 id 归还 Promise。
 * 事件统一进入 eventHandlers，页面本身不需要知道协议细节。
 */
class CdpClient {
  constructor(url) {
    this.nextId = 1
    this.pending = new Map()
    this.eventHandlers = new Map()
    this.socket = new WebSocket(url)
  }

  async connect() {
    await new Promise((resolve, reject) => {
      this.socket.addEventListener('open', resolve, { once: true })
      this.socket.addEventListener('error', reject, { once: true })
    })
    this.socket.addEventListener('message', (event) => this.onMessage(event.data))
  }

  onMessage(raw) {
    const message = JSON.parse(String(raw))
    if (message.id) {
      const task = this.pending.get(message.id)
      if (!task) return
      this.pending.delete(message.id)
      if (message.error) task.reject(new Error(message.error.message))
      else task.resolve(message.result)
      return
    }
    const handlers = this.eventHandlers.get(message.method) || []
    for (const handler of handlers) handler(message.params)
  }

  send(method, params = {}) {
    const id = this.nextId++
    return new Promise((resolve, reject) => {
      this.pending.set(id, { resolve, reject })
      this.socket.send(JSON.stringify({ id, method, params }))
    })
  }

  on(method, handler) {
    const handlers = this.eventHandlers.get(method) || []
    handlers.push(handler)
    this.eventHandlers.set(method, handlers)
  }

  close() {
    this.socket.close()
  }
}

async function evaluate(client, expression) {
  const response = await client.send('Runtime.evaluate', {
    expression,
    awaitPromise: true,
    returnByValue: true
  })
  if (response.exceptionDetails) {
    throw new Error(response.exceptionDetails.text || '页面脚本执行失败')
  }
  return response.result?.value
}

async function navigate(client, path, waitMs = 900) {
  await client.send('Page.navigate', { url: `${FRONTEND}${path}` })
  await sleep(waitMs)
}

/**
 * Element Plus 输入框是受控组件，必须触发原生 input 事件，
 * 只改 DOM value 不会更新 Vue 的 v-model。
 */
async function loginThroughForm(client, username, password) {
  await navigate(client, '/login')
  const result = await evaluate(client, `(() => {
    const inputs = [...document.querySelectorAll('.el-input__inner')]
    if (inputs.length < 2) return { ok: false, reason: '登录输入框数量不足' }
    const setter = Object.getOwnPropertyDescriptor(HTMLInputElement.prototype, 'value').set
    setter.call(inputs[0], ${JSON.stringify(username)})
    inputs[0].dispatchEvent(new Event('input', { bubbles: true }))
    setter.call(inputs[1], ${JSON.stringify(password)})
    inputs[1].dispatchEvent(new Event('input', { bubbles: true }))
    document.querySelector('.submit-btn')?.click()
    return { ok: true }
  })()`)
  if (!result?.ok) throw new Error(result?.reason || '登录表单操作失败')

  const deadline = Date.now() + 10000
  while (Date.now() < deadline) {
    await sleep(250)
    const session = await evaluate(client, `({
      path: location.pathname,
      token: localStorage.getItem('jobhelper_token'),
      role: localStorage.getItem('jobhelper_role')
    })`)
    if (session.token && session.path !== '/login') return session
  }
  throw new Error(`${username} 登录后未进入业务页面`)
}

async function auditPage(client, runtime) {
  await sleep(700)
  return evaluate(client, `({
    path: location.pathname,
    title: document.title,
    h1: document.querySelector('h1')?.innerText?.trim() || '',
    text: document.body.innerText
  })`)
}

/**
 * 验证“上传简历 -> 用条目合成”弹窗真的会打开，并且按后端契约预选
 * 当前全部 confirmed+enabled 能力条目。操作到最后点击取消，不写演示数据。
 */
async function verifyUploadComposeDialog(client, runtime) {
  await navigate(client, '/resumes')
  const opened = await evaluate(client, `(() => {
    const button = [...document.querySelectorAll('button')]
      .find((item) => item.innerText.trim() === '用条目合成')
    if (!button) return { ok: false, reason: '未找到“用条目合成”按钮' }
    button.click()
    return { ok: true }
  })()`)
  if (!opened?.ok) return [opened?.reason || '无法打开上传合成弹窗']

  await sleep(900)
  const dialog = await evaluate(client, `(() => {
    const visible = [...document.querySelectorAll('.el-dialog')]
      .filter((item) => item.offsetParent !== null)
    const target = visible.find((item) => item.innerText.includes('用当前能力库合成简历'))
    if (!target) return { ok: false, reason: '上传合成弹窗未出现' }
    const boxes = [...target.querySelectorAll('.el-checkbox')]
    const checked = boxes.filter((item) => item.classList.contains('is-checked')).length
    const disabled = boxes.filter((item) => item.classList.contains('is-disabled')).length
    const cancel = [...target.querySelectorAll('button')]
      .find((item) => item.innerText.trim() === '取消')
    cancel?.click()
    return { ok: true, checked, disabled }
  })()`)
  if (!dialog?.ok) return [dialog?.reason || '上传合成弹窗校验失败']

  const problems = []
  if (dialog.checked === 0) problems.push('上传合成弹窗未预选任何能力条目')
  if (dialog.disabled !== dialog.checked) {
    problems.push(`上传合成条目应全部锁定，checked=${dialog.checked}，disabled=${dialog.disabled}`)
  }
  problems.push(...runtime.exceptions.map((item) => `脚本异常：${item}`))
  problems.push(...runtime.failedResponses.map((item) => `请求失败：${item}`))
  return problems
}

const userRoutes = [
  // Dashboard 的主标题包含动态问候语和昵称，因此只校验稳定文本标记。
  ['/dashboard', null, ['求职进度中枢', '最近任务']],
  ['/abilities', '个人能力库', ['个人能力库']],
  ['/resumes', '简历工作台', ['简历工作台']],
  ['/diagnosis', 'AI 简历诊断', ['AI 简历诊断']],
  ['/deliveries', '投递看板', ['投递看板']],
  ['/interviews', '模拟面试', ['模拟面试']],
  ['/profile', '个人中心', ['个人中心']]
]

const adminRoutes = [
  ['/admin', '数据总览', ['用户总数', 'AI 调用趋势']],
  ['/admin/users', '用户管理', ['用户管理', '今日配额']],
  ['/admin/diagnoses', '诊断记录', ['诊断记录', '失败摘要']],
  ['/admin/ai-logs', 'AI 调用日志', ['AI 调用日志', '估算成本']],
  ['/admin/logs', '系统日志', ['系统日志', '脱敏备注']],
  ['/admin/configs', '系统配置', ['系统配置', '每日默认配额', '批量保存说明']]
]

async function main() {
  const profileDir = await mkdtemp(join(tmpdir(), 'jobhelper-ui-smoke-'))
  const chrome = spawn(findChrome(), [
    '--headless=new',
    `--remote-debugging-port=${DEBUG_PORT}`,
    `--user-data-dir=${profileDir}`,
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-extensions',
    '--disable-background-networking',
    '--window-size=1440,1000',
    '--remote-allow-origins=*'
  ], { stdio: 'ignore' })

  let client
  const failures = []
  try {
    await waitForDebugPort()
    const target = await fetch(
      `http://127.0.0.1:${DEBUG_PORT}/json/new?${encodeURIComponent(`${FRONTEND}/login`)}`,
      { method: 'PUT' }
    ).then((response) => response.json())

    client = new CdpClient(target.webSocketDebuggerUrl)
    await client.connect()
    await Promise.all([
      client.send('Page.enable'),
      client.send('Runtime.enable'),
      client.send('Network.enable'),
      client.send('Log.enable')
    ])

    const runtime = { exceptions: [], failedResponses: [] }
    client.on('Runtime.exceptionThrown', ({ exceptionDetails }) => {
      runtime.exceptions.push(exceptionDetails.exception?.description || exceptionDetails.text)
    })
    client.on('Log.entryAdded', ({ entry }) => {
      if (entry.level === 'error') runtime.exceptions.push(entry.text)
    })
    client.on('Network.responseReceived', ({ response }) => {
      if (response.url.includes('/api/') && response.status >= 400) {
        runtime.failedResponses.push(`${response.status} ${response.url}`)
      }
    })

    const demoSession = await loginThroughForm(client, 'demo', '123456')
    if (demoSession.role !== 'user') throw new Error('demo 登录角色不是 user')
    console.log(`登录通过：demo -> ${demoSession.path}`)

    for (const [path, expectedH1, markers] of userRoutes) {
      runtime.exceptions.length = 0
      runtime.failedResponses.length = 0
      await navigate(client, path)
      const page = await auditPage(client, runtime)
      const problems = []
      if (page.path !== path) problems.push(`路径为 ${page.path}`)
      if (expectedH1 != null && page.h1 !== expectedH1) {
        problems.push(`H1 为 ${page.h1 || '空'}`)
      }
      for (const marker of markers) {
        if (!page.text.includes(marker)) problems.push(`缺少文本“${marker}”`)
      }
      problems.push(...runtime.exceptions.map((item) => `脚本异常：${item}`))
      problems.push(...runtime.failedResponses.map((item) => `请求失败：${item}`))
      if (problems.length) failures.push({ path, problems })
      else console.log(`页面通过：${path}`)
    }

    runtime.exceptions.length = 0
    runtime.failedResponses.length = 0
    const composeProblems = await verifyUploadComposeDialog(client, runtime)
    if (composeProblems.length) {
      failures.push({ path: '/resumes 上传合成弹窗', problems: composeProblems })
    } else {
      console.log('交互通过：/resumes 上传简历用条目合成')
    }

    await evaluate(client, 'localStorage.clear()')
    const adminSession = await loginThroughForm(client, 'admin', 'admin123')
    if (adminSession.role !== 'admin') throw new Error('admin 登录角色不是 admin')
    console.log(`登录通过：admin -> ${adminSession.path}`)

    for (const [path, expectedH1, markers] of adminRoutes) {
      runtime.exceptions.length = 0
      runtime.failedResponses.length = 0
      await navigate(client, path)
      const page = await auditPage(client, runtime)
      const problems = []
      if (page.path !== path) problems.push(`路径为 ${page.path}`)
      if (expectedH1 != null && page.h1 !== expectedH1) {
        problems.push(`H1 为 ${page.h1 || '空'}`)
      }
      for (const marker of markers) {
        if (!page.text.includes(marker)) problems.push(`缺少文本“${marker}”`)
      }
      problems.push(...runtime.exceptions.map((item) => `脚本异常：${item}`))
      problems.push(...runtime.failedResponses.map((item) => `请求失败：${item}`))
      if (problems.length) failures.push({ path, problems })
      else console.log(`页面通过：${path}`)
    }

    // 额外确认后端确实可达，避免把“全空白但无 4xx”的异常情况当成通过。
    const health = await fetch(`${BACKEND}/api/auth/login`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username: 'demo', password: '123456' })
    })
    if (!health.ok || (await health.json()).code !== 0) {
      failures.push({ path: '后端健康检查', problems: ['登录接口未返回 code=0'] })
    }
  } finally {
    client?.close()
    chrome.kill()
    await sleep(300)
    await rm(profileDir, { recursive: true, force: true }).catch(() => {})
  }

  if (failures.length) {
    console.error('\n页面巡检失败：')
    for (const failure of failures) {
      console.error(`- ${failure.path}: ${failure.problems.join('；')}`)
    }
    process.exitCode = 1
    return
  }
  console.log('\n全部页面巡检通过。')
}

main().catch((error) => {
  console.error(error)
  process.exitCode = 1
})
