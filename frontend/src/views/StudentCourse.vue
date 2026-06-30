<template>
  <div class="page-container">
    <h2 class="page-title">我的课程</h2>
    
    <!-- 通知公告 -->
    <div v-if="notifications.length > 0" class="notification-section">
      <el-alert
        v-for="item in notifications"
        :key="item.id"
        :title="item.title"
        :description="item.content"
        type="info"
        show-icon
        :closable="false"
        class="notification-item"
      >
        <template #default>
          <span style="color: #909399; font-size: 12px;">{{ item.createTime }}</span>
          <el-button type="primary" size="small" @click="viewRequirement(item)" style="margin-left: 10px;">查看详情</el-button>
        </template>
      </el-alert>
    </div>
    
    <div class="course-container">
      <!-- 已选课程 -->
      <div class="section">
        <h3 class="section-title">已选修课程</h3>
        <div v-if="selectedCourses.length > 0" class="course-list">
          <el-card v-for="course in selectedCourses" :key="course.id" class="course-card" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="course-name">{{ course.courseName }}</span>
                <el-tag type="success">已选修</el-tag>
              </div>
            </template>
            <div class="course-info">
              <p v-if="course.teacherName"><strong>授课教师：</strong>{{ course.teacherName }}</p>
              <p><strong>课程描述：</strong>{{ course.description || '暂无描述' }}</p>
              <p><strong>创建时间：</strong>{{ course.createTime }}</p>
            </div>
            <template #footer>
              <div class="card-footer">
                <el-button type="primary" size="small" @click="viewAssignments(course)">查看作业</el-button>
                <el-button type="warning" size="small" @click="viewCourseRequirements(course)">作业要求</el-button>
                <el-button type="danger" size="small" @click="handleCancel(course)">取消选课</el-button>
              </div>
            </template>
          </el-card>
        </div>
        <el-empty v-else description="您还没有选修任何课程" />
      </div>
      
      <!-- 可选课程 -->
      <div class="section">
        <h3 class="section-title">可选课程</h3>
        <div v-if="availableCourses.length > 0" class="course-list">
          <el-card v-for="course in availableCourses" :key="course.id" class="course-card available" shadow="hover">
            <template #header>
              <div class="card-header">
                <span class="course-name">{{ course.courseName }}</span>
                <el-tag type="info">可选</el-tag>
              </div>
            </template>
            <div class="course-info">
              <p v-if="course.teacherName"><strong>授课教师：</strong>{{ course.teacherName }}</p>
              <p><strong>课程描述：</strong>{{ course.description || '暂无描述' }}</p>
            </div>
            <template #footer>
              <div class="card-footer">
                <el-button type="primary" @click="handleSelect(course)">选修此课程</el-button>
              </div>
            </template>
          </el-card>
        </div>
        <el-empty v-else description="暂无可选课程" />
      </div>
    </div>

    <!-- 作业列表对话框 -->
    <el-dialog v-model="assignmentDialogVisible" :title="'课程作业 - ' + currentCourse?.courseName" width="90%" top="3vh" :close-on-click-modal="false">
      <div v-if="assignments.length > 0" class="assignment-list">
        <el-card 
          v-for="item in assignments" 
          :key="item.requirement.id" 
          class="assignment-card"
          :class="{ 'submitted': item.submitted, 'overdue': isOverdue(item.requirement.deadline) }"
        >
          <template #header>
            <div class="assignment-header">
              <div class="assignment-title-row">
                <span class="assignment-title">{{ item.requirement.title }}</span>
                <el-tag 
                  :type="getDeadlineTagType(item.requirement.deadline)" 
                  size="small"
                >
                  {{ getDeadlineText(item.requirement.deadline) }}
                </el-tag>
              </div>
              <div class="assignment-status-tags">
                <el-tag v-if="item.submitted" type="success" size="small">已提交</el-tag>
                <el-tag v-else type="info" size="small">未提交</el-tag>
                <el-tag v-if="item.report?.totalScore" type="warning" size="small" style="margin-left:4px">
                  得分: {{ item.report.totalScore }}
                </el-tag>
              </div>
            </div>
          </template>
          <div class="assignment-content">
            <p><strong>截止时间：</strong>{{ item.requirement.deadline || '未设置' }}</p>
            <p><strong>详细内容：</strong></p>
            <div class="content-text">{{ item.requirement.content || '无详细内容' }}</div>
            <p v-if="item.requirement.fileName" style="margin-top: 10px;">
              <strong>附件文档：</strong>
              <el-link type="primary" @click="downloadRequirementDocument(item.requirement.id)" :underline="false">
                <el-icon><Download /></el-icon> {{ item.requirement.fileName }}
              </el-link>
            </p>
            <div v-if="item.submitted" class="submission-info">
              <p><strong>已提交文件：</strong>{{ item.report?.fileName || '-' }}</p>
              <p><strong>提交时间：</strong>{{ item.report?.createTime || '-' }}</p>
            </div>
          </div>
          <template #footer>
            <div class="assignment-footer">
              <el-button 
                v-if="item.submitted" 
                type="success" 
                @click="goToEditAssignment(item)"
                :disabled="isOverdue(item.requirement.deadline)"
              >
                {{ isOverdue(item.requirement.deadline) ? '已截止' : '编辑提交' }}
              </el-button>
              <el-button 
                v-else 
                type="primary" 
                @click="goToSubmitAssignment(item)"
                :disabled="isOverdue(item.requirement.deadline)"
              >
                {{ isOverdue(item.requirement.deadline) ? '已截止' : '提交作业' }}
              </el-button>
              <el-button 
                v-if="item.submitted" 
                type="info" 
                size="small"
                @click="viewReportDetail(item.report)"
              >
                查看详情
              </el-button>
            </div>
          </template>
        </el-card>
      </div>
      <el-empty v-else description="暂无作业" />
      <template #footer>
        <el-button @click="assignmentDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 提交/编辑作业对话框 -->
    <el-dialog 
      v-model="submitDialogVisible" 
      :title="submitDialogTitle" 
      width="750px" 
      top="3vh"
      :close-on-click-modal="false"
      @close="resetSubmitForm"
    >
      <el-form :model="submitForm" label-width="100px">
        <el-form-item label="作业标题">
          <el-input :model-value="currentAssignment?.requirement?.title" disabled />
        </el-form-item>
        <el-form-item label="成果文件" prop="files">
          <el-upload
            ref="uploadRef"
            :auto-upload="false"
            multiple
            :limit="20"
            :on-change="handleFileChange"
            :on-remove="handleFileRemove"
            v-model:file-list="fileList"
            accept=".doc,.docx,.pdf,.txt,.md,.py,.java,.js,.ts,.html,.css,.sql,.c,.cpp,.go,.rs,.php,.json,.xml,.yaml,.yml,.zip,.png,.jpg,.jpeg,.gif,.bmp,.webp"
            drag
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div>将文件拖到此处，或<em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">
                支持文档、代码、截图、压缩包，可同时上传多个文件，总大小不超过50MB
              </div>
            </template>
          </el-upload>
        </el-form-item>
        <el-form-item v-if="fileCategories.length > 0" label="文件类型">
          <el-tag v-for="cat in fileCategories" :key="cat.type" :type="cat.color" style="margin-right:8px">
            {{ cat.icon }} {{ cat.type }} × {{ cat.count }}
          </el-tag>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="submitDialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitAssignment" :loading="submitting" :disabled="fileList.length === 0">
          {{ isEditMode ? '更新提交' : '提交作业' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 报告详情对话框 -->
    <el-dialog v-model="detailDialogVisible" title="提交详情" width="800px" top="3vh" :close-on-click-modal="false">
      <div v-if="currentReport">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="作业标题">{{ currentReport.requirementTitle || currentReport.title }}</el-descriptions-item>
          <el-descriptions-item label="学生">{{ currentReport.studentName }}</el-descriptions-item>
          <el-descriptions-item label="课程">{{ currentReport.courseName }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ currentReport.fileName }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="currentReport.status === 1 ? 'success' : 'warning'">
              {{ currentReport.statusName }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentReport.createTime }}</el-descriptions-item>
        </el-descriptions>
        
        <div v-if="currentReport.totalScore" class="score-section">
          <h4>评价结果</h4>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="score-item">
                <p class="label">完整性</p>
                <p class="value">{{ currentReport.completenessScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">规范性</p>
                <p class="value">{{ currentReport.specificationScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">知识点</p>
                <p class="value">{{ currentReport.knowledgeScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item total">
                <p class="label">总分</p>
                <p class="value">{{ currentReport.totalScore }}</p>
              </div>
            </el-col>
          </el-row>
          
          <div v-if="currentReport.aiEvaluation" class="ai-evaluation">
            <h5>AI评价详情</h5>
            <pre>{{ formatEvaluation(currentReport.aiEvaluation) }}</pre>
          </div>
          
          <div v-if="currentReport.manualEvaluation" class="manual-evaluation">
            <h5>教师评价</h5>
            <p>{{ currentReport.manualEvaluation }}</p>
          </div>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailDialogVisible = false">关闭</el-button>
        <el-button 
          v-if="currentReport?.totalScore" 
          type="warning" 
          @click="openAiChat"
        >
          <el-icon style="margin-right:4px"><ChatDotRound /></el-icon>
          AI对话反馈
        </el-button>
      </template>
    </el-dialog>

    <!-- AI对话反馈对话框 -->
    <el-dialog 
      v-model="chatDialogVisible" 
      title="AI对话反馈" 
      width="80%" 
      top="2vh"
      :close-on-click-modal="false"
      @opened="initChat"
    >
      <div class="ai-chat-container">
        <!-- 扣分点速览面板 -->
        <div v-if="deductionPoints.length > 0" class="deduction-panel">
          <div class="deduction-panel-title">
            <el-icon><WarningFilled /></el-icon>
            扣分点速览（点击可快速提问）
          </div>
          <div class="deduction-list">
            <el-tag 
              v-for="(point, idx) in deductionPoints" 
              :key="idx"
              :type="getDeductionTagType(point)"
              class="deduction-tag"
              @click="quickAsk(point)"
            >
              {{ idx + 1 }}. {{ point.dimension }}
              <span class="deduction-score">-{{ point.deducted }}分</span>
            </el-tag>
          </div>
        </div>

        <!-- 聊天消息区域 -->
        <div class="chat-messages" ref="chatMessagesRef">
          <div 
            v-for="(msg, idx) in chatMessages" 
            :key="idx" 
            :class="['chat-message', msg.role === 'user' ? 'user-message' : 'ai-message']"
          >
            <div class="message-avatar">
              <el-avatar :size="32" :icon="msg.role === 'user' ? UserFilled : ChatDotRound" />
            </div>
            <div class="message-content">
              <div class="message-sender">{{ msg.role === 'user' ? '我' : 'AI助教' }}</div>
              <div class="message-text" v-html="formatChatMessage(msg.content)"></div>
              <div class="message-time">{{ msg.time }}</div>
            </div>
          </div>
          <div v-if="chatLoading" class="chat-message ai-message">
            <div class="message-avatar">
              <el-avatar :size="32" :icon="ChatDotRound" />
            </div>
            <div class="message-content">
              <div class="message-sender">AI助教</div>
              <div class="typing-indicator">
                <span></span><span></span><span></span>
              </div>
            </div>
          </div>
        </div>

        <!-- 快捷提问 -->
        <div class="quick-asks" v-if="chatMessages.length <= 1">
          <span class="quick-asks-label">快捷提问：</span>
          <el-button size="small" v-for="q in quickQuestions" :key="q" @click="quickAskText(q)" plain>{{ q }}</el-button>
        </div>

        <!-- 输入区域 -->
        <div class="chat-input-area">
          <el-input
            v-model="chatInput"
            type="textarea"
            :rows="2"
            placeholder="输入你的问题，例如：第1点为什么扣分？我应该怎么改？"
            @keydown.enter.exact="sendChatMessage"
            :disabled="chatLoading"
            resize="none"
          />
          <el-button 
            type="primary" 
            @click="sendChatMessage" 
            :loading="chatLoading"
            :disabled="!chatInput.trim()"
            class="send-btn"
          >
            <el-icon><Promotion /></el-icon>
            发送
          </el-button>
        </div>
      </div>
      <template #footer>
        <el-button @click="chatDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- 作业要求对话框 -->
    <el-dialog v-model="requirementDialogVisible" :title="'作业要求 - ' + currentCourse?.courseName" width="700px">
      <div v-if="courseRequirements.length > 0" class="requirement-list">
        <el-card 
          v-for="req in courseRequirements" 
          :key="req.id" 
          class="requirement-card"
          :class="{ 'overdue': isOverdue(req.deadline) }"
        >
          <template #header>
            <div class="requirement-header">
              <span class="requirement-title">{{ req.title }}</span>
              <el-tag :type="isOverdue(req.deadline) ? 'danger' : 'success'" size="small">
                {{ isOverdue(req.deadline) ? '已截止' : '进行中' }}
              </el-tag>
            </div>
          </template>
          <div class="requirement-content">
            <p><strong>截止时间：</strong>{{ req.deadline || '未设置' }}</p>
            <p><strong>详细内容：</strong></p>
            <div class="content-text">{{ req.content || '无详细内容' }}</div>
            <p v-if="req.fileName" style="margin-top: 10px;">
              <strong>附件文档：</strong>
              <el-link type="primary" @click="downloadRequirementDocument(req.id)" :underline="false">
                <el-icon><Download /></el-icon> {{ req.fileName }}
              </el-link>
            </p>
          </div>
        </el-card>
      </div>
      <el-empty v-else description="暂无作业要求" />
      <template #footer>
        <el-button @click="requirementDialogVisible = false">关闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled, ChatDotRound, WarningFilled, UserFilled, Promotion, Download } from '@element-plus/icons-vue'
import { getAvailableCourses, selectCourse, cancelCourse, getMyCourses } from '../api/course'
import { getCourseRequirements, downloadRequirementDocument } from '../api/reportRequirement'
import { getNotificationList, markAsRead } from '../api/notification'
import { getReport, getMySubmission, uploadMultipleReports, uploadReport, updateReport } from '../api/report'
import { chatFeedback } from '../api/evaluation'
import { useUserStoreHook } from '../stores'

const userStore = useUserStoreHook()
const selectedCourses = ref([])
const availableCourses = ref([])
const notifications = ref([])
const courseRequirements = ref([])

// 作业相关
const assignmentDialogVisible = ref(false)
const submitDialogVisible = ref(false)
const detailDialogVisible = ref(false)
const requirementDialogVisible = ref(false)
const currentCourse = ref(null)
const currentAssignment = ref(null)
const currentReport = ref(null)
const assignments = ref([])
const submitting = ref(false)
const uploadRef = ref()
const fileList = ref([])

const submitForm = ref({ files: [] })

const isEditMode = computed(() => !!currentAssignment.value?.submitted)
const submitDialogTitle = computed(() => isEditMode.value ? '编辑作业提交' : '提交作业')

// 文件类型分类
const fileCategories = computed(() => {
  const cats = {}
  fileList.value.forEach(f => {
    const ext = (f.name || '').split('.').pop()?.toLowerCase() || 'unknown'
    const info = getFileTypeInfo(ext)
    if (!cats[info.type]) cats[info.type] = { ...info, count: 0 }
    cats[info.type].count++
  })
  return Object.values(cats)
})

const getFileTypeInfo = (ext) => {
  const map = {
    doc: { type: '文档', icon: '📄', color: 'primary' },
    docx: { type: '文档', icon: '📄', color: 'primary' },
    pdf: { type: '文档', icon: '📄', color: 'primary' },
    txt: { type: '文档', icon: '📄', color: 'primary' },
    md: { type: '文档', icon: '📄', color: 'primary' },
    py: { type: '代码', icon: '💻', color: 'success' },
    java: { type: '代码', icon: '💻', color: 'success' },
    js: { type: '代码', icon: '💻', color: 'success' },
    ts: { type: '代码', icon: '💻', color: 'success' },
    html: { type: '代码', icon: '💻', color: 'success' },
    css: { type: '代码', icon: '💻', color: 'success' },
    sql: { type: '代码', icon: '💻', color: 'success' },
    png: { type: '截图', icon: '🖼️', color: 'warning' },
    jpg: { type: '截图', icon: '🖼️', color: 'warning' },
    jpeg: { type: '截图', icon: '🖼️', color: 'warning' },
    gif: { type: '截图', icon: '🖼️', color: 'warning' },
    zip: { type: '压缩包', icon: '📦', color: 'info' }
  }
  return map[ext] || { type: '其他', icon: '📁', color: '' }
}

const loadMyCourses = async () => {
  try {
    const res = await getMyCourses()
    selectedCourses.value = res.data || []
    loadAvailableCourses()
  } catch (error) {
    console.error(error)
  }
}

const loadAvailableCourses = async () => {
  try {
    const res = await getAvailableCourses()
    const selectedIds = selectedCourses.value.map(c => c.id)
    availableCourses.value = (res.data || []).filter(c => !selectedIds.includes(c.id))
  } catch (error) {
    console.error(error)
  }
}

const loadNotifications = async () => {
  try {
    const res = await getNotificationList({ pageNum: 1, pageSize: 10 })
    notifications.value = (res.data?.records || []).filter(n => n.type === 1 && n.isRead === 0)
  } catch (error) {
    console.error(error)
  }
}

const viewRequirement = async (notification) => {
  try {
    await markAsRead(notification.id)
    const course = selectedCourses.value.find(c => notification.relatedId)
    if (course) {
      await viewCourseRequirements(course)
    }
    notifications.value = notifications.value.filter(n => n.id !== notification.id)
  } catch (error) {
    console.error(error)
  }
}

// 查看课程作业列表
const viewAssignments = async (course) => {
  currentCourse.value = course
  try {
    const res = await getCourseRequirements(course.id)
    const requirements = res.data || []
    
    // 检查每个作业的提交状态
    const items = await Promise.all(requirements.map(async (req) => {
      try {
        const subRes = await getMySubmission(req.id)
        const hasSubmitted = subRes.data != null
        return {
          requirement: req,
          submitted: hasSubmitted,
          report: subRes.data
        }
      } catch {
        return {
          requirement: req,
          submitted: false,
          report: null
        }
      }
    }))
    
    assignments.value = items
    assignmentDialogVisible.value = true
  } catch (error) {
    console.error(error)
    ElMessage.error('获取作业列表失败')
  }
}

const viewCourseRequirements = async (course) => {
  currentCourse.value = course
  try {
    const res = await getCourseRequirements(course.id)
    courseRequirements.value = res.data || []
    requirementDialogVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

const isOverdue = (deadline) => {
  if (!deadline) return false
  return new Date(deadline) < new Date()
}

const getDeadlineTagType = (deadline) => {
  if (!deadline) return 'info'
  return new Date(deadline) < new Date() ? 'danger' : 'success'
}

const getDeadlineText = (deadline) => {
  if (!deadline) return '无截止时间'
  return new Date(deadline) < new Date() ? '已截止' : '进行中'
}

// 提交作业
const goToSubmitAssignment = (item) => {
  currentAssignment.value = item
  submitForm.value = { files: [] }
  fileList.value = []
  submitDialogVisible.value = true
}

// 编辑已提交的作业
const goToEditAssignment = (item) => {
  currentAssignment.value = item
  submitForm.value = { files: [] }
  fileList.value = []
  submitDialogVisible.value = true
}

const handleFileChange = () => {
  submitForm.value.files = fileList.value.map(f => f.raw).filter(Boolean)
}

const handleFileRemove = () => {
  submitForm.value.files = fileList.value.map(f => f.raw).filter(Boolean)
}

const resetSubmitForm = () => {
  submitForm.value = { files: [] }
  fileList.value = []
  currentAssignment.value = null
}

const handleSubmitAssignment = async () => {
  if (fileList.value.length === 0) {
    ElMessage.warning('请选择至少一个文件')
    return
  }

  submitting.value = true
  try {
    const req = currentAssignment.value.requirement
    const formData = new FormData()
    formData.append('studentId', userStore.userInfo.userId)
    formData.append('courseId', req.courseId)
    formData.append('requirementId', req.id)
    formData.append('title', req.title)

    const files = fileList.value.map(f => f.raw).filter(Boolean)
    
    if (isEditMode.value) {
      // 编辑模式：更新已有报告
      const reportId = currentAssignment.value.report.id
      if (files.length > 1) {
        files.forEach(file => formData.append('files', file))
        await updateReport(reportId, formData)
      } else {
        formData.append('file', files[0])
        await updateReport(reportId, formData)
      }
      ElMessage.success('作业已更新')
    } else {
      // 新提交
      if (files.length > 1) {
        files.forEach(file => formData.append('files', file))
        await uploadMultipleReports(formData)
      } else {
        formData.append('file', files[0])
        await uploadReport(formData)
      }
      ElMessage.success('作业提交成功')
    }

    submitDialogVisible.value = false
    resetSubmitForm()
    
    // 刷新作业列表
    if (currentCourse.value) {
      await viewAssignments(currentCourse.value)
    }
  } catch (error) {
    console.error(error)
  } finally {
    submitting.value = false
  }
}

// 查看报告详情
const viewReportDetail = async (report) => {
  if (!report?.id) return
  try {
    const res = await getReport(report.id)
    currentReport.value = res.data
    detailDialogVisible.value = true
  } catch (error) {
    console.error(error)
  }
}

const formatEvaluation = (jsonStr) => {
  try {
    return JSON.stringify(JSON.parse(jsonStr), null, 2)
  } catch {
    return jsonStr
  }
}

// ==================== AI对话反馈相关 ====================
const chatDialogVisible = ref(false)
const chatMessages = ref([])
const chatInput = ref('')
const chatLoading = ref(false)
const chatMessagesRef = ref(null)
const deductionPoints = ref([])
const conversationHistory = ref([]) // 完整的对话历史（用于发送给后端）

const quickQuestions = [
  '我扣分最多的点是什么？',
  '怎么改进才能拿更高分？',
  '我哪里做得比较好？',
  '有没有参考范例？'
]

// 打开AI对话
const openAiChat = () => {
  chatMessages.value = []
  conversationHistory.value = []
  deductionPoints.value = []
  chatInput.value = ''
  chatDialogVisible.value = true
}

// 初始化聊天（对话框打开后）
const initChat = async () => {
  if (!currentReport.value?.id) return

  chatLoading.value = true
  try {
    const res = await chatFeedback(currentReport.value.id, [])
    const reply = res.data?.reply || '你好！我是AI评价助教，有什么可以帮你的？'
    const dpStr = res.data?.deductionPoints || ''

    // 解析扣分点
    deductionPoints.value = parseDeductionPoints(dpStr)

    // 添加AI欢迎消息
    addChatMessage('assistant', reply)

  } catch (error) {
    console.error('初始化AI对话失败:', error)
    addChatMessage('assistant', '抱歉，AI对话服务暂时不可用，请稍后重试。')
  } finally {
    chatLoading.value = false
  }
}

// 发送消息
const sendChatMessage = async () => {
  const text = chatInput.value.trim()
  if (!text || chatLoading.value) return

  chatInput.value = ''

  // 添加用户消息
  addChatMessage('user', text)
  conversationHistory.value.push({ role: 'user', content: text })

  chatLoading.value = true
  try {
    const res = await chatFeedback(currentReport.value.id, conversationHistory.value)
    const reply = res.data?.reply || '抱歉，我没有理解你的问题，请换个方式问一下。'

    // 添加AI回复
    addChatMessage('assistant', reply)
    conversationHistory.value.push({ role: 'assistant', content: reply })

  } catch (error) {
    console.error('AI对话失败:', error)
    addChatMessage('assistant', '抱歉，消息发送失败，请稍后重试。')
  } finally {
    chatLoading.value = false
  }

  // 滚动到底部
  nextTick(() => {
    if (chatMessagesRef.value) {
      chatMessagesRef.value.scrollTop = chatMessagesRef.value.scrollHeight
    }
  })
}

// 添加聊天消息
const addChatMessage = (role, content) => {
  const now = new Date()
  const time = `${now.getHours().toString().padStart(2, '0')}:${now.getMinutes().toString().padStart(2, '0')}`
  chatMessages.value.push({ role, content, time })
}

// 快速提问（点击扣分点tag）
const quickAsk = (point) => {
  const question = `请详细解释一下第${chatMessages.value.length > 0 ? '这个' : ''}"${point.dimension}"维度的扣分原因，我具体哪里做得不好？应该怎么改？`
  chatInput.value = question
  sendChatMessage()
}

// 快速提问文本
const quickAskText = (question) => {
  chatInput.value = question
  sendChatMessage()
}

// 解析扣分点字符串
const parseDeductionPoints = (str) => {
  if (!str || str === '暂无详细扣分明细') return []
  const points = []
  const lines = str.split('\n').filter(l => l.trim())
  for (const line of lines) {
    // 匹配格式: "1. 【维度名】得分X.X/XX分（扣X.X分）: 说明"
    const match = line.match(/^(\d+)\.\s*【(.+?)】得分([\d.]+)\/([\d.]+)分（扣([\d.]+)分）(?::\s*(.*))?/)
    if (match) {
      points.push({
        index: parseInt(match[1]),
        dimension: match[2],
        score: parseFloat(match[3]),
        maxScore: parseFloat(match[4]),
        deducted: parseFloat(match[5]),
        comment: match[7] || ''
      })
    }
  }
  return points
}

// 扣分点标签颜色
const getDeductionTagType = (point) => {
  if (point.deducted >= 10) return 'danger'
  if (point.deducted >= 5) return 'warning'
  return 'info'
}

// 格式化聊天消息（支持简单的markdown和换行）
const formatChatMessage = (content) => {
  if (!content) return ''
  return content
    .replace(/\n/g, '<br>')
    .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.+?)\*/g, '<em>$1</em>')
    .replace(/`(.+?)`/g, '<code>$1</code>')
}

const handleSelect = async (course) => {
  await ElMessageBox.confirm(`确定选修「${course.courseName}」吗？`, '选课确认', { type: 'info' })
  try {
    await selectCourse(course.id)
    ElMessage.success('选课成功')
    loadMyCourses()
  } catch (error) {
    console.error(error)
  }
}

const handleCancel = async (course) => {
  await ElMessageBox.confirm(`确定取消选修「${course.courseName}」吗？`, '取消选课', { type: 'warning' })
  try {
    await cancelCourse(course.id)
    ElMessage.success('已取消选课')
    loadMyCourses()
  } catch (error) {
    console.error(error)
  }
}

onMounted(() => {
  loadMyCourses()
  loadNotifications()
})
</script>

<style scoped>
.notification-section {
  margin-bottom: 20px;
}

.notification-item {
  margin-bottom: 10px;
}

.course-container {
  display: flex;
  flex-direction: column;
  gap: 30px;
}

.section-title {
  margin-bottom: 16px;
  padding-bottom: 8px;
  border-bottom: 2px solid #409eff;
  color: #303133;
}

.course-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
  gap: 16px;
}

.course-card {
  transition: transform 0.2s;
}

.course-card:hover {
  transform: translateY(-4px);
}

.course-card.available {
  border: 1px dashed #409eff;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.course-name {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.course-info p {
  margin: 8px 0;
  font-size: 14px;
  color: #606266;
}

.card-footer {
  display: flex;
  justify-content: center;
  gap: 16px;
}

/* 作业列表 */
.assignment-list {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(380px, 1fr));
  gap: 20px;
  max-height: 68vh;
  overflow-y: auto;
  padding: 4px;
}

.assignment-card {
  border-left: 4px solid #409eff;
  transition: transform 0.2s, box-shadow 0.2s;
}

.assignment-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0,0,0,0.1);
}

.assignment-card.submitted {
  border-left-color: #67c23a;
}

.assignment-card.overdue {
  border-left-color: #f56c6c;
  opacity: 0.85;
}

.assignment-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
}

.assignment-title-row {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex: 1;
  min-width: 0;
}

.assignment-title {
  font-weight: bold;
  color: #303133;
  font-size: 16px;
  word-break: break-word;
}

.assignment-status-tags {
  display: flex;
  align-items: center;
  flex-shrink: 0;
  gap: 6px;
}

.assignment-content {
  padding: 4px 0;
}

.assignment-content p {
  margin: 10px 0;
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.content-text {
  background: #f5f7fa;
  padding: 14px 16px;
  border-radius: 6px;
  white-space: pre-wrap;
  line-height: 1.8;
  max-height: 200px;
  overflow-y: auto;
  font-size: 13px;
  color: #606266;
}

.submission-info {
  margin-top: 14px;
  padding: 12px 16px;
  background: #f0f9eb;
  border-radius: 6px;
  border: 1px solid #e1f3d8;
}

.submission-info p {
  margin: 5px 0;
  font-size: 13px;
  color: #67c23a;
}

.assignment-footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  flex-wrap: wrap;
  padding-top: 4px;
}

/* 作业要求 */
.requirement-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
  max-height: 500px;
  overflow-y: auto;
}

