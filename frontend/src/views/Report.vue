<template>
  <div class="page-container">
    <h2 class="page-title">{{ pageTitle }}</h2>

    <!-- 教师端：搜索表单 -->
    <div class="search-form" v-if="isTeacher">
      <el-form :inline="true" :model="searchForm">
        <el-form-item label="课程">
          <el-select v-model="searchForm.courseId" placeholder="全部课程" clearable filterable style="width: 200px" @change="handleCourseChange">
            <el-option v-for="c in myCourses" :key="c.id" :label="c.courseName" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="班级">
          <el-select v-model="searchForm.classId" placeholder="全部班级" clearable filterable style="width: 200px">
            <el-option v-for="c in classes" :key="c.id" :label="c.className" :value="c.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="状态">
          <el-select v-model="searchForm.status" placeholder="全部" clearable style="width: 130px">
            <el-option label="待评价" :value="0" />
            <el-option label="已评价" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" @click="handleSearch">查询</el-button>
          <el-button @click="handleReset">重置</el-button>
        </el-form-item>
      </el-form>
    </div>

    <!-- 数据表格 -->
    <div class="table-container">
      <el-empty v-if="!loading && tableData.length === 0" description="暂无数据">
        <template #extra>
          <el-button type="primary" @click="loadData">刷新数据</el-button>
          <p v-if="isTeacher" style="color: #909399; font-size: 13px; margin-top: 8px;">
            学生提交作业后，报告会自动出现在此列表中。
          </p>
        </template>
      </el-empty>

      <el-table v-else :data="tableData" stripe v-loading="loading">
        <el-table-column prop="studentName" label="学生" width="90" fixed />
        <el-table-column prop="className" label="班级" width="120" />
        <el-table-column prop="courseName" label="课程" width="140" show-overflow-tooltip />
        <el-table-column :prop="isTeacher ? 'reportTitle' : 'title'" label="报告标题" min-width="160" show-overflow-tooltip />
        <el-table-column prop="fileName" label="文件名" width="140" show-overflow-tooltip v-if="isTeacher" />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag v-if="getStatus(row) === 1" type="success" size="small">已评价</el-tag>
            <el-tag v-else type="warning" size="small">待评价</el-tag>
          </template>
        </el-table-column>

        <!-- 教师端：评分明细 -->
        <template v-if="isTeacher">
          <el-table-column label="完整性" width="85" align="center">
            <template #default="{ row }">
              <span v-if="getStatus(row) === 1 && !hasDynamicScores(row)">{{ row.completenessScore }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="规范性" width="85" align="center">
            <template #default="{ row }">
              <span v-if="getStatus(row) === 1 && !hasDynamicScores(row)">{{ row.specificationScore }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
          <el-table-column label="知识点" width="85" align="center">
            <template #default="{ row }">
              <span v-if="getStatus(row) === 1 && !hasDynamicScores(row)">{{ row.knowledgeScore }}</span>
              <span v-else class="text-muted">-</span>
            </template>
          </el-table-column>
        </template>

        <el-table-column label="综合总分" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="getStatus(row) === 1" :type="getScoreTagType(row.totalScore)" size="small">
              {{ row.totalScore }}分
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column v-if="isTeacher" label="评价方式" width="95" align="center">
          <template #default="{ row }">
            <el-tag v-if="getStatus(row) === 1" :type="row.isAi === 1 ? 'primary' : 'info'" size="small">
              {{ row.isAi === 1 ? 'AI批改' : '人工评定' }}
            </el-tag>
            <span v-else class="text-muted">-</span>
          </template>
        </el-table-column>

        <el-table-column :prop="isTeacher ? 'evaluateTime' : 'createTime'" label="时间" width="160">
          <template #default="{ row }">
            {{ formatTime(isTeacher ? row.evaluateTime : row.createTime) }}
          </template>
        </el-table-column>

        <!-- 操作列 -->
        <el-table-column label="操作" :width="isTeacher ? 370 : 220" align="center" fixed="right">
          <template #default="{ row }">
            <div class="action-buttons">
              <el-button type="primary" size="small" plain @click="handleView(row)">详情</el-button>
              <el-button type="success" size="small" plain @click="handleDownload(row)">下载</el-button>

              <!-- 教师端专属操作 -->
              <template v-if="isTeacher">
                <el-button
                  v-if="getStatus(row) === 1"
                  type="warning" size="small" plain
                  @click="handleViewAnnotation(row)"
                >AI批注</el-button>
                <el-button
                  v-if="getStatus(row) === 1"
                  type="warning" size="small" plain
                  @click="handleEdit(row)"
                >修改评分</el-button>
                <el-button
                  v-if="getStatus(row) !== 1"
                  type="success" size="small" plain
                  @click="handleQuickEvaluate(row)"
                >AI评价</el-button>
                <el-button type="danger" size="small" plain @click="handleDelete(row)">删除</el-button>
              </template>
            </div>
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

    <!-- ========== 详情弹窗 ========== -->
    <el-dialog v-model="detailVisible" title="报告评价详情" width="820px">
      <div v-if="currentDetail" v-loading="detailLoading">
        <el-descriptions :column="2" border>
          <el-descriptions-item label="学生">{{ currentDetail.studentName }}</el-descriptions-item>
          <el-descriptions-item label="班级">{{ currentDetail.className }}</el-descriptions-item>
          <el-descriptions-item label="课程">{{ currentDetail.courseName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="报告标题">{{ currentDetail.reportTitle || currentDetail.title }}</el-descriptions-item>
          <el-descriptions-item label="文件名">{{ currentDetail.fileName || '-' }}</el-descriptions-item>
          <el-descriptions-item label="状态">
            <el-tag :type="(getStatus(currentDetail) === 1) ? 'success' : 'warning'">
              {{ getStatus(currentDetail) === 1 ? '已评价' : '待评价' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item v-if="isTeacher" label="评价方式">
            <el-tag :type="currentDetail.isAi === 1 ? 'primary' : 'info'">
              {{ currentDetail.isAi === 1 ? 'AI智能批改' : '人工评定' }}
            </el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="最终总分" :span="2">
            <b style="font-size: 18px; color: #409eff;">{{ currentDetail.totalScore || '-' }} 分</b>
          </el-descriptions-item>
        </el-descriptions>

        <!-- AI批注入口 -->
        <div v-if="isTeacher && currentDetail.reportId" style="margin-top: 16px; text-align: center;">
          <el-button type="primary" @click="openAnnotationViewer(currentDetail.reportId)">
            <el-icon style="margin-right: 4px;"><EditPen /></el-icon>
            查看AI批注
          </el-button>
          <span style="margin-left: 12px; font-size: 12px; color: #909399;">
            AI逐段分析报告原文，精确定位问题并给出修改建议
          </span>
        </div>

        <!-- 自定义动态指标 -->
        <div v-if="parsedDynamicScores" class="score-section">
          <h4 style="color: #e6a23c;"><el-icon><Compass /></el-icon> 教师定制化指标考核分布明细</h4>
          <div class="dynamic-scores-wrapper">
            <div v-for="(val, key) in parsedDynamicScores" :key="key" class="dynamic-score-card">
              <div class="dynamic-score-meta">
                <span class="dynamic-key-name">{{ key }}</span>
                <span class="dynamic-key-val">得分：<b>{{ val.score }}</b> 分</span>
              </div>
              <el-progress
                :percentage="Math.min(Math.round((val.score || 0) * 2.5), 100)"
                :status="val.score >= 60 ? 'success' : 'exception'"
                :stroke-width="12"
              />
              <p class="dynamic-key-comment" v-if="val.comment">
                <el-icon><ChatLineSquare /></el-icon> 维度评语：{{ val.comment }}
              </p>
            </div>
          </div>
        </div>

        <!-- 传统三大指标 -->
        <div v-else-if="currentDetail.totalScore" class="score-section">
          <h4>评分详情</h4>
          <el-row :gutter="20">
            <el-col :span="6">
              <div class="score-item">
                <p class="label">完整性 (30分)</p>
                <p class="value">{{ currentDetail.completenessScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">规范性 (30分)</p>
                <p class="value">{{ currentDetail.specificationScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item">
                <p class="label">知识点 (40分)</p>
                <p class="value">{{ currentDetail.knowledgeScore }}</p>
              </div>
            </el-col>
            <el-col :span="6">
              <div class="score-item total">
                <p class="label">综合总分</p>
                <p class="value">{{ currentDetail.totalScore }}</p>
              </div>
            </el-col>
          </el-row>
        </div>

        <!-- AI改进建议 -->
        <div v-if="parsedSuggestions.length" class="evaluation-content" style="background: #f0f9eb; border: 1px solid #c2e7b0;">
          <h5 style="color: #67c23a; font-weight: bold; margin: 0 0 8px 0;">
            <el-icon><Opportunity /></el-icon> AI改进建议
          </h5>
          <ul style="margin: 0; padding-left: 20px; color: #606266; font-size: 13px; line-height: 1.8;">
            <li v-for="(s, i) in parsedSuggestions" :key="i">{{ s }}</li>
          </ul>
        </div>

        <!-- 教师终审备注 -->
        <div v-if="currentDetail.manualEvaluation" class="evaluation-content">
          <h5>教师终审定性备注</h5>
          <p style="margin: 0; font-size: 13px; color: #303133;">{{ currentDetail.manualEvaluation }}</p>
        </div>

        <!-- 学生端：AI评价原文 -->
        <div v-if="!isTeacher && currentDetail.aiEvaluation" class="evaluation-content">
          <h5>AI评价详情</h5>
          <pre style="white-space: pre-wrap; word-break: break-all; margin: 0;">{{ formatJson(currentDetail.aiEvaluation) }}</pre>
        </div>
      </div>
      <template #footer>
        <el-button @click="detailVisible = false">关闭</el-button>
      </template>
    </el-dialog>

    <!-- ========== 修改评分弹窗 ========== -->
    <el-dialog v-model="editVisible" title="教学质检 - 教师人工订正评分" width="520px">
      <el-form ref="formRef" :model="form" label-width="110px">
        <div v-if="form.isCustom">
          <div style="background: #fff8f8; border: 1px solid #fde2e2; border-radius: 4px; padding: 10px 14px; margin-bottom: 16px; font-size: 13px; color: #f56c6c;">
            当前作业启用大模型自定义评分标准。修改各单项分值后将重算总分。
          </div>
          <el-form-item v-for="(item, key) in form.dynamicScores" :key="key" :label="String(key)">
            <el-input-number v-model="item.score" :min="0" :max="100" :precision="1" :step="1" />
          </el-form-item>
        </div>
        <div v-else>
          <el-form-item label="完整性得分">
            <el-input-number v-model="form.completenessScore" :min="0" :max="30" :precision="1" />
          </el-form-item>
          <el-form-item label="规范性得分">
            <el-input-number v-model="form.specificationScore" :min="0" :max="30" :precision="1" />
          </el-form-item>
          <el-form-item label="知识点得分">
            <el-input-number v-model="form.knowledgeScore" :min="0" :max="40" :precision="1" />
          </el-form-item>
        </div>
        <el-form-item label="审定折合总分">
          <el-input-number v-model="form.totalScore" :min="0" :max="100" :precision="1" readonly style="background: #f5f7fa;" />
        </el-form-item>
        <el-form-item label="教师终审备注">
          <el-input v-model="form.manualEvaluation" type="textarea" :rows="3" placeholder="请输入人工修正或最终定性评语..." />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="editVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmitEdit">保存修正</el-button>
      </template>
    </el-dialog>

    <!-- ========== AI全格式评价弹窗（三步流程） ========== -->
    <el-dialog v-model="aiDialogVisible" title="AI智能评价" width="700px" :close-on-click-modal="false">
      <el-steps :active="aiStep" align-center style="margin-bottom: 30px">
        <el-step title="评分标准" description="确认评分维度" />
        <el-step title="AI评价中" description="大模型分析中" />
        <el-step title="评价完成" description="查看完整报告" />
      </el-steps>

      <!-- 步骤1：评分标准确认 -->
      <div v-if="aiStep === 0">
        <el-alert title="评分标准确认" type="info" :closable="false" style="margin-bottom: 16px">
          请选择评分标准。可自动根据成果类型生成、或使用教师发布的报告要求标准。
        </el-alert>
        <el-form label-width="100px">
          <el-form-item label="选择报告">
            <el-select v-model="aiForm.reportId" placeholder="请选择待评价的学生报告" filterable style="width:100%" @change="onAiReportChange">
              <el-option v-for="r in pendingReports" :key="r.id" :label="`${r.studentName} - ${r.reportTitle || r.title}`" :value="r.id" />
            </el-select>
          </el-form-item>
          <el-divider />
          <el-form-item label="评分标准">
            <el-radio-group v-model="aiForm.rubricMode" style="margin-bottom: 12px" @change="handleRubricModeChange">
              <el-radio value="auto">自动生成（根据成果类型智能推荐）</el-radio>
              <el-radio value="default">使用默认标准（完整/规范/质量）</el-radio>
              <el-radio value="requirement">报告要求标准</el-radio>
            </el-radio-group>
          </el-form-item>

          <!-- 自动生成的评分标准预览 -->
          <el-form-item v-if="aiForm.rubricMode === 'auto' && aiForm.rubricPreview.length > 0" label="生成结果">
            <div style="background:#f5f7fa;padding:12px;border-radius:4px;width:100%">
              <el-table :data="aiForm.rubricPreview" size="small" border>
                <el-table-column prop="name" label="维度" width="120" />
                <el-table-column prop="weight" label="权重(%)" width="80" />
                <el-table-column prop="maxScore" label="满分" width="80" />
                <el-table-column prop="description" label="评分要点" show-overflow-tooltip />
              </el-table>
            </div>
          </el-form-item>

          <!-- 报告要求标准预览 -->
          <el-form-item v-if="aiForm.rubricMode === 'requirement' && aiForm.rubricPreview.length > 0" label="报告要求标准">
            <div style="background:#f0f9eb;padding:12px;border-radius:4px;width:100%">
              <el-table :data="aiForm.rubricPreview" size="small" border>
                <el-table-column prop="name" label="考核维度" width="140" />
                <el-table-column prop="weight" label="权重(%)" width="80" />
                <el-table-column prop="maxScore" label="满分" width="80" />
                <el-table-column prop="description" label="考核细则" show-overflow-tooltip />
              </el-table>
              <div style="margin-top: 8px; font-size: 12px; color: #67c23a;">
                <el-icon><CircleCheckFilled /></el-icon> 已加载教师发布的自定义评分标准（共{{ aiForm.rubricPreview.length }}个维度）
              </div>
            </div>
          </el-form-item>

          <!-- 报告要求标准 - 无自定义标准时显示正文 -->
          <el-form-item v-if="aiForm.rubricMode === 'requirement' && aiForm.rubricPreview.length === 0 && aiForm.requirementContent" label="报告要求标准">
            <el-alert
              title="该报告要求未设定自定义评分标准，将使用教师的报告要求正文作为评价参考"
              type="info"
              :closable="false"
              show-icon
              style="margin-bottom: 8px;"
            />
            <div style="background:#f5f7fa;padding:12px;border-radius:4px;width:100%;max-height:200px;overflow-y:auto;white-space:pre-wrap;line-height:1.6;font-size:13px;color:#606266;">
              {{ aiForm.requirementContent }}
            </div>
          </el-form-item>

          <!-- 报告要求标准 - 加载中 -->
          <el-form-item v-if="aiForm.rubricMode === 'requirement' && requirementLoading" label="报告要求标准">
            <div style="color:#909399;font-size:13px;">
              <el-icon class="is-loading"><Loading /></el-icon> 正在加载教师发布的报告要求标准...
            </div>
          </el-form-item>
        </el-form>
        <div style="text-align:right;margin-top:12px">
          <el-button v-if="aiForm.rubricMode === 'auto' && aiForm.reportId" type="success" @click="handleAutoGenerateRubric" :loading="rubricGenerating">
            生成评分标准
          </el-button>
        </div>
      </div>

      <!-- 步骤2：AI评价中 -->
      <div v-if="aiStep === 1" style="text-align:center;padding:40px">
        <el-icon class="is-loading" :size="48" color="#409eff"><Loading /></el-icon>
        <p style="margin-top:16px;color:#606266">AI 正在分析成果内容并生成评价报告...</p>
        <p style="font-size:12px;color:#909399">正在解析文件内容 → 匹配评分标准 → 生成7段式报告</p>
      </div>

      <!-- 步骤3：完整报告展示 -->
      <div v-if="aiStep === 2 && fullReport">
        <el-tabs v-model="reportTab">
          <el-tab-pane label="📋 成果概览" name="overview">
            <el-descriptions :column="2" border size="small" v-if="fullReport.overview">
              <el-descriptions-item label="文件数">{{ fullReport.overview.fileCount }}</el-descriptions-item>
              <el-descriptions-item label="文件类型">{{ fullReport.overview.fileTypes }}</el-descriptions-item>
              <el-descriptions-item label="成果类型">{{ fullReport.overview.resultType }}</el-descriptions-item>
              <el-descriptions-item label="完成度">{{ fullReport.overview.completeness }}</el-descriptions-item>
            </el-descriptions>
            <div v-if="fullReport.documentSummary" style="margin-top:16px">
              <h4>📄 文档摘要</h4>
              <pre style="background:#f5f7fa;padding:12px;border-radius:4px;font-size:13px;white-space:pre-wrap">{{ formatJson(fullReport.documentSummary) }}</pre>
            </div>
          </el-tab-pane>
          <el-tab-pane label="💻 代码/截图" name="media" v-if="fullReport.imageSummary || fullReport.codeSummary">
            <div v-if="fullReport.imageSummary" style="margin-bottom:16px">
              <h4>🖼️ 截图分析</h4>
              <pre style="background:#f5f7fa;padding:12px;border-radius:4px;font-size:13px;white-space:pre-wrap">{{ formatJson(fullReport.imageSummary) }}</pre>
            </div>
            <div v-if="fullReport.codeSummary">
              <h4>💻 代码分析</h4>
              <pre style="background:#f5f7fa;padding:12px;border-radius:4px;font-size:13px;white-space:pre-wrap">{{ formatJson(fullReport.codeSummary) }}</pre>
            </div>
          </el-tab-pane>
          <el-tab-pane label="📊 评分详情" name="scoring">
            <div v-if="fullReport.scoringDetails && fullReport.scoringDetails.length">
              <el-table :data="fullReport.scoringDetails" border size="small">
                <el-table-column prop="dimension" label="评分维度" width="120" />
                <el-table-column prop="maxScore" label="满分" width="70" />
                <el-table-column prop="score" label="得分" width="70">
                  <template #default="{ row: r }">
                    <el-tag :type="getScoreRatioType(r.score, r.maxScore)">{{ r.score }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column prop="comment" label="评分说明" show-overflow-tooltip min-width="200" />
              </el-table>
              <div style="text-align:center;margin-top:16px">
                <span style="font-size:32px;font-weight:bold;color:#409eff">{{ fullReport.totalScore }}</span>
                <span style="font-size:16px;color:#909399"> / 100 分</span>
              </div>
            </div>
          </el-tab-pane>
          <el-tab-pane label="🎯 综合评价" name="evaluation">
            <div v-if="fullReport.overallEvaluation">
              <el-alert :title="'✅ 优点：' + fullReport.overallEvaluation.strengths" type="success" :closable="false" style="margin-bottom:12px" />
              <el-alert :title="'⚠️ 不足：' + fullReport.overallEvaluation.weaknesses" type="warning" :closable="false" />
            </div>
          </el-tab-pane>
          <el-tab-pane label="💡 改进建议" name="suggestions">
            <el-empty v-if="!fullReport.suggestions?.length" description="无改进建议" />
            <el-timeline v-else>
              <el-timeline-item v-for="(sug, i) in fullReport.suggestions" :key="i" :timestamp="'建议' + (i + 1)" placement="top">
                <el-card>{{ sug }}</el-card>
              </el-timeline-item>
            </el-timeline>
          </el-tab-pane>
          <el-tab-pane label="⚠️ 核查要点" name="checks">
            <el-empty v-if="!fullReport.checkPoints?.length" description="✅ 无需特别核查" />
            <ul v-else style="line-height:2">
              <li v-for="(cp, i) in fullReport.checkPoints" :key="i">{{ cp }}</li>
            </ul>
          </el-tab-pane>
        </el-tabs>
      </div>

      <template #footer>
        <el-button @click="aiDialogVisible = false">关闭</el-button>
        <el-button v-if="aiStep === 0" type="primary" @click="handleStartAiEvaluate" :loading="aiLoading" :disabled="!aiForm.reportId">
          开始AI评价
        </el-button>
        <el-button v-if="aiStep === 2" type="success" @click="finishAiEvaluate">完成，刷新列表</el-button>
      </template>
    </el-dialog>

    <!-- ========== AI智能批注全屏弹窗 ========== -->
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
          <el-empty :description="annotationError">
            <el-button type="primary" @click="retryAnnotation">重试</el-button>
          </el-empty>
        </div>
        <AnnotationViewer
          v-else-if="!annotationLoading && annotationData.length !== undefined"
          :report-content="annotationReportContent"
          :annotations="annotationData"
        />
      </div>
      <template #footer>
        <div style="display: flex; justify-content: space-between; align-items: center;">
          <div>
            <el-button v-if="annotationData.length > 0" type="danger" plain @click="handleDeleteAnnotations">
              清除批注
            </el-button>
          </div>
          <div>
            <el-button @click="annotationDialogVisible = false">关闭</el-button>
            <el-button type="primary" @click="handleRegenerateAnnotations" :loading="annotationLoading">
              重新生成批注
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>

    <!-- 悬浮按钮：AI智能评价 -->
    <el-button v-if="isTeacher" type="success" class="ai-float-btn" @click="openAiDialog">
      <el-icon><MagicStick /></el-icon>
      AI智能评价
    </el-button>
  </div>
</template>

<script setup>
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  getEvaluationList, getEvaluation, manualEvaluate, aiEvaluate,
  aiFullEvaluate, autoGenerateRubric, deleteEvaluation,
  generateAnnotations, getAnnotations, deleteAnnotations
} from '../api/evaluation'
import { getReportList, deleteReport, getReport, downloadReport } from '../api/report'
import { getAllClasses } from '../api/class'
import { getMyCourses } from '../api/course'
import { getReportRequirement, getRequirementCriteria } from '../api/reportRequirement'
import { useUserStoreHook } from '../stores'
import AnnotationViewer from '../components/AnnotationViewer.vue'
import {
  EditPen, Compass, ChatLineSquare, Opportunity,
  Loading, MagicStick, CircleCheckFilled
} from '@element-plus/icons-vue'

const route = useRoute()
const userStore = useUserStoreHook()
const isTeacher = computed(() => userStore.userInfo?.role === 2)
const isStudent = computed(() => userStore.userInfo?.role === 3)

const pageTitle = computed(() => isStudent.value ? '我的实训报告' : '报告与评价')

// ==================== 核心状态 ====================
const loading = ref(false)
const detailVisible = ref(false)
const detailLoading = ref(false)
const editVisible = ref(false)
const aiDialogVisible = ref(false)
const aiLoading = ref(false)
const formRef = ref()

const classes = ref([])
const myCourses = ref([])
const currentDetail = ref(null)
const pendingReports = ref([])

const searchForm = reactive({ courseId: null, classId: null, status: null })
const pagination = reactive({ pageNum: 1, pageSize: 10, total: 0 })
const tableData = ref([])

// 修改评分表单
const form = reactive({
  id: null, isCustom: false,
  completenessScore: 0, specificationScore: 0, knowledgeScore: 0,
  dynamicScores: {}, totalScore: 0, manualEvaluation: ''
})

// AI评价表单
const aiForm = reactive({
  reportId: null,
  rubricMode: 'auto',
  rubricPreview: [],
  requirementContent: ''
})

// AI批注状态
const annotationDialogVisible = ref(false)
const annotationLoading = ref(false)
const annotationError = ref('')
const annotationData = ref([])
const annotationReportContent = ref('')
const annotationCurrentReportId = ref(null)

// AI评价步骤
const aiStep = ref(0)
const rubricGenerating = ref(false)
const requirementLoading = ref(false)
const fullReport = ref(null)
const reportTab = ref('overview')

// ==================== 计算属性 ====================
const getStatus = (row) => {
  return row.reportStatus != null ? row.reportStatus : (row.status != null ? row.status : null)
}

const hasDynamicScores = (row) => {
  return !!(row.dynamicScoresJson)
}

const parsedDynamicScores = computed(() => {
  if (!currentDetail.value || !currentDetail.value.dynamicScoresJson) return null
  try {
    return JSON.parse(currentDetail.value.dynamicScoresJson).scores || null
  } catch {
    try {
      return JSON.parse(currentDetail.value.aiEvaluation).scores || null
    } catch { return null }
  }
})

const parsedSuggestions = computed(() => {
  if (!currentDetail.value || !currentDetail.value.aiEvaluation) return []
  try {
    return JSON.parse(currentDetail.value.aiEvaluation).suggestions || []
  } catch { return [] }
})

// ==================== 监听器 ====================
watch(() => [form.completenessScore, form.specificationScore, form.knowledgeScore], () => {
  if (!form.isCustom) {
    form.totalScore = form.completenessScore + form.specificationScore + form.knowledgeScore
  }
})

watch(() => form.dynamicScores, (v) => {
  if (form.isCustom && v) {
    form.totalScore = Object.values(v).reduce((s, item) => s + (parseFloat(item.score) || 0), 0)
  }
}, { deep: true })

// ==================== 工具方法 ====================
const getScoreTagType = (s) => {
  if (!s && s !== 0) return 'info'
  if (s >= 90) return 'success'
  if (s >= 80) return 'primary'
  if (s >= 60) return 'warning'
  return 'danger'
}

const getScoreRatioType = (score, maxScore) => {
  if (!score || !maxScore) return 'info'
  const r = score / maxScore
  if (r >= 0.9) return 'success'
  if (r >= 0.75) return 'primary'
  if (r >= 0.6) return 'warning'
  return 'danger'
}

const formatTime = (t) => {
  if (!t) return '-'
  const d = new Date(t)
  const pad = (n) => String(n).padStart(2, '0')
  return `${pad(d.getMonth() + 1)}-${pad(d.getDate())} ${pad(d.getHours())}:${pad(d.getMinutes())}`
}

const formatJson = (data) => {
  if (!data) return ''
  if (typeof data === 'string') {
    try { return JSON.stringify(JSON.parse(data), null, 2) } catch { return data }
  }
  return JSON.stringify(data, null, 2)
}

// ==================== 数据加载 ====================
const loadData = async () => {
  loading.value = true
  try {
    if (isTeacher.value) {
      // 教师端：使用评价列表API（含完整评价数据）
      const params = { ...pagination }
      if (searchForm.courseId) params.courseId = searchForm.courseId
      if (searchForm.classId) params.classId = searchForm.classId
      if (searchForm.status != null && searchForm.status !== '') {
        params.reportStatus = searchForm.status
      }
      const res = await getEvaluationList(params)
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    } else {
      // 学生端：使用报告列表API
      const params = { ...pagination }
      const courseId = route.query.courseId ? Number(route.query.courseId) : null
      if (courseId) params.courseId = courseId
      const res = await getReportList(params)
      tableData.value = res.data.records || []
      pagination.total = res.data.total || 0
    }
  } catch (error) {
    console.error('加载列表失败:', error)
    ElMessage.error('加载列表失败: ' + (error.message || '请检查网络'))
    tableData.value = []
    pagination.total = 0
  } finally {
    loading.value = false
  }
}

const loadClasses = async () => {
  try { const res = await getAllClasses(); classes.value = res.data || [] } catch {}
}

const loadMyCourses = async () => {
  try { const res = await getMyCourses(); myCourses.value = res.data || [] } catch {}
}

const loadPendingReports = async () => {
  try {
    const res = await getReportList({ status: 0, pageNum: 1, pageSize: 200 })
    pendingReports.value = (res.data.records || []).map(r => ({
      id: r.id, studentName: r.studentName,
      reportTitle: r.title, title: r.title,
      requirementId: r.requirementId
    }))
  } catch {}
}

// ==================== 搜索 ====================
const handleSearch = () => { pagination.pageNum = 1; loadData() }
const handleReset = () => {
  searchForm.courseId = null; searchForm.classId = null; searchForm.status = null
  handleSearch()
}
const handleCourseChange = () => { handleSearch() }

// ==================== 查看详情 ====================
const handleView = async (row) => {
  detailLoading.value = true
  detailVisible.value = true
  try {
    if (isTeacher.value && row.id) {
      const res = await getEvaluation(row.id)
      currentDetail.value = res.data
    } else {
      const res = await getReport(row.id)
      currentDetail.value = res.data
    }
  } catch (error) {
    console.error(error)
    ElMessage.error('获取详情失败')
  } finally {
    detailLoading.value = false
  }
}

// ==================== 下载 ====================
const handleDownload = async (row) => {
  try {
    const res = await downloadReport(row.id || row.reportId)
    if (res.data instanceof Blob && res.data.type === 'application/json') {
      const text = await res.data.text()
      ElMessage.error(JSON.parse(text).message || '下载失败')
      return
    }
    const disposition = res.headers['content-disposition']
    let filename = row.fileName || 'report'
    if (disposition) {
      const m = disposition.match(/filename[^;=\n]*=((['"]).*?\2|[^;\n]*)/)
      if (m?.[1]) filename = decodeURIComponent(m[1].replace(/['"]/g, ''))
    }
    const url = window.URL.createObjectURL(new Blob([res.data]))
    const a = document.createElement('a')
    a.href = url; a.download = filename
    document.body.appendChild(a); a.click()
    document.body.removeChild(a); window.URL.revokeObjectURL(url)
    ElMessage.success('下载成功')
  } catch (error) {
    if (error.response?.status === 404) {
      ElMessage.error('文件不存在或已被删除')
    } else {
      ElMessage.error('下载失败')
    }
  }
}

// ==================== 删除 ====================
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm('确定删除该记录吗？', '提示', { type: 'warning' })
  } catch { return }

  try {
    if (isTeacher.value && row.id) {
      await deleteEvaluation(row.id)
    } else {
      await deleteReport(row.id)
    }
    ElMessage.success('删除成功')
    loadData()
  } catch (error) {
    console.error(error)
  }
}

// ==================== 修改评分 ====================
const handleEdit = (row) => {
  currentDetail.value = row
  form.id = row.id
  form.manualEvaluation = row.manualEvaluation || ''
  form.totalScore = parseFloat(row.totalScore) || 0

  if (row.dynamicScoresJson) {
    form.isCustom = true
    try {
      form.dynamicScores = JSON.parse(JSON.stringify(JSON.parse(row.dynamicScoresJson).scores || {}))
    } catch { form.dynamicScores = {} }
  } else {
    form.isCustom = false
    form.completenessScore = parseFloat(row.completenessScore) || 0
    form.specificationScore = parseFloat(row.specificationScore) || 0
    form.knowledgeScore = parseFloat(row.knowledgeScore) || 0
    form.dynamicScores = {}
  }
  editVisible.value = true
}

const handleSubmitEdit = async () => {
  const submitData = { totalScore: form.totalScore, manualEvaluation: form.manualEvaluation }
  if (form.isCustom) {
    const raw = currentDetail.value?.dynamicScoresJson ? JSON.parse(currentDetail.value.dynamicScoresJson) : {}
    raw.scores = form.dynamicScores; raw.totalScore = form.totalScore
    submitData.dynamicScores = raw
  } else {
    submitData.completenessScore = form.completenessScore
    submitData.specificationScore = form.specificationScore
    submitData.knowledgeScore = form.knowledgeScore
  }
  try {
    await manualEvaluate(form.id, submitData)
    ElMessage.success('人工评分审定保存成功！')
    editVisible.value = false
    loadData()
  } catch (error) { console.error(error) }
}

// ==================== AI快速评价 ====================
const handleQuickEvaluate = async (row) => {
  const reportId = row.reportId || row.id
  if (!reportId) { ElMessage.warning('报告信息不完整'); return }
  try {
    await ElMessageBox.confirm(
      `确定要对「${row.studentName}」的报告「${row.reportTitle || row.title}」进行AI智能评价吗？`,
      '确认AI评价', { type: 'info', confirmButtonText: '开始评价', cancelButtonText: '取消' }
    )
  } catch { return }
  aiLoading.value = true
  try {
    await aiEvaluate(reportId)
    ElMessage.success('AI评价完成！')
    loadData()
  } catch (error) {
    ElMessage.error(error.message || 'AI评价失败')
  } finally { aiLoading.value = false }
}

// ==================== AI全格式评价弹窗 ====================
const openAiDialog = () => {
  aiDialogVisible.value = true
  aiStep.value = 0
  aiForm.reportId = null
  aiForm.rubricMode = 'auto'
  aiForm.rubricPreview = []
  aiForm.requirementContent = ''
  fullReport.value = null
}

const onAiReportChange = () => {
  aiForm.rubricPreview = []
  aiForm.requirementContent = ''
  if (aiForm.rubricMode === 'requirement') {
    handleRubricModeChange('requirement')
  }
}

const handleAutoGenerateRubric = async () => {
  if (!aiForm.reportId) { ElMessage.warning('请先选择报告'); return }
  rubricGenerating.value = true
  try {
    const res = await autoGenerateRubric(aiForm.reportId)
    aiForm.rubricPreview = res.data || []
    ElMessage.success('评分标准已自动生成')
  } catch (error) { ElMessage.error('生成失败: ' + (error.message || '')) }
  finally { rubricGenerating.value = false }
}

const handleRubricModeChange = async (mode) => {
  if (mode !== 'requirement') {
    aiForm.requirementContent = ''
    return
  }

  if (!aiForm.reportId) {
    ElMessage.warning('请先选择报告')
    aiForm.rubricMode = 'auto'
    return
  }

  const selectedReport = pendingReports.value.find(r => r.id === aiForm.reportId)
  if (!selectedReport || !selectedReport.requirementId) {
    ElMessage.warning('该报告未关联任何报告要求，无法获取标准')
    aiForm.rubricMode = 'auto'
    return
  }

  requirementLoading.value = true
  aiForm.rubricPreview = []
  aiForm.requirementContent = ''

  try {
    const reqRes = await getReportRequirement(selectedReport.requirementId)
    const requirement = reqRes.data
    if (!requirement) {
      ElMessage.warning('未找到关联的报告要求')
      aiForm.rubricMode = 'auto'
      return
    }

    aiForm.requirementContent = requirement.content || ''

    if (requirement.hasCustomCriterion === 1) {
      const criteriaRes = await getRequirementCriteria(selectedReport.requirementId)
      const criteria = criteriaRes.data || []
      if (criteria.length > 0) {
        aiForm.rubricPreview = criteria.map(item => ({
          name: item.name || '',
          weight: item.weight || 0,
          maxScore: item.maxScore || item.weight,
          description: item.description || ''
        }))
        ElMessage.success(`已加载教师定义的标准：共${criteria.length}个考核维度`)
      } else {
        ElMessage.info('该报告要求未设置具体评分标准，将使用报告正文要求作为评价参考')
      }
    } else {
      ElMessage.info('该报告要求使用默认标准，将结合报告正文要求进行评价')
    }
  } catch (error) {
    console.error('加载报告要求标准失败:', error)
    ElMessage.error('加载报告要求标准失败')
    aiForm.rubricMode = 'auto'
  } finally {
    requirementLoading.value = false
  }
}

const handleStartAiEvaluate = async () => {
  if (!aiForm.reportId) { ElMessage.warning('请选择要进行评价的报告'); return }

  let rubricJson = null
  if (aiForm.rubricMode === 'auto' && aiForm.rubricPreview.length > 0) {
    rubricJson = JSON.stringify(aiForm.rubricPreview)
  } else if (aiForm.rubricMode === 'requirement') {
    if (aiForm.rubricPreview.length > 0) {
      rubricJson = JSON.stringify(aiForm.rubricPreview)
    } else if (aiForm.requirementContent) {
      rubricJson = JSON.stringify([{
        name: '内容完整度', weight: 40, maxScore: 40,
        description: '根据报告要求正文：' + aiForm.requirementContent.substring(0, 200)
      }, {
        name: '格式规范度', weight: 30, maxScore: 30,
        description: '格式规范，条理清晰'
      }, {
        name: '技术深度', weight: 30, maxScore: 30,
        description: '技术分析和理解深入'
      }])
    }
  }

  aiLoading.value = true; aiStep.value = 1
  try {
    const res = await aiFullEvaluate(aiForm.reportId, { rubricJson })
    let reportData = res.data
    if (res.data?.aiEvaluation) {
      try { reportData = JSON.parse(res.data.aiEvaluation) } catch { reportData = res.data }
    }
    fullReport.value = reportData; aiStep.value = 2
    ElMessage.success('AI全格式评价完成！')
  } catch (error) {
    ElMessage.error('AI评价失败: ' + (error.message || '请稍后重试'))
    aiStep.value = 0
  } finally { aiLoading.value = false }
}

const finishAiEvaluate = () => {
  aiDialogVisible.value = false; aiStep.value = 0
  fullReport.value = null; aiForm.reportId = null
  aiForm.rubricPreview = []; aiForm.requirementContent = ''; aiForm.rubricMode = 'auto'
  loadData(); loadPendingReports()
}

// ==================== AI批注 ====================
const handleViewAnnotation = async (row) => {
  const reportId = row.reportId || row.id
  annotationCurrentReportId.value = reportId
  detailVisible.value = false
  await openAnnotationViewer(reportId)
}

const openAnnotationViewer = async (reportId) => {
  if (!reportId) { ElMessage.warning('报告信息不完整'); return }
  annotationCurrentReportId.value = reportId
  annotationDialogVisible.value = true
  annotationLoading.value = true
  annotationError.value = ''

  try {
    const reportRes = await getReport(reportId)
    annotationReportContent.value = reportRes.data?.content || ''
  } catch { annotationReportContent.value = '' }

  try {
    const existing = await getAnnotations(reportId)
    if (existing.data?.length > 0) {
      annotationData.value = existing.data
      ElMessage.success(`已加载${existing.data.length}条批注`)
    } else {
      await doGenerateAnnotations(reportId)
    }
  } catch {
    await doGenerateAnnotations(reportId)
  } finally {
    annotationLoading.value = false
  }
}

const doGenerateAnnotations = async (reportId) => {
  annotationLoading.value = true; annotationError.value = ''
  try {
    const res = await generateAnnotations(reportId)
    annotationData.value = res.data || []
    if (annotationData.value.length === 0) {
      annotationError.value = 'AI未发现明显问题，报告质量较好！'
    } else {
      ElMessage.success(`AI智能批注完成，共生成${annotationData.value.length}条批注`)
    }
  } catch (error) {
    annotationError.value = 'AI批注生成失败: ' + (error.message || '请稍后重试')
    annotationData.value = []
  } finally { annotationLoading.value = false }
}

const handleRegenerateAnnotations = async () => {
  if (!annotationCurrentReportId.value) return
  await doGenerateAnnotations(annotationCurrentReportId.value)
}

const handleDeleteAnnotations = async () => {
  if (!annotationCurrentReportId.value) return
  try {
    await ElMessageBox.confirm('确定要删除该报告的所有AI批注吗？', '确认删除', { type: 'warning' })
    await deleteAnnotations(annotationCurrentReportId.value)
    annotationData.value = []
    ElMessage.success('批注已清除')
  } catch (error) {
    if (error !== 'cancel') console.error(error)
  }
}

const retryAnnotation = () => {
  if (annotationCurrentReportId.value) openAnnotationViewer(annotationCurrentReportId.value)
}

// ==================== 初始化 ====================
onMounted(() => {
  loadData()
  loadClasses()
  if (isTeacher.value) {
    loadMyCourses()
    loadPendingReports()
  }
})
</script>

<style scoped>
.page-container { overflow: hidden; }
.search-form :deep(.el-select) { width: 200px; }
.text-muted { color: #c0c4cc; }

.action-buttons {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  justify-content: center;
}

.pagination-container { margin-top: 16px; }

.score-section {
  margin-top: 20px;
  h4 { margin-bottom: 16px; color: #303133; }
}

.dynamic-scores-wrapper {
  background: #fafafa; border: 1px solid #e4e7ed;
  border-radius: 6px; padding: 16px;
}

.dynamic-score-card {
  background: #fff; border: 1px solid #e4e7ed;
  border-radius: 4px; padding: 12px 14px; margin-bottom: 12px;
}
.dynamic-score-meta {
  display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px;
}
.dynamic-key-name { font-weight: bold; color: #303133; font-size: 14px; }
.dynamic-key-val { font-size: 13px; color: #606266; b { color: #409eff; font-size: 15px; } }
.dynamic-key-comment {
  font-size: 12px; color: #e6a23c; margin: 6px 0 0 0;
  line-height: 1.4; background: #fffdf5; padding: 4px 6px; border-radius: 2px;
}

.score-item {
  text-align: center; padding: 16px; background: #f5f7fa; border-radius: 4px;
  .label { font-size: 14px; color: #909399; margin-bottom: 8px; }
  .value { font-size: 24px; font-weight: bold; color: #303133; }
  &.total .value { color: #409eff; }
}

.evaluation-content {
  margin-top: 16px; padding: 16px; background: #f5f7fa; border-radius: 4px;
  h5 { margin-bottom: 8px; color: #606266; font-weight: bold; }
}

.ai-float-btn {
  position: fixed; right: 40px; bottom: 100px; z-index: 100;
}
</style>
