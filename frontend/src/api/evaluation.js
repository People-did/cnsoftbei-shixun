import request from '../utils/request'

export function getEvaluationList(params) {
  return request.get('/evaluation/list', { params })
}

export function getEvaluation(id) {
  return request.get(`/evaluation/${id}`)
}

export function getEvaluationByReport(reportId) {
  return request.get(`/evaluation/report/${reportId}`)
}

export function aiEvaluate(reportId) {
  return request.post(`/evaluation/ai/${reportId}`)
}

/**
 * 【全新】全格式AI智能评价 — 支持文档+截图+代码+ZIP混合成果
 * @param {number} reportId - 报告ID
 * @param {object} params - { rubricJson: string|null }
 */
export function aiFullEvaluate(reportId, params) {
  return request.post(`/evaluation/ai/full/${reportId}`, params || {})
}

/**
 * 【全新】自动生成评分标准
 * @param {number} reportId - 报告ID
 */
export function autoGenerateRubric(reportId) {
  return request.post(`/evaluation/rubric/auto-generate/${reportId}`)
}

/**
 * 修改/手动保存评价评分
 * 升级版：无损兼容传统三大指标修改与多维度自定义动态指标修改的数据输送
 */
export function manualEvaluate(id, data) {
  return request.put(`/evaluation/${id}`, data)
}

/**
 * 删除评价记录
 */
export function deleteEvaluation(id) {
  return request.delete(`/evaluation/${id}`)
}

/**
 * 【全新】AI对话式反馈 — 学生就评分结果与AI进行多轮对话
 * @param {number} reportId - 报告ID
 * @param {Array} conversationHistory - 对话历史 [{role:'user'|'assistant', content:''}, ...]
 * @returns {Promise} { reply: string, deductionPoints: string }
 */
export function chatFeedback(reportId, conversationHistory) {
  return request.post(`/evaluation/chat/${reportId}`, {
    conversationHistory: conversationHistory || []
  })
}

/**
 * 【全新】AI个性化改进计划 — 根据学生所有已评价报告，分析薄弱项并生成改进计划
 * @returns {Promise} { overview, weakPoints, improvementPlan, resources, motivation }
 */
export function generateImprovementPlan() {
  return request.get('/evaluation/improvement-plan')
}

/**
 * 【全新】AI智能批注 — 对报告原文逐段分析，精确定位问题并生成批注
 * @param {number} reportId - 报告ID
 * @returns {Promise} 批注列表 [{id, reportId, startPos, endPos, highlightedText, comment, severity, category, suggestion, createTime}]
 */
export function generateAnnotations(reportId) {
  return request.post(`/evaluation/annotate/${reportId}`)
}

/**
 * 获取某报告的所有AI批注
 * @param {number} reportId - 报告ID
 * @returns {Promise} 批注列表
 */
export function getAnnotations(reportId) {
  return request.get(`/evaluation/annotations/${reportId}`)
}

/**
 * 删除某报告的所有AI批注
 * @param {number} reportId - 报告ID
 */
export function deleteAnnotations(reportId) {
  return request.delete(`/evaluation/annotations/${reportId}`)
}