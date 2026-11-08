<template>
  <div class="page">
    <div class="page-head">
      <div><h1>简历工作台</h1><p>版本一旦保存就是快照；后续修改能力库，不会悄悄改动已投递的简历。</p></div>
      <div class="toolbar">
        <el-button size="large" @click="openUpload">上传解析</el-button>
        <el-button type="primary" size="large" @click="openGenerate">能力库生成</el-button>
      </div>
    </div>

    <div class="resume-layout">
      <section>
        <div v-if="resumes.length" class="resume-grid">
          <article v-for="resume in resumes" :key="resume.id" class="resume-card panel">
            <div class="paper-head">
              <span class="source-chip" :class="resume.sourceType">{{ sourceLabel(resume.sourceType) }}</span>
              <span class="status-dot" :class="resume.status">{{ statusLabel(resume.status) }}</span>
            </div>
            <h3>{{ resume.name }}</h3>
            <p class="target">{{ resume.targetPosition || '通用版本 · 未指定目标岗位' }}</p>
            <div class="resume-stats">
              <div><strong>{{ resume.items?.length || 0 }}</strong><span>条目</span></div>
              <div><strong>{{ resume.syncStatus ? '有更新' : '最新' }}</strong><span>能力库</span></div>
            </div>
            <p class="time">更新于 {{ resume.updatedAt }}</p>
            <div class="resume-actions">
              <el-button size="small" @click="viewResume(resume)">预览</el-button>
              <el-button v-if="resume.sourceType === 'upload'" size="small" @click="openSynthesize(resume)">用条目合成</el-button>
              <button class="delete-link" @click="remove(resume)">删除</button>
            </div>
          </article>
        </div>
        <div v-else class="panel empty">还没有简历版本。可以上传 PDF/DOCX/TXT，或从能力库生成。</div>
        <div class="pager" v-if="total > pageSize">
          <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" />
        </div>
      </section>

      <aside class="side-stack">
        <div class="panel panel-pad guide-card">
          <span class="pill">制作建议</span>
          <h3>先确认事实，再生成版本</h3>
          <ol>
            <li>能力库至少确认 3 条真实经历</li>
            <li>针对具体岗位生成，而不是一份简历投所有公司</li>
            <li>保存后再发起诊断，报告才有稳定的对照快照</li>
          </ol>
          <el-button text type="primary" @click="$router.push('/abilities')">去完善能力库 →</el-button>
        </div>
        <div class="panel panel-pad">
          <div class="side-title"><h3>版本统计</h3><span>{{ total }} 个版本</span></div>
          <div class="side-metric"><span>上传解析</span><strong>{{ countBySource('upload') }}</strong></div>
          <div class="side-metric"><span>能力库生成</span><strong>{{ countBySource('generate') }}</strong></div>
          <div class="side-metric"><span>诊断合成</span><strong>{{ countBySource('diagnose') }}</strong></div>
        </div>
      </aside>
    </div>

    <el-dialog v-model="uploadVisible" title="上传已有简历" width="min(560px, 94vw)">
      <div class="upload-tip">支持 PDF、DOCX、TXT，单文件不超过 10MB。系统会抽取文本并拆成待确认真实条目。</div>
      <el-upload drag :auto-upload="false" :limit="1" accept=".pdf,.docx,.txt" :on-change="onFileChange" :on-remove="() => uploadFile = null">
        <div class="upload-symbol">⇧</div>
        <div class="el-upload__text">拖拽文件到这里，或<em>点击选择</em></div>
      </el-upload>
      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploading" @click="startUpload">上传并解析</el-button>
      </template>
    </el-dialog>

    <el-dialog
      v-model="generateVisible"
      :title="composeMode === 'upload' ? '用当前能力库合成简历' : '从能力库生成简历'"
      width="min(820px, 95vw)"
    >
      <div class="form-grid">
        <el-form-item label="版本名称" required><el-input v-model="generateForm.name" placeholder="例如：字节跳动-后端开发定制版" /></el-form-item>
        <el-form-item label="目标岗位（可选）"><el-input v-model="generateForm.targetPosition" placeholder="例如：Java 后端开发工程师" /></el-form-item>
      </div>
      <div class="select-head">
        <strong>{{ composeMode === 'upload' ? '上传合成会包含全部已确认且启用的条目' : '选择已确认且有启用的条目' }}</strong>
        <span>已选 {{ generateForm.entryIds.length }} 条</span>
      </div>
      <div class="entry-selector">
        <label v-for="entry in availableEntries" :key="entry.id" class="entry-option" :class="{ selected: generateForm.entryIds.includes(entry.id) }">
          <el-checkbox v-model="generateForm.entryIds" :value="entry.id" :disabled="composeMode === 'upload'" />
          <div><strong>{{ entry.title }}</strong><span>{{ abilityType(entry.entryType) }} · {{ detailPreview(entry) }}</span></div>
        </label>
        <div v-if="!availableEntries.length" class="empty">没有可用条目，请先在能力库确认并启用经历。</div>
      </div>
      <template #footer>
        <el-button @click="generateVisible = false">取消</el-button>
        <el-button type="primary" :loading="generating" @click="startGenerate">
          {{ composeMode === 'upload' ? '生成并保存版本' : '开始生成' }}
        </el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="previewVisible" :title="previewMode === 'draft' ? '草稿预览' : '简历预览'" width="min(900px, 96vw)">
      <div class="preview-meta" v-if="previewMode === 'saved'">
        <span>{{ previewResume?.name }}</span><span>{{ previewResume?.targetPosition || '通用版本' }}</span>
      </div>
      <div class="markdown-box preview-box">{{ previewText || '暂无正文' }}</div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button v-if="previewMode === 'draft'" type="primary" :loading="savingVersion" @click="saveDraft">保存为新版本</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { abilityApi, resumeApi, taskApi } from '../api'
