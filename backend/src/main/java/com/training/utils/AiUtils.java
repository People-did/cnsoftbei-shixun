package com.training.utils;

import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.training.entity.EvaluationCriterion;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.*;

@Slf4j
@Component
public class AiUtils {
    
    @Value("${ai.api-url}")
    private String apiUrl;
    
    @Value("${ai.api-key}")
    private String apiKey;
    
    @Value("${ai.model}")
    private String model;
    
    @Value("${ai.timeout:120}")
    private int timeout;
    
    private static final String EVALUATION_PROMPT = """
        你是一位专业的软件工程教师，请对学生的实训报告进行评价。

        评价维度：
        1. 完整性（30分）：报告是否包含引言、需求分析、设计、实现、测试、总结等完整章节
        2. 规范性（30分）：格式是否规范，代码是否符合编码规范，文档结构是否清晰
        3. 知识点覆盖（40分）：是否覆盖核心知识点，包括但不限于：需求分析、设计模式、数据库设计、接口设计、测试方法等

        请严格根据报告内容进行评分，评分应当客观公正。

        请以JSON格式返回评价结果，不要包含任何其他内容：
        {
          "completeness": {"score": 分数(0-30), "comment": "评价说明"},
          "specification": {"score": 分数(0-30), "comment": "评价说明"},
          "knowledge": {"score": 分数(0-40), "comment": "评价说明"},
          "totalScore": 总分,
          "suggestions": ["改进建议1", "改进建议2", "改进建议3"]
        }

        以下是学生的实训报告内容：
        """;

    /** 全格式智能评价 Prompt — 支持文档+截图描述+代码+ZIP 混合成果，输出完整7段式报告 */
    private static final String FULL_REPORT_PROMPT = """
        你是一位资深的实训指导教师，负责对大学生提交的实训成果进行全方位智能评价。
        你的评价必须客观、具体、有依据，每条评语都要引用成果原文中的具体内容。

        ## 评价任务
        请根据以下学生提交的实训成果内容，生成一份完整的《实训成果评价报告》。

        ## 评分标准
        %s

        ## 输出格式（严格按此JSON结构返回，不要包含任何markdown标记或额外文字）
        {
          "overview": {
            "fileCount": <文件数量>,
            "fileTypes": "<文档/截图/代码/混合>",
            "resultType": "<识别的成果类型，如：Web开发实训/数据库设计/算法实现/网络配置>",
            "completeness": "<✅ 完整 / ⚠️ 基本完整 / ❌ 部分缺失>"
          },
          "documentSummary": {
            "chapters": ["<章节1>", "<章节2>", "..."],
            "abstract": "<核心内容摘要，200字以内>",
            "completeness": "<含引言/正文/结论 — 完整 / 缺少xxx部分>",
            "wordCount": <字数>
          },
          "imageSummary": {
            "count": <截图数量>,
            "operations": ["<步骤1描述>", "<步骤2描述>", "..."],
            "coverage": "<功能覆盖评估：完整 / 部分 / 不足>"
          },
          "codeSummary": {
            "language": "<编程语言/技术栈>",
            "modules": ["<模块1>", "<模块2>", "..."],
            "totalLines": <代码行数>,
            "fileCount": <文件数量>,
            "structureAssessment": "<清晰 / 一般 / 混乱>"
          },
          "scoringDetails": [
            {
              "dimension": "<评分维度名称>",
              "maxScore": <满分>,
              "score": <得分>,
              "comment": "<评分说明，必须引用成果原文中的具体内容作为依据>"
            }
          ],
          "totalScore": <总分>,
          "overallEvaluation": {
            "strengths": "<最突出的优点，1-2句话>",
            "weaknesses": "<最需改进的问题，1-2句话>"
          },
          "suggestions": ["<改进建议1>", "<改进建议2>", "<改进建议3>"],
          "checkPoints": ["<需要教师人工核查的内容>"]
        }

        ## 评分原则
        1. 每条评分的comment必须引用文件中的原文内容或具体函数/类名作为依据
        2. 扣分必须说明具体原因和扣分幅度
        3. 满分项也要说明为什么值得满分
        4. suggestions 中的每条建议必须具体、可操作
        5. checkPoints 如无则返回空数组 []

        ## 学生提交的实训成果内容：
        %s
        """;

    /** 对话式反馈 System Prompt — 学生就评分结果与AI进行多轮对话 */
    private static final String CHAT_FEEDBACK_SYSTEM_PROMPT = """
        你是一位耐心、专业的实训指导教师。你正在对一名学生已经提交并被打分的实训作业进行答疑辅导。

        ## 你的身份
        你就是刚才给这份作业打分的老师，你完全了解评分过程和每个扣分点的细节。

        ## 对话规则
        1. 请用中文与学生交流，语气亲切、鼓励、有建设性
        2. 学生可以询问任何扣分点（如"第1点为什么扣分"），你必须详细解释：
           - 扣分原因（引述作业原文中的具体问题）
           - 应该如何改进（给出具体、可操作的修改建议）
           - 如果改好了能加几分
        3. 学生也可以问"怎么才能拿满分"、"我这个思路对吗"等一般性问题
        4. 回答要具体、有针对性，不要泛泛而谈
        5. 如果学生的问题与本次作业无关，礼貌地引导回作业反馈主题
        6. 每次回答控制在300字以内，简洁有力

        ## 当前作业信息
        - 作业标题：%s
        - 得分：%s分（满分100分）

        ## 扣分点列表
        %s

        ## 原始评价报告
        %s

        ## 学生作业原文
        %s

        现在开始与学生对话。如果学生第一次进入，请主动说"你好！我是你的AI评价助教。我看到你的作业有以下扣分点，你可以点击任何一条来了解详细原因和修改建议哦～"并列出扣分点序号。之后根据学生的提问逐一回答。
        """;

