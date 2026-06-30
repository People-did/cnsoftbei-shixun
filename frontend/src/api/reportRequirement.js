import request from '../utils/request'

export function getReportRequirementList(params) {
  return request.get('/report-requirement/list', { params })
}

export function getReportRequirement(id) {
  return request.get(`/report-requirement/${id}`)
}

/**
 * 创建报告要求
 * 升级版：配合后端入参接收 CustomRequirementDTO 结构，支持级联发送自定义指标细则
 */
export function createReportRequirement(data) {
  return request.post('/report-requirement', data)
}

export function updateReportRequirement(data) {
  return request.put('/report-requirement', data)
}

export function deleteReportRequirement(id) {
  return request.delete(`/report-requirement/${id}`)
}

export function updateRequirementStatus(id, status) {
  return request.put(`/report-requirement/status/${id}?status=${status}`)
}

export function getCourseRequirements(courseId) {
  return request.get(`/report-requirement/course/${courseId}`)
}

/**
 * 【硬核创新接口】上传教学大纲/标准文档文件流，一键调用大模型智能解构出评分指标、权重与描述
 * @param {File} file 老师上传的 PDF 或 Word 文件
 */
export function parseCriterionPdf(file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post('/report-requirement/parse-pdf', formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 为已存在的报告要求上传附件文档
 * @param {number} id - 报告要求ID
 * @param {File} file - 要上传的文件
 * @returns {Promise} { filePath, fileName }
 */
export function uploadRequirementDocument(id, file) {
  const formData = new FormData()
  formData.append('file', file)
  return request.post(`/report-requirement/${id}/upload-document`, formData, {
    headers: {
      'Content-Type': 'multipart/form-data'
    }
  })
}

/**
 * 获取报告要求的自定义评分标准明细
 * @param {number} id - 报告要求ID
 * @returns {Promise} 评价标准列表
 */
export function getRequirementCriteria(id) {
  return request.get(`/report-requirement/${id}/criteria`)
}

/**
 * 下载报告要求附件文档
 * @param {number} id - 报告要求ID
 */
export function downloadRequirementDocument(id) {
  window.open(`/api/report-requirement/download-document/${id}`, '_blank')
}

export function exportWord(id) {
  window.open(`/api/report-requirement/export/word/${id}`, '_blank')
}

export function exportPdf(id) {
  window.open(`/api/report-requirement/export/pdf/${id}`, '_blank')
}