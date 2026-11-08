/**
 * 轮询异步任务直到 QUEUED/RUNNING 之外的终态。
 * 超时后返回最后一次结果，由页面展示失败或继续手动刷新。
 */
export async function pollTask(taskApi, taskId, options = {}) {
  const interval = options.interval || 1200
  const maxCount = options.maxCount || 100
  for (let count = 0; count < maxCount; count++) {
    const task = await taskApi.get(taskId)
    if (task.status !== 'QUEUED' && task.status !== 'RUNNING') {
      return task
    }
    await new Promise((resolve) => setTimeout(resolve, interval))
  }
  throw new Error('任务处理超时，请稍后刷新')
}
