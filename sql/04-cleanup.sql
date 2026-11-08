-- =====================================================================
-- AI 求职助手 · 04-cleanup.sql
-- 用途：数据保留期清理 + 注销物理/脱敏清理 + 演示数据重置
-- 目标环境：MySQL 5.7.16
-- 重要：本文件不使用 MySQL EVENT（项目禁止事件调度器）。
--       下面每条语句由应用层定时任务调用，调用方与触发条件见各段标题；
--       应用层执行完后必须写一条 system_log（action=file_clean / log_clean）留痕。
-- 参数来源（均可热更新，禁止写死在代码里）：
--       文件保留   → system_config['file.keep-days']      默认 90
--       AI 日志    → system_config['log.ai.keep-days']    默认 180
--       报告、任务、面试正文及系统日志固定 180 天，不读取 AI 日志保留期配置
-- =====================================================================

USE `ai_jobs`;

SET NAMES utf8mb4;

-- =====================================================================
-- 一、每日 03:00 · M1 FileCleanJob 调 M2 ResumeFileCleanService：上传文件到期清理（FR-2、FR-38）
--     条件：file_path 非空 且 created_at 早于 (NOW() - file.keep-days 天)
--     顺序：① SELECT 待清理路径 → ② 删磁盘文件 → ③ 仅对删盘成功的行置 NULL
--           删盘失败的行保持原值，下一轮重试（保证"库里没路径、盘上还有文件"不会发生）
--     注意：只清 file_path，raw_text 与 content_json 永久保留
-- =====================================================================
-- ① 待清理清单（应用层按此逐条删盘）
SELECT `id`, `user_id`, `file_path`
  FROM `user_resume`
 WHERE `file_path` IS NOT NULL
   AND `created_at` < DATE_SUB(NOW(), INTERVAL 90 DAY)      -- 90 = file.keep-days
 ORDER BY `id`
 LIMIT 200;

-- ② 删盘成功后的等价 SQL：只把"删盘已成功"的 id 批量置空（IN 列表由应用层按 ① 的返回结果拼接）
--    下面写 0 占位（空集合、不影响任何行），保证本文件可整体执行；
--    应用层把它替换为 ① 中删盘成功的 id 列表，例如 IN (12,13,27)
--    不直接按时间条件 UPDATE 的原因：删盘失败的行必须保留 file_path，下一轮重试
UPDATE `user_resume`
   SET `file_path` = NULL
 WHERE `id` IN (0)
   AND `file_path` IS NOT NULL;

-- ③ 审计留痕（条数写 remark，不写正文）
-- INSERT INTO system_log (user_id, username, action, success, ip, remark, created_at)
-- VALUES (NULL, NULL, 'file_clean', :success, 'job-file-clean', 'scanned=..,cleaned=..,failed=..,keepDays=..', NOW());

