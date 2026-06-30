package com.training.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("report_annotation")
public class ReportAnnotation {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long reportId;
    private Integer startPos;
    private Integer endPos;
    private String highlightedText;
    private String comment;
    private String severity;
    private String category;
    private String suggestion;
    private LocalDateTime createTime;
}
