<template>
  <div class="page-container">
    <div class="page-header">
      <h2 class="page-title">我的成绩</h2>
      <el-button type="primary" class="ai-plan-btn" @click="openImprovementPlan" :loading="planLoading">
        <el-icon><MagicStick /></el-icon>
        AI个性化改进计划
      </el-button>
    </div>
    
    <div class="search-form">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="全部课程" clearable style="width: 220px">
            <el-option v-for="c in myCourses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>
    
    <div class="stats-container">
      <el-row :gutter="16">
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ stats.total }}</div>
            <div class="stat-label">提交报告数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ stats.evaluated }}</div>
            <div class="stat-label">已评价数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ stats.pending }}</div>
            <div class="stat-label">待评价数</div>
          </div>
        </el-col>
        <el-col :span="6">
          <div class="stat-card">
            <div class="stat-value">{{ stats.avgScore || '-' }}</div>
            <div class="stat-label">平均成绩</div>
          </div>
        </el-col>
      </el-row>
    </div>
    
    <div class="table-container">
      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="courseName" label="课程" min-width="120" />
        <el-table-column prop="title" label="报告标题" min-width="150" />
        <el-table-column prop="createTime" label="提交时间" width="160">
          <template #default="{ row }">
            {{ formatTime(row.createTime) }}
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100">
          <template #default="{ row }">
            <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">
              {{ row.status === 1 ? '已评价' : '待评价' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="成绩" width="300">
          <template #default="{ row }">
            <div v-if="row.status === 1" class="score-container">
              <div class="score-item">
                <span class="score-label">完整性</span>
                <el-tag type="info" size="small">{{ row.completenessScore ?? '-' }}</el-tag>
              </div>
              <div class="score-item">
                <span class="score-label">规范性</span>
                <el-tag type="info" size="small">{{ row.specificationScore ?? '-' }}</el-tag>
              </div>
              <div class="score-item">
                <span class="score-label">专业性</span>
                <el-tag type="info" size="small">{{ row.knowledgeScore ?? '-' }}</el-tag>
              </div>
              <div class="score-item">
                <span class="score-label">总分</span>
                <el-tag :type="getScoreType(row.totalScore)" size="small">{{ row.totalScore ?? '-' }}</el-tag>
              </div>
            </div>
            <span v-else class="pending-text">待教师评价</span>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="100" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" link @click="handleViewDetail(row)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <div class="pagination-container">
        <el-pagination
          v-model:current-page="pagination.pageNum"
          v-model:page-size="pagination.pageSize"
          :total="pagination.total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next"
          @size-change="loadData"
          @current-change="loadData"
        />
      </div>
    </div>

    <el-dialog v-model="detailDialogVisible" title="报告详情" width="700px">
      <div v-if="currentReport">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="课程名称">{{ currentReport.courseName }}</el-descriptions-item>
          <el-descriptions-item label="报告标题">{{ currentReport.title }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentReport.createTime }}</el-descriptions-item>
          <el-descriptions-item label="评价状态">
            <el-tag :type="currentReport.status === 1 ? 'success' : 'warning'" size="small">
              {{ currentReport.status === 1 ? '已评价' : '待评价' }}
            </el-tag>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="currentReport.status === 1" class="evaluation-section">
          <h4>成绩详情</h4>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="score-detail-card">
                <div class="score-detail-label">完整性</div>
                <div class="score-detail-value">{{ currentReport.completenessScore ?? '-' }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-detail-card">
                <div class="score-detail-label">规范性</div>
                <div class="score-detail-value">{{ currentReport.specificationScore ?? '-' }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-detail-card">
                <div class="score-detail-label">专业性</div>
                <div class="score-detail-value">{{ currentReport.knowledgeScore ?? '-' }}</div>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-detail-card highlight">
                <div class="score-detail-label">总分</div>
                <div class="score-detail-value">{{ currentReport.totalScore ?? '-' }}</div>
              </div>
            </el-col>
          </el-row>
          
          <div v-if="currentReport.aiEvaluation" class="evaluation-content">
            <h4>AI评价</h4>
            <div class="content-box">{{ currentReport.aiEvaluation }}</div>
          </div>
          
          <div v-if="currentReport.manualEvaluation" class="evaluation-content">
            <h4>教师评语</h4>
            <div class="content-box">{{ currentReport.manualEvaluation }}</div>
          </div>
          
          <div class="evaluation-time">
            评价时间：{{ currentReport.evaluateTime || '-' }}
          </div>
        </div>
        
        <el-empty v-else description="该报告尚未被评价，请耐心等待" />
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- AI个性化改进计划弹窗 -->
    <el-dialog v-model="planDialogVisible" title="AI个性化改进计划" width="900px" top="2vh" class="improvement-dialog">
      <div v-loading="planGenerating" element-loading-text="AI正在分析你的学习数据，生成个性化改进计划...">
        <!-- 错误提示 -->
        <el-alert v-if="planError" :title="planError" type="error" show-icon :closable="false" />

        <!-- 改进计划内容 -->
        <div v-if="planData && !planError" class="improvement-content">
          <!-- 概览 -->
          <div class="plan-section overview-section">
            <h3 class="section-title">
              <el-icon><DataAnalysis /></el-icon>
              学习画像总览
            </h3>
            <div class="stats-mini">
              <el-tag type="info" size="large">共 {{ planData.totalReports || 0 }} 份报告</el-tag>
              <el-tag type="success" size="large">{{ planData.evaluatedReports || 0 }} 份已评价</el-tag>
            </div>
            <p class="overview-text">{{ planData.overview }}</p>
          </div>

          <!-- 薄弱项分析 -->
          <div v-if="planData.weakPoints && planData.weakPoints.length > 0" class="plan-section">
            <h3 class="section-title">
              <el-icon><WarningFilled /></el-icon>
              薄弱项诊断
            </h3>
            <div class="weakpoint-list">
              <div v-for="(wp, idx) in planData.weakPoints" :key="idx" class="weakpoint-card" :class="'severity-' + (wp.severity || '中')">
                <div class="wp-header">
                  <span class="wp-name">{{ wp.dimension }}</span>
                  <el-tag :type="severityType(wp.severity)" size="small" effect="dark">
                    {{ wp.severity || '中' }}风险
                  </el-tag>
                  <el-tag v-if="wp.trend" type="info" size="small" effect="plain" class="wp-trend">
                    {{ wp.trend }}
                  </el-tag>
                </div>
                <div v-if="wp.evidence" class="wp-evidence">
                  <strong>数据证据：</strong>{{ wp.evidence }}
                </div>
                <div v-if="wp.rootCause" class="wp-cause">
                  <strong>根因分析：</strong>{{ wp.rootCause }}
                </div>
              </div>
            </div>
          </div>

          <!-- 改进计划 -->
          <div v-if="planData.improvementPlan" class="plan-section">
            <h3 class="section-title">
              <el-icon><TrophyBase /></el-icon>
              分阶段改进计划
            </h3>
            <p class="plan-summary">{{ planData.improvementPlan.summary }}</p>
            <el-timeline>
              <el-timeline-item
                v-for="stage in planData.improvementPlan.stages"
                :key="stage.stage"
                :timestamp="'阶段 ' + stage.stage + ' · ' + (stage.duration || '')"
                placement="top"
                :color="stageColor(stage.stage)"
                :hollow="true"
              >
                <el-card shadow="hover">
                  <h4 class="stage-title">{{ stage.title }}</h4>
                  <div v-if="stage.goals && stage.goals.length > 0" class="stage-goals">
                    <strong>🎯 目标：</strong>
                    <ul>
                      <li v-for="g in stage.goals" :key="g">{{ g }}</li>
                    </ul>
                  </div>
                  <div v-if="stage.actions && stage.actions.length > 0" class="stage-actions">
                    <strong>📋 行动项：</strong>
                    <ul>
                      <li v-for="a in stage.actions" :key="a">{{ a }}</li>
                    </ul>
                  </div>
                  <div v-if="stage.expectedOutcome" class="stage-outcome">
                    <strong>📈 预期成果：</strong>{{ stage.expectedOutcome }}
                  </div>
                </el-card>
              </el-timeline-item>
            </el-timeline>
          </div>

          <!-- 学习资源推荐 -->
          <div v-if="planData.resources && planData.resources.length > 0" class="plan-section">
            <h3 class="section-title">
              <el-icon><Collection /></el-icon>
              推荐学习资源
            </h3>
            <div class="resource-list">
              <div v-for="(res, idx) in planData.resources" :key="idx" class="resource-card">
                <div class="res-header">
                  <el-tag :type="resourceTypeColor(res.type)" size="small">{{ res.type }}</el-tag>
                  <span class="res-title">{{ res.title }}</span>
                </div>
                <p v-if="res.description" class="res-desc">{{ res.description }}</p>
                <div class="res-meta">
                  <span v-if="res.targetWeakPoint" class="res-target">
                    <el-icon><Connection /></el-icon> 针对：{{ res.targetWeakPoint }}
                  </span>
                  <span v-if="res.recommendReason" class="res-reason">
                    <el-icon><Star /></el-icon> {{ res.recommendReason }}
                  </span>
                </div>
                <el-button v-if="res.url" type="primary" link size="small" @click="openResource(res.url)">
                  查看资源 →
                </el-button>
              </div>
            </div>
          </div>

          <!-- 鼓励语 -->
          <div v-if="planData.motivation" class="plan-section motivation-section">
            <p class="motivation-text">💪 {{ planData.motivation }}</p>
          </div>

          <!-- 生成时间 -->
          <div v-if="planData.generatedAt" class="generated-time">
            计划生成时间：{{ planData.generatedAt }}
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="planDialogVisible = false">关闭</el-button>
        <el-button v-if="planData" type="primary" @click="regeneratePlan" :loading="planGenerating">
          重新生成
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted, computed } from 'vue'
import { getMyScores } from '../api/report'
import { getMyCourses } from '../api/course'
import { generateImprovementPlan } from '../api/evaluation'
import { ElMessage } from 'element-plus'
import { MagicStick, DataAnalysis, WarningFilled, TrophyBase, Collection, Connection, Star } from '@element-plus/icons-vue'

const loading = ref(false)
const tableData = ref([])
const myCourses = ref([])
const detailDialogVisible = ref(false)
const currentReport = ref(null)

const searchForm = reactive({
  courseId: null
})

const pagination = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0
})

