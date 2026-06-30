USE `training_system`;

-- 为 training_report 表添加 requirement_id 字段，关联报告要求（作业）
ALTER TABLE training_report ADD COLUMN requirement_id BIGINT DEFAULT NULL COMMENT '关联的报告要求ID（作业）' AFTER course_id;

-- 添加索引
ALTER TABLE training_report ADD INDEX idx_requirement_id (requirement_id);

-- 为 student_id + requirement_id 添加唯一索引，确保每个学生对每个作业只有一份提交
ALTER TABLE training_report ADD UNIQUE INDEX uk_student_requirement (student_id, requirement_id);