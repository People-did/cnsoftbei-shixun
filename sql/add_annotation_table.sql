-- AI智能批注功能 - 数据库迁移
-- 在 training_system 数据库中执行

USE `training_system`;

DROP TABLE IF EXISTS `report_annotation`;
CREATE TABLE `report_annotation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `report_id` bigint NOT NULL COMMENT '关联的报告ID',
  `start_pos` int DEFAULT 0 COMMENT '批注起始字符位置',
  `end_pos` int DEFAULT 0 COMMENT '批注结束字符位置',
  `highlighted_text` text COMMENT '被标注的原文片段',
  `comment` text COMMENT 'AI批注评语（问题描述）',
  `severity` varchar(20) DEFAULT 'info' COMMENT '严重程度: error/warning/info/suggestion',
  `category` varchar(50) DEFAULT NULL COMMENT '问题分类: 规范性/完整性/知识点/代码质量/文档结构/逻辑问题/表述问题',
  `suggestion` text COMMENT '修改建议',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_report_id` (`report_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI智能批注记录表';