import { pollTask } from '../utils/poll'
import { abilityTypeLabels } from '../utils/format'

const resumes = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 9
const uploadVisible = ref(false)
const uploadFile = ref(null)
const uploading = ref(false)
const generateVisible = ref(false)
const generating = ref(false)
const composeMode = ref('generate')
const composeSourceResumeId = ref(null)
const availableEntries = ref([])
const generateForm = reactive({ name: '', targetPosition: '', entryIds: [] })
const previewVisible = ref(false)
const previewMode = ref('saved')
const previewText = ref('')
const previewResume = ref(null)
const draftTask = ref(null)
const savingVersion = ref(false)

const sourceLabel = (type) => ({ upload: '上传', generate: '生成', diagnose: '诊断合成' }[type] || type)
const statusLabel = (status) => ({ parsing: '解析中', ready: '就绪', failed: '失败' }[status] || status)
const abilityType = (type) => abilityTypeLabels[type] || type
function detailPreview(entry) {
  const text = entry.detail?.description || entry.detail?.result || entry.detail?.level || entry.detail?.school || ''
  return String(text).slice(0, 60) || '已确认条目'
}
function countBySource(type) { return resumes.value.filter((item) => item.sourceType === type).length }

async function load() {
  const data = await resumeApi.list({ page: page.value, size: pageSize })
  resumes.value = data.records
  total.value = data.total
}
function openUpload() { uploadFile.value = null; uploadVisible.value = true }
function onFileChange(file) { uploadFile.value = file.raw }
async function startUpload() {
  if (!uploadFile.value) return ElMessage.warning('请先选择文件')
  uploading.value = true
  try {
    const form = new FormData()
    form.append('file', uploadFile.value)
    const { taskId } = await resumeApi.upload(form)
    const task = await pollTask(taskApi, taskId)
    if (task.status !== 'SUCCESS') throw new Error(task.failReason || '解析失败')
    ElMessage.success(`解析完成：新增 ${task.resultData?.inserted || 0} 条，跳过 ${task.resultData?.skipped || 0} 条`)
    uploadVisible.value = false
    page.value = 1
    await load()
  } catch (error) {
    if (error.message) ElMessage.error(error.message)
  } finally { uploading.value = false }
}
async function openGenerate() {
  composeMode.value = 'generate'
  composeSourceResumeId.value = null
  generateForm.name = ''
  generateForm.targetPosition = ''
  generateForm.entryIds = []
  const data = await abilityApi.list({ status: 'confirmed', enabled: 1, page: 1, size: 100 })
  availableEntries.value = data.records
  generateVisible.value = true
}
async function startGenerate() {
  if (!generateForm.name.trim()) return ElMessage.warning('请填写版本名称')
  if (!generateForm.entryIds.length) return ElMessage.warning('至少选择一条能力经历')
  generating.value = true
  try {
    /*
     * 上传版本“用条目合成”走 sourceType=upload 分支：
     * 后端要求请求集合与当前全部 confirmed+enabled 条目完全一致，
     * 因此这里一次性提交并直接落版本，不再走 AI 草稿预览。
     */
    if (composeMode.value === 'upload') {
      await resumeApi.save({
        name: generateForm.name.trim(),
        targetPosition: generateForm.targetPosition || null,
        sourceType: 'upload',
        entryIds: [...generateForm.entryIds],
        sourceResumeId: composeSourceResumeId.value
      })
      ElMessage.success('已用当前能力库合成新版本')
      generateVisible.value = false
      page.value = 1
      await load()
      return
    }
    const { taskId } = await resumeApi.generate({ entryIds: generateForm.entryIds, jdText: null })
    const task = await pollTask(taskApi, taskId)
    if (task.status !== 'SUCCESS') throw new Error(task.failReason || '生成失败')
    draftTask.value = task
    previewText.value = task.resultData?.contentMd || ''
    previewMode.value = 'draft'
    previewVisible.value = true
    generateVisible.value = false
  } catch (error) {
    if (error.message) ElMessage.error(error.message)
  } finally { generating.value = false }
}
async function saveDraft() {
  savingVersion.value = true
  try {
    await resumeApi.save({
      name: generateForm.name,
      targetPosition: generateForm.targetPosition || null,
      sourceType: 'generate',
      draftTaskId: draftTask.value.taskId
    })
    ElMessage.success('简历版本已保存')
    previewVisible.value = false
    await load()
  } finally { savingVersion.value = false }
}
function viewResume(resume) {
  previewResume.value = resume
  previewText.value = resume.contentMd || ''
  previewMode.value = 'saved'
  previewVisible.value = true
}
async function openSynthesize(resume) {
  composeMode.value = 'upload'
  composeSourceResumeId.value = resume.id
  generateForm.name = `${resume.name} · 条目合成版`
  generateForm.targetPosition = resume.targetPosition || ''
  const data = await abilityApi.list({ status: 'confirmed', enabled: 1, page: 1, size: 100 })
  availableEntries.value = data.records
  generateForm.entryIds = data.records.map((entry) => entry.id)
  generateVisible.value = true
}
async function remove(resume) {
  await ElMessageBox.confirm(`删除简历版本“${resume.name}”？`, '删除版本', { type: 'warning' })
  await resumeApi.remove(resume.id)
  ElMessage.success('已删除')
  await load()
}
onMounted(load)
</script>