.requirement-card {
  border-left: 4px solid #409eff;
}

.requirement-card.overdue {
  border-left-color: #f56c6c;
  opacity: 0.8;
}

.requirement-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.requirement-title {
  font-weight: bold;
  color: #303133;
}

.requirement-content p {
  margin: 8px 0;
  font-size: 14px;
  color: #606266;
}

/* 评分 */
.score-section {
  margin-top: 20px;
}

.score-section h4 {
  margin-bottom: 16px;
  color: #303133;
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

.ai-evaluation, .manual-evaluation {
  margin-top: 16px;
  padding: 16px;
  background: #f5f7fa;
  border-radius: 4px;
}

.ai-evaluation h5, .manual-evaluation h5 {
  margin-bottom: 8px;
  color: #606266;
}

.ai-evaluation pre {
  white-space: pre-wrap;
  word-break: break-all;
  font-family: inherit;
  margin: 0;
}

/* ============ AI对话反馈 ============ */
.ai-chat-container {
  display: flex;
  flex-direction: column;
  height: 65vh;
  min-height: 500px;
}

.deduction-panel {
  background: #fef0f0;
  border: 1px solid #fbc4c4;
  border-radius: 8px;
  padding: 12px 16px;
  margin-bottom: 12px;
  flex-shrink: 0;
}

.deduction-panel-title {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 14px;
  font-weight: bold;
  color: #e6a23c;
  margin-bottom: 10px;
}

.deduction-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.deduction-tag {
  cursor: pointer;
  transition: all 0.2s;
  font-size: 13px;
}

.deduction-tag:hover {
  transform: scale(1.05);
  box-shadow: 0 2px 8px rgba(0,0,0,0.15);
}

.deduction-score {
  margin-left: 4px;
  opacity: 0.8;
  font-weight: bold;
}

.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 8px 4px;
  display: flex;
  flex-direction: column;
  gap: 16px;
  background: #f8f9fb;
  border-radius: 8px;
  margin-bottom: 12px;
}

