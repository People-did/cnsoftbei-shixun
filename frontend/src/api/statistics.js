import request from '../utils/request'

export function getClassStatistics(classId) {
  return request.get(`/statistics/class/${classId}`)
}

export function getAllClassesStatistics() {
  return request.get('/statistics/all')
}

export function exportScores(classId) {
  return request.get(`/statistics/export/${classId}`, { responseType: 'blob' })
}

/**
 * 【全新】学生多维度能力雷达图 — 获取学生五维能力画像数据
 * @returns {Promise} { dimensions: [{name, score, fullMark}], totalReports, evaluatedReports, studentName }
 */
export function getStudentRadar() {
  return request.get('/statistics/radar')
}
