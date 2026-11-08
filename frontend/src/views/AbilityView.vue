<template>
  <div class="page">
    <div class="page-head">
      <div><h1>个人能力库</h1><p>只把真实、可追问的经历放进来，它们会成为简历和面试的唯一事实源。</p></div>
      <el-button type="primary" size="large" @click="openCreate">＋ 新增经历</el-button>
    </div>

    <div class="panel toolbar-card">
      <el-input v-model="filters.keyword" clearable placeholder="搜索标题或明细" class="search" @keyup.enter="load" />
      <el-select v-model="filters.entryType" clearable placeholder="全部类型" @change="load">
        <el-option v-for="spec in specs" :key="spec.entryType" :label="spec.label" :value="spec.entryType" />
      </el-select>
      <el-select v-model="filters.status" clearable placeholder="全部状态" @change="load">
        <el-option label="已确认" value="confirmed" /><el-option label="待确认" value="pending" />
      </el-select>
      <el-select v-model="filters.enabled" clearable placeholder="启停状态" @change="load">
        <el-option label="启用" :value="1" /><el-option label="停用" :value="0" />
      </el-select>
      <el-button @click="load">筛选</el-button>
      <div class="toolbar-spacer"></div>
      <span class="muted small">共 {{ total }} 条</span>
    </div>

    <div v-if="records.length" class="ability-grid">
      <article v-for="entry in records" :key="entry.id" class="ability-card panel" :class="{ pending: entry.status === 'pending', disabled: !entry.enabled }">
        <div class="card-top">
          <span class="type-chip">{{ typeLabel(entry.entryType) }}</span>
          <div class="card-tools">
            <button @click="openEdit(entry)">编辑</button>
            <button class="danger" @click="remove(entry)">删除</button>
          </div>
        </div>
        <h3>{{ entry.title }}</h3>
        <div class="detail-list">
          <div v-for="field in fieldsOf(entry.entryType)" :key="field.key" v-show="entry.detail?.[field.key] !== null && entry.detail?.[field.key] !== ''">
            <span>{{ field.label }}</span>
            <p>{{ entry.detail?.[field.key] }}</p>
          </div>
        </div>
        <div class="card-foot">
          <div class="badges">
            <span v-if="entry.status === 'pending'" class="status-badge pending">待确认 · AI 解析</span>
            <span v-else class="status-badge">已确认</span>
            <span v-if="entry.source === 'ai'" class="source-badge">AI 来源</span>
          </div>
          <div v-if="entry.status === 'pending'" class="pending-actions">
            <el-button size="small" @click="reject(entry)">忽略</el-button>
            <el-button size="small" type="primary" @click="confirm(entry)">确认入库</el-button>
          </div>
          <el-switch v-else v-model="entry.enabled" :active-value="1" :inactive-value="0" inline-prompt active-text="启用" inactive-text="停用" @change="toggle(entry)" />
        </div>
      </article>
    </div>
    <div v-else class="panel empty">暂无匹配的能力条目，点击右上角新增第一条经历。</div>

    <div class="pager" v-if="total > filters.size">
      <el-pagination v-model:current-page="filters.page" :page-size="filters.size" :total="total" layout="prev, pager, next" @current-change="load" />
    </div>

    <el-dialog v-model="dialogVisible" :title="editingId ? '编辑能力条目' : '新增能力条目'" width="min(680px, 94vw)" destroy-on-close>
      <el-form ref="formRef" :model="form" label-position="top">
        <div class="grid grid-2">
          <el-form-item label="条目类型" required>
            <el-select v-model="form.entryType" @change="changeType">
              <el-option v-for="spec in specs" :key="spec.entryType" :label="spec.label" :value="spec.entryType" />
            </el-select>
          </el-form-item>
          <el-form-item label="条目标题" required>
            <el-input v-model="form.title" placeholder="例如：字节跳动 · 后端开发实习生" maxlength="500" />
          </el-form-item>
        </div>
        <div class="grid grid-2">
          <el-form-item v-for="field in currentFields" :key="field.key" :label="field.label" :required="field.required">
            <el-input
              v-model="form.detail[field.key]"
              :type="field.type === 'textarea' ? 'textarea' : field.type === 'number' ? 'number' : 'text'"
              :rows="field.type === 'textarea' ? 3 : undefined"
              :placeholder="`请输入${field.label}`"
            />
          </el-form-item>
        </div>
        <el-form-item label="排序号">
          <el-input-number v-model="form.sortOrder" :min="0" :max="9999" />
          <span class="muted small form-tip">数字越小越靠前，默认自动追加</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="saving" @click="save">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { abilityApi } from '../api'
import { abilityTypeLabels } from '../utils/format'

const specs = ref([])
const records = ref([])
const total = ref(0)
const dialogVisible = ref(false)
const editingId = ref(null)
const saving = ref(false)
const filters = reactive({ keyword: '', entryType: '', status: '', enabled: null, page: 1, size: 12 })
const form = reactive({ entryType: 'project', title: '', detail: {}, sortOrder: null })

const currentSpec = computed(() => specs.value.find((item) => item.entryType === form.entryType))
const currentFields = computed(() => currentSpec.value?.fields || [])

function typeLabel(type) { return abilityTypeLabels[type] || type }
function fieldsOf(type) { return specs.value.find((item) => item.entryType === type)?.fields || [] }

