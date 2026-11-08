<template>
  <div class="page">
    <div class="page-head">
      <div>
        <h1>系统配置</h1>
        <p>13 个白名单参数支持热更新；保存后新请求立即读取新值，不包含任何密钥或基础设施配置。</p>
      </div>
      <div class="head-actions">
        <span v-if="dirtyCount" class="dirty-hint">{{ dirtyCount }} 项待保存</span>
        <el-button :disabled="saving" @click="load">重新加载</el-button>
        <el-button type="primary" :disabled="!dirtyCount" :loading="saving" @click="save">
          保存全部修改
        </el-button>
      </div>
    </div>

    <div v-if="!loading && !items.length" class="panel empty">暂无可读取的系统配置。</div>

    <section v-for="group in groupedItems" :key="group.title" class="config-section">
      <div class="section-head">
        <span class="group-icon">{{ group.icon }}</span>
        <div><h2>{{ group.title }}</h2><p>{{ group.description }}</p></div>
      </div>
      <div class="config-grid">
        <article v-for="item in group.items" :key="item.configKey" class="panel config-card">
          <div class="card-head">
            <div>
              <strong>{{ labelOf(item.configKey) }}</strong>
              <code>{{ item.configKey }}</code>
            </div>
            <span class="type-tag">{{ typeLabel(item.valueType) }}</span>
          </div>
          <p class="remark">{{ item.remark || '暂无配置说明' }}</p>
          <el-input
            v-model="item.configValue"
            :placeholder="placeholderOf(item.configKey)"
            maxlength="500"
          >
            <template v-if="unitOf(item.configKey)" #append>{{ unitOf(item.configKey) }}</template>
          </el-input>
          <div class="card-foot">
            <span>范围：{{ rangeText(item.configKey) }}</span>
            <span>{{ item.updatedAt || '尚未落库' }}</span>
          </div>
        </article>
      </div>
    </section>

    <div class="panel notice-panel">
      <strong>批量保存说明</strong>
      <p>保存前会一次校验全部 13 项；任一项非法时整批不提交。服务端在单个事务内逐项 upsert，提交后刷新内存配置快照。</p>
    </div>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi } from '../../api'

const items = ref([])
const loading = ref(false)
const saving = ref(false)
const originalValues = ref({})

const labels = {
  'quota.daily.default': '每日默认配额',
  'ai.model': 'AI 模型标识',
  'ai.timeout-ms': '单次调用超时',
  'ai.max-input-tokens': '最大输入 Tokens',
  'ai.retry.max': '额外重试次数',
  'ai.task.timeout-min': '异步任务超时',
  'ai.circuit.fail-rate': '熔断失败率阈值',
  'ai.circuit.window-min': '熔断统计窗口',
  'ai.circuit.max-calls': '单用户日调用上限',
  'ai.price.input': '输入 Token 单价',
  'ai.price.output': '输出 Token 单价',
  'file.keep-days': '上传文件保留天数',
  'log.ai.keep-days': 'AI 日志保留天数'
}

const groups = [
  {
    title: '配额与数据保留',
    description: '控制默认 AI 配额和用户数据生命周期。',
    icon: '额',
    keys: ['quota.daily.default', 'file.keep-days', 'log.ai.keep-days']
  },
  {
    title: '模型调用参数',
    description: '控制模型标识、超时、上下文和重试策略。',
    icon: '模',
    keys: ['ai.model', 'ai.timeout-ms', 'ai.max-input-tokens', 'ai.retry.max', 'ai.task.timeout-min']
  },
  {
    title: '熔断与调用上限',
    description: '避免外部模型故障拖垮业务，并限制单用户日尝试量。',
    icon: '熔',
    keys: ['ai.circuit.fail-rate', 'ai.circuit.window-min', 'ai.circuit.max-calls']
  },
  {
    title: '成本估算',
    description: '按输入、输出 Token 单价计算每次调用的估算成本。',
    icon: '价',
    keys: ['ai.price.input', 'ai.price.output']
  }
]

const groupedItems = computed(() => groups.map((group) => ({
  ...group,
  items: group.keys
    .map((key) => items.value.find((item) => item.configKey === key))
    .filter(Boolean)
})))

const dirtyCount = computed(() => items.value.filter(
  (item) => item.configValue !== originalValues.value[item.configKey]
).length)

const labelOf = (key) => labels[key] || key
const typeLabel = (type) => ({ string: '文本', integer: '整数', number: '数值' }[type] || type)

function unitOf(key) {
  return {
    'quota.daily.default': '次/日',
    'ai.timeout-ms': '毫秒',
    'ai.max-input-tokens': 'tokens',
    'ai.retry.max': '次',
    'ai.task.timeout-min': '分钟',
    'ai.circuit.window-min': '分钟',
    'ai.circuit.max-calls': '次/日',
    'file.keep-days': '天',
    'log.ai.keep-days': '天'
  }[key] || ''
}