-- =====================================================================
-- 二、每日 03:00 · M1 LogCleanJob 调 M3 ReportCleanService：诊断报告正文过期置空（FR-7、FR-38）
--     条件：report_json 非空 且 created_at 早于 NOW() - 180 天
--     保留：position_snapshot、score、status、fail_reason、cost_seconds、tokens、created_at
--     清空：report_json（内含 sourceSnapshot，一并失效），过期后详情/采纳/合成都返回 code=404
-- =====================================================================
UPDATE `resume_diagnose_record`
   SET `report_json` = NULL
 WHERE `report_json` IS NOT NULL
   AND `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY);

-- =====================================================================
-- 三、每日 03:00 · M1 LogCleanJob 调 M7 AiCallLogService：AI 调用日志过期删除（FR-38）
--     分批删除，避免一次大事务长时间持锁；每批 5000 行，循环直到影响行数为 0
-- =====================================================================
DELETE FROM `ai_call_log`
 WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY)
 ORDER BY `id`
 LIMIT 5000;

-- =====================================================================
-- 四、每日 03:00 · M1 LogCleanJob 调 M1 SystemLogService：系统日志过期删除（FR-4、FR-38）
-- =====================================================================
DELETE FROM `system_log`
 WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY)
 ORDER BY `id`
 LIMIT 5000;

-- ⑤ 清理审计留痕（一~五 执行完后写一条，条数入 remark）
-- INSERT INTO system_log (user_id, username, action, success, ip, remark, created_at)
-- VALUES (NULL, NULL, 'log_clean', :success, 'job-log-clean',
--         'report=..,interview=..,task=..,ai=..,system=..,failed=..', NOW());

-- =====================================================================
-- 五、每日 03:00 · M1 LogCleanJob 调 M7 AiTaskContentCleanService / M5 InterviewContentCleanService
--     条件：created_at 早于 NOW() - 180 天；各模块仅执行本模块 SQL
--     只清正文、不删行：保留 status/score/progress_index/created_at 等统计元数据
--     API 派生：ai_task.resultExpired=true；面试详情 contentExpired=true
--     M7 只清成功或确认退款的失败任务；M5 在用户协调锁内 current read 确认请求全部终态
--     M5 存在未结算点评时保留整份台账、推进扫描游标；结算后下轮清理
-- =====================================================================
UPDATE `ai_task`
   SET `input_snapshot` = NULL,
       `result_json` = NULL
 WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY)
   AND (`status` = 'SUCCESS' OR (`status` = 'FAIL' AND `quota_state` = 'refunded' AND `refunded` = 1))
   AND (`input_snapshot` IS NOT NULL OR `result_json` IS NOT NULL);

-- M5 在锁内复核请求全部终态后传入安全 ID；0 占位不影响任何行
UPDATE `mock_interview_record`
   SET `questions_json` = NULL,
       `answers_json` = NULL,
       `feedback_json` = NULL,
       `request_log_json` = NULL,
       `review_text` = NULL
 WHERE `id` IN (0)
   AND `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY)
   AND (`questions_json` IS NOT NULL OR `answers_json` IS NOT NULL
        OR `feedback_json` IS NOT NULL
        OR `request_log_json` IS NOT NULL OR `review_text` IS NOT NULL);

-- =====================================================================
-- 六、注销清理（FR-7 一键删除全部数据 + L596 注销与删除并发约束）
--     触发：用户提交注销（应用层先校验二次确认输入的用户名）
--     执行方式：M1 先取得用户协调锁，再以 TransactionTemplate 开启同一 MySQL 事务
--     下列仅为各归属模块 Handler 的等价示例，禁止由 M1 持跨模块 Mapper 整段执行
--     示例对象：@uid = 3（chenwei）
--     文件仅登记 afterCommit；事务回滚不得删除磁盘文件
-- =====================================================================
-- SET @uid = 3;
-- -- 应用层：UserOperationCoordinator.runWithLock(@uid, ...)，锁内执行下述事务
-- START TRANSACTION;
--
-- -- M1：锁账号行再置注销；所有新写路径先校验/锁 user，提交后 token 立即失效
-- SELECT `id` FROM `user` WHERE `id` = @uid FOR UPDATE;
-- UPDATE `user` SET `deleted` = 1, `deleted_at` = NOW() WHERE `id` = @uid AND `deleted` = 0;
--
-- -- @Order(100) M7 AiTaskPurgeHandler：锁全部本人任务，含 FAIL 待退与未绑定预留
-- SELECT * FROM `ai_task` WHERE `user_id` = @uid ORDER BY `id` FOR UPDATE;
-- -- SUCCESS 不退；已确认 refunded=1/quota_state=refunded 不重复退
-- -- 其余经 M1 findCharge 校验原凭证并恢复日期/代次；CHARGED 调
-- -- QuotaService.refund(String.valueOf(taskId), @uid, quotaDate, quotaGeneration, scene)
-- -- REFUNDED/ALREADY_REFUNDED/STALE 幂等接受；未绑定预留仅在凭证窗口内且确认无扣费时删除
-- -- 已知 charged 却缺凭证、超过核验窗口、字段冲突或 Redis 异常：抛错并整笔回滚
-- -- 结算全部确认后才允许删除任务；普通失败流程允许先提交 FAIL 再补退，不能提前删载体
-- DELETE FROM `ai_task` WHERE `user_id` = @uid;
--
-- -- @Order(200) M3 DiagnosisPurgeHandler：脱敏保留元数据，清空正文与全部指向
-- UPDATE `resume_diagnose_record`
--    SET `report_json` = NULL, `user_id` = NULL, `username_snapshot` = '已注销',
--        `resume_id` = NULL, `job_target_id` = NULL, `delivery_id` = NULL, `task_id` = NULL
--  WHERE `user_id` = @uid;
--
-- -- @Order(300) M5 InterviewPurgeHandler：先锁本人的面试记录并核验未完成点评请求
-- -- completedAt 已提交不退；未完成按 requestId 原凭证结算，未知或失败则整笔回滚
-- SELECT * FROM `mock_interview_record` WHERE `user_id` = @uid ORDER BY `id` FOR UPDATE;
-- DELETE FROM `mock_interview_record` WHERE `user_id` = @uid;
--
-- -- @Order(400) M2 ResumePurgeHandler：收集受控相对路径，登记 afterCommit 后删行
-- SELECT `file_path` FROM `user_resume` WHERE `user_id` = @uid AND `file_path` IS NOT NULL;
-- DELETE FROM `user_resume` WHERE `user_id` = @uid;
-- -- @Order(500) M2 AbilityPurgeHandler
-- DELETE FROM `ability_entry` WHERE `user_id` = @uid;
-- -- @Order(600) M4 DeliveryPurgeHandler
-- DELETE FROM `delivery_record`       WHERE `user_id` = @uid;
-- DELETE FROM `job_target`            WHERE `user_id` = @uid;
-- -- @Order(700) M7 AiCallLogPurgeHandler：解绑用户，保留成功/settled 事实供同步 JD 核验
-- UPDATE `ai_call_log` SET `user_id` = NULL WHERE `user_id` = @uid;
--
-- -- system_config 不动；原 system_log 与 user 行保留
-- COMMIT;
--
-- -- 提交后 M2 执行登记的文件删除，失败仅告警，由 M2 孤儿扫描重试
-- -- M1 按真实提交结果用 REQUIRES_NEW 记录 delete_all；以下为成功例
-- INSERT INTO `system_log` (`user_id`,`username`,`action`,`success`,`ip`,`remark`,`created_at`)
-- VALUES (@uid, 'chenwei', 'delete_all', 1, '127.0.0.1', CONCAT('账号注销，物理删除简历/条目/投递/面试/任务，脱敏保留诊断记录'), NOW());
--
-- 任一 Handler 失败：MySQL 全回滚、无 afterCommit 删除，M1 独立写 success=0 审计并返回 500
-- Redis 已退款不补扣，重试使用同凭证幂等收敛；协调锁直到提交/回滚及收尾后释放
-- FileCleanJob 只调用 M2 ResumeFileCleanService；M2 扫描受控目录并重试超过 24 小时无引用文件
-- 同步 JD 由 M4 经 M1/M7 查原凭证及持久 settled 事实后恢复，不能把日志解绑当作退款依据