    /** 个性化改进计划 Prompt — 根据学生多次评价的薄弱项，生成改进计划与学习资源推荐 */
    private static final String IMPROVEMENT_PLAN_PROMPT = """
        你是一位资深的软件工程教育专家和学习规划师。请根据以下学生的历史实训报告评价数据，
        对学生进行全面的学习诊断，并生成一份个性化的改进计划。

        ## 你的任务
        1. 分析学生在多次作业中的表现，识别反复出现的薄弱项（维度）
        2. 基于薄弱项，制定一个分阶段、可执行的改进计划
        3. 针对每个薄弱项，推荐具体的学习资源（书籍、在线课程、文章、工具等）

        ## 分析要求
        - 关注趋势：哪些维度持续低分？哪些在进步？哪些在退步？
        - 区分根本原因：是知识盲区、方法不当、还是态度/投入问题？
        - 建议要具体、可操作，避免泛泛而谈
        - 资源推荐要真实可用，标注推荐理由

        ## 输出格式（纯JSON，不要任何markdown标记）
        {
          "overview": "整体评估概述（200字以内）",
          "weakPoints": [
            {
              "dimension": "薄弱维度名称",
              "severity": "严重程度（高/中/低）",
              "trend": "趋势（持续偏低/波动/近期恶化/正在改善）",
              "evidence": "具体证据（引用得分数据）",
              "rootCause": "根本原因分析"
            }
          ],
          "improvementPlan": {
            "summary": "改进计划总述（100字以内）",
            "stages": [
              {
                "stage": 1,
                "title": "阶段标题",
                "duration": "建议周期（如：2周）",
                "goals": ["目标1", "目标2"],
                "actions": ["具体行动1", "具体行动2"],
                "expectedOutcome": "预期成果"
              }
            ]
          },
          "resources": [
            {
              "type": "书籍/在线课程/工具/文章/视频",
              "title": "资源名称",
              "description": "资源简介",
              "url": "推荐链接（如无可填null）",
              "targetWeakPoint": "对应的薄弱维度",
              "recommendReason": "推荐理由"
            }
          ],
          "motivation": "鼓励性结语（100字以内）"
        }
        
        ## 学生历史评价数据：
        %s
        """;

    /** AI智能批注 Prompt — 逐段分析报告，找出具体问题并给出精确位置的批注 */
    private static final String ANNOTATION_PROMPT = """
        你是一位资深的软件工程教师，正在逐段仔细批改一份学生实训报告。
        你的任务不是给整体分数，而是像真人教师改作业一样——找出报告中每一个需要批注的具体问题，
        并给出精确的原文标注和修改建议。

        ## 批注维度
        请从以下维度审视报告内容：
        1. **规范性**：格式是否规范，命名是否合理，文档结构是否清晰
        2. **完整性**：是否有明显的章节缺失，内容是否充分
        3. **知识点**：技术表述是否准确，概念使用是否正确，是否有知识性错误
        4. **代码质量**：代码片段是否规范，逻辑是否正确，是否有明显bug
        5. **文档结构**：章节组织是否合理，逻辑是否连贯
        6. **逻辑问题**：论述是否有前后矛盾、逻辑漏洞
        7. **表述问题**：语句是否通顺，用词是否准确，是否存在语病

        ## 输出格式（纯JSON数组，不要包含任何markdown标记或其他文字）
        [
          {
            "highlightedText": "报告中需要批注的原文（请精确摘录，至少15个字符，确保可在原文中唯一定位）",
            "category": "规范性",
            "severity": "error",
            "comment": "具体的问题说明，指出这里为什么有问题（50-150字）",
            "suggestion": "具体的修改建议，给出可操作的修改方式（50-200字）"
          }
        ]

        ## 严重程度说明
        - error：严重问题（知识性错误、逻辑硬伤、关键功能遗漏）
        - warning：需要改进（格式不规范、表述不清晰、内容不充分）
        - info：建议提醒（可优化的地方、补充说明的建议）
        - suggestion：优化建议（锦上添花的改进方向）

        ## 批注要求
        1. 只标注确实存在问题的内容，不要过度批注（每1000字控制在5-8条）
        2. highlightedText 必须是原文中的精确文字，不要改写
        3. 每条批注的comment要具体，必须引用原文细节说明问题
        4. suggestion要给出可操作的修改方案，不要空话套话
        5. 优先批注严重问题（error），其次是一般问题（warning），最后是建议（info/suggestion）
        6. 同一段文字如果同时有多个问题，请分别列出
        7. 如果原文确实找不到明显问题，返回空数组 []

        ## 学生报告内容：
        %s
        """;

