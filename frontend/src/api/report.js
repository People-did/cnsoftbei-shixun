import request from '../utils/request'

export function getReportList(params) {
  return request.get('/report/list', { params })
}

export function getReport(id) {
  return request.get(`/report/${id}`)
}

export function uploadReport(data) {
  return request.post('/report/upload', data)
}

/**
 * 【全新】多文件批量上传 — 支持文档+截图+代码+ZIP混合上传
 */
export function uploadMultipleReports(data) {
  return request.post('/report/upload/multi', data)
}

/**
 * 获取学生在某个作业下的已有提交
 */
export function getMySubmission(requirementId) {
  return request.get('/report/my-submission', { params: { requirementId } })
}

/**
 * 【全新】教师端：获取某作业下所有学生的提交列表
 */
export function getSubmissionsByRequirement(requirementId) {
  return request.get(`/report/submissions/${requirementId}`)
}

/**
 * 更新报告（学生编辑已提交的报告）
 */
export function updateReport(id, data) {
  return request.put(`/report/${id}`, data)
}

export function deleteReport(id) {
  return request.delete(`/report/${id}`)
}

export function downloadReport(id) {
  return request.get(`/report/download/${id}`, { responseType: 'blob' })
}

// 获取学生自己的成绩列表
export function getMyScores(params) {
  return request.get('/report/my-scores', { params })
}