.chat-message {
  display: flex;
  gap: 10px;
  align-items: flex-start;
}

.user-message {
  flex-direction: row-reverse;
}

.message-avatar {
  flex-shrink: 0;
}

.message-content {
  max-width: 75%;
  min-width: 100px;
}

.user-message .message-content {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.message-sender {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}

.message-text {
  padding: 10px 14px;
  border-radius: 12px;
  font-size: 14px;
  line-height: 1.6;
  word-break: break-word;
}

.ai-message .message-text {
  background: #ffffff;
  border: 1px solid #e4e7ed;
  color: #303133;
}

.user-message .message-text {
  background: #409eff;
  color: #ffffff;
}

.message-text code {
  background: rgba(0,0,0,0.08);
  padding: 2px 6px;
  border-radius: 3px;
  font-size: 13px;
}

.message-time {
  font-size: 11px;
  color: #c0c4cc;
  margin-top: 4px;
}

/* 打字指示器 */
.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 12px 14px;
  background: #ffffff;
  border: 1px solid #e4e7ed;
  border-radius: 12px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #c0c4cc;
  animation: typing 1.4s infinite both;
}

.typing-indicator span:nth-child(2) { animation-delay: 0.2s; }
.typing-indicator span:nth-child(3) { animation-delay: 0.4s; }

@keyframes typing {
  0%, 60%, 100% { transform: translateY(0); opacity: 0.4; }
  30% { transform: translateY(-6px); opacity: 1; }
}

.quick-asks {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  flex-shrink: 0;
}

.quick-asks-label {
  font-size: 13px;
  color: #909399;
  white-space: nowrap;
}

.chat-input-area {
  display: flex;
  gap: 10px;
  align-items: flex-end;
  flex-shrink: 0;
}

.chat-input-area .el-textarea {
  flex: 1;
}

.send-btn {
  flex-shrink: 0;
  height: 40px;
}

/* 消息文本中的样式修正 */
.user-message .message-text code {
  background: rgba(255,255,255,0.2);
  color: #fff;
}
</style>
