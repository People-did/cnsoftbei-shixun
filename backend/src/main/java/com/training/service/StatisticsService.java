package com.training.service;

import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.training.common.Result;
import com.training.dto.ScoreDistribution;
import com.training.dto.StatisticsDTO;
import com.training.entity.EvaluationRecord;
import com.training.entity.SysClass;
import com.training.entity.SysUser;
import com.training.entity.TrainingReport;
import com.training.mapper.EvaluationRecordMapper;
import com.training.mapper.SysClassMapper;
import com.training.mapper.SysUserMapper;
import com.training.mapper.TrainingReportMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsService {
    
    private final SysClassMapper classMapper;
    private final SysUserMapper userMapper;
    private final TrainingReportMapper reportMapper;
    private final EvaluationRecordMapper evaluationMapper;
    
    public Result<StatisticsDTO> getClassStatistics(Long classId, Long currentUserId, Integer currentRole) {
        // 学生只能查看自己所在班级的统计
        if (currentRole != null && currentRole == com.training.common.Constants.ROLE_STUDENT) {
            SysUser student = userMapper.selectById(currentUserId);
            if (student == null || student.getClassId() == null || !student.getClassId().equals(classId)) {
                return Result.error("只能查看自己班级的统计信息");
            }
        }
        
        SysClass sysClass = classMapper.selectById(classId);
        if (sysClass == null) {
            return Result.error("班级不存在");
        }
        
        StatisticsDTO dto = new StatisticsDTO();
        dto.setClassId(classId);
        dto.setClassName(sysClass.getClassName());
        
        // 统计学生人数
        List<SysUser> students = userMapper.selectByClassId(classId);
        dto.setTotalStudents(students.size());
        
        // 统计已评价的报告
        List<Map<String, Object>> scores = evaluationMapper.selectAllScores(classId);
        
        dto.setTotalReports(scores.size());
        
        int evaluatedCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        BigDecimal minScore = BigDecimal.valueOf(100);
        
        for (Map<String, Object> score : scores) {
            Object totalObj = score.get("total_score");
            if (totalObj != null) {
                BigDecimal scoreValue = new BigDecimal(totalObj.toString());
                evaluatedCount++;
                totalScore = totalScore.add(scoreValue);
                
                if (scoreValue.compareTo(maxScore) > 0) {
                    maxScore = scoreValue;
                }
                if (scoreValue.compareTo(minScore) < 0) {
                    minScore = scoreValue;
                }
            }
        }
        
        dto.setEvaluatedReports(evaluatedCount);
        
        if (evaluatedCount > 0) {
            dto.setAvgScore(totalScore.divide(BigDecimal.valueOf(evaluatedCount), 2, RoundingMode.HALF_UP));
        } else {
            dto.setAvgScore(BigDecimal.ZERO);
        }
        
        dto.setMaxScore(evaluatedCount > 0 ? maxScore : BigDecimal.ZERO);
        dto.setMinScore(evaluatedCount > 0 ? minScore : BigDecimal.ZERO);
        
        // 计算分数段分布
        dto.setScoreDistribution(calculateScoreDistribution(scores));
        
        // 成绩列表（将 MyBatis 自动转换的驼峰 key 还原为下划线格式，确保前端匹配）
        List<Map<String, Object>> normalizedScores = new ArrayList<>();
        for (Map<String, Object> score : scores) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            for (Map.Entry<String, Object> entry : score.entrySet()) {
                String key = entry.getKey();
                // 将 camelCase 转回 snake_case
                String snakeKey = key.replaceAll("([a-z])([A-Z])", "$1_$2").toLowerCase();
                normalized.put(snakeKey, entry.getValue());
            }
            normalizedScores.add(normalized);
        }
        dto.setScoreList(normalizedScores);
        
        return Result.success(dto);
    }
    
    public Result<List<Map<String, Object>>> getAllClassesStatistics(Long currentUserId, Integer currentRole) {
        // 学生只能看到自己所在班级
        if (currentRole != null && currentRole == com.training.common.Constants.ROLE_STUDENT) {
            SysUser student = userMapper.selectById(currentUserId);
            if (student == null || student.getClassId() == null) {
                return Result.error("未分配班级");
            }
            SysClass sysClass = classMapper.selectById(student.getClassId());
            if (sysClass == null) {
                return Result.error("班级不存在");
            }
            List<Map<String, Object>> result = new ArrayList<>();
            Map<String, Object> stat = buildClassStat(sysClass.getId(), sysClass.getClassName());
            result.add(stat);
            return Result.success(result);
        }
        
        List<SysClass> classes = classMapper.selectList(null);
        
        List<Map<String, Object>> result = new ArrayList<>();
        
        for (SysClass sysClass : classes) {
            Map<String, Object> stat = buildClassStat(sysClass.getId(), sysClass.getClassName());
            result.add(stat);
        }
        
        return Result.success(result);
    }
    
    private Map<String, Object> buildClassStat(Long classId, String className) {
        Map<String, Object> stat = new HashMap<>();
        stat.put("classId", classId);
        stat.put("className", className);
        
        List<Map<String, Object>> scores = evaluationMapper.selectAllScores(classId);
        
        int evaluatedCount = 0;
        BigDecimal totalScore = BigDecimal.ZERO;
        BigDecimal maxScore = BigDecimal.ZERO;
        BigDecimal minScore = BigDecimal.valueOf(100);
        
        for (Map<String, Object> score : scores) {
            Object totalObj = score.get("total_score");
            if (totalObj != null) {
                BigDecimal scoreValue = new BigDecimal(totalObj.toString());
                evaluatedCount++;
                totalScore = totalScore.add(scoreValue);
                
                if (scoreValue.compareTo(maxScore) > 0) {
                    maxScore = scoreValue;
                }
                if (scoreValue.compareTo(minScore) < 0) {
                    minScore = scoreValue;
                }
            }
        }
        
        stat.put("totalReports", scores.size());
        stat.put("evaluatedReports", evaluatedCount);
        stat.put("avgScore", evaluatedCount > 0 
                ? totalScore.divide(BigDecimal.valueOf(evaluatedCount), 2, RoundingMode.HALF_UP) 
                : BigDecimal.ZERO);
        stat.put("maxScore", evaluatedCount > 0 ? maxScore : BigDecimal.ZERO);
        stat.put("minScore", evaluatedCount > 0 ? minScore : BigDecimal.ZERO);
        
        return stat;
    }
    
    private List<ScoreDistribution> calculateScoreDistribution(List<Map<String, Object>> scores) {
        int[] ranges = {0, 0, 0, 0, 0}; // 0-60, 60-70, 70-80, 80-90, 90-100
        
        for (Map<String, Object> score : scores) {
            Object totalObj = score.get("total_score");
            if (totalObj != null) {
                BigDecimal scoreValue = new BigDecimal(totalObj.toString());
                double s = scoreValue.doubleValue();
                
                if (s < 60) {
                    ranges[0]++;
                } else if (s < 70) {
                    ranges[1]++;
                } else if (s < 80) {
                    ranges[2]++;
                } else if (s < 90) {
                    ranges[3]++;
                } else {
                    ranges[4]++;
                }
            }
        }
        
        int total = scores.size();
        List<ScoreDistribution> distributions = new ArrayList<>();
        String[] labels = {"0-60分", "60-70分", "70-80分", "80-90分", "90-100分"};
        
        for (int i = 0; i < 5; i++) {
            ScoreDistribution dist = new ScoreDistribution();
            dist.setRange(labels[i]);
            dist.setCount(ranges[i]);
            dist.setPercentage(total > 0 ? (double) Math.round(ranges[i] * 100.0 / total) : 0.0);
            distributions.add(dist);
        }
        
        return distributions;
    }
    
    public Result<Map<String, Object>> exportScores(Long classId, String exportPath, Long currentUserId, Integer currentRole) {
        // 学生只能导出自己班级的成绩
        if (currentRole != null && currentRole == com.training.common.Constants.ROLE_STUDENT) {
            SysUser student = userMapper.selectById(currentUserId);
            if (student == null || student.getClassId() == null || !student.getClassId().equals(classId)) {
                return Result.error("只能导出自己班级的成绩");
            }
        }
        
        Result<StatisticsDTO> statResult = getClassStatistics(classId, currentUserId, currentRole);
        if (statResult.getCode() != 200) {
            return Result.error("导出失败");
        }
        
        StatisticsDTO dto = statResult.getData();
        
        List<Map<String, Object>> rawData = dto.getScoreList();
        
        // 写入Excel
        String filename = dto.getClassName() + "_成绩单_" + System.currentTimeMillis() + ".xlsx";
        String fullPath = exportPath + "/" + filename;
        
        try {
            // 创建目录
            new java.io.File(exportPath).mkdirs();
            
            // 将 Map 数据转为 List<List<Object>>，确保列顺序和 head 一致
            DateTimeFormatter dtf = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            List<List<Object>> rows = new ArrayList<>();
            for (Map<String, Object> map : rawData) {
                List<Object> row = new ArrayList<>();
                row.add(map.get("username"));
                row.add(map.get("student_name"));
                row.add(map.get("class_name"));
                row.add(map.get("report_title"));
                row.add(map.get("completeness_score"));
                row.add(map.get("specification_score"));
                row.add(map.get("knowledge_score"));
                row.add(map.get("total_score"));
                
                // 格式化评价时间为字符串，避免 EasyExcel 写入 Timestamp 导致列宽不足显示为星号
                Object timeObj = map.get("evaluate_time");
                String timeStr = "";
                if (timeObj != null) {
                    if (timeObj instanceof LocalDateTime) {
                        timeStr = ((LocalDateTime) timeObj).format(dtf);
                    } else if (timeObj instanceof java.util.Date) {
                        LocalDateTime ldt = ((java.util.Date) timeObj).toInstant()
                                .atZone(ZoneId.systemDefault()).toLocalDateTime();
                        timeStr = ldt.format(dtf);
                    } else if (timeObj instanceof java.sql.Timestamp) {
                        LocalDateTime ldt = ((java.sql.Timestamp) timeObj).toLocalDateTime();
                        timeStr = ldt.format(dtf);
                    } else {
                        timeStr = timeObj.toString();
                    }
                }
                row.add(timeStr);
                rows.add(row);
            }
            
            EasyExcel.write(fullPath)
                    .head(createHeaders())
                    .sheet("成绩单")
                    .doWrite(rows);
            
            Map<String, Object> result = new HashMap<>();
            result.put("filename", filename);
            result.put("path", fullPath);
            
            return Result.success(result);
        } catch (Exception e) {
            return Result.error("导出失败: " + e.getMessage());
        }
    }
    
    private List<List<String>> createHeaders() {
        List<List<String>> headers = new ArrayList<>();
        headers.add(Arrays.asList("学号"));
        headers.add(Arrays.asList("姓名"));
        headers.add(Arrays.asList("班级"));
        headers.add(Arrays.asList("报告标题"));
        headers.add(Arrays.asList("完整性得分"));
        headers.add(Arrays.asList("规范性得分"));
        headers.add(Arrays.asList("知识点得分"));
        headers.add(Arrays.asList("总分"));
        headers.add(Arrays.asList("评价时间"));
        return headers;
    }

    /**
     * 【全新】学生多维度能力雷达图 — 根据学生所有已评价报告的AI评分，
     * 聚合计算出代码能力、文档能力、设计能力、测试能力、团队协作五个维度的得分
     *
     * @param studentId 学生ID
     * @return 雷达图数据 { dimensions: [{name, score, fullMark}], totalReports, evaluatedReports }
     */
    public Result<Map<String, Object>> getStudentRadar(Long studentId) {
        // 1. 查询学生所有报告
        LambdaQueryWrapper<TrainingReport> reportWrapper = new LambdaQueryWrapper<>();
        reportWrapper.eq(TrainingReport::getStudentId, studentId);
        List<TrainingReport> reports = reportMapper.selectList(reportWrapper);

        if (reports.isEmpty()) {
            return Result.error("您还没有提交过报告，无法生成能力雷达图");
        }

        // 2. 五维能力：代码能力、文档能力、设计能力、测试能力、团队协作
        // 每个维度收集所有评价中的相关得分
        Map<String, List<BigDecimal>> dimensionScores = new LinkedHashMap<>();
        dimensionScores.put("代码能力", new ArrayList<>());
        dimensionScores.put("文档能力", new ArrayList<>());
        dimensionScores.put("设计能力", new ArrayList<>());
        dimensionScores.put("测试能力", new ArrayList<>());
        dimensionScores.put("团队协作", new ArrayList<>());

        int evaluatedCount = 0;

        for (TrainingReport report : reports) {
            LambdaQueryWrapper<EvaluationRecord> evalWrapper = new LambdaQueryWrapper<>();
            evalWrapper.eq(EvaluationRecord::getReportId, report.getId());
            EvaluationRecord evaluation = evaluationMapper.selectOne(evalWrapper);

            if (evaluation == null) continue;
            evaluatedCount++;

            // 3. 尝试解析 dynamicScoresJson（自定义多维度指标）
            if (evaluation.getDynamicScoresJson() != null && !evaluation.getDynamicScoresJson().isEmpty()) {
                try {
                    JSONObject dynamicJson = JSON.parseObject(evaluation.getDynamicScoresJson());
                    JSONObject scores = dynamicJson.getJSONObject("scores");
                    if (scores != null) {
                        // 遍历每个自定义维度，映射到五大能力
                        for (String key : scores.keySet()) {
                            JSONObject dimScore = scores.getJSONObject(key);
                            BigDecimal score = dimScore != null ? dimScore.getBigDecimal("score") : null;
                            if (score != null) {
                                // 根据维度名称关键词匹配到五大能力
                                mapToDimension(dimensionScores, key, score);
                            }
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析dynamicScoresJson失败: {}", e.getMessage());
                }
            }

            // 4. 传统三大维度得分也纳入计算
            if (evaluation.getCompletenessScore() != null) {
                // 完整性 → 文档能力、设计能力
                BigDecimal compScore = evaluation.getCompletenessScore();
                BigDecimal compNorm = compScore.divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
                dimensionScores.get("文档能力").add(compNorm);
                dimensionScores.get("设计能力").add(compNorm.multiply(new BigDecimal("0.6")));
            }
            if (evaluation.getSpecificationScore() != null) {
                // 规范性 → 代码能力、文档能力
                BigDecimal specScore = evaluation.getSpecificationScore();
                BigDecimal specNorm = specScore.divide(new BigDecimal("30"), 4, RoundingMode.HALF_UP);
                dimensionScores.get("代码能力").add(specNorm);
                dimensionScores.get("文档能力").add(specNorm.multiply(new BigDecimal("0.5")));
            }
            if (evaluation.getKnowledgeScore() != null) {
                // 知识点 → 设计能力、测试能力
                BigDecimal knowScore = evaluation.getKnowledgeScore();
                BigDecimal knowNorm = knowScore.divide(new BigDecimal("40"), 4, RoundingMode.HALF_UP);
                dimensionScores.get("设计能力").add(knowNorm);
                dimensionScores.get("测试能力").add(knowNorm.multiply(new BigDecimal("0.7")));
            }

            // 5. 从AI评价文本中提取维度关键词（辅助增强）
            String aiEvalText = evaluation.getAiEvaluation();
            if (aiEvalText != null && !aiEvalText.isEmpty()) {
                extractFromAiText(dimensionScores, aiEvalText, evaluation.getTotalScore());
            }
        }

        if (evaluatedCount == 0) {
            return Result.error("您的报告尚未被评价，无法生成能力雷达图");
        }

        // 6. 计算每个维度的最终得分（归一化到0-100）
        List<Map<String, Object>> dimensions = new ArrayList<>();
        for (Map.Entry<String, List<BigDecimal>> entry : dimensionScores.entrySet()) {
            String dimName = entry.getKey();
            List<BigDecimal> scores = entry.getValue();

            Map<String, Object> dim = new LinkedHashMap<>();
            dim.put("name", dimName);

            if (scores.isEmpty()) {
                dim.put("score", 0);
            } else {
                // 取平均值
                BigDecimal sum = BigDecimal.ZERO;
                for (BigDecimal s : scores) {
                    sum = sum.add(s);
                }
                BigDecimal avg = sum.divide(new BigDecimal(scores.size()), 2, RoundingMode.HALF_UP);
                // 转换为百分制
                int finalScore = avg.multiply(new BigDecimal("100")).intValue();
                // 限制在0-100之间
                finalScore = Math.max(0, Math.min(100, finalScore));
                dim.put("score", finalScore);
            }
            dim.put("fullMark", 100);
            dimensions.add(dim);
        }

        // 7. 添加总分雷达维度
        Map<String, Object> totalDim = new LinkedHashMap<>();
        totalDim.put("name", "综合总分");
        int totalAvg = 0;
        int dimCount = 0;
        for (Map<String, Object> d : dimensions) {
            if (!"综合总分".equals(d.get("name"))) {
                totalAvg += (int) d.get("score");
                dimCount++;
            }
        }
        totalDim.put("score", dimCount > 0 ? totalAvg / dimCount : 0);
        totalDim.put("fullMark", 100);
        dimensions.add(totalDim);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dimensions", dimensions);
        result.put("totalReports", reports.size());
        result.put("evaluatedReports", evaluatedCount);
        result.put("studentName", reports.get(0).getStudentId() != null
                ? Optional.ofNullable(userMapper.selectById(reports.get(0).getStudentId()))
                    .map(SysUser::getRealName).orElse("")
                : "");

        return Result.success(result);
    }

    /**
     * 将自定义维度名称映射到五大能力维度
     */
    private void mapToDimension(Map<String, List<BigDecimal>> dimensionScores, String dimName, BigDecimal score) {
        String lowerName = dimName.toLowerCase();
        BigDecimal normalized = BigDecimal.ZERO;
        // 尝试根据分数值进行归一化：如果分数 > 10，假设满分是100；否则假设是10分制
        if (score.compareTo(new BigDecimal("10")) > 0) {
            normalized = score.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);
        } else {
            normalized = score.divide(new BigDecimal("10"), 4, RoundingMode.HALF_UP);
        }

        // 关键词匹配
        if (containsAny(lowerName, "代码", "编码", "编程", "实现", "code", "coding", "program")) {
            dimensionScores.get("代码能力").add(normalized);
        }
        if (containsAny(lowerName, "文档", "报告", "书写", "表达", "doc", "document", "report", "writing")) {
            dimensionScores.get("文档能力").add(normalized);
        }
        if (containsAny(lowerName, "设计", "架构", "方案", "结构", "design", "architecture", "plan", "structure")) {
            dimensionScores.get("设计能力").add(normalized);
        }
        if (containsAny(lowerName, "测试", "调试", "验证", "test", "debug", "verify", "quality")) {
            dimensionScores.get("测试能力").add(normalized);
        }
        if (containsAny(lowerName, "协作", "团队", "沟通", "合作", "team", "collaborate", "communication", "group")) {
            dimensionScores.get("团队协作").add(normalized);
        }
        // 通用维度：根据内容智能分配
        if (containsAny(lowerName, "完整性", "完整", "completeness")) {
            dimensionScores.get("文档能力").add(normalized);
            dimensionScores.get("设计能力").add(normalized.multiply(new BigDecimal("0.5")));
        }
        if (containsAny(lowerName, "规范", "规范性", "格式", "standard", "format", "specification")) {
            dimensionScores.get("代码能力").add(normalized);
            dimensionScores.get("文档能力").add(normalized.multiply(new BigDecimal("0.6")));
        }
        if (containsAny(lowerName, "知识点", "知识", "理论", "knowledge", "theory")) {
            dimensionScores.get("设计能力").add(normalized);
            dimensionScores.get("测试能力").add(normalized.multiply(new BigDecimal("0.7")));
        }
        if (containsAny(lowerName, "创新", "创新性", "创意", "innovation", "creativity")) {
            dimensionScores.get("设计能力").add(normalized);
            dimensionScores.get("代码能力").add(normalized.multiply(new BigDecimal("0.5")));
        }
        // 默认兜底：如果都没有匹配，按权重均分到五个维度
        if (!containsAny(lowerName, "代码", "编码", "编程", "实现", "code", "coding", "program",
                "文档", "报告", "书写", "表达", "doc", "document", "report", "writing",
                "设计", "架构", "方案", "结构", "design", "architecture", "plan", "structure",
                "测试", "调试", "验证", "test", "debug", "verify", "quality",
                "协作", "团队", "沟通", "合作", "team", "collaborate", "communication", "group",
                "完整性", "完整", "completeness",
                "规范", "规范性", "格式", "standard", "format", "specification",
                "知识点", "知识", "理论", "knowledge", "theory",
                "创新", "创新性", "创意", "innovation", "creativity")) {
            BigDecimal weight = new BigDecimal("0.2");
            dimensionScores.get("代码能力").add(normalized.multiply(weight));
            dimensionScores.get("文档能力").add(normalized.multiply(weight));
            dimensionScores.get("设计能力").add(normalized.multiply(weight));
            dimensionScores.get("测试能力").add(normalized.multiply(weight));
            dimensionScores.get("团队协作").add(normalized.multiply(weight));
        }
    }

    /**
     * 从AI评价文本中提取能力相关关键词，辅助增强维度得分
     */
    private void extractFromAiText(Map<String, List<BigDecimal>> dimensionScores, String aiText, BigDecimal totalScore) {
        if (totalScore == null) return;
        BigDecimal baseNorm = totalScore.divide(new BigDecimal("100"), 4, RoundingMode.HALF_UP);

        // 代码能力关键词
        if (containsAny(aiText.toLowerCase(), "代码", "编码", "算法", "编程", "实现")) {
            dimensionScores.get("代码能力").add(baseNorm);
        }
        // 文档能力关键词
        if (containsAny(aiText.toLowerCase(), "文档", "报告", "格式", "排版", "表达")) {
            dimensionScores.get("文档能力").add(baseNorm);
        }
        // 设计能力关键词
        if (containsAny(aiText.toLowerCase(), "设计", "架构", "方案", "结构", "模式")) {
            dimensionScores.get("设计能力").add(baseNorm);
        }
        // 测试能力关键词
        if (containsAny(aiText.toLowerCase(), "测试", "调试", "验证", "质量")) {
            dimensionScores.get("测试能力").add(baseNorm);
        }
        // 团队协作关键词
        if (containsAny(aiText.toLowerCase(), "协作", "团队", "沟通", "合作", "分工")) {
            dimensionScores.get("团队协作").add(baseNorm);
        }
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }
}
