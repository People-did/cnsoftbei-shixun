<template>
  <div class="page-container">
    <h2 class="page-title">评价查看</h2>
    <p class="page-desc">以下是你提交的实训报告所获得的评价，仅供查看。</p>
    
    <div class="table-container">
      <el-table :data="tableData" stripe v-loading="loading">
        <el-table-column prop="studentName" label="学生" />
        <el-table-column prop="className" label="班级" />
        <el-table-column prop="reportTitle" label="报告" show-overflow-tooltip />
        <el-table-column label="完整性 (旧)" prop="completenessScore" width="100">
          <template #default="{ row }">
            <span>{{ row.dynamicScoresJson ? '-' : row.completenessScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="规范性 (旧)" prop="specificationScore" width="100">
          <template #default="{ row }">
            <span>{{ row.dynamicScoresJson ? '-' : row.specificationScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="知识点 (旧)" prop="knowledgeScore" width="100">
          <template #default="{ row }">
            <span>{{ row.dynamicScoresJson ? '-' : row.knowledgeScore }}</span>
          </template>
        </el-table-column>
        <el-table-column label="综合总分" prop="totalScore" width="100">
          <template #default="{ row }">
            <el-tag :type="getScoreType(row.totalScore)">{{ row.totalScore }}分</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="评价方式" width="100">
          <template #default="{ row }">
            <el-tag :type="row.isAi === 1 ? 'primary' : 'info'">
              {{ row.isAi === 1 ? 'AI智能批改' : '人工评定' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="evaluateTime" label="评价时间" width="180" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button type="primary" size="small" plain icon="View" @click="handleView(row)">查看详情</el-button>
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
    
    <el-dialog v-model="detailVisible" title="报告评价详情" width="750px">
      <div v-if="currentEval">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生">{{ currentEval.studentName }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ currentEval.className }}</el-descriptions-item>
          <el-descriptions-item label="报告标题" :span="2">{{ currentEval.reportTitle }}</el-descriptions-item>
          <el-descriptions-item label="评价人/教师">{{ currentEval.teacherName || '大模型智能引擎' }}</el-descriptions-item>
          <el-descriptions-item label="评价方式">
            <el-tag :type="currentEval.isAi === 1 ? 'primary' : 'info'">
              {{ currentEval.isAi === 1 ? 'AI智能批改' : '人工评定' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最终考核总分" :span="2">
            <b style="font-size: 18px; color: #409eff;">{{ currentEval.totalScore }} 分</b>
          </el-descriptions-item>
        </el-descriptions>
        
        <div v-if="parsedDynamicScores" class="score-section">
          <h4 style="color: #e6a23c;"><el-icon><Compass /></el-icon> 教师定制化指标考核分布明细</h4>
          <div style="background: #fafafa; border: 1px solid #e4e7ed; border-radius: 6px; padding: 16px;">
            <div v-for="(val, key) in parsedDynamicScores" :key="key" class="dynamic-score-card">
              <div class="dynamic-score-meta">
                <span class="dynamic-key-name">{{ key }}</span>
                <span class="dynamic-key-val">得分：<b>{{ val.score }}</b> 分</span>
              </div>
              <el-progress 
                :percentage="calculatePercentage(val.score, key)" 
                :status="val.score >= 60 ? 'success' : 'exception'"
                :stroke-width="12" 
              />
              <p class="dynamic-key-comment" v-if="val.comment">
                <el-icon><ChatLineSquare /></el-icon> 维度评语：{{ val.comment }}
              </p>
            </div>
          </div>
        </div>

        <div v-else class="score-section">
          <h4>评分详情 (系统默认标准)</h4>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="score-item">
                <p class="label">完整性 (30分)</p>
                <p class="value">{{ currentEval.completenessScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">规范性 (30分)</p>
                <p class="value">{{ currentEval.specificationScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">知识点 (40分)</p>
                <p class="value">{{ currentEval.knowledgeScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item total">
                <p class="label">综合总分</p>
                <p class="value">{{ currentEval.totalScore }}</p>
              </div>
            </el-col>
          </el-row>
        </div>
        
        <div v-if="parsedSuggestions && parsedSuggestions.length" class="evaluation-content" style="background: #f0f9eb; border: 1px solid #c2e7b0;">
          <h5 style="color: #67c23a; font-weight: bold; margin: 0 0 8px 0;"><el-icon><Opportunity /></el-icon> AI 赋能实训改进建议列表</h5>
          <ul style="margin: 0; padding-left: 20px; color: #606266; font-size: 13px; line-height: 1.8;">
            <li v-for="(sug, sIdx) in parsedSuggestions" :key="sIdx">{{ sug }}</li>
          </ul>
        </div>
        
        <div v-if="currentEval.manualEvaluation" class="evaluation-content">
          <h5>教师终审定性备注</h5>
          <p style="margin: 0; font-size: 13px; color: #303133;">{{ currentEval.manualEvaluation }}</p>
        </div>

        <!-- AI智能批注入口 -->
        <div style="margin-top: 16px; text-align: center;">
          <el-button type="primary" @click="openAnnotationViewer">
            <el-icon style="margin-right: 4px;"><View /></el-icon>
            查看AI批注
          </el-button>
          <span style="margin-left: 12px; font-size: 12px; color: #909399;">
            查看AI对报告原文的逐段批注和修改建议
          </span>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- AI智能批注查看弹窗 -->
    <el-dialog
      v-model="annotationDialogVisible"
      title="AI智能批注 - 原文标注"
      width="90%"
      top="3vh"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <div v-loading="annotationLoading" style="min-height: 500px;">
        <div v-if="annotationError" style="text-align: center; padding: 60px;">
          <el-empty :description="annotationError" />
        </div>
        <AnnotationViewer
          v-else-if="!annotationLoading && annotationData.length !== undefined"
          :report-content="annotationReportContent"
          :annotations="annotationData"
        />
      </div>
      <template #footer>
        <el-button @click="annotationDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, computed, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { getEvaluationList, getEvaluation, getAnnotations } from '../api/evaluation'
import { getReport } from '../api/report'
import AnnotationViewer from '../components/AnnotationViewer.vue'

const loading = ref(false)
const detailVisible = ref(false)
const currentEval = ref(null)

// AI智能批注相关状态
const annotationDialogVisible = ref(false)
const annotationLoading = ref(false)
const annotationError = ref('')
const annotationData = ref([])
const annotationReportContent = ref('')

const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref([])

const parsedDynamicScores = computed(() => {
  if (!currentEval.value || !currentEval.value.dynamicScoresJson) return null
  try {
    const obj = JSON.parse(currentEval.value.dynamicScoresJson)
    return obj.scores || null
  } catch (e) {
    try {
      const obj = JSON.parse(currentEval.value.aiEvaluation)
      return obj.scores || null
    } catch {
      return null
    }
  }
})

const parsedSuggestions = computed(() => {
  if (!currentEval.value || !currentEval.value.aiEvaluation) return []
  try {
    const obj = JSON.parse(currentEval.value.aiEvaluation)
    return obj.suggestions || []
  } catch {
    return []
  }
})

const calculatePercentage = (score, key) => {
  const s = parseFloat(score) || 0
  if (s <= 0) return 0
  return Math.min(Math.round(s * 2.5), 100)
}

const getScoreType = (score) => {
  if (!score) return 'info'
  if (score >= 90) return 'success'
  if (score >= 80) return 'primary'
  if (score >= 60) return 'warning'
  return 'danger'
}

const loadData = async () => {
  loading.value = true
  try {
    const res = await getEvaluationList({ ...pagination })
    tableData.value = res.data.records
    pagination.total = res.data.total
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const handleView = async (row) => {
  try {
    const res = await getEvaluation(row.id)
    currentEval.value = res.data
    detailVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

// 打开AI批注查看器（学生端仅查看，不触发生成）
const openAnnotationViewer = async () => {
  if (!currentEval.value || !currentEval.value.reportId) {
    ElMessage.warning('请先查看报告详情')
    return
  }

  const reportId = currentEval.value.reportId
  annotationDialogVisible.value = true
  annotationLoading.value = true
  annotationError.value = ''
  annotationData.value = []

  // 并行获取报告原文和已有批注
  try {
    const [reportRes, annotationsRes] = await Promise.all([
      getReport(reportId),
      getAnnotations(reportId)
    ])

    annotationReportContent.value = reportRes.data?.content || ''

    if (annotationsRes.data && annotationsRes.data.length > 0) {
      annotationData.value = annotationsRes.data
    } else {
      annotationError.value = '该报告暂无AI批注，请联系教师生成批注'
    }
  } catch (error) {
    console.error('获取批注失败:', error)
    annotationError.value = '获取批注失败，请稍后重试'
  } finally {
    annotationLoading.value = false
  }
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.page-desc {
  color: #909399;
  font-size: 13px;
  margin-top: 8px;
  margin-bottom: 16px;
}
.score-section {
  margin-top: 20px;
}
.score-section h4 {
  margin-bottom: 16px;
  color: #303133;
}
.dynamic-score-card {
  background: #fff;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  padding: 12px 14px;
  margin-bottom: 12px;
}
.dynamic-score-meta {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 6px;
}
.dynamic-key-name {
  font-weight: bold;
  color: #303133;
  font-size: 14px;
}
.dynamic-key-val {
  font-size: 13px;
  color: #606266;
}
.dynamic-key-val b {
  color: #409eff;
  font-size: 15px;
}
.dynamic-key-comment {
  font-size: 12px;
  color: #e6a23c;
  margin: 6px 0 0 0;
  line-height: 1.4;
  background: #fffdf5;
  padding: 4px 6px;
  border-radius: 2px;
}
.score-item {
  text-align: center;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.score-item .label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}
.score-item .value {
  font-size: 24px;
  font-weight: bold;
  color: #303133;
}
.score-item.total .value {
  color: #409eff;
}
.evaluation-content {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}
.evaluation-content h5 {
  margin-bottom: 8px;
  color: #606266;
  font-weight: bold;
}
</style>