const stats = computed(() => {
  const evaluated = tableData.value.filter(item => item.status === 1).length
  const total = tableData.value.length
  const pending = total - evaluated
  const evaluatedItems = tableData.value.filter(item => item.status === 1 && item.totalScore != null)
  const avgScore = evaluatedItems.length > 0 
    ? (evaluatedItems.reduce((sum, item) => sum + (item.totalScore || 0), 0) / evaluatedItems.length).toFixed(1)
    : null
  
  return {
    total: pagination.total,
    evaluated,
    pending,
    avgScore
  }
})

const formatTime = (timeStr) => {
  if (!timeStr) return '-'
  const date = new Date(timeStr)
  const month = String(date.getMonth() + 1).padStart(2, '0')
  const day = String(date.getDate()).padStart(2, '0')
  const hours = String(date.getHours()).padStart(2, '0')
  const minutes = String(date.getMinutes()).padStart(2, '0')
  const seconds = String(date.getSeconds()).padStart(2, '0')
  return `${month}-${day} ${hours}:${minutes}:${seconds}`
}

const getScoreType = (score) => {
  if (score == null) return 'info'
  if (score >= 90) return 'success'
  if (score >= 80) return ''
  if (score >= 70) return 'warning'
  return 'danger'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getMyScores({ ...pagination, courseId: searchForm.courseId })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const loadMyCourses = async () => {
  try {
    const res = await getMyCourses()
    myCourses.value = res.data || []
  } catch (error) {
    console.error(error)
  }
}

const handleSearch = () => {
  pagination.pageNum = 1
  loadData()
}

const handleReset = () => {
  searchForm.courseId = null
  handleSearch()
}

const handleViewDetail = (row) => {
  currentReport.value = row
  detailDialogVisible.value = true
}

// ==================== AI个性化改进计划 ====================
const planDialogVisible = ref(false)
const planLoading = ref(false)
const planGenerating = ref(false)
const planData = ref(null)
const planError = ref('')

const openImprovementPlan = async () => {
  planDialogVisible.value = true
  planError.value = ''
  if (!planData.value) {
    await loadImprovementPlan()
  }
}

const loadImprovementPlan = async () => {
  planGenerating.value = true
  planError.value = ''
  planData.value = null
  try {
    const res = await generateImprovementPlan()
    if (res.data && res.code === 200) {
      planData.value = res.data
    } else {
      planError.value = res.message || '生成改进计划失败'
    }
  } catch (error) {
    console.error(error)
    planError.value = error?.response?.data?.message || '生成改进计划失败，请确认已有被评价的报告后重试'
  } finally {
    planGenerating.value = false
  }
}

const regeneratePlan = () => {
  loadImprovementPlan()
}

const severityType = (severity) => {
  if (!severity) return 'warning'
  if (severity.includes('高')) return 'danger'
  if (severity.includes('中')) return 'warning'
  return 'info'
}

const stageColor = (stage) => {
  const colors = ['#409eff', '#67c23a', '#e6a23c', '#f56c6c']
  return colors[(stage - 1) % colors.length]
}

const resourceTypeColor = (type) => {
  if (!type) return 'info'
  if (type.includes('书籍')) return 'success'
  if (type.includes('课程')) return 'warning'
  if (type.includes('工具')) return ''
  if (type.includes('文章')) return 'info'
  if (type.includes('视频')) return 'danger'
  return 'info'
}

const openResource = (url) => {
  if (url && url !== 'null') {
    window.open(url, '_blank')
  }
}

onMounted(() => {
  loadData()
  loadMyCourses()
})
</script>

<style scoped>
.page-container {
  padding: 20px;
}

.page-title {
  margin-bottom: 20px;
  font-size: 20px;
  color: #303133;
}

.search-form {
  margin-bottom: 20px;
  padding: 16px;
  background: #fff;
  border-radius: 4px;
}

.stats-container {
  margin-bottom: 20px;
}

.stat-card {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  padding: 20px;
  border-radius: 8px;
  text-align: center;
  color: #fff;
}

.stat-card:nth-child(2) {
  background: linear-gradient(135deg, #11998e 0%, #38ef7d 100%);
}

.stat-card:nth-child(3) {
  background: linear-gradient(135deg, #f093fb 0%, #f5576c 100%);
}

.stat-card:nth-child(4) {
  background: linear-gradient(135deg, #4facfe 0%, #00f2fe 100%);
}

.stat-value {
  font-size: 32px;
  font-weight: bold;
}

.stat-label {
  font-size: 14px;
  margin-top: 8px;
  opacity: 0.9;
}

.table-container {
  background: #fff;
  padding: 16px;
  border-radius: 4px;
}

.score-container {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}

.score-item {
  display: flex;
  align-items: center;
  gap: 4px;
}

.score-label {
  font-size: 12px;
  color: #909399;
}

.pending-text {
  color: #909399;
  font-size: 14px;
}

.evaluation-section {
  margin-top: 20px;
}

.evaluation-section h4 {
  margin: 16px 0 12px;
  color: #303133;
  font-size: 16px;
}

.score-detail-card {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  text-align: center;
}

.score-detail-card.highlight {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  color: #fff;
}

.score-detail-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 8px;
}

.score-detail-card.highlight .score-detail-label {
  color: rgba(255, 255, 255, 0.8);
}

.score-detail-value {
  font-size: 24px;
  font-weight: bold;
}

.evaluation-content {
  margin-top: 16px;
}

.content-box {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 4px;
  line-height: 1.8;
  white-space: pre-wrap;
}

.evaluation-time {
  margin-top: 16px;
  text-align: right;
  color: #909399;
  font-size: 12px;
}

.pagination-container {
  margin-top: 16px;
  display: flex;
  justify-content: flex-end;
}

/* ========== AI改进计划样式 ========== */
.page-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}

.page-header .page-title {
  margin-bottom: 0;
}

.ai-plan-btn {
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  font-weight: 600;
  letter-spacing: 1px;
  transition: all 0.3s;
}

.ai-plan-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 15px rgba(102, 126, 234, 0.5);
}

.improvement-dialog :deep(.el-dialog__body) {
  max-height: 70vh;
  overflow-y: auto;
  padding: 20px 30px;
}

.improvement-content {
  padding: 0;
}

.plan-section {
  margin-bottom: 28px;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  color: #303133;
  margin: 0 0 16px 0;
  padding-bottom: 8px;
  border-bottom: 2px solid #ebeef5;
}

.section-title .el-icon {
  color: #667eea;
  font-size: 20px;
}

/* 概览区 */
.overview-section .stats-mini {
  display: flex;
  gap: 12px;
  margin-bottom: 12px;
}

.overview-text {
  color: #606266;
  line-height: 1.8;
  font-size: 15px;
  background: #f0f5ff;
  padding: 16px;
  border-radius: 8px;
  border-left: 4px solid #667eea;
}

/* 薄弱项卡片 */
.weakpoint-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.weakpoint-card {
  padding: 14px 18px;
  border-radius: 8px;
  border: 1px solid #e4e7ed;
  background: #fff;
  transition: all 0.2s;
}

.weakpoint-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.08);
}