function rangeText(key) {
  return {
    'quota.daily.default': '0～999',
    'ai.model': '非空，最多 500 字符',
    'ai.timeout-ms': '1000～600000',
    'ai.max-input-tokens': '100～100000',
    'ai.retry.max': '0～5',
    'ai.task.timeout-min': '1～60',
    'ai.circuit.fail-rate': '0～1',
    'ai.circuit.window-min': '1～1440',
    'ai.circuit.max-calls': '1～10000',
    'ai.price.input': '大于等于 0',
    'ai.price.output': '大于等于 0',
    'file.keep-days': '1～3650',
    'log.ai.keep-days': '1～3650'
  }[key] || '—'
}

function placeholderOf(key) {
  return key === 'ai.model' ? '例如 deepseek-chat' : '请输入配置值'
}

/**
 * 前端校验用于尽早给出可读提示，最终合法性和事务原子性仍由服务端保证。
 * 返回第一条错误即可，避免一次弹出大量重复消息。
 */
function validate(itemsToCheck) {
  const integerRanges = {
    'quota.daily.default': [0, 999],
    'ai.timeout-ms': [1000, 600000],
    'ai.max-input-tokens': [100, 100000],
    'ai.retry.max': [0, 5],
    'ai.task.timeout-min': [1, 60],
    'ai.circuit.window-min': [1, 1440],
    'ai.circuit.max-calls': [1, 10000],
    'file.keep-days': [1, 3650],
    'log.ai.keep-days': [1, 3650]
  }
  for (const item of itemsToCheck) {
    const value = String(item.configValue ?? '').trim()
    if (!value) return `${labelOf(item.configKey)}不能为空`
    if (item.configValue !== value) item.configValue = value

    if (Object.hasOwn(integerRanges, item.configKey)) {
      if (!/^-?\d+$/.test(value)) return `${labelOf(item.configKey)}必须是整数`
      const [min, max] = integerRanges[item.configKey]
      const number = Number(value)
      if (number < min || number > max) return `${labelOf(item.configKey)}必须在 ${min}～${max}`
    }
    if (item.configKey === 'ai.circuit.fail-rate') {
      const number = Number(value)
      if (!Number.isFinite(number) || number < 0 || number > 1) {
        return '熔断失败率阈值必须在 0～1'
      }
    }
    if (item.configKey === 'ai.price.input' || item.configKey === 'ai.price.output') {
      const number = Number(value)
      if (!Number.isFinite(number) || number < 0) return `${labelOf(item.configKey)}必须是大于等于 0 的数值`
    }
  }
  return ''
}

async function load() {
  loading.value = true
  try {
    const data = await adminApi.configs()
    items.value = data.items || []
    originalValues.value = Object.fromEntries(
      items.value.map((item) => [item.configKey, item.configValue])
    )
  } finally {
    loading.value = false
  }
}

async function save() {
  const error = validate(items.value)
  if (error) {
    ElMessage.warning(error)
    return
  }
  saving.value = true
  try {
    const data = await adminApi.updateConfigs(items.value.map((item) => ({
      configKey: item.configKey,
      configValue: String(item.configValue).trim()
    })))
    items.value = data.items || []
    originalValues.value = Object.fromEntries(
      items.value.map((item) => [item.configKey, item.configValue])
    )
    ElMessage.success('配置已保存并刷新')
  } finally {
    saving.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.head-actions { display: flex; align-items: center; gap: 9px; }
.dirty-hint { color: #bd7217; background: #fff5e8; border-radius: 999px; padding: 5px 9px; font-size: 10px; }
.config-section { margin-bottom: 24px; }
.section-head { display: flex; align-items: center; gap: 11px; margin-bottom: 12px; }
.group-icon { width: 35px; height: 35px; border-radius: 10px; display: grid; place-items: center; background: var(--brand-soft); color: var(--brand); font-size: 11px; font-weight: 800; }
.section-head h2 { margin: 0 0 3px; font-size: 15px; }
.section-head p { margin: 0; color: var(--muted); font-size: 10px; }
.config-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 13px; }
.config-card { padding: 17px; display: flex; flex-direction: column; gap: 11px; }
.card-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 10px; }
.card-head > div { display: flex; flex-direction: column; gap: 4px; }
.card-head strong { font-size: 13px; }
.card-head code { color: var(--muted); font-size: 9px; }
.type-tag { padding: 4px 7px; border-radius: 6px; background: #f1f3f9; color: var(--muted); font-size: 9px; white-space: nowrap; }
.remark { margin: 0; color: var(--muted); font-size: 10px; line-height: 1.6; min-height: 32px; }
.card-foot { display: flex; justify-content: space-between; gap: 8px; color: #8a92a8; font-size: 9px; }
.notice-panel { padding: 16px 18px; margin-top: 3px; }
.notice-panel strong { font-size: 12px; }
.notice-panel p { margin: 6px 0 0; color: var(--muted); font-size: 10px; line-height: 1.7; }
:deep(.el-input-group__append) { font-size: 10px; padding: 0 10px; }
@media (max-width: 1150px) { .config-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 720px) {
  .page-head { align-items: flex-start; }
  .head-actions { flex-wrap: wrap; }
  .config-grid { grid-template-columns: 1fr; }
}
</style>
