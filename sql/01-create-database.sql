-- =====================================================================
-- AI 求职助手 · 01-create-database.sql（数据库设计 V1.1 基线）
-- 用途：创建项目主库 ai_jobs
-- 目标环境：MySQL 5.7.16 / InnoDB / utf8mb4
-- 执行方式：mysql -uroot -p < 01-create-database.sql
--           或在 SQLyog / Navicat 中整体执行
-- 可重复执行：是（IF NOT EXISTS）
-- 说明：与《功能点执行手册》#1 的语句一致，仅补 IF NOT EXISTS 以保证重复执行不报错
-- 部署前置：max_allowed_packet >= 64MB；否则 MEDIUMTEXT 大字段写入可能报 Packet too large
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `ai_jobs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_general_ci;

USE `ai_jobs`;

-- 验收 A1：库已创建且字符集为 utf8mb4（期望 1 行：ai_jobs / utf8mb4 / utf8mb4_general_ci）
SELECT SCHEMA_NAME     AS db_name,
       DEFAULT_CHARACTER_SET_NAME AS charset,
       DEFAULT_COLLATION_NAME     AS collation
  FROM information_schema.SCHEMATA
 WHERE SCHEMA_NAME = 'ai_jobs';

-- 验收 A2：当前连接的库字符集（期望 utf8mb4）
SELECT @@character_set_database AS current_db_charset,
       @@version                AS mysql_version,
       @@innodb_file_format     AS innodb_file_format,
       @@max_allowed_packet     AS max_allowed_packet;
