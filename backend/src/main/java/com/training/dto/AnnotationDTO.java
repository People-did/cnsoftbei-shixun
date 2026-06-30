package com.training.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class AnnotationDTO {
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