    /** 评分标准自动生成 Prompt */
    private static final String RUBRIC_AUTO_GEN_PROMPT = """
        你是一位资深的教学质量管理专家。请根据以下学生实训成果的内容，
        自动生成一套科学合理的评分标准（Rubric）。

        ## 成果类型判定规则
        - 文档报告类（含设计文档/实验报告/论文）：内容完整性 + 逻辑结构 + 技术深度 + 格式规范
        - 代码/程序类（源码为主）：功能实现正确性 + 代码结构规范 + 注释与可读性 + 文档完整性
        - 截图操作类（操作截图为主）：操作步骤完整 + 功能覆盖度 + 界面规范度 + 说明清晰度
        - 混合成果（文档+代码+截图）：文档质量 + 代码质量 + 功能演示 + 整体完整度
        - 无法识别类型：完整性 + 规范性 + 核心内容质量

        ## 要求
        1. 生成4个评分维度，权重之和必须恰好为100
        2. 每个维度的 maxScore 等于其 weight 值
        3. description 要具体描述该维度的评分要点

        ## 输出格式（纯JSON数组，不要任何markdown标记）
        [
          {
            "name": "<维度名称>",
            "weight": <权重整数>,
            "maxScore": <最高分>,
            "description": "<评分要点描述>"
          }
        ]

        ## 学生提交的成果内容：
        %s
        """;

