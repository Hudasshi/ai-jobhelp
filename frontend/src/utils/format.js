export const statusLabels = {
  pending: '待投递',
  applied: '已投递',
  exam: '笔试',
  interview: '面试',
  offer: 'Offer',
  rejected: '已结束'
}

export const nextStatuses = {
  pending: ['applied', 'rejected'],
  applied: ['exam', 'rejected'],
  exam: ['interview', 'rejected'],
  interview: ['exam', 'offer', 'rejected'],
  offer: [],
  rejected: []
}

export const abilityTypeLabels = {
  education: '教育经历',
  internship: '实习经历',
  project: '项目经历',
  skill: '技能',
  certificate: '证书',
  other: '其他'
}

export function shortDate(value) {
  return value ? String(value).slice(0, 10) : '—'
}

export function percent(value) {
  return value == null ? '暂无' : `${value}%`
}
