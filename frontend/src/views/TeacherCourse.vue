<template>
  <div class="page-container">
    <h2 class="page-title">我的课程</h2>
    
    <!-- 工具栏：班级筛选 + 报告管理 -->
    <div class="toolbar">
      <div class="filter-group">
        <span class="filter-label">授课班级：</span>
        <el-select v-model="selectedClassId" placeholder="全部班级" clearable style="width: 200px;">
          <el-option v-for="cls in classOptions" :key="cls.id" :label="cls.className" :value="cls.id" />
        </el-select>
      </div>
      <div class="action-group">
        <el-button type="success" @click="goToAllReports">报告管理（查看所有）</el-button>
      </div>
    </div>
    
    <!-- 课程列表 -->
    <div class="course-list" v-loading="loading">
      <el-empty v-if="!loading && filteredCourses.length === 0" :description="selectedClassId ? '该班级暂无课程' : '暂无分配的课程'" />
      
      <el-table v-else :data="filteredCourses" stripe>
        <el-table-column prop="courseName" label="课程名称" />
        <el-table-column prop="courseCode" label="课程编号" width="150" />
        <el-table-column prop="className" label="授课班级" width="200" />
        <el-table-column prop="credit" label="学分" width="80" align="center" />
        <el-table-column prop="studentCount" label="学生人数" width="100" align="center">
          <template #default="{ row }">
            <el-tag type="info">{{ row.studentCount || 0 }} 人</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="reportCount" label="已提交报告" width="120" align="center">
          <template #default="{ row }">
            <el-tag :type="row.reportCount > 0 ? 'success' : 'warning'">{{ row.reportCount || 0 }} 份</el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="380" align="center">
          <template #default="{ row }">
            <el-button type="primary" size="small" @click="viewDetail(row)">详情</el-button>
            <el-button type="warning" size="small" @click="viewAssignments(row)">查看作业</el-button>
            <el-button type="success" size="small" @click="goToCourseReport(row)">查看报告</el-button>
          </template>
        </el-table-column>
      </el-table>
    </div>

    <!-- 详情对话框 -->
    <el-dialog v-model="detailVisible" title="课程详情" width="600px">
      <el-descriptions :column="2" border v-if="currentCourse">
        <el-descriptions-item label="课程名称" :span="2">{{ currentCourse.courseName }}</el-descriptions-item>
        <el-descriptions-item label="课程编号">{{ currentCourse.courseCode || '-' }}</el-descriptions-item>
        <el-descriptions-item label="授课班级">{{ currentCourse.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="学分">{{ currentCourse.credit || '-' }}</el-descriptions-item>
        <el-descriptions-item label="课时">{{ currentCourse.hours || '-' }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentCourse.status === 1 ? 'success' : 'info'">
            {{ currentCourse.status === 1 ? '进行中' : '已结束' }}
          </el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="分配时间">{{ currentCourse.createTime || '-' }}</el-descriptions-item>
        <el-descriptions-item label="课程描述" :span="2">
          {{ currentCourse.description || '暂无描述' }}
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
        <el-button type="success" @click="goToCourseReport(currentCourse)">查看报告</el-button>
      </template>
    </el-dialog>

    <!-- 作业列表对话框 -->
    <el-dialog v-model="assignmentDialogVisible" :title="`作业列表 - ${assignmentCourse?.courseName || ''}`" width="900px" top="3vh">
      <div v-loading="assignmentLoading">
        <el-empty v-if="!assignmentLoading && requirements.length === 0" description="该课程暂无作业" />
        <el-table v-else :data="requirements" stripe>
          <el-table-column prop="title" label="作业标题" min-width="200" show-overflow-tooltip />
          <el-table-column prop="deadline" label="截止时间" width="180">
            <template #default="{ row }">
              <el-tag :type="isOverdue(row.deadline) ? 'danger' : 'success'" size="small">
                {{ row.deadline || '未设置' }}
                {{ isOverdue(row.deadline) ? '(已截止)' : '' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="submissionCount" label="已提交" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.submissionCount > 0 ? 'success' : 'info'">
                {{ row.submissionCount || 0 }} 人
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="viewSubmissions(row)">查看提交</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="assignmentDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 学生提交列表对话框 -->
    <el-dialog v-model="submissionDialogVisible" :title="`提交列表 - ${currentRequirement?.title || ''}`" width="1000px" top="3vh">
      <div v-loading="submissionLoading">
        <el-empty v-if="!submissionLoading && submissions.length === 0" description="暂无学生提交" />
        <el-table v-else :data="submissions" stripe>
          <el-table-column prop="studentName" label="学生姓名" width="120" />
          <el-table-column prop="className" label="班级" width="150" />
          <el-table-column prop="title" label="报告标题" min-width="180" show-overflow-tooltip />
          <el-table-column prop="fileName" label="附件" width="140" show-overflow-tooltip />
          <el-table-column prop="statusName" label="状态" width="100" align="center">
            <template #default="{ row }">
              <el-tag :type="row.status === 1 ? 'success' : 'warning'" size="small">{{ row.statusName }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column prop="totalScore" label="得分" width="80" align="center">
            <template #default="{ row }">
              <span v-if="row.totalScore != null" :class="row.totalScore >= 60 ? 'score-pass' : 'score-fail'">
                {{ row.totalScore }}
              </span>
              <span v-else class="no-score">-</span>
            </template>
          </el-table-column>
          <el-table-column prop="createTime" label="提交时间" width="160">
            <template #default="{ row }">
              {{ formatTime(row.createTime) }}
            </template>
          </el-table-column>
          <el-table-column label="操作" width="180" align="center" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" size="small" @click="viewSubmissionDetail(row)">详情</el-button>
              <el-button type="success" size="small" @click="downloadSubmission(row)">下载</el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
      <template #footer>
        <el-button @click="submissionDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 单个提交详情对话框 -->
    <el-dialog v-model="detailReportVisible" title="提交详情" width="700px" top="3vh">
      <el-descriptions v-if="currentReport" :column="2" border>
        <el-descriptions-item label="学生姓名">{{ currentReport.studentName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="班级">{{ currentReport.className || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报告标题" :span="2">{{ currentReport.title || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件名">{{ currentReport.fileName || '-' }}</el-descriptions-item>
        <el-descriptions-item label="文件类型">{{ currentReport.fileType || '-' }}</el-descriptions-item>
        <el-descriptions-item label="提交时间">{{ formatTime(currentReport.createTime) }}</el-descriptions-item>
        <el-descriptions-item label="最后更新">{{ formatTime(currentReport.updateTime) }}</el-descriptions-item>
        <el-descriptions-item label="状态">
          <el-tag :type="currentReport.status === 1 ? 'success' : 'warning'">{{ currentReport.statusName }}</el-tag>
        </el-descriptions-item>
        <el-descriptions-item label="AI得分">
          <span v-if="currentReport.totalScore != null" class="score-highlight">{{ currentReport.totalScore }} 分</span>
          <span v-else>未评分</span>
        </el-descriptions-item>
        <el-descriptions-item label="作业标题" :span="2">{{ currentReport.requirementTitle || '-' }}</el-descriptions-item>
        <el-descriptions-item label="截止时间">{{ currentReport.deadline || '-' }}</el-descriptions-item>
        <el-descriptions-item label="报告内容" :span="2">
          <div class="report-content-preview">{{ currentReport.content || '无内容' }}</div>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button @click="detailReportVisible = false">关闭</el-button>
        <el-button v-if="currentReport" type="success" @click="downloadSubmission(currentReport)">下载附件</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { getMyCourses, getCourseStudents } from '../api/course'
import { getReportList, getSubmissionsByRequirement, downloadReport } from '../api/report'
import { getAllClasses } from '../api/class'
import { getCourseRequirements } from '../api/reportRequirement'
import { ElMessage } from 'element-plus'

const router = useRouter()
const loading = ref(false)
const tableData = ref([])
const allClasses = ref([])
const selectedClassId = ref(null)
const detailVisible = ref(false)
const currentCourse = ref(null)

// 班级下拉选项
const classOptions = computed(() => {
  return allClasses.value.filter(cls => 
    tableData.value.some(course => course.classId === cls.id)
  )
})

// 根据班级筛选课程
const filteredCourses = computed(() => {
  if (!selectedClassId.value) {
    return tableData.value
  }
  return tableData.value.filter(course => course.classId === selectedClassId.value)
})

const loadClasses = async () => {
  try {
    const res = await getAllClasses()
    allClasses.value = res.data || []
  } catch (error) {
    console.error(error)
  }
}

const loadData = async () => {
  loading.value = true
  try {
    // 获取课程列表
    const courseRes = await getMyCourses()
    const courses = courseRes.data || []
    
    // 获取班级列表
    await loadClasses()
    
    // 补充班级名称和学生数量
    const result = []
    for (const course of courses) {
      // 查找班级名称
      const classInfo = allClasses.value.find(c => c.id === course.classId)
      
      // 获取学生数量
      let studentCount = 0
      if (course.classId) {
        try {
          const studentRes = await getCourseStudents(course.classId)
          studentCount = studentRes.data?.length || 0
        } catch (e) {
          console.error(e)
        }
      }
      
      // 获取报告数量
      let reportCount = 0
      try {
        const reportRes = await getReportList({ pageNum: 1, pageSize: 1, courseId: course.id })
        reportCount = reportRes.data?.total || 0
      } catch (e) {
        console.error(e)
      }
      
      result.push({
        ...course,
        className: classInfo?.className || '-',
        studentCount,
        reportCount
      })
    }
    
    tableData.value = result
  } catch (error) {
    console.error(error)
  } finally {
    loading.value = false
  }
}

const viewDetail = (course) => {
  currentCourse.value = course
  detailVisible.value = true
}

// 查看当前课程（筛选后）的报告
const goToCourseReport = (course) => {
  router.push({ path: '/report', query: { courseId: course.id } })
}

// 查看所有报告
const goToAllReports = () => {
  router.push({ path: '/report' })
}

// ==================== 作业列表与提交查看 ====================
const assignmentDialogVisible = ref(false)
const assignmentLoading = ref(false)
const assignmentCourse = ref(null)
const requirements = ref([])

const submissionDialogVisible = ref(false)
const submissionLoading = ref(false)
const currentRequirement = ref(null)
const submissions = ref([])

const detailReportVisible = ref(false)
const currentReport = ref(null)

// 查看课程下的作业列表
const viewAssignments = async (course) => {
  assignmentCourse.value = course
  assignmentDialogVisible.value = true
  assignmentLoading.value = true
  try {
    const res = await getCourseRequirements(course.id)
    const reqs = res.data || []

    // 获取每个作业的提交数量
    const items = await Promise.all(reqs.map(async (req) => {
      let submissionCount = 0
      try {
        const subRes = await getSubmissionsByRequirement(req.id)
        submissionCount = subRes.data?.length || 0
      } catch (e) {
        console.error(e)
      }
      return { ...req, submissionCount }
    }))

    requirements.value = items
  } catch (error) {
    console.error(error)
    ElMessage.error('获取作业列表失败')
  } finally {
    assignmentLoading.value = false
  }
}

// 查看某作业的所有学生提交
const viewSubmissions = async (requirement) => {
  currentRequirement.value = requirement
  submissionDialogVisible.value = true
  submissionLoading.value = true
  try {
    const res = await getSubmissionsByRequirement(requirement.id)
    submissions.value = res.data || []
  } catch (error) {
    console.error(error)
    ElMessage.error('获取提交列表失败')
  } finally {
    submissionLoading.value = false
  }
}

// 查看单个提交详情
const viewSubmissionDetail = (report) => {
  currentReport.value = report
  detailReportVisible.value = true
}

// 下载提交附件
const downloadSubmission = (report) => {
  if (!report?.id) return
  try {
    downloadReport(report.id)
  } catch (error) {
    console.error(error)
    ElMessage.error('下载失败')
  }
}

// 判断是否已截止
const isOverdue = (deadline) => {
  if (!deadline) return false
  return new Date(deadline) < new Date()
}

// 格式化时间
const formatTime = (time) => {
  if (!time) return '-'
  return time
}

onMounted(() => {
  loadData()
})
</script>

<style scoped>
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 16px;
  background: #fff;
  border-radius: 4px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.filter-group {
  display: flex;
  align-items: center;
  gap: 10px;
}

.filter-label {
  font-weight: 500;
  color: #606266;
}

.action-group {
  display: flex;
  gap: 10px;
}

.course-list {
  margin-top: 0;
}

.score-pass {
  color: #67c23a;
  font-weight: bold;
  font-size: 15px;
}

.score-fail {
  color: #f56c6c;
  font-weight: bold;
  font-size: 15px;
}

.no-score {
  color: #c0c4cc;
}

.score-highlight {
  font-size: 18px;
  font-weight: bold;
  color: #e6a23c;
}

.report-content-preview {
  max-height: 200px;
  overflow-y: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
  background: #f5f7fa;
  padding: 10px;
  border-radius: 4px;
}
</style>