    /**
     * 原有默认打分方法（100% 保持不动，确保旧功能完全兼容）
     */
    public EvaluationResult evaluateReport(String reportContent) {
        try {
            String fullPrompt = EVALUATION_PROMPT + "\n\n" + reportContent;
            
            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            
            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", fullPrompt);
            messages.add(message);
            requestBody.put("messages", messages);
            
            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();
            
            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                JSONObject choices = result.getJSONArray("choices").getJSONObject(0);
                JSONObject messageObj = choices.getJSONObject("message");
                String content = messageObj.getString("content");
                
                return parseEvaluationResult(content);
            } else {
                log.error("AI API调用失败: {}", response.body());
                return getDefaultEvaluationResult();
            }
        } catch (Exception e) {
            log.error("AI评价异常: ", e);
            return getDefaultEvaluationResult();
        }
    }

    /**
     * 从教师上传的教学大纲 PDF/文档纯文本中，智能提取并解构出评分指标、权重和描述
     */
    public List<EvaluationCriterion> parseCriterionFromPdf(String docText) {
        List<EvaluationCriterion> list = new ArrayList<>();
        try {
            String prompt = """
                你是一个资深的教务专家与软件工程教学质量检查助手。
                请从业界或老师提供的这份教学大纲、实训指导书或评分标准文档中，智能识别、提炼出软件实训作业的各项具体评分指标、权重占比和详细打分描述。
                
                【硬性规则限制】：
                1. 必须精准识别各单项指标，且所有指标的权重占比(weight)相加之和必须【100% 恰好等于 100】。
                2. 指标的最高上限值(maxScore)即等同于它的权重值(例如：权重为20，最高分就是20.00)。
                3. 你必须【严格、只能】以标准JSON数组格式返回，不要包含任何 markdown 语法标记(如 ```json)、不要包含任何解释性文字。
                
                【返回格式范例】：
                [
                  {
                    "name": "代码健壮性",
                    "weight": 25,
                    "maxScore": 25.0,
                    "description": "检查代码是否包含异常处理机制、是否有硬编码或潜在内存泄漏隐患。"
                  },
                  {
                    "name": "文档规范性",
                    "weight": 20,
                    "maxScore": 20.0,
                    "description": "检查实训报告格式、排版、目录以及软件截图是否齐全规范。"
                  }
                ]
                
                以下是老师上传的教学指导文档内容：
                \n\n
                """ + docText;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                String aiReply = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                String jsonStr = extractJsonArray(aiReply);
                
                JSONArray arr = JSON.parseArray(jsonStr);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    EvaluationCriterion criterion = new EvaluationCriterion();
                    criterion.setName(obj.getString("name"));
                    criterion.setWeight(obj.getInteger("weight"));
                    criterion.setMaxScore(obj.getBigDecimal("maxScore") != null ? obj.getBigDecimal("maxScore") : BigDecimal.valueOf(obj.getDouble("weight")));
                    criterion.setDescription(obj.getString("description"));
                    list.add(criterion);
                }
            }
        } catch (Exception e) {
            log.error("大模型智能提取评分指标异常: ", e);
        }
        return list;
    }

    /**
     * 【硬核创新点】核心 AI 智能打分（完全适应自定义动态指标）
     */
    public String evaluateWithCustomCriteria(String reportContent, List<EvaluationCriterion> criteria) {
        try {
            StringBuilder criteriaPrompt = new StringBuilder();
            criteriaPrompt.append("你是一位资深的软件工程项目评审专家。请严格根据老师制定的【自定义评分标准】对学生的实训报告进行深度、客观的智能评测打分。\n\n");
            criteriaPrompt.append("【本次实训作业专属的评分指标与限制如下】：\n");
            
            for (int i = 0; i < criteria.size(); i++) {
                EvaluationCriterion c = criteria.get(i);
                criteriaPrompt.append(String.format("%d. 指标名称:【%s】, 满分上限:【%s分】, 权重:【%d%%】。评分细则要求: %s\n", 
                        i + 1, c.getName(), c.getMaxScore().toString(), c.getWeight(), c.getDescription()));
            }
            
            criteriaPrompt.append("\n【重要返回规则约束】：\n");
            criteriaPrompt.append("1. 你对各项指标给出的得分，【绝对不能】超过该项指标的满分上限值。\n");
            criteriaPrompt.append("2. 必须计算并返回正确的综合总分(totalScore)。\n");
            criteriaPrompt.append("3. 必须【严格、只能】以标准JSON格式返回结果，不要混杂任何其他标点和说明，返回结构必须完美对应如下：\n");
            criteriaPrompt.append("{\n");
            criteriaPrompt.append("  \"scores\": {\n");
            
            for (int i = 0; i < criteria.size(); i++) {
                criteriaPrompt.append(String.format("    \"%s\": {\"score\": 具体的得分, \"comment\": \"对应的单项核心评价说明\"}%s\n", 
                        criteria.get(i).getName(), (i == criteria.size() - 1) ? "" : ","));
            }
            criteriaPrompt.append("  },\n");
            criteriaPrompt.append("  \"totalScore\": 最终计算的总分,\n");
            criteriaPrompt.append("  \"suggestions\": [\"针对性的改进建议1\", \"针对性的改进建议2\"]\n");
            criteriaPrompt.append("}\n\n");
            criteriaPrompt.append("以下是当前需要批改的学生实训报告正文内容：\n");

            String finalPrompt = criteriaPrompt.toString() + "\n\n" + reportContent;

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", finalPrompt)));

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                String rawResult = result.getJSONArray("choices").getJSONObject(0).getJSONObject("message").getString("content");
                return extractJson(rawResult);
            } else {
                log.error("动态AI评价失败，接口状态码异常: {}", response.body());
                return createFallbackDynamicJson(criteria);
            }
        } catch (Exception e) {
            log.error("自定义标准AI评分出现严重异常: ", e);
            return createFallbackDynamicJson(criteria);
        }
    }

    /**
     * 【全新】全格式智能评价 — 支持文档+截图描述+代码+ZIP混合成果
     * 输出完整7段式实训成果评价报告JSON
     *
     * @param combinedContent 合并后的所有文件解析内容
     * @param rubricJson      评分标准JSON字符串（用户提供或自动生成），为null时使用默认三大维度
     * @return 完整评价报告JSON字符串
     */
    public String evaluateWithFullReport(String combinedContent, String rubricJson) {
        try {
            // 构建评分标准描述
            String rubricDesc;
            if (rubricJson != null && !rubricJson.isEmpty()) {
                rubricDesc = buildRubricDescription(rubricJson);
            } else {
                rubricDesc = """
                    默认评分标准：
                    1. 内容完整性（30分）：成果是否包含必要的章节和核心内容
                    2. 规范性（30分）：格式、命名、结构是否规范
                    3. 核心内容质量（40分）：技术深度、分析论证、实现质量""";
            }

            String finalPrompt = String.format(FULL_REPORT_PROMPT, rubricDesc, combinedContent);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", finalPrompt)));
            requestBody.put("temperature", 0.3); // 低温度保证评价一致性

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                String rawResult = result.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                return extractJson(rawResult);
            } else {
                log.error("全格式AI评价失败: {}", response.body());
                return createFallbackFullReport();
            }
        } catch (Exception e) {
            log.error("全格式AI评价异常: ", e);
            return createFallbackFullReport();
        }
    }

    /**
     * 【全新】自动生成评分标准（Rubric）
     * 根据上传成果的内容特征，AI自动匹配最合适的评分维度和分值
     *
     * @param combinedContent 合并后的所有文件解析内容
     * @return 评分标准JSON数组字符串
     */
    public List<EvaluationCriterion> autoGenerateRubric(String combinedContent) {
        List<EvaluationCriterion> list = new ArrayList<>();
        try {
            String prompt = String.format(RUBRIC_AUTO_GEN_PROMPT,
                    combinedContent.length() > 3000 ? combinedContent.substring(0, 3000) + "\n... (内容过长已截断)" : combinedContent);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            requestBody.put("temperature", 0.3);

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                String aiReply = result.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                String jsonStr = extractJsonArray(aiReply);

                JSONArray arr = JSON.parseArray(jsonStr);
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    EvaluationCriterion criterion = new EvaluationCriterion();
                    criterion.setName(obj.getString("name"));
                    criterion.setWeight(obj.getInteger("weight"));
                    criterion.setMaxScore(obj.getBigDecimal("maxScore") != null
                            ? obj.getBigDecimal("maxScore")
                            : BigDecimal.valueOf(obj.getDouble("weight")));
                    criterion.setDescription(obj.getString("description"));
                    list.add(criterion);
                }
            }
        } catch (Exception e) {
            log.error("自动生成评分标准异常: ", e);
            // 返回默认评分标准兜底
            return getDefaultRubric();
        }
        return list.isEmpty() ? getDefaultRubric() : list;
    }

    /**
     * 默认评分标准兜底（4维度 × 各25分）
     */
    private List<EvaluationCriterion> getDefaultRubric() {
        List<EvaluationCriterion> list = new ArrayList<>();
        EvaluationCriterion c1 = new EvaluationCriterion();
        c1.setName("完整性"); c1.setWeight(35); c1.setMaxScore(new BigDecimal("35"));
        c1.setDescription("成果的整体完成度，是否包含了实训任务的核心交付物");
        list.add(c1);
        EvaluationCriterion c2 = new EvaluationCriterion();
        c2.setName("规范性"); c2.setWeight(30); c2.setMaxScore(new BigDecimal("30"));
        c2.setDescription("文件组织是否有序，命名是否规范，格式是否统一");
        list.add(c2);
        EvaluationCriterion c3 = new EvaluationCriterion();
        c3.setName("核心内容质量"); c3.setWeight(35); c3.setMaxScore(new BigDecimal("35"));
        c3.setDescription("可识别的核心内容是否有价值，是否体现了一定的专业能力");
        list.add(c3);
        return list;
    }

    /**
     * 将评分标准列表转换为AI Prompt中的描述文本
     */
    private String buildRubricDescription(String rubricJson) {
        try {
            JSONArray criteria = JSON.parseArray(rubricJson);
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < criteria.size(); i++) {
                JSONObject c = criteria.getJSONObject(i);
                sb.append(String.format("%d. %s（%s分）：%s\n",
                        i + 1,
                        c.getString("name"),
                        c.get("maxScore") != null ? c.get("maxScore").toString() : c.getString("weight"),
                        c.getString("description")));
            }
            return sb.toString();
        } catch (Exception e) {
            // 如果不是JSON数组格式，尝试作为纯文本直接使用
            return rubricJson;
        }
    }

    /**
     * 解析全格式评价报告JSON为结构化对象
     */
    public FullReportResult parseFullReport(String reportJson) {
        try {
            JSONObject json = JSON.parseObject(reportJson);
            FullReportResult result = new FullReportResult();

            // 概览
            if (json.containsKey("overview")) {
                JSONObject ov = json.getJSONObject("overview");
                result.setFileCount(ov.getInteger("fileCount"));
                result.setFileTypes(ov.getString("fileTypes"));
                result.setResultType(ov.getString("resultType"));
                result.setCompleteness(ov.getString("completeness"));
            }

            // 评分详情
            if (json.containsKey("scoringDetails")) {
                JSONArray details = json.getJSONArray("scoringDetails");
                List<FullReportResult.ScoreDetail> scoreDetails = new ArrayList<>();
                for (int i = 0; i < details.size(); i++) {
                    JSONObject d = details.getJSONObject(i);
                    FullReportResult.ScoreDetail detail = new FullReportResult.ScoreDetail();
                    detail.setDimension(d.getString("dimension"));
                    detail.setMaxScore(d.getBigDecimal("maxScore"));
                    detail.setScore(d.getBigDecimal("score"));
                    detail.setComment(d.getString("comment"));
                    scoreDetails.add(detail);
                }
                result.setScoringDetails(scoreDetails);
            }

            // 总分
            result.setTotalScore(json.getBigDecimal("totalScore"));

            // 综合评价
            if (json.containsKey("overallEvaluation")) {
                JSONObject ev = json.getJSONObject("overallEvaluation");
                result.setStrengths(ev.getString("strengths"));
                result.setWeaknesses(ev.getString("weaknesses"));
            }

            // 建议
            if (json.containsKey("suggestions")) {
                result.setSuggestions(json.getJSONArray("suggestions").toList(String.class));
            }

            // 核查要点
            if (json.containsKey("checkPoints")) {
                result.setCheckPoints(json.getJSONArray("checkPoints").toList(String.class));
            }

            // 各摘要
            if (json.containsKey("documentSummary")) {
                result.setDocumentSummary(json.getJSONObject("documentSummary").toString());
            }
            if (json.containsKey("imageSummary")) {
                result.setImageSummary(json.getJSONObject("imageSummary").toString());
            }
            if (json.containsKey("codeSummary")) {
                result.setCodeSummary(json.getJSONObject("codeSummary").toString());
            }

            return result;
        } catch (Exception e) {
            log.error("解析全格式评价报告失败: ", e);
            return null;
        }
    }

    private String createFallbackFullReport() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("overview", Map.of(
                "fileCount", 1, "fileTypes", "文档", "resultType", "未知",
                "completeness", "⚠️ 基本完整"
        ));
        fallback.put("documentSummary", Map.of(
                "chapters", List.of(), "abstract", "AI评价暂时不可用，请稍后重试",
                "completeness", "未知", "wordCount", 0
        ));
        fallback.put("imageSummary", Map.of("count", 0, "operations", List.of(), "coverage", "无截图"));
        fallback.put("codeSummary", Map.of(
                "language", "未知", "modules", List.of(), "totalLines", 0,
                "fileCount", 0, "structureAssessment", "未知"
        ));
        fallback.put("scoringDetails", List.of(
                Map.of("dimension", "完整性", "maxScore", 35, "score", 21, "comment", "AI评价服务暂时不可用，此为兜底默认分值"),
                Map.of("dimension", "规范性", "maxScore", 30, "score", 18, "comment", "AI评价服务暂时不可用，此为兜底默认分值"),
                Map.of("dimension", "核心内容质量", "maxScore", 35, "score", 21, "comment", "AI评价服务暂时不可用，此为兜底默认分值")
        ));
        fallback.put("totalScore", 60);
        fallback.put("overallEvaluation", Map.of(
                "strengths", "AI评价服务暂时不可用",
                "weaknesses", "请稍后重新触发评价"
        ));
        fallback.put("suggestions", List.of("请稍后重新触发AI评价", "或联系教师进行人工评价"));
        fallback.put("checkPoints", List.of("AI评价未完成，建议教师人工核查"));
        return JSON.toJSONString(fallback);
    }

    private String extractJsonArray(String content) {
        int start = content.indexOf("[");
        int end = content.lastIndexOf("]");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    public String extractJson(String content) {
        int start = content.indexOf("{");
        int end = content.lastIndexOf("}");
        if (start != -1 && end != -1 && end > start) {
            return content.substring(start, end + 1);
        }
        return content;
    }

    private String createFallbackDynamicJson(List<EvaluationCriterion> criteria) {
        Map<String, Object> fallback = new HashMap<>();
        Map<String, Object> scoresMap = new HashMap<>();
        BigDecimal total = BigDecimal.ZERO;
        
        for (EvaluationCriterion c : criteria) {
            BigDecimal score = c.getMaxScore().multiply(BigDecimal.valueOf(0.6)).setScale(2, java.math.RoundingMode.HALF_UP);
            total = total.add(score);
            scoresMap.put(c.getName(), Map.of("score", score, "comment", "报告内容基本涵盖，格式尚可。"));
        }
        fallback.put("scores", scoresMap);
        fallback.put("totalScore", total);
        fallback.put("suggestions", Arrays.asList("请联系老师重新触发AI评价", "进一步参照细则丰富实训过程描述"));
        return JSON.toJSONString(fallback);
    }
    
    private EvaluationResult parseEvaluationResult(String content) {
        try {
            String jsonStr = extractJson(content);
            JSONObject json = JSON.parseObject(jsonStr);
            
            EvaluationResult result = new EvaluationResult();
            
            JSONObject completeness = json.getJSONObject("completeness");
            result.setCompletenessScore(completeness.getBigDecimal("score"));
            result.setCompletenessComment(completeness.getString("comment"));
            
            JSONObject specification = json.getJSONObject("specification");
            result.setSpecificationScore(specification.getBigDecimal("score"));
            // 🛠 修复点：移除了原本导致编译报错的非函数接口类型 Lambda 代码行
            result.setSpecificationComment(specification.getString("comment"));
            
            JSONObject knowledge = json.getJSONObject("knowledge");
            result.setKnowledgeScore(knowledge.getBigDecimal("score"));
            result.setKnowledgeComment(knowledge.getString("comment"));
            
            result.setTotalScore(json.getBigDecimal("totalScore"));
            
            List<String> suggestions = json.getJSONArray("suggestions").toList(String.class);
            result.setSuggestions(suggestions);
            
            return result;
        } catch (Exception e) {
            log.error("解析AI响应失败: ", e);
            return getDefaultEvaluationResult();
        }
    }
    
    private EvaluationResult getDefaultEvaluationResult() {
        EvaluationResult result = new EvaluationResult();
        result.setCompletenessScore(java.math.BigDecimal.valueOf(20));
        result.setCompletenessComment("报告结构基本完整");
        result.setSpecificationScore(java.math.BigDecimal.valueOf(20));
        result.setSpecificationComment("格式规范");
        result.setKnowledgeScore(java.math.BigDecimal.valueOf(25));
        result.setKnowledgeComment("知识点覆盖一般");
        result.setTotalScore(java.math.BigDecimal.valueOf(65));
        result.setSuggestions(Arrays.asList("建议完善报告内容", "加强知识点描述"));
        return result;
    }
    
    @Data
    public static class EvaluationResult {
        private java.math.BigDecimal completenessScore;
        private String completenessComment;
        private java.math.BigDecimal specificationScore;
        private String specificationComment;
        private java.math.BigDecimal knowledgeScore;
        private String knowledgeComment;
        private java.math.BigDecimal totalScore;
        private List<String> suggestions;
        
        public String toJson() {
            Map<String, Object> map = new HashMap<>();
            map.put("completeness", Map.of(
                "score", completenessScore,
                "comment", completenessComment
            ));
            map.put("specification", Map.of(
                "score", specificationScore,
                "comment", specificationComment
            ));
            map.put("knowledge", Map.of(
                "score", knowledgeScore,
                "comment", knowledgeComment
            ));
            map.put("totalScore", totalScore);
            map.put("suggestions", suggestions);
            return JSON.toJSONString(map);
        }
    }

    /**
     * 【全新】对话式反馈 — 学生可以就评分结果与AI进行多轮对话
     *
     * @param conversationHistory 对话历史（List<Map<role, content>>），role为"user"或"assistant"
     * @param assignmentTitle     作业标题
     * @param totalScore          总分
     * @param deductionPoints     扣分点列表（每个元素包含序号、维度名称、扣分描述）
     * @param evaluationReport    完整评价报告JSON
     * @param assignmentContent   学生作业原文
     * @return AI回复文本
     */
    public String chatFeedback(
            List<Map<String, String>> conversationHistory,
            String assignmentTitle,
            String totalScore,
            String deductionPoints,
            String evaluationReport,
            String assignmentContent) {
        try {
            // 构建 System Prompt
            String systemPrompt = String.format(CHAT_FEEDBACK_SYSTEM_PROMPT,
                    assignmentTitle != null ? assignmentTitle : "未知",
                    totalScore != null ? totalScore : "未知",
                    deductionPoints != null ? deductionPoints : "暂无扣分点详情",
                    evaluationReport != null ? evaluationReport : "暂无评价报告",
                    assignmentContent != null ? (assignmentContent.length() > 3000
                            ? assignmentContent.substring(0, 3000) + "\n... (内容过长已截断)"
                            : assignmentContent) : "暂无作业内容");

            // 构建消息列表
            List<Map<String, Object>> messages = new ArrayList<>();

            // System 消息
            Map<String, Object> systemMsg = new HashMap<>();
            systemMsg.put("role", "system");
            systemMsg.put("content", systemPrompt);
            messages.add(systemMsg);

            // 对话历史
            if (conversationHistory != null) {
                for (Map<String, String> msg : conversationHistory) {
                    Map<String, Object> histMsg = new HashMap<>();
                    histMsg.put("role", msg.get("role"));
                    histMsg.put("content", msg.get("content"));
                    messages.add(histMsg);
                }
            }

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.7); // 对话模式使用较高温度，更有创造性

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                JSONObject choices = result.getJSONArray("choices").getJSONObject(0);
                JSONObject messageObj = choices.getJSONObject("message");
                return messageObj.getString("content");
            } else {
                log.error("AI对话反馈API调用失败: {}", response.body());
                return "抱歉，AI服务暂时不可用，请稍后重试。你也可以联系老师获取反馈。";
            }
        } catch (Exception e) {
            log.error("AI对话反馈异常: ", e);
            return "抱歉，AI对话服务出现了异常：" + e.getMessage() + "。请稍后重试或联系老师。";
        }
    }

    /**
     * 【全新】AI个性化改进计划 — 根据学生历史评价数据，生成薄弱项分析和改进计划
     *
     * @param studentHistoryData 学生的历史评价汇总数据（JSON格式，包含各次作业得分、扣分点、AI评价摘要）
     * @return AI生成的改进计划（JSON字符串）
     */
    public String generateImprovementPlan(String studentHistoryData) {
        try {
            String fullPrompt = String.format(IMPROVEMENT_PLAN_PROMPT, studentHistoryData);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);

            List<Map<String, Object>> messages = new ArrayList<>();
            Map<String, Object> message = new HashMap<>();
            message.put("role", "user");
            message.put("content", fullPrompt);
            messages.add(message);
            requestBody.put("messages", messages);
            requestBody.put("temperature", 0.5);

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject result = JSON.parseObject(response.body());
                JSONObject choices = result.getJSONArray("choices").getJSONObject(0);
                JSONObject messageObj = choices.getJSONObject("message");
                return messageObj.getString("content");
            } else {
                log.error("AI改进计划生成失败: {}", response.body());
                return null;
            }
        } catch (Exception e) {
            log.error("AI改进计划生成异常: ", e);
            return null;
        }
    }

    /**
     * 【全新】AI智能批注生成 — 逐段分析报告文本，返回批注列表
     * AI输出批注JSON数组，后端匹配原文确定文本位置
     *
     * @param reportContent 报告完整文本内容
     * @return 批注DTO列表（含精确字符位置）
     */
    public List<com.training.dto.AnnotationDTO> generateAnnotations(String reportContent) {
        List<com.training.dto.AnnotationDTO> result = new ArrayList<>();
        try {
            // 截断过长的内容（保留前8000字，确保AI能充分理解）
            String contentForAi = reportContent.length() > 8000
                    ? reportContent.substring(0, 8000) + "\n... (内容过长已截断)"
                    : reportContent;

            String fullPrompt = String.format(ANNOTATION_PROMPT, contentForAi);

            Map<String, Object> requestBody = new HashMap<>();
            requestBody.put("model", model);
            requestBody.put("messages", List.of(Map.of("role", "user", "content", fullPrompt)));
            requestBody.put("temperature", 0.3); // 低温度保证批注质量稳定

            HttpResponse response = HttpRequest.post(apiUrl)
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .body(JSON.toJSONString(requestBody))
                    .timeout(timeout * 1000)
                    .execute();

            if (response.isOk()) {
                JSONObject respJson = JSON.parseObject(response.body());
                String aiReply = respJson.getJSONArray("choices").getJSONObject(0)
                        .getJSONObject("message").getString("content");
                result = parseAnnotationResult(aiReply);
                // 在原文中匹配每个批注的精确位置
                matchPositions(result, reportContent);
            } else {
                log.error("AI批注生成API调用失败: {}", response.body());
            }
        } catch (Exception e) {
            log.error("AI批注生成异常: ", e);
        }
        return result;
    }

    /**
     * 解析AI返回的批注JSON数组
     */
    public List<com.training.dto.AnnotationDTO> parseAnnotationResult(String aiResponse) {
        List<com.training.dto.AnnotationDTO> annotations = new ArrayList<>();
        try {
            String jsonStr = extractJsonArray(aiResponse);
            JSONArray arr = JSON.parseArray(jsonStr);
            if (arr != null) {
                for (int i = 0; i < arr.size(); i++) {
                    JSONObject obj = arr.getJSONObject(i);
                    com.training.dto.AnnotationDTO dto = new com.training.dto.AnnotationDTO();
                    dto.setHighlightedText(obj.getString("highlightedText"));
                    dto.setCategory(obj.getString("category"));
                    dto.setSeverity(obj.getString("severity") != null ? obj.getString("severity") : "info");
                    dto.setComment(obj.getString("comment"));
                    dto.setSuggestion(obj.getString("suggestion"));
                    annotations.add(dto);
                }
            }
        } catch (Exception e) {
            log.error("解析AI批注结果失败: ", e);
        }
        return annotations;
    }

    /**
     * 在原文中匹配每个批注的精确字符位置
     * 使用 indexOf 定位 highlightedText，填充 startPos 和 endPos
     * 对于找不到匹配的批注，startPos/endPos 保持为 null
     */
    public void matchPositions(List<com.training.dto.AnnotationDTO> annotations, String fullText) {
        if (fullText == null || fullText.isEmpty()) return;

        // 记录已匹配的位置，避免后续重复匹配同一位置
        int lastMatchEnd = 0;

        for (com.training.dto.AnnotationDTO ann : annotations) {
            String snippet = ann.getHighlightedText();
            if (snippet == null || snippet.trim().isEmpty()) continue;

            // 从上次匹配位置之后开始搜索，确保批注按顺序排列
            int pos = fullText.indexOf(snippet, lastMatchEnd);
            // 如果后面找不到，尝试全文搜索
            if (pos == -1) {
                pos = fullText.indexOf(snippet);
            }
            // 如果精确匹配失败，尝试trim后匹配
            if (pos == -1) {
                String trimmed = snippet.trim();
                pos = fullText.indexOf(trimmed, lastMatchEnd);
                if (pos == -1) pos = fullText.indexOf(trimmed);
            }
            // 如果仍然失败，尝试匹配前30个字符
            if (pos == -1 && snippet.length() > 30) {
                String prefix = snippet.substring(0, 30);
                pos = fullText.indexOf(prefix, lastMatchEnd);
                if (pos == -1) pos = fullText.indexOf(prefix);
                if (pos != -1) {
                    // 扩大匹配范围到原文中对应的完整句子
                    int endPos = pos + snippet.length();
                    // 限制不超过原文长度
                    if (endPos > fullText.length()) endPos = fullText.length();
                    ann.setStartPos(pos);
                    ann.setEndPos(endPos);
                    lastMatchEnd = endPos;
                    continue;
                }
            }

            if (pos != -1) {
                ann.setStartPos(pos);
                ann.setEndPos(pos + snippet.length());
                lastMatchEnd = pos + snippet.length();
            } else {
                // 无法定位：startPos/endPos 保持 null，前端将显示为浮动批注
                log.warn("批注文本无法在原文中定位: {}", snippet.length() > 50 ? snippet.substring(0, 50) + "..." : snippet);
            }
        }
    }

    /** 全格式评价报告结构化结果 */
    @Data
    public static class FullReportResult {
        // 概览
        private Integer fileCount;
        private String fileTypes;
        private String resultType;
        private String completeness;
        // 各摘要JSON
        private String documentSummary;
        private String imageSummary;
        private String codeSummary;
        // 评分
        private List<ScoreDetail> scoringDetails;
        private BigDecimal totalScore;
        // 评价
        private String strengths;
        private String weaknesses;
        private List<String> suggestions;
        private List<String> checkPoints;

        @Data
        public static class ScoreDetail {
            private String dimension;
            private BigDecimal maxScore;
            private BigDecimal score;
            private String comment;
        }
    }
}