async function load() {
  const data = await abilityApi.list({
    keyword: filters.keyword || undefined,
    entryType: filters.entryType || undefined,
    status: filters.status || undefined,
    enabled: filters.enabled,
    page: filters.page,
    size: filters.size
  })
  records.value = data.records
  total.value = data.total
}
function resetDetail(type) {
  form.detail = {}
  ;(specs.value.find((item) => item.entryType === type)?.fields || []).forEach((field) => {
    form.detail[field.key] = ''
  })
}
function changeType(type) { resetDetail(type) }
function openCreate() {
  editingId.value = null
  form.entryType = 'project'
  form.title = ''
  form.sortOrder = null
  resetDetail(form.entryType)
  dialogVisible.value = true
}
function openEdit(entry) {
  editingId.value = entry.id
  form.entryType = entry.entryType
  form.title = entry.title
  form.detail = { ...(entry.detail || {}) }
  form.sortOrder = entry.sortOrder
  dialogVisible.value = true
}
async function save() {
  if (!form.title.trim()) return ElMessage.warning('请填写条目标题')
  const missing = currentFields.value.find((field) => field.required && !String(form.detail[field.key] ?? '').trim())
  if (missing) return ElMessage.warning(`请填写${missing.label}`)
  const detail = { ...form.detail }
  currentFields.value.filter((field) => field.type === 'number').forEach((field) => {
    if (detail[field.key] !== '' && detail[field.key] != null) detail[field.key] = Number(detail[field.key])
  })
  saving.value = true
  try {
    const payload = { id: editingId.value, entryType: form.entryType, title: form.title.trim(), detail, sortOrder: form.sortOrder }
    if (editingId.value) await abilityApi.update(payload)
    else await abilityApi.create(payload)
    ElMessage.success(editingId.value ? '条目已更新' : '条目已新增')
    dialogVisible.value = false
    await load()
  } finally { saving.value = false }
}
async function confirm(entry) {
  await abilityApi.confirm(entry.id)
  ElMessage.success('已确认入库')
  await load()
}
async function reject(entry) {
  await ElMessageBox.confirm('忽略后该 AI 条目会被删除，确定继续吗？', '忽略条目', { type: 'warning' })
  await abilityApi.reject(entry.id)
  ElMessage.success('已忽略')
  await load()
}
async function toggle(entry) {
  try {
    await abilityApi.enabled(entry.id, entry.enabled)
    ElMessage.success(entry.enabled ? '已启用' : '已停用')
  } catch {
    entry.enabled = entry.enabled ? 0 : 1
  }
}
async function remove(entry) {
  await ElMessageBox.confirm(`删除“${entry.title}”？历史简历版本不会被回溯修改。`, '删除条目', { type: 'warning' })
  await abilityApi.remove(entry.id)
  ElMessage.success('已删除')
  await load()
}

onMounted(async () => {
  const specData = await abilityApi.fieldSpec()
  specs.value = specData.entryTypes
  await load()
})
</script>

<style scoped>
.toolbar-card { padding: 15px 17px; display: flex; gap: 10px; align-items: center; flex-wrap: wrap; margin-bottom: 20px; }
.search { width: 250px; }
.ability-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 17px; }
.ability-card { padding: 20px; display: flex; flex-direction: column; min-height: 250px; transition: .2s; border-top: 3px solid #dce3ff; }
.ability-card:hover { transform: translateY(-2px); }
.ability-card.pending { border-top-color: var(--warning); background: linear-gradient(180deg, #fffaf3, #fff 40%); }
.ability-card.disabled { opacity: .62; }
.card-top { display: flex; align-items: center; justify-content: space-between; }
.type-chip { padding: 5px 9px; border-radius: 7px; background: var(--brand-soft); color: var(--brand); font-size: 10px; font-weight: 700; }
.card-tools { display: flex; gap: 8px; }
.card-tools button { border: 0; background: transparent; color: var(--muted); font-size: 11px; cursor: pointer; }
.card-tools button:hover { color: var(--brand); }
.card-tools .danger:hover { color: var(--danger); }
.ability-card h3 { font-size: 16px; line-height: 1.5; margin: 15px 0; }
.detail-list { flex: 1; display: flex; flex-direction: column; gap: 9px; }
.detail-list div { display: grid; grid-template-columns: 68px 1fr; gap: 8px; }
.detail-list span { color: var(--muted); font-size: 11px; padding-top: 2px; }
.detail-list p { margin: 0; color: #39435c; font-size: 12px; line-height: 1.6; word-break: break-word; }
.card-foot { display: flex; align-items: center; justify-content: space-between; gap: 10px; border-top: 1px solid #f0f2f8; padding-top: 14px; margin-top: 16px; }
.badges { display: flex; gap: 6px; flex-wrap: wrap; }
.status-badge, .source-badge { padding: 3px 7px; border-radius: 6px; font-size: 10px; background: #edf8f4; color: var(--success); }
.status-badge.pending { background: #fff3e4; color: #bf7617; }
.source-badge { background: #f0f2f8; color: var(--muted); }
.pending-actions { display: flex; gap: 5px; }
.pager { display: flex; justify-content: center; margin-top: 24px; }
.form-tip { margin-left: 10px; }
@media (max-width: 1200px) { .ability-grid { grid-template-columns: repeat(2, minmax(0,1fr)); } }
@media (max-width: 720px) { .ability-grid { grid-template-columns: 1fr; } .search { width: 100%; } }
</style>