-- =====================================================================
-- 七、演示数据重置（仅开发/演示环境使用，生产禁止执行）
--     说明：不走 TRUNCATE（会重置自增并绕过 WHERE 校验），按 id 区间精确删除，
--           保证 admin/demo/chenwei 三个账号与 13 个配置键不受影响
-- =====================================================================
-- DELETE FROM `ability_entry`          WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `user_resume`            WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `job_target`             WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `delivery_record`        WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `resume_diagnose_record` WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `mock_interview_record`  WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `ai_task`                WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `ai_call_log`            WHERE `id` BETWEEN 1 AND 100;
-- DELETE FROM `system_log`             WHERE `id` BETWEEN 1 AND 100;
-- -- 重置后重新执行 03-init-data.sql 即可恢复演示数据

-- =====================================================================
-- 八、清理结果校验（期望：0 / 0 / 过期行数归零）
-- =====================================================================
SELECT 'F1 仍有未到期文件路径' AS assertion, COUNT(*) AS actual, 0 AS expected
  FROM `user_resume` WHERE `file_path` IS NOT NULL AND `created_at` < DATE_SUB(NOW(), INTERVAL 90 DAY);

SELECT 'F2 仍有未到期报告正文' AS assertion, COUNT(*) AS actual, 0 AS expected
  FROM `resume_diagnose_record` WHERE `report_json` IS NOT NULL AND `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY);

SELECT 'F3 仍有未删除的过期 AI 日志' AS assertion, COUNT(*) AS actual, 0 AS expected
  FROM `ai_call_log` WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY);

SELECT 'F4 仍有未删除的过期系统日志' AS assertion, COUNT(*) AS actual, 0 AS expected
  FROM `system_log` WHERE `created_at` < DATE_SUB(NOW(), INTERVAL 180 DAY);