.weakpoint-card.severity-高 {
  border-left: 4px solid #f56c6c;
  background: #fef0f0;
}

.weakpoint-card.severity-中 {
  border-left: 4px solid #e6a23c;
  background: #fdf6ec;
}

.weakpoint-card.severity-低 {
  border-left: 4px solid #67c23a;
  background: #f0f9eb;
}

.wp-header {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.wp-name {
  font-weight: 700;
  font-size: 15px;
  color: #303133;
}

.wp-trend {
  font-size: 12px;
}

.wp-evidence, .wp-cause {
  font-size: 13px;
  color: #606266;
  margin-top: 6px;
  line-height: 1.6;
}

/* 改进计划时间线 */
.plan-summary {
  color: #606266;
  line-height: 1.7;
  margin-bottom: 16px;
  font-size: 14px;
  background: #f5f7fa;
  padding: 12px 16px;
  border-radius: 6px;
}

.stage-title {
  margin: 0 0 10px 0;
  font-size: 16px;
  color: #303133;
}

.stage-goals, .stage-actions, .stage-outcome {
  margin-top: 8px;
  font-size: 13px;
  color: #606266;
}

.stage-goals ul, .stage-actions ul {
  margin: 4px 0 0 16px;
  padding: 0;
}

.stage-goals li, .stage-actions li {
  line-height: 1.8;
}

/* 资源推荐 */
.resource-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 12px;
}

.resource-card {
  padding: 14px 16px;
  border: 1px solid #e4e7ed;
  border-radius: 8px;
  background: #fff;
  transition: all 0.2s;
}

.resource-card:hover {
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.06);
  border-color: #667eea;
}

.res-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
}

.res-title {
  font-weight: 600;
  font-size: 14px;
  color: #303133;
}

.res-desc {
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  margin: 0 0 8px 0;
}

.res-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  font-size: 12px;
  color: #909399;
}

.res-target, .res-reason {
  display: flex;
  align-items: center;
  gap: 4px;
}

/* 鼓励语 */
.motivation-section {
  background: linear-gradient(135deg, #667eea15 0%, #764ba215 100%);
  border-radius: 8px;
  padding: 20px;
  text-align: center;
}

.motivation-text {
  font-size: 15px;
  color: #303133;
  line-height: 1.8;
  margin: 0;
  font-weight: 500;
}

/* 生成时间 */
.generated-time {
  text-align: right;
  color: #c0c4cc;
  font-size: 12px;
  margin-top: 8px;
}
</style>