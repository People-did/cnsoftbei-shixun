package com.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.Constants;
import com.training.common.PageResult;
import com.training.common.Result;
import com.training.dto.AnnotationDTO;
import com.training.dto.EvaluationDTO;
import com.training.entity.Course;
import com.training.entity.EvaluationRecord;
import com.training.entity.ReportAnnotation;
import com.training.entity.SysClass;
import com.training.entity.SysUser;
import com.training.entity.TrainingReport;
import com.training.entity.ReportRequirement;
import com.training.entity.EvaluationCriterion;
import com.training.mapper.CourseMapper;
import com.training.mapper.EvaluationRecordMapper;
import com.training.mapper.ReportAnnotationMapper;
import com.training.mapper.SysClassMapper;
import com.training.mapper.SysUserMapper;
import com.training.mapper.TrainingReportMapper;
import com.training.mapper.ReportRequirementMapper;
import com.training.mapper.EvaluationCriterionMapper;
import com.training.utils.AiUtils;
import com.training.utils.FileUtils;
import com.alibaba.fastjson2.JSON;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluationService extends ServiceImpl<EvaluationRecordMapper, EvaluationRecord> {
    
    private final EvaluationRecordMapper evaluationMapper;
    private final TrainingReportMapper reportMapper;
    private final SysUserMapper userMapper;
    private final SysClassMapper classMapper;
    private final CourseMapper courseMapper;
    private final ReportService reportService;
    private final AiUtils aiUtils;
    
    // 注入必要的关联老表持久层，无损扩容
    private final ReportRequirementMapper requirementMapper;
    private final EvaluationCriterionMapper criterionMapper;
    private final ReportAnnotationMapper annotationMapper;
    
    public PageResult<EvaluationDTO> pageList(Integer pageNum, Integer pageSize, Long reportId, 
                                               Long teacherId, Long classId, Long courseId,
                                               Integer currentRole, Long currentUserId) {
        Page<EvaluationRecord> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        
        if (reportId != null) {
            wrapper.eq(EvaluationRecord::getReportId, reportId);
        }
        if (teacherId != null) {
            wrapper.eq(EvaluationRecord::getTeacherId, teacherId);
        }
        
        if (currentRole != null && currentRole == Constants.ROLE_TEACHER) {
            LambdaQueryWrapper<Course> courseWrapper = new LambdaQueryWrapper<>();
            courseWrapper.eq(Course::getTeacherId, currentUserId);
            List<Course> teacherCourses = courseMapper.selectList(courseWrapper);
            List<Long> courseIds = teacherCourses.stream().map(Course::getId).toList();
            
            if (!courseIds.isEmpty()) {
                LambdaQueryWrapper<TrainingReport> reportWrapper = new LambdaQueryWrapper<>();
                reportWrapper.in(TrainingReport::getCourseId, courseIds);
                List<TrainingReport> reports = reportMapper.selectList(reportWrapper);
                List<Long> reportIds = reports.stream().map(TrainingReport::getId).toList();
                
                if (!reportIds.isEmpty()) {
                    wrapper.in(EvaluationRecord::getReportId, reportIds);
                } else {
                    wrapper.eq(EvaluationRecord::getId, -1L);
                }
            } else {
                wrapper.eq(EvaluationRecord::getId, -1L);
            }
        }
        
        // 学生角色：只能查看自己提交的报告的评价
        if (currentRole != null && currentRole == Constants.ROLE_STUDENT) {
            LambdaQueryWrapper<TrainingReport> studentReportWrapper = new LambdaQueryWrapper<>();
            studentReportWrapper.eq(TrainingReport::getStudentId, currentUserId);
            List<TrainingReport> studentReports = reportMapper.selectList(studentReportWrapper);
            List<Long> studentReportIds = studentReports.stream().map(TrainingReport::getId).toList();
            
            if (!studentReportIds.isEmpty()) {
                wrapper.in(EvaluationRecord::getReportId, studentReportIds);
            } else {
                wrapper.eq(EvaluationRecord::getId, -1L);
            }
        }
        
        wrapper.orderByDesc(EvaluationRecord::getEvaluateTime);
        
        Page<EvaluationRecord> result = evaluationMapper.selectPage(page, wrapper);
        
        List<EvaluationDTO> dtoList = result.getRecords().stream()
                .map(this::convertToDTO)
                .toList();
        
        return new PageResult<>(result.getTotal(), dtoList);
    }
    
    /**
     * 【重构升级】AI智能评价核心算法（支持在宿主层自动分流处理传统三大指标与新扩容自定义指标）
     */
    @Transactional
    public Result<EvaluationDTO> aiEvaluate(Long reportId, Long teacherId) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return Result.error("报告不存在");
        }
        
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvaluationRecord::getReportId, reportId);
        EvaluationRecord existing = evaluationMapper.selectOne(wrapper);
        
        try {
            // 1. 业务逻辑溯源：反查这份实训报告所对应的发布作业要求
            ReportRequirement requirement = null;
            // 假设你的 training_report 表中有字段标记了它对应哪一条报告要求，若通过标题或课程反查：
            // 此处采用最安全的兼容查找：基于课程ID获取当前进行中的最新作业要求进行匹配
            LambdaQueryWrapper<ReportRequirement> reqWrapper = new LambdaQueryWrapper<>();
            reqWrapper.eq(ReportRequirement::getCourseId, report.getCourseId());
            reqWrapper.orderByDesc(ReportRequirement::getCreateTime);
            List<ReportRequirement> requirements = requirementMapper.selectList(reqWrapper);
            if (!requirements.isEmpty()) {
                requirement = requirements.get(0);
            }

            EvaluationRecord evaluation = (existing != null) ? existing : new EvaluationRecord();
            evaluation.setReportId(reportId);
            evaluation.setTeacherId(teacherId);
            evaluation.setIsAi(Constants.EVALUATION_AI);
            evaluation.setEvaluateTime(LocalDateTime.now());
            evaluation.setUpdateTime(LocalDateTime.now());

            // 2. 智能化分流决策树判定
            if (requirement != null && requirement.getHasCustomCriterion() == 1) {
                // 【自定义标准路由】
                // 级联查询出当前作业关联的所有动态细则条目集合
                LambdaQueryWrapper<EvaluationCriterion> criterionWrapper = new LambdaQueryWrapper<>();
                criterionWrapper.eq(EvaluationCriterion::getRequirementId, requirement.getId());
                List<EvaluationCriterion> criteria = criterionMapper.selectList(criterionWrapper);

                if (!criteria.isEmpty()) {
                    // 调用升级后的动态 Prompt 大模型拼接机制
                    String dynamicJsonResult = aiUtils.evaluateWithCustomCriteria(report.getContent(), criteria);
                    
                    // 解析出总分，以便更新在最外层用于班级报表统计（Sp_class_statistics存储过程兼容）
                    com.alibaba.fastjson2.JSONObject jsonObject = com.alibaba.fastjson2.JSON.parseObject(dynamicJsonResult);
                    BigDecimal calculatedTotal = jsonObject.getBigDecimal("totalScore");
                    
                    evaluation.setTotalScore(calculatedTotal != null ? calculatedTotal : BigDecimal.ZERO);
                    // 塞进新字段物理长口袋中
                    evaluation.setDynamicScoresJson(dynamicJsonResult);
                    evaluation.setAiEvaluation(dynamicJsonResult); // 备份供原有弹窗详情提取
                    
                    // 默认旧字段归零清空，防止旧数据污染
                    evaluation.setCompletenessScore(BigDecimal.ZERO);
                    evaluation.setSpecificationScore(BigDecimal.ZERO);
                    evaluation.setKnowledgeScore(BigDecimal.ZERO);
                } else {
                    // 如果老师开辟了自定义开关但没写指标，则安全滑落回传统三大指标兜底
                    AiUtils.EvaluationResult aiResult = aiUtils.evaluateReport(report.getContent());
                    fillDefaultScores(evaluation, aiResult);
                }
            } else {
                // 【传统三大指标路由】
                AiUtils.EvaluationResult aiResult = aiUtils.evaluateReport(report.getContent());
                fillDefaultScores(evaluation, aiResult);
            }
            
            // 3. 执行物理入库
            if (existing != null) {
                evaluationMapper.updateById(evaluation);
            } else {
                evaluation.setCreateTime(LocalDateTime.now());
                evaluationMapper.insert(evaluation);
            }
            
            reportService.updateStatus(reportId, Constants.REPORT_EVALUATED);
            return Result.success("AI评价完成", convertToDTO(evaluation));
            
        } catch (Exception e) {
            return Result.error("AI评价失败: " + e.getMessage());
        }
    }

    /**
     * 【全新】全格式智能评价 — 支持文档+截图+代码+ZIP混合成果
     * 输出完整7段式评价报告，存入 ai_evaluation 字段
     *
     * @param reportId  报告ID
     * @param teacherId 教师ID
     * @param rubricJson 自定义评分标准JSON（可为null，将自动生成）
     */
    @Transactional
    public Result<EvaluationDTO> aiFullEvaluate(Long reportId, Long teacherId, String rubricJson) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return Result.error("报告不存在");
        }

        try {
            // 1. 收集所有关联文件的内容
            StringBuilder combinedContent = new StringBuilder();
            combinedContent.append("=== 报告标题 ===\n").append(report.getTitle()).append("\n\n");

            // 报告正文（content字段中的文本）
            if (report.getContent() != null && !report.getContent().isEmpty()) {
                combinedContent.append("=== 文档正文 ===\n").append(report.getContent()).append("\n\n");
            }

            // 附件文件（如果有）
            if (report.getFilePath() != null) {
                combinedContent.append("=== 附件文件 ===\n");
                combinedContent.append("文件名: ").append(report.getFileName()).append("\n");
                combinedContent.append("文件类型: ").append(report.getFileType()).append("\n");

                // 对于文本类文件，尝试提取内容
                if (FileUtils.isDocumentType(report.getFileType()) || FileUtils.isCodeType(report.getFileType())) {
                    try {
                        java.io.File attachedFile = new java.io.File(report.getFilePath());
                        if (attachedFile.exists()) {
                            String extractedText = FileUtils.extractTextFromFile(attachedFile);
                            if (extractedText.length() > 5000) {
                                extractedText = extractedText.substring(0, 5000) + "\n... (内容过长已截断)";
                            }
                            combinedContent.append("文件内容:\n").append(extractedText).append("\n");
                        }
                    } catch (Exception e) {
                        combinedContent.append("[文件内容提取失败: ").append(e.getMessage()).append("]\n");
                    }
                } else if (FileUtils.isImageType(report.getFileType())) {
                    combinedContent.append("[此为图片文件，AI将基于文件名和报告正文进行评价]\n");
                    combinedContent.append("图片文件名: ").append(report.getFileName()).append("\n");
                } else if (FileUtils.isArchiveType(report.getFileType())) {
                    try {
                        java.io.File zipFile = new java.io.File(report.getFilePath());
                        if (zipFile.exists()) {
                            String zipSummary = FileUtils.extractZipFileSummary(zipFile);
                            combinedContent.append(zipSummary).append("\n");
                        }
                    } catch (Exception e) {
                        combinedContent.append("[ZIP文件解析失败: ").append(e.getMessage()).append("]\n");
                    }
                }
            }

            // 2. 评分标准处理
            if (rubricJson == null || rubricJson.isEmpty()) {
                // 自动生成评分标准
                List<EvaluationCriterion> autoRubric = aiUtils.autoGenerateRubric(combinedContent.toString());
                rubricJson = JSON.toJSONString(autoRubric);
            }

            // 3. 调用AI全格式评价
            String fullReportJson = aiUtils.evaluateWithFullReport(combinedContent.toString(), rubricJson);

            // 4. 解析报告获取总分
            AiUtils.FullReportResult fullResult = aiUtils.parseFullReport(fullReportJson);
            BigDecimal totalScore = fullResult != null && fullResult.getTotalScore() != null
                    ? fullResult.getTotalScore() : BigDecimal.valueOf(60);

            // 5. 存入 evaluation_record
            LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(EvaluationRecord::getReportId, reportId);
            EvaluationRecord existing = evaluationMapper.selectOne(wrapper);

            EvaluationRecord evaluation = (existing != null) ? existing : new EvaluationRecord();
            evaluation.setReportId(reportId);
            evaluation.setTeacherId(teacherId);
            evaluation.setIsAi(Constants.EVALUATION_AI);
            evaluation.setEvaluateTime(LocalDateTime.now());
            evaluation.setUpdateTime(LocalDateTime.now());
            evaluation.setTotalScore(totalScore);
            evaluation.setAiEvaluation(fullReportJson); // 完整7段式报告JSON
            evaluation.setDynamicScoresJson(rubricJson); // 评分标准

            // 旧字段清零（兼容）
            evaluation.setCompletenessScore(BigDecimal.ZERO);
            evaluation.setSpecificationScore(BigDecimal.ZERO);
            evaluation.setKnowledgeScore(BigDecimal.ZERO);

            if (existing != null) {
                evaluationMapper.updateById(evaluation);
            } else {
                evaluation.setCreateTime(LocalDateTime.now());
                evaluationMapper.insert(evaluation);
            }

            reportService.updateStatus(reportId, Constants.REPORT_EVALUATED);
            return Result.success("AI全格式评价完成", convertToDTO(evaluation));

        } catch (Exception e) {
            log.error("全格式AI评价失败", e);
            return Result.error("AI全格式评价失败: " + e.getMessage());
        }
    }

    /**
     * 【全新】自动生成评分标准
     * 根据报告内容自动匹配评分维度和分值
     */
    public Result<List<EvaluationCriterion>> autoGenerateRubric(Long reportId) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return Result.error("报告不存在");
        }

        try {
            StringBuilder content = new StringBuilder();
            content.append("标题: ").append(report.getTitle()).append("\n");
            if (report.getContent() != null) {
                content.append(report.getContent());
            }
            if (report.getFileType() != null) {
                content.append("\n文件类型: ").append(report.getFileType());
                content.append("\n文件类别: ").append(FileUtils.getFileCategoryLabel(report.getFileType()));
            }

            List<EvaluationCriterion> rubric = aiUtils.autoGenerateRubric(content.toString());
            return Result.success("评分标准生成成功", rubric);
        } catch (Exception e) {
            log.error("自动生成评分标准失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    private void fillDefaultScores(EvaluationRecord evaluation, AiUtils.EvaluationResult aiResult) {
        evaluation.setCompletenessScore(aiResult.getCompletenessScore());
        evaluation.setSpecificationScore(aiResult.getSpecificationScore());
        evaluation.setKnowledgeScore(aiResult.getKnowledgeScore());
        evaluation.setTotalScore(aiResult.getTotalScore());
        evaluation.setAiEvaluation(aiResult.toJson());
        evaluation.setDynamicScoresJson(null); // 旧指标不启用动态JSON
    }
    
    /**
     * 原有传统手动修改评分接口（仅教师和管理员可操作）
     */
    @Transactional
    public Result<Void> manualEvaluate(Long id, BigDecimal completenessScore, BigDecimal specificationScore,
                                       BigDecimal knowledgeScore, BigDecimal totalScore, String manualEvaluation, 
                                       Long userId, Integer role) {
        // 学生无权修改评分
        if (role != null && role == Constants.ROLE_STUDENT) {
            return Result.error("学生无权修改评分，仅可查看");
        }
        
        EvaluationRecord evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            return Result.error("评价记录不存在");
        }
        
        evaluation.setCompletenessScore(completenessScore);
        evaluation.setSpecificationScore(specificationScore);
        evaluation.setKnowledgeScore(knowledgeScore);
        evaluation.setTotalScore(totalScore);
        evaluation.setManualEvaluation(manualEvaluation);
        evaluation.setTeacherId(userId);
        evaluation.setIsAi(Constants.EVALUATION_MANUAL);
        evaluation.setEvaluateTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        
        evaluationMapper.updateById(evaluation);
        return Result.success();
    }

    /**
     * 【全新扩展方法】教师/学生手动修改自定义多维度动态指标成绩接口
     */
    @Transactional
    public Result<Void> manualEvaluateCustom(Long id, BigDecimal totalScore, String dynamicScoresJsonStr, 
                                             String manualEvaluation, Long userId, Integer role) {
        // 学生无权修改评分
        if (role != null && role == Constants.ROLE_STUDENT) {
            return Result.error("学生无权修改评分，仅可查看");
        }
        
        EvaluationRecord evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            return Result.error("评价记录不存在");
        }
        
        evaluation.setTotalScore(totalScore); // 更新纠偏后的综合大总分
        evaluation.setDynamicScoresJson(dynamicScoresJsonStr); // 覆写指标新数据
        evaluation.setManualEvaluation(manualEvaluation);
        evaluation.setTeacherId(userId);
        evaluation.setIsAi(Constants.EVALUATION_MANUAL); // 切换为人工订正标记
        evaluation.setEvaluateTime(LocalDateTime.now());
        evaluation.setUpdateTime(LocalDateTime.now());
        
        evaluationMapper.updateById(evaluation);
        return Result.success();
    }
    
    public Result<EvaluationDTO> getEvaluation(Long id) {
        EvaluationRecord evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            return Result.error("评价记录不存在");
        }
        
        return Result.success(convertToDTO(evaluation));
    }
    
    public Result<EvaluationDTO> getEvaluationByReport(Long reportId) {
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvaluationRecord::getReportId, reportId);
        EvaluationRecord evaluation = evaluationMapper.selectOne(wrapper);
        
        if (evaluation == null) {
            return Result.error("该报告暂无评价");
        }
        
        return Result.success(convertToDTO(evaluation));
    }
    
    /**
     * 删除评价记录（教师和管理员均可操作，学生不能删除）
     */
    @Transactional
    public Result<Void> deleteEvaluation(Long id, Integer role, Long userId) {
        // 学生不能删除评价
        if (role != null && role == Constants.ROLE_STUDENT) {
            return Result.error("学生无权删除评价");
        }
        
        EvaluationRecord evaluation = evaluationMapper.selectById(id);
        if (evaluation == null) {
            return Result.error("评价记录不存在");
        }
        
        // 删除评价记录
        evaluationMapper.deleteById(id);
        
        // 同时将关联报告的状态回退为"未评价"
        if (evaluation.getReportId() != null) {
            TrainingReport report = reportMapper.selectById(evaluation.getReportId());
            if (report != null) {
                report.setStatus(Constants.REPORT_PENDING);
                reportMapper.updateById(report);
            }
        }
        
        Result<Void> result = Result.success();
        result.setMessage("评价已删除");
        return result;
    }
    
    /**
     * 【全新】AI对话式反馈 — 学生就评分结果与AI进行多轮对话
     *
     * @param reportId            报告ID
     * @param conversationHistory 对话历史JSON数组字符串
     * @param studentId           当前学生ID（用于权限校验）
     * @return AI回复文本
     */
    public Result<Map<String, Object>> chatFeedback(Long reportId, String conversationHistory, Long studentId) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return Result.error("报告不存在");
        }

        // 权限校验：只有提交者本人可以对话
        if (!report.getStudentId().equals(studentId)) {
            return Result.error("您只能对自己的作业进行AI对话反馈");
        }

        // 检查是否有评价
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvaluationRecord::getReportId, reportId);
        EvaluationRecord evaluation = evaluationMapper.selectOne(wrapper);
        if (evaluation == null) {
            return Result.error("该作业尚未被评价，无法进行AI对话反馈");
        }

        try {
            // 解析对话历史
            List<Map<String, String>> history = new java.util.ArrayList<>();
            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                try {
                    com.alibaba.fastjson2.JSONArray historyArr = com.alibaba.fastjson2.JSON.parseArray(conversationHistory);
                    for (int i = 0; i < historyArr.size(); i++) {
                        com.alibaba.fastjson2.JSONObject msg = historyArr.getJSONObject(i);
                        Map<String, String> m = new java.util.HashMap<>();
                        m.put("role", msg.getString("role"));
                        m.put("content", msg.getString("content"));
                        history.add(m);
                    }
                } catch (Exception e) {
                    log.warn("解析对话历史失败，将使用空历史: {}", e.getMessage());
                }
            }

            // 构建扣分点列表
            String deductionPoints = buildDeductionPoints(evaluation);

            // 获取评价报告
            String evalReport = evaluation.getAiEvaluation() != null ? evaluation.getAiEvaluation() : "";
            if (evalReport.isEmpty() && evaluation.getDynamicScoresJson() != null) {
                evalReport = evaluation.getDynamicScoresJson();
            }

            // 获取作业内容
            String content = report.getContent() != null ? report.getContent() : "";

            // 调用AI对话
            String aiReply = aiUtils.chatFeedback(
                    history,
                    report.getTitle(),
                    evaluation.getTotalScore() != null ? evaluation.getTotalScore().toString() : "0",
                    deductionPoints,
                    evalReport,
                    content
            );

            Map<String, Object> result = new java.util.HashMap<>();
            result.put("reply", aiReply);
            result.put("deductionPoints", deductionPoints);
            return Result.success("对话成功", result);
        } catch (Exception e) {
            log.error("AI对话反馈异常", e);
            return Result.error("AI对话反馈失败: " + e.getMessage());
        }
    }

    /**
     * 【全新】AI个性化改进计划 — 根据学生所有已评价报告，分析薄弱项并生成改进计划
     *
     * @param studentId 学生ID
     * @return AI生成的改进计划JSON
     */
    public Result<Map<String, Object>> generateImprovementPlan(Long studentId) {
        try {
            // 1. 查询该学生所有已提交的报告
            LambdaQueryWrapper<TrainingReport> reportWrapper = new LambdaQueryWrapper<>();
            reportWrapper.eq(TrainingReport::getStudentId, studentId)
                         .orderByDesc(TrainingReport::getCreateTime);
            List<TrainingReport> reports = reportMapper.selectList(reportWrapper);

            if (reports.isEmpty()) {
                return Result.error("您还没有提交过报告，无法生成改进计划");
            }

            // 2. 收集所有报告的评价数据，构建历史数据摘要
            StringBuilder historyData = new StringBuilder();
            int evaluatedCount = 0;

            for (int i = 0; i < reports.size(); i++) {
                TrainingReport report = reports.get(i);

                // 查询该报告的评价记录
                LambdaQueryWrapper<EvaluationRecord> evalWrapper = new LambdaQueryWrapper<>();
                evalWrapper.eq(EvaluationRecord::getReportId, report.getId());
                EvaluationRecord evaluation = evaluationMapper.selectOne(evalWrapper);

                historyData.append(String.format("\n### 作业%d: %s\n", i + 1, report.getTitle() != null ? report.getTitle() : "未命名"));

                // 获取课程名称
                if (report.getCourseId() != null) {
                    Course course = courseMapper.selectById(report.getCourseId());
                    if (course != null) {
                        historyData.append(String.format("- 课程: %s\n", course.getCourseName()));
                    }
                }

                // 获取作业要求标题
                if (report.getRequirementId() != null) {
                    ReportRequirement req = requirementMapper.selectById(report.getRequirementId());
                    if (req != null) {
                        historyData.append(String.format("- 作业要求: %s\n", req.getTitle()));
                        if (req.getDeadline() != null) {
                            historyData.append(String.format("- 截止时间: %s\n", req.getDeadline()));
                        }
                    }
                }

                historyData.append(String.format("- 提交时间: %s\n", report.getCreateTime()));

                if (evaluation != null) {
                    evaluatedCount++;

                    // 三大维度得分
                    if (evaluation.getCompletenessScore() != null) {
                        historyData.append(String.format("- 完整性得分: %.1f/30\n", evaluation.getCompletenessScore()));
                    }
                    if (evaluation.getSpecificationScore() != null) {
                        historyData.append(String.format("- 规范性得分: %.1f/30\n", evaluation.getSpecificationScore()));
                    }
                    if (evaluation.getKnowledgeScore() != null) {
                        historyData.append(String.format("- 知识点得分: %.1f/40\n", evaluation.getKnowledgeScore()));
                    }
                    if (evaluation.getTotalScore() != null) {
                        historyData.append(String.format("- 总分: %.1f/100\n", evaluation.getTotalScore()));
                    }

                    // 动态多维度得分（如果有）
                    if (evaluation.getDynamicScoresJson() != null && !evaluation.getDynamicScoresJson().isEmpty()) {
                        historyData.append("- 动态指标得分: ").append(evaluation.getDynamicScoresJson()).append("\n");
                    }

                    // 扣分点
                    String deductionPoints = buildDeductionPoints(evaluation);
                    if (deductionPoints != null && !deductionPoints.isEmpty()) {
                        historyData.append("- 扣分点:\n").append(deductionPoints);
                    }

                    // AI评价摘要（截取前500字）
                    if (evaluation.getAiEvaluation() != null && !evaluation.getAiEvaluation().isEmpty()) {
                        String aiEval = evaluation.getAiEvaluation();
                        if (aiEval.length() > 500) {
                            aiEval = aiEval.substring(0, 500) + "...";
                        }
                        historyData.append("- AI评价摘要: ").append(aiEval).append("\n");
                    }

                    // 教师评语
                    if (evaluation.getManualEvaluation() != null && !evaluation.getManualEvaluation().isEmpty()) {
                        historyData.append("- 教师评语: ").append(evaluation.getManualEvaluation()).append("\n");
                    }
                } else {
                    historyData.append("- 状态: 尚未被评价\n");
                }
            }

            if (evaluatedCount == 0) {
                return Result.error("您还没有被评价过的报告，无法生成改进计划");
            }

            // 添加统计摘要
            historyData.insert(0, String.format(
                "该学生共有%d份报告，其中%d份已被评价。\n以下为详细历史数据：\n",
                reports.size(), evaluatedCount
            ));

            // 3. 调用AI生成改进计划
            String aiResponse = aiUtils.generateImprovementPlan(historyData.toString());
            if (aiResponse == null) {
                return Result.error("AI服务暂时不可用，请稍后重试");
            }

            // 4. 解析AI返回的JSON
            String cleanJson = aiUtils.extractJson(aiResponse);
            if (cleanJson == null) {
                // 如果不是JSON格式，尝试作为纯文本返回
                Map<String, Object> fallback = new java.util.HashMap<>();
                fallback.put("rawResponse", aiResponse);
                fallback.put("totalReports", reports.size());
                fallback.put("evaluatedReports", evaluatedCount);
                return Result.success("生成成功（非结构化）", fallback);
            }

            Map<String, Object> resultMap = JSON.parseObject(cleanJson, Map.class);
            resultMap.put("totalReports", reports.size());
            resultMap.put("evaluatedReports", evaluatedCount);
            resultMap.put("generatedAt", LocalDateTime.now().toString());

            return Result.success("改进计划生成成功", resultMap);

        } catch (Exception e) {
            log.error("生成AI改进计划异常", e);
            return Result.error("生成改进计划失败: " + e.getMessage());
        }
    }

    /**
     * 【全新】AI智能批注生成 — 对报告内容进行逐段分析，精确定位问题文本并生成批注
     * 如果该报告已有批注则覆盖（先删后插）
     *
     * @param reportId  报告ID
     * @param teacherId 触发批注的教师ID
     * @return 批注列表
     */
    @Transactional
    public Result<List<AnnotationDTO>> generateAnnotations(Long reportId, Long teacherId) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report == null) {
            return Result.error("报告不存在");
        }

        // 检查报告是否有内容
        String content = report.getContent();
        if (content == null || content.trim().isEmpty()) {
            return Result.error("报告内容为空，无法生成批注");
        }

        try {
            // 1. 调用AI生成批注
            List<AnnotationDTO> annotations = aiUtils.generateAnnotations(content);

            // 2. 删除该报告的旧批注（覆盖逻辑）
            LambdaQueryWrapper<ReportAnnotation> deleteWrapper = new LambdaQueryWrapper<>();
            deleteWrapper.eq(ReportAnnotation::getReportId, reportId);
            annotationMapper.delete(deleteWrapper);

            // 3. 批量插入新批注
            if (!annotations.isEmpty()) {
                for (AnnotationDTO dto : annotations) {
                    ReportAnnotation entity = new ReportAnnotation();
                    entity.setReportId(reportId);
                    entity.setStartPos(dto.getStartPos());
                    entity.setEndPos(dto.getEndPos());
                    entity.setHighlightedText(dto.getHighlightedText());
                    entity.setComment(dto.getComment());
                    entity.setSeverity(dto.getSeverity() != null ? dto.getSeverity() : "info");
                    entity.setCategory(dto.getCategory());
                    entity.setSuggestion(dto.getSuggestion());
                    entity.setCreateTime(LocalDateTime.now());
                    annotationMapper.insert(entity);

                    // 回填ID给DTO
                    dto.setId(entity.getId());
                    dto.setReportId(reportId);
                    dto.setCreateTime(entity.getCreateTime());
                }
            }

            return Result.success("AI智能批注完成，共生成" + annotations.size() + "条批注", annotations);
        } catch (Exception e) {
            log.error("AI智能批注生成失败", e);
            return Result.error("AI智能批注生成失败: " + e.getMessage());
        }
    }

    /**
     * 获取某报告的所有批注（按文本位置排序）
     */
    public Result<List<AnnotationDTO>> getAnnotations(Long reportId) {
        try {
            LambdaQueryWrapper<ReportAnnotation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportAnnotation::getReportId, reportId)
                   .orderByAsc(ReportAnnotation::getStartPos);
            List<ReportAnnotation> entities = annotationMapper.selectList(wrapper);

            List<AnnotationDTO> dtos = entities.stream().map(entity -> {
                AnnotationDTO dto = new AnnotationDTO();
                dto.setId(entity.getId());
                dto.setReportId(entity.getReportId());
                dto.setStartPos(entity.getStartPos());
                dto.setEndPos(entity.getEndPos());
                dto.setHighlightedText(entity.getHighlightedText());
                dto.setComment(entity.getComment());
                dto.setSeverity(entity.getSeverity());
                dto.setCategory(entity.getCategory());
                dto.setSuggestion(entity.getSuggestion());
                dto.setCreateTime(entity.getCreateTime());
                return dto;
            }).toList();

            return Result.success(dtos);
        } catch (Exception e) {
            log.error("获取批注列表失败", e);
            return Result.error("获取批注失败: " + e.getMessage());
        }
    }

    /**
     * 删除某报告的所有批注
     */
    @Transactional
    public Result<Void> deleteAnnotations(Long reportId) {
        try {
            LambdaQueryWrapper<ReportAnnotation> wrapper = new LambdaQueryWrapper<>();
            wrapper.eq(ReportAnnotation::getReportId, reportId);
            annotationMapper.delete(wrapper);
            return Result.success();
        } catch (Exception e) {
            log.error("删除批注失败", e);
            return Result.error("删除批注失败: " + e.getMessage());
        }
    }

    /**
     * 从评价记录中提取扣分点列表
     */
    private String buildDeductionPoints(EvaluationRecord evaluation) {
        StringBuilder sb = new StringBuilder();

        // 尝试从全格式报告提取 scoringDetails
        if (evaluation.getAiEvaluation() != null && !evaluation.getAiEvaluation().isEmpty()) {
            try {
                com.alibaba.fastjson2.JSONObject aiJson = com.alibaba.fastjson2.JSON.parseObject(evaluation.getAiEvaluation());
                if (aiJson.containsKey("scoringDetails")) {
                    com.alibaba.fastjson2.JSONArray details = aiJson.getJSONArray("scoringDetails");
                    for (int i = 0; i < details.size(); i++) {
                        com.alibaba.fastjson2.JSONObject detail = details.getJSONObject(i);
                        String dimName = detail.getString("dimension");
                        java.math.BigDecimal score = detail.getBigDecimal("score");
                        java.math.BigDecimal maxScore = detail.getBigDecimal("maxScore");
                        String comment = detail.getString("comment");
                        java.math.BigDecimal deducted = maxScore.subtract(score);
                        sb.append(String.format("%d. 【%s】得分%.1f/%.1f分（扣%.1f分）: %s\n",
                                i + 1, dimName, score, maxScore, deducted, comment));
                    }
                    return sb.toString();
                }
            } catch (Exception e) {
                log.warn("从全格式报告提取扣分点失败，尝试传统格式");
            }
        }

        // 传统三大维度格式
        if (evaluation.getCompletenessScore() != null && evaluation.getCompletenessScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal deducted = java.math.BigDecimal.valueOf(30).subtract(evaluation.getCompletenessScore());
            sb.append(String.format("1. 【完整性】得分%.1f/30分（扣%.1f分）\n",
                    evaluation.getCompletenessScore(), deducted));
        }
        if (evaluation.getSpecificationScore() != null && evaluation.getSpecificationScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal deducted = java.math.BigDecimal.valueOf(30).subtract(evaluation.getSpecificationScore());
            sb.append(String.format("2. 【规范性】得分%.1f/30分（扣%.1f分）\n",
                    evaluation.getSpecificationScore(), deducted));
        }
        if (evaluation.getKnowledgeScore() != null && evaluation.getKnowledgeScore().compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal deducted = java.math.BigDecimal.valueOf(40).subtract(evaluation.getKnowledgeScore());
            sb.append(String.format("3. 【知识点】得分%.1f/40分（扣%.1f分）\n",
                    evaluation.getKnowledgeScore(), deducted));
        }

        if (sb.length() == 0) {
            sb.append("暂无详细扣分明细");
        }

        return sb.toString();
    }

    private EvaluationDTO convertToDTO(EvaluationRecord evaluation) {
        EvaluationDTO dto = new EvaluationDTO();
        dto.setId(evaluation.getId());
        dto.setReportId(evaluation.getReportId());
        dto.setTeacherId(evaluation.getTeacherId());
        dto.setCompletenessScore(evaluation.getCompletenessScore());
        dto.setSpecificationScore(evaluation.getSpecificationScore());
        dto.setKnowledgeScore(evaluation.getKnowledgeScore());
        dto.setTotalScore(evaluation.getTotalScore());
        dto.setAiEvaluation(evaluation.getAiEvaluation());
        dto.setManualEvaluation(evaluation.getManualEvaluation());
        dto.setIsAi(evaluation.getIsAi());
        dto.setEvaluateTime(evaluation.getEvaluateTime());
        dto.setCreateTime(evaluation.getCreateTime());
        
        // 【数据拼载安全保障】自动向前端下发动态 JSON 得分细则内容
        dto.setDynamicScoresJson(evaluation.getDynamicScoresJson());
        
        if (evaluation.getReportId() != null) {
            TrainingReport report = reportMapper.selectById(evaluation.getReportId());
            if (report != null) {
                dto.setReportTitle(report.getTitle());
                
                SysUser student = userMapper.selectById(report.getStudentId());
                if (student != null) {
                    dto.setStudentName(student.getRealName());
                    
                    if (student.getClassId() != null) {
                        SysClass sysClass = classMapper.selectById(student.getClassId());
                        if (sysClass != null) {
                            dto.setClassName(sysClass.getClassName());
                        }
                    }
                }
            }
        }
        
        if (evaluation.getTeacherId() != null) {
            SysUser teacher = userMapper.selectById(evaluation.getTeacherId());
            if (teacher != null) {
                dto.setTeacherName(teacher.getRealName());
            }
        }
        
        return dto;
    }
}