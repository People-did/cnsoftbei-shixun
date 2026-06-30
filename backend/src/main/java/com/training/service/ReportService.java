package com.training.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.training.common.Constants;
import com.training.common.PageResult;
import com.training.common.Result;
import com.training.dto.ReportDTO;
import com.training.entity.Course;
import com.training.entity.EvaluationRecord;
import com.training.entity.ReportRequirement;
import com.training.entity.SysClass;
import com.training.entity.SysUser;
import com.training.entity.TrainingReport;
import com.training.mapper.EvaluationRecordMapper;
import com.training.mapper.CourseMapper;
import com.training.mapper.ReportRequirementMapper;
import com.training.mapper.SysClassMapper;
import com.training.mapper.SysUserMapper;
import com.training.mapper.TrainingReportMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReportService extends ServiceImpl<TrainingReportMapper, TrainingReport> {
    
    private final TrainingReportMapper reportMapper;
    private final SysUserMapper userMapper;
    private final SysClassMapper classMapper;
    private final EvaluationRecordMapper evaluationMapper;
    private final CourseMapper courseMapper;
    private final ReportRequirementMapper requirementMapper;
    
    @Value("${upload.path}")
    private String uploadPath;
    
    /**
     * 获取上传目录，确保路径存在且可写。
     */
    private File getUploadDir() {
        File dir = new File(uploadPath);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }
    
    public PageResult<ReportDTO> pageList(Integer pageNum, Integer pageSize, Long studentId, 
                                         Long courseId, Long classId, Integer status, Long currentUserId, Integer currentRole) {
        Page<TrainingReport> page = new Page<>(pageNum, pageSize);
        
        if (currentRole != null && currentRole == Constants.ROLE_STUDENT) {
            studentId = currentUserId;
        }
        
        IPage<ReportDTO> result = reportMapper.selectReportPage(page, studentId, courseId, classId, status);
        
        return new PageResult<>(result.getTotal(), result.getRecords());
    }
    
    public Result<ReportDTO> getReport(Long id) {
        TrainingReport report = reportMapper.selectById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        
        return Result.success(convertToDTO(report));
    }
    
    public Result<Void> uploadReport(Long studentId, Long courseId, Long requirementId, String title, MultipartFile file) {
        try {
            if (courseId == null) {
                return Result.error("请选择课程");
            }
            if (requirementId == null) {
                return Result.error("请指定作业要求");
            }
            
            // 检查截止时间
            ReportRequirement requirement = requirementMapper.selectById(requirementId);
            if (requirement != null && requirement.getDeadline() != null) {
                if (LocalDateTime.now().isAfter(requirement.getDeadline())) {
                    return Result.error("该作业已截止，无法提交");
                }
            }
            
            // 查找是否已有该学生对同一作业的提交
            TrainingReport existingReport = findExistingSubmission(studentId, requirementId);
            
            String originalFilename = file.getOriginalFilename();
            String extension = com.training.utils.FileUtils.getFileExtension(originalFilename);
            
            if (!com.training.utils.FileUtils.isAllowedFileType(extension)) {
                return Result.error("不支持的文件类型，仅支持doc、docx、pdf");
            }
            
            String filename = com.training.utils.FileUtils.generateUniqueFilename(originalFilename);
            File uploadDir = getUploadDir();
            
            // 先提取文本
            String content;
            try {
                content = com.training.utils.FileUtils.extractTextFromFile(file);
            } catch (Exception e) {
                content = "[无法提取文本: " + e.getMessage() + "]";
            }
            if (content != null && content.length() > 1000) {
                content = content.substring(0, 1000);
            }
            
            File destFile = new File(uploadDir, filename);
            file.transferTo(destFile);
            
            if (existingReport != null) {
                // 覆盖更新：删除旧文件
                if (existingReport.getFilePath() != null) {
                    new File(existingReport.getFilePath()).delete();
                }
                existingReport.setTitle(title);
                existingReport.setContent(content);
                existingReport.setFilePath(destFile.getAbsolutePath());
                existingReport.setFileName(originalFilename);
                existingReport.setFileType(extension);
                existingReport.setStatus(Constants.REPORT_PENDING);
                existingReport.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(existingReport);
            } else {
                TrainingReport report = new TrainingReport();
                report.setStudentId(studentId);
                report.setCourseId(courseId);
                report.setRequirementId(requirementId);
                report.setTitle(title);
                report.setContent(content);
                report.setFilePath(destFile.getAbsolutePath());
                report.setFileName(originalFilename);
                report.setFileType(extension);
                report.setStatus(Constants.REPORT_PENDING);
                report.setCreateTime(LocalDateTime.now());
                reportMapper.insert(report);
            }

            return Result.success();
        } catch (Exception e) {
            return Result.error("上传失败: " + e.getMessage());
        }
    }

    /**
     * 【全新】多文件批量上传 — 支持文档+截图+代码+ZIP混合上传
     * 如果同一学生+同一作业已存在提交，则覆盖更新
     */
    public Result<Void> uploadMultipleFiles(Long studentId, Long courseId, Long requirementId, String title, List<MultipartFile> files) {
        try {
            if (courseId == null) {
                return Result.error("请选择课程");
            }
            if (requirementId == null) {
                return Result.error("请指定作业要求");
            }
            if (files == null || files.isEmpty()) {
                return Result.error("请至少上传一个文件");
            }

            // 检查截止时间
            ReportRequirement requirement = requirementMapper.selectById(requirementId);
            if (requirement != null && requirement.getDeadline() != null) {
                if (LocalDateTime.now().isAfter(requirement.getDeadline())) {
                    return Result.error("该作业已截止，无法提交");
                }
            }

            // 查找是否已有该学生对同一作业的提交
            TrainingReport existingReport = findExistingSubmission(studentId, requirementId);
            
            // 如果覆盖更新，先删除旧文件
            if (existingReport != null && existingReport.getFilePath() != null) {
                new File(existingReport.getFilePath()).delete();
            }

            StringBuilder allContent = new StringBuilder();
            allContent.append("【多文件上传成果】\n标题: ").append(title).append("\n\n");
            allContent.append("上传文件清单:\n");

            List<String> fileNames = new ArrayList<>();
            List<String> fileTypes = new ArrayList<>();
            String primaryFilePath = null;
            String primaryFileName = null;
            String primaryFileType = null;

            int idx = 0;
            for (MultipartFile file : files) {
                idx++;
                String originalFilename = file.getOriginalFilename();
                String extension = com.training.utils.FileUtils.getFileExtension(originalFilename);

                if (!com.training.utils.FileUtils.isAllowedFileType(extension)) {
                    return Result.error("不支持的文件类型: " + extension + "（文件: " + originalFilename + "）");
                }

                String filename = com.training.utils.FileUtils.generateUniqueFilename(originalFilename);
                File uploadDir = getUploadDir();

                allContent.append("\n--- 文件").append(idx).append(": ").append(originalFilename).append(" ---\n");
                allContent.append("类型: ").append(com.training.utils.FileUtils.getFileCategoryLabel(extension)).append("\n");

                try {
                    String extractedText = com.training.utils.FileUtils.extractTextFromFile(file);
                    if (extractedText != null && extractedText.length() > 2000) {
                        extractedText = extractedText.substring(0, 2000) + "\n... (已截断)";
                    }
                    allContent.append("内容:\n").append(extractedText).append("\n");
                } catch (Exception e) {
                    allContent.append("[无法提取文本: ").append(e.getMessage()).append("]\n");
                }

                File destFile = new File(uploadDir, filename);
                file.transferTo(destFile);

                fileNames.add(originalFilename);
                fileTypes.add(extension);

                if (idx == 1) {
                    primaryFilePath = destFile.getAbsolutePath();
                    primaryFileName = originalFilename;
                    primaryFileType = extension;
                }
            }

            if (existingReport != null) {
                existingReport.setTitle(title);
                existingReport.setContent(allContent.toString());
                existingReport.setFilePath(primaryFilePath);
                existingReport.setFileName(String.join("; ", fileNames));
                existingReport.setFileType(String.join(";", fileTypes));
                existingReport.setStatus(Constants.REPORT_PENDING);
                existingReport.setUpdateTime(LocalDateTime.now());
                reportMapper.updateById(existingReport);
            } else {
                TrainingReport report = new TrainingReport();
                report.setStudentId(studentId);
                report.setCourseId(courseId);
                report.setRequirementId(requirementId);
                report.setTitle(title);
                report.setContent(allContent.toString());
                report.setFilePath(primaryFilePath);
                report.setFileName(String.join("; ", fileNames));
                report.setFileType(String.join(";", fileTypes));
                report.setStatus(Constants.REPORT_PENDING);
                report.setCreateTime(LocalDateTime.now());
                reportMapper.insert(report);
            }

            return Result.success();
        } catch (Exception e) {
            return Result.error("批量上传失败: " + e.getMessage());
        }
    }

    /**
     * 查找学生对某个作业的已有提交
     */
    private TrainingReport findExistingSubmission(Long studentId, Long requirementId) {
        LambdaQueryWrapper<TrainingReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingReport::getStudentId, studentId)
               .eq(TrainingReport::getRequirementId, requirementId);
        return reportMapper.selectOne(wrapper);
    }

    /**
     * 获取学生在某个作业下的提交
     */
    public Result<ReportDTO> getMySubmission(Long studentId, Long requirementId) {
        LambdaQueryWrapper<TrainingReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingReport::getStudentId, studentId)
               .eq(TrainingReport::getRequirementId, requirementId);
        TrainingReport report = reportMapper.selectOne(wrapper);
        if (report == null) {
            // 未提交是正常业务状态，返回 success + null data，避免前端拦截器弹出错误弹窗
            return Result.success(null);
        }
        return Result.success(convertToDTO(report));
    }

    /**
     * 【全新】教师端：获取某作业下所有学生的提交列表
     */
    public Result<List<ReportDTO>> getSubmissionsByRequirement(Long requirementId, Long teacherId) {
        // 验证该作业属于教师的课程
        ReportRequirement requirement = requirementMapper.selectById(requirementId);
        if (requirement == null) {
            return Result.error("作业不存在");
        }
        Course course = courseMapper.selectById(requirement.getCourseId());
        if (course == null || !course.getTeacherId().equals(teacherId)) {
            return Result.error("无权查看该作业的提交");
        }

        LambdaQueryWrapper<TrainingReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingReport::getRequirementId, requirementId);
        wrapper.orderByDesc(TrainingReport::getCreateTime);
        List<TrainingReport> reports = reportMapper.selectList(wrapper);
        List<ReportDTO> dtos = reports.stream().map(this::convertToDTO).collect(Collectors.toList());
        return Result.success(dtos);
    }

    /**
     * 更新报告（单文件）
     */
    public Result<Void> updateReportFile(Long id, Long studentId, String title, MultipartFile file) {
        TrainingReport report = reportMapper.selectById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        if (!report.getStudentId().equals(studentId)) {
            return Result.error("无权修改他人报告");
        }
        // 检查截止时间
        if (report.getRequirementId() != null) {
            ReportRequirement requirement = requirementMapper.selectById(report.getRequirementId());
            if (requirement != null && requirement.getDeadline() != null) {
                if (LocalDateTime.now().isAfter(requirement.getDeadline())) {
                    return Result.error("该作业已截止，无法编辑");
                }
            }
        }
        
        try {
            String originalFilename = file.getOriginalFilename();
            String extension = com.training.utils.FileUtils.getFileExtension(originalFilename);
            if (!com.training.utils.FileUtils.isAllowedFileType(extension)) {
                return Result.error("不支持的文件类型");
            }
            
            // 删除旧文件
            if (report.getFilePath() != null) {
                new File(report.getFilePath()).delete();
            }
            
            String content;
            try {
                content = com.training.utils.FileUtils.extractTextFromFile(file);
            } catch (Exception e) {
                content = "[无法提取文本: " + e.getMessage() + "]";
            }
            if (content != null && content.length() > 1000) {
                content = content.substring(0, 1000);
            }
            
            String filename = com.training.utils.FileUtils.generateUniqueFilename(originalFilename);
            File destFile = new File(getUploadDir(), filename);
            file.transferTo(destFile);
            
            report.setTitle(title);
            report.setContent(content);
            report.setFilePath(destFile.getAbsolutePath());
            report.setFileName(originalFilename);
            report.setFileType(extension);
            report.setStatus(Constants.REPORT_PENDING);
            report.setUpdateTime(LocalDateTime.now());
            reportMapper.updateById(report);
            
            return Result.success();
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新报告（多文件）
     */
    public Result<Void> updateReportFiles(Long id, Long studentId, String title, List<MultipartFile> files) {
        TrainingReport report = reportMapper.selectById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        if (!report.getStudentId().equals(studentId)) {
            return Result.error("无权修改他人报告");
        }
        if (report.getRequirementId() != null) {
            ReportRequirement requirement = requirementMapper.selectById(report.getRequirementId());
            if (requirement != null && requirement.getDeadline() != null) {
                if (LocalDateTime.now().isAfter(requirement.getDeadline())) {
                    return Result.error("该作业已截止，无法编辑");
                }
            }
        }
        
        try {
            // 删除旧文件
            if (report.getFilePath() != null) {
                new File(report.getFilePath()).delete();
            }
            
            StringBuilder allContent = new StringBuilder();
            allContent.append("【多文件上传成果】\n标题: ").append(title).append("\n\n");
            
            List<String> fileNames = new ArrayList<>();
            List<String> fileTypes = new ArrayList<>();
            String primaryFilePath = null;
            String primaryFileName = null;
            String primaryFileType = null;
            
            int idx = 0;
            for (MultipartFile file : files) {
                idx++;
                String originalFilename = file.getOriginalFilename();
                String extension = com.training.utils.FileUtils.getFileExtension(originalFilename);
                if (!com.training.utils.FileUtils.isAllowedFileType(extension)) {
                    return Result.error("不支持的文件类型: " + extension);
                }
                
                allContent.append("\n--- 文件").append(idx).append(": ").append(originalFilename).append(" ---\n");
                try {
                    String text = com.training.utils.FileUtils.extractTextFromFile(file);
                    if (text != null && text.length() > 2000) text = text.substring(0, 2000);
                    allContent.append("内容:\n").append(text).append("\n");
                } catch (Exception e) {
                    allContent.append("[无法提取文本]\n");
                }
                
                String filename = com.training.utils.FileUtils.generateUniqueFilename(originalFilename);
                File destFile = new File(getUploadDir(), filename);
                file.transferTo(destFile);
                
                fileNames.add(originalFilename);
                fileTypes.add(extension);
                if (idx == 1) {
                    primaryFilePath = destFile.getAbsolutePath();
                    primaryFileName = originalFilename;
                    primaryFileType = extension;
                }
            }
            
            report.setTitle(title);
            report.setContent(allContent.toString());
            report.setFilePath(primaryFilePath);
            report.setFileName(String.join("; ", fileNames));
            report.setFileType(String.join(";", fileTypes));
            report.setStatus(Constants.REPORT_PENDING);
            report.setUpdateTime(LocalDateTime.now());
            reportMapper.updateById(report);
            
            return Result.success();
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    public Result<Void> deleteReport(Long id) {
        TrainingReport report = reportMapper.selectById(id);
        if (report == null) {
            return Result.error("报告不存在");
        }
        
        if (report.getFilePath() != null) {
            new File(report.getFilePath()).delete();
        }
        
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvaluationRecord::getReportId, id);
        evaluationMapper.delete(wrapper);
        
        reportMapper.deleteById(id);
        
        return Result.success();
    }
    
    public String getFilePath(Long id) {
        TrainingReport report = reportMapper.selectById(id);
        return report != null ? report.getFilePath() : null;
    }
    
    public void updateStatus(Long reportId, Integer status) {
        TrainingReport report = reportMapper.selectById(reportId);
        if (report != null) {
            report.setStatus(status);
            report.setUpdateTime(LocalDateTime.now());
            reportMapper.updateById(report);
        }
    }
    
    public List<Map<String, Object>> getStudentScores(Long studentId) {
        return reportMapper.selectScoreByClassId(studentId);
    }

    public PageResult<ReportDTO> getMyScores(Long studentId, Integer pageNum, Integer pageSize, Long courseId) {
        Page<TrainingReport> page = new Page<>(pageNum, pageSize);
        
        LambdaQueryWrapper<TrainingReport> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(TrainingReport::getStudentId, studentId);
        if (courseId != null) {
            wrapper.eq(TrainingReport::getCourseId, courseId);
        }
        wrapper.orderByDesc(TrainingReport::getCreateTime);
        
        Page<TrainingReport> result = reportMapper.selectPage(page, wrapper);
        
        List<ReportDTO> dtoList = result.getRecords().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
        
        return new PageResult<>(result.getTotal(), dtoList);
    }
    
    private ReportDTO convertToDTO(TrainingReport report) {
        ReportDTO dto = new ReportDTO();
        dto.setId(report.getId());
        dto.setStudentId(report.getStudentId());
        dto.setCourseId(report.getCourseId());
        dto.setRequirementId(report.getRequirementId());
        dto.setTitle(report.getTitle());
        dto.setContent(report.getContent());
        dto.setFilePath(report.getFilePath());
        dto.setFileName(report.getFileName());
        dto.setFileType(report.getFileType());
        dto.setStatus(report.getStatus());
        dto.setStatusName(Constants.getReportStatusName(report.getStatus()));
        dto.setCreateTime(report.getCreateTime());
        dto.setUpdateTime(report.getUpdateTime());
        
        // 关联的报告要求信息
        if (report.getRequirementId() != null) {
            ReportRequirement requirement = requirementMapper.selectById(report.getRequirementId());
            if (requirement != null) {
                dto.setRequirementTitle(requirement.getTitle());
                if (requirement.getDeadline() != null) {
                    dto.setDeadline(requirement.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
                }
            }
        }
        
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
        
        if (report.getCourseId() != null) {
            com.training.entity.Course course = courseMapper.selectById(report.getCourseId());
            if (course != null) {
                dto.setCourseName(course.getCourseName());
            }
        }
        
        LambdaQueryWrapper<EvaluationRecord> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(EvaluationRecord::getReportId, report.getId());
        EvaluationRecord evaluation = evaluationMapper.selectOne(wrapper);
        if (evaluation != null) {
            dto.setEvaluationId(evaluation.getId());
            dto.setCompletenessScore(evaluation.getCompletenessScore());
            dto.setSpecificationScore(evaluation.getSpecificationScore());
            dto.setKnowledgeScore(evaluation.getKnowledgeScore());
            dto.setTotalScore(evaluation.getTotalScore());
            dto.setAiEvaluation(evaluation.getAiEvaluation());
            dto.setManualEvaluation(evaluation.getManualEvaluation());
            dto.setDynamicScoresJson(evaluation.getDynamicScoresJson());
            dto.setEvaluateTime(evaluation.getEvaluateTime());
        }
        
        return dto;
    }
}