<style scoped>
.resume-layout { display: grid; grid-template-columns: minmax(0, 1fr) 300px; gap: 20px; align-items: start; }
.resume-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 17px; }
.resume-card { padding: 21px; border-top: 4px solid var(--brand); min-height: 230px; display: flex; flex-direction: column; }
.paper-head { display: flex; align-items: center; justify-content: space-between; gap: 10px; }
.source-chip { padding: 4px 8px; border-radius: 6px; background: var(--brand-soft); color: var(--brand); font-size: 10px; font-weight: 700; }
.source-chip.generate { background: #e9f8f3; color: var(--success); }
.source-chip.diagnose { background: #fff4e8; color: #bc741a; }
.status-dot { font-size: 10px; color: var(--muted); }
.status-dot.ready { color: var(--success); }
.status-dot.failed { color: var(--danger); }
.resume-card h3 { margin: 16px 0 7px; font-size: 17px; line-height: 1.5; }
.target { color: var(--muted); font-size: 12px; margin: 0; flex: 1; }
.resume-stats { display: flex; gap: 22px; margin: 18px 0 12px; }
.resume-stats div { display: flex; flex-direction: column; gap: 3px; }
.resume-stats strong { font-size: 16px; }
.resume-stats span { color: var(--muted); font-size: 10px; }
.time { color: #9aa3b9; font-size: 10px; margin: 0 0 13px; }
.resume-actions { display: flex; align-items: center; gap: 7px; border-top: 1px solid #f0f2f8; padding-top: 13px; }
.delete-link { margin-left: auto; border: 0; background: transparent; color: #a3aabc; font-size: 11px; cursor: pointer; }
.delete-link:hover { color: var(--danger); }
.side-stack { display: flex; flex-direction: column; gap: 17px; }
.guide-card h3 { margin: 15px 0 12px; font-size: 17px; }
.guide-card ol { padding-left: 18px; color: #525c73; font-size: 12px; line-height: 1.9; }
.side-title { display: flex; justify-content: space-between; align-items: center; }
.side-title h3 { margin: 0; font-size: 15px; }
.side-title span { font-size: 10px; color: var(--muted); }
.side-metric { display: flex; justify-content: space-between; padding: 11px 0; border-bottom: 1px solid #f0f2f8; font-size: 12px; color: var(--muted); }
.side-metric:last-child { border-bottom: 0; }
.side-metric strong { color: var(--ink); }
.pager { display: flex; justify-content: center; margin-top: 24px; }
.upload-tip { background: #f6f8fd; border-radius: 10px; padding: 12px 14px; color: var(--muted); font-size: 12px; line-height: 1.7; margin-bottom: 15px; }
.upload-symbol { font-size: 32px; color: var(--brand); }
.form-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 15px; }
.select-head { display: flex; justify-content: space-between; align-items: center; margin: 6px 0 10px; }
.select-head strong { font-size: 13px; }
.select-head span { color: var(--muted); font-size: 11px; }
.entry-selector { max-height: 320px; overflow: auto; border: 1px solid var(--line); border-radius: 12px; padding: 8px; }
.entry-option { display: flex; align-items: flex-start; gap: 9px; padding: 10px; border-radius: 9px; cursor: pointer; }
.entry-option:hover { background: #f7f9ff; }
.entry-option.selected { background: #f1f5ff; }
.entry-option div { display: flex; flex-direction: column; gap: 4px; }
.entry-option strong { font-size: 12px; }
.entry-option span { color: var(--muted); font-size: 10px; }
.preview-meta { display: flex; justify-content: space-between; color: var(--muted); font-size: 11px; border-bottom: 1px solid var(--line); padding-bottom: 11px; margin-bottom: 16px; }
.preview-box { max-height: 62vh; overflow: auto; background: #fbfcfe; border-radius: 12px; padding: 24px; }
@media (max-width: 1100px) { .resume-layout { grid-template-columns: 1fr; } .side-stack { display: grid; grid-template-columns: 1fr 1fr; } }
@media (max-width: 720px) { .resume-grid, .side-stack, .form-grid { grid-template-columns: 1fr; } }
</style>
