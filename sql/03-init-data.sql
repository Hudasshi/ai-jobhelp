-- =====================================================================
-- AI 求职助手 · 03-init-data.sql
-- 用途：初始化 3 个账号、13 个配置键、演示业务数据，并附四条断言
-- 执行前置：02-create-tables.sql 已执行
-- 可重复执行：是
--   · system_config 用 INSERT IGNORE —— 13 个键只在首次初始化插入；配置修改走管理端接口，重跑不覆盖；
--   · 业务演示数据用 INSERT IGNORE —— 重跑只补缺、不覆盖，避免吞掉演示过程中产生的真实操作痕迹；
--   · 末尾 5 条断言（A1~A5）用于拦截"静默漏插"（INSERT IGNORE 会整行跳过非法/重复行）。
-- 重置演示数据：见 04-cleanup.sql 末尾的"演示数据重置"段（按 id 区间 DELETE，非 TRUNCATE）。
-- 密码：admin/admin123、demo/123456、chenwei/123456（BCrypt cost=10，$2b$ 前缀，Spring Security 可直接校验）
-- =====================================================================

USE `ai_jobs`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1) user —— 3 个账号（id 固定 1/2/3，与契约 §5 一致）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `user`
  (`id`,`username`,`password`,`nickname`,`email`,`role`,`status`,`quota_override`,`quota_generation`,`ai_call_limit_override`,`last_login_at`,`deleted`,`deleted_at`,`created_at`,`updated_at`)
VALUES
  (1,'admin','$2b$10$EkyTtP5/Au05hoZQNFtma.dA89fenAOGy8yKWFbpRnWdO.xakaq4S','系统管理员','admin@example.com','admin',1,NULL,0,NULL,'2026-11-10 09:00:00',0,NULL,'2026-10-01 10:00:00','2026-11-10 09:00:00'),
  (2,'demo','$2b$10$H5mXN4rDSkUBdxQZejoF8uABP1T3uBdKunVPEi3u5f8id6jS0iGMa','演示用户','demo@example.com','user',1,50,0,NULL,'2026-11-10 20:15:00',0,NULL,'2026-10-05 14:30:00','2026-11-10 20:15:00'),
  (3,'chenwei','$2b$10$1WrmwIomYOD2tqsGppxcHOzPBiLD4MlbGo3lgLoVdHUnE9sVmeEji','陈伟','chenwei@example.com','user',1,NULL,0,NULL,'2026-11-09 19:40:00',0,NULL,'2026-10-12 09:05:00','2026-11-09 19:40:00');

-- ---------------------------------------------------------------------
-- 2) system_config —— 13 个键（值 = 契约 §6 默认值；顺序 = FR-32 表格顺序）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_config` (`id`,`config_key`,`config_value`,`remark`) VALUES
  (1 ,'quota.daily.default','50'         ,'每用户每日 AI 配额默认值（次）；用户 quota_override 非空时以其为准'),
  (2 ,'ai.model','deepseek-chat'         ,'AI 模型标识，写入 ai_call_log.model'),
  (3 ,'ai.timeout-ms','60000'            ,'单次 AI 调用超时（毫秒）'),
  (4 ,'ai.max-input-tokens','6000'       ,'单次请求输入 token 上限，超出则裁剪上下文'),
  (5 ,'ai.retry.max','3'                 ,'初次调用后的额外重试次数；每次外部尝试各记 1 条 ai_call_log，业务 operation 只扣 1 次配额'),
  (6 ,'ai.task.timeout-min','5'          ,'异步任务超时分钟数，创建时写入 ai_task.timeout_at'),
  (7 ,'ai.circuit.fail-rate','0.5'       ,'熔断失败率阈值（60 分钟窗口内 > 该值即熔断）'),
  (8 ,'ai.circuit.window-min','60'       ,'熔断统计窗口（分钟）'),
  (9 ,'ai.circuit.max-calls','200'       ,'单用户当日外部模型尝试上限；与 60 分钟失败率熔断同时生效，user.ai_call_limit_override 可覆盖'),
  (10,'ai.price.input','0.000001'        ,'输入 token 单价（元/token），用于 cost_estimate'),
  (11,'ai.price.output','0.000002'       ,'输出 token 单价（元/token），用于 cost_estimate'),
  (12,'file.keep-days','90'              ,'上传简历文件保留天数，到期置空 user_resume.file_path'),
  (13,'log.ai.keep-days','180'           ,'AI 调用日志保留天数；报告、任务与面试正文固定保留 180 天');

-- ---------------------------------------------------------------------
-- 3) ability_entry —— chenwei 1 条（id=1）+ demo 12 条（id=2~13，含 1 条待确认）
--    detail_json 键名严格对应 FR-10 六类字段契约
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `ability_entry`
  (`id`,`user_id`,`entry_type`,`title`,`detail_json`,`source`,`status`,`enabled`,`sort_no`,`source_resume_id`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1,3,'internship','腾讯科技 · 后端开发实习生',
   '{"company":"腾讯科技","role":"后端开发实习生","tech":"Java,Spring Boot,MySQL","period":"2022.07-2022.12","description":"参与游戏运营后台的接口开发，负责活动配置与数据导出模块，日均调用量 200 万。","result":"独立完成 3 个模块上线，导出任务耗时从 42s 优化到 6s。"}',
   'manual','confirmed',1,1,NULL,0,'2026-10-12 10:20:00','2026-10-12 10:20:00'),
  (2,2,'education','华中科技大学 · 计算机科学与技术 · 本科',
   '{"school":"华中科技大学","major":"计算机科学与技术","degree":"本科","period":"2019.09-2023.06","gpa":"3.6/4.0"}',
   'manual','confirmed',1,1,NULL,0,'2026-10-05 15:00:00','2026-10-05 15:00:00'),
  (3,2,'internship','字节跳动 · 后端开发实习生',
   '{"company":"字节跳动","role":"后端开发实习生","tech":"Go,MySQL,Redis,Kafka","period":"2022.09-2023.03","description":"参与电商交易链路的订单模块开发，承担状态机改造与幂等设计。","result":"重复下单率由 0.8% 降至 0.1%，补齐 27 个核心接口的幂等测试。"}',
   'ai','confirmed',1,2,1,0,'2026-10-20 11:05:00','2026-10-20 11:05:00'),
  (4,2,'internship','武汉光谷创业公司 · Java 开发实习生',
   '{"company":"武汉光谷某创业公司","role":"Java 开发实习生","tech":"Java,Spring Boot,MyBatis","period":"2021.07-2021.09","description":"负责 SaaS 后台的权限与日志模块，完成 RBAC 表结构设计与接口开发。","result":"权限模块覆盖 12 个菜单，缺陷率低于团队均值。"}',
   'ai','confirmed',1,3,1,0,'2026-10-20 11:05:00','2026-10-20 11:05:00'),
  (5,2,'project','校园二手交易平台',
   '{"name":"校园二手交易平台","role":"后端负责人","tech":"Spring Boot,MySQL,Redis","period":"2022.03-2022.06","description":"设计商品发布、检索与订单流程，实现基于 Redis 的热点商品缓存与分布式锁。","result":"日均订单 300+，接口平均响应 80ms，支撑 5000 注册用户。"}',
   'manual','confirmed',1,4,NULL,0,'2026-10-06 09:30:00','2026-10-06 09:30:00'),
  (6,2,'project','分布式任务调度系统',
   '{"name":"分布式任务调度系统","role":"核心开发","tech":"Java,Netty,ZooKeeper","period":"2021.10-2022.01","description":"实现基于 Netty 的 RPC 通信与基于 ZooKeeper 的选主和任务分片。","result":"单集群 200 个执行器，任务调度延迟 P99 小于 200ms。"}',
   'manual','confirmed',1,5,NULL,0,'2026-10-06 09:40:00','2026-10-06 09:40:00'),
  (7,2,'project','高并发秒杀系统',
   '{"name":"高并发秒杀系统","role":"后端开发","tech":"Spring Boot,Redis,RabbitMQ","period":"2023.01-2023.04","description":"完成库存预热、令牌桶限流、异步下单与订单状态补偿。","result":"压测峰值 1.2 万 QPS，超卖 0 单。"}',
   'ai','confirmed',1,6,1,0,'2026-10-20 11:05:00','2026-10-20 11:05:00'),
  (8,2,'project','AI 求职助手（毕业设计）',
   '{"name":"AI 求职助手","role":"独立开发","tech":"Spring Boot,MyBatis-Plus,Vue,DeepSeek","period":"2023.09-2024.05","description":"实现能力库、简历合成、AI 诊断、模拟面试与投递看板五大模块。","result":"5 大模块 38 个功能点，AI 调用成功率 98.6%，平均耗时 4.2s。"}',
   'manual','confirmed',1,7,NULL,0,'2026-10-25 16:20:00','2026-10-25 16:20:00'),
  (9,2,'skill','Java',
   '{"level":"熟练","years":3}',
   'manual','confirmed',1,8,NULL,0,'2026-10-05 15:10:00','2026-10-05 15:10:00'),
  (10,2,'skill','MySQL',
   '{"level":"熟悉","years":2}',
   'manual','confirmed',1,9,NULL,0,'2026-10-05 15:12:00','2026-10-05 15:12:00'),
  (11,2,'certificate','软件设计师（中级）',
   '{"level":"国家级中级","date":"2022.05"}',
   'manual','confirmed',1,10,NULL,0,'2026-10-05 15:15:00','2026-10-05 15:15:00'),
  (12,2,'certificate','大学英语六级',
   '{"level":"CET-6 512 分","date":"2021.12"}',
   'manual','confirmed',1,11,NULL,0,'2026-10-05 15:16:00','2026-10-05 15:16:00'),
  (13,2,'other','开源贡献：Apache 某组件文档修订',
   '{"description":"为 Apache 某中间件提交 3 个文档 PR 与 1 个单测修复 PR，均已合入主干。"}',
   'ai','pending',1,12,1,0,'2026-11-08 21:10:00','2026-11-08 21:10:00');

-- ---------------------------------------------------------------------
-- 4) user_resume —— demo 2 个版本（id=1 上传 / id=2 生成）+ chenwei 1 个版本（id=3 上传）
--    content_json 为条目快照（entryId 指向 ability_entry.id），content_md 仅展示导出
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `user_resume`
  (`id`,`user_id`,`name`,`source_type`,`status`,`target_position`,`content_json`,`content_md`,`file_path`,`raw_text`,`sync_status`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1,2,'通用简历 v1','upload','ready',NULL,
   '[{"entryId":2,"order":1,"title":"华中科技大学 · 计算机科学与技术 · 本科","text":"2019.09-2023.06 就读于华中科技大学计算机科学与技术专业，本科，GPA 3.6/4.0。"},{"entryId":5,"order":2,"title":"校园二手交易平台","text":"担任后端负责人，使用 Spring Boot + MySQL + Redis 实现商品发布、检索与订单流程，日均订单 300+，接口平均响应 80ms。"},{"entryId":3,"order":3,"title":"字节跳动 · 后端开发实习生","text":"参与电商交易链路开发，负责订单状态机与幂等改造，将重复下单率由 0.8% 降至 0.1%。"},{"entryId":9,"order":4,"title":"Java","text":"熟练：Spring Boot、MyBatis-Plus、JUC 并发工具，使用年限 3 年。"}]',
   '# 演示用户\n\n## 教育背景\n华中科技大学 · 计算机科学与技术 · 本科（2019.09-2023.06，GPA 3.6/4.0）\n\n## 项目经历\n**校园二手交易平台**（后端负责人，2022.03-2022.06）\n- 使用 Spring Boot + MySQL + Redis 实现商品发布、检索与订单流程\n- 日均订单 300+，接口平均响应 80ms\n\n## 实习经历\n**字节跳动 · 后端开发实习生**\n- 负责订单状态机与幂等改造，重复下单率由 0.8% 降至 0.1%\n\n## 技能\n- Java：熟练，Spring Boot、MyBatis-Plus、JUC 并发工具，3 年',
   '2026/10/9f2b1c34-6a1e-4c77-9b0d-2f5e6d1a8c33.pdf',
   '演示用户 求职意向：Java 后端开发\n电话：13800000000 邮箱：demo@example.com\n教育背景：华中科技大学 计算机科学与技术 本科 2019.09-2023.06 GPA 3.6/4.0\n实习经历：字节跳动 后端开发实习生 2022.09-2023.03\n项目经历：校园二手交易平台 后端负责人 2022.03-2022.06\n技能：Java、Spring Boot、MySQL、Redis',
   0,0,'2026-10-20 11:30:00','2026-10-20 11:30:00'),
  (2,2,'字节跳动-后端开发工程师 定制版','generate','ready','后端开发工程师',
   '[{"entryId":3,"order":1,"title":"字节跳动 · 后端开发实习生","text":"参与电商交易链路开发，负责订单状态机与幂等改造，重复下单率由 0.8% 降至 0.1%。"},{"entryId":7,"order":2,"title":"高并发秒杀系统","text":"完成库存预热、令牌桶限流、异步下单与订单状态补偿，压测峰值 1.2 万 QPS，超卖 0 单。"},{"entryId":6,"order":3,"title":"分布式任务调度系统","text":"实现 Netty RPC 通信与 ZooKeeper 选主和任务分片，单集群 200 个执行器，调度延迟 P99 小于 200ms。"},{"entryId":10,"order":4,"title":"MySQL","text":"熟悉：索引优化、事务隔离级别、explain 执行计划分析，使用年限 2 年。"}]',
   '# 演示用户\n\n## 求职意向\n后端开发工程师\n\n## 实习经历\n**字节跳动 · 后端开发实习生**（2022.09-2023.03）\n- 负责订单状态机与幂等改造，重复下单率由 0.8% 降至 0.1%\n\n## 项目经历\n**高并发秒杀系统**\n- 完成库存预热、令牌桶限流、异步下单与订单状态补偿，峰值 1.2 万 QPS\n\n**分布式任务调度系统**\n- 实现 Netty RPC 通信与 ZooKeeper 选主与任务分片，延迟 P99 小于 200ms\n\n## 技能\n- MySQL：熟悉索引优化与事务隔离级别，2 年',
   NULL,NULL,1,0,'2026-11-08 20:40:00','2026-11-08 20:40:00'),
  (3,3,'通用简历 v1','upload','ready',NULL,
   '[{"entryId":1,"order":1,"title":"腾讯科技 · 后端开发实习生","text":"参与游戏运营后台接口开发，负责活动配置与数据导出模块，日均调用量 200 万，导出耗时由 42s 优化至 6s。"}]',
   '# 陈伟\n\n## 实习经历\n**腾讯科技 · 后端开发实习生**（2022.07-2022.12）\n- 参与游戏运营后台的接口开发，负责活动配置与数据导出模块，日均调用量 200 万\n- 独立完成 3 个模块上线，导出任务耗时从 42s 优化到 6s',
   '2026/10/1a7c9e52-3b48-4d21-8f66-7c9d0b2e4a15.docx',
   '陈伟 求职意向：Java 后端开发\n电话：13900000000 邮箱：chenwei@example.com\n实习经历：腾讯科技 后端开发实习生 2022.07-2022.12\n技能：Java、Spring Boot、MySQL',
   0,0,'2026-10-12 10:35:00','2026-10-12 10:35:00');

-- ---------------------------------------------------------------------
-- 5) job_target —— chenwei 1 条（id=1）+ demo 2 条（id=2、3）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `job_target`
  (`id`,`user_id`,`company`,`position`,`jd_raw`,`skills`,`highlights`,`deadline`,`created_at`,`updated_at`)
VALUES
  (1,3,'腾讯科技','Java 后端开发',
   '岗位职责：1. 负责游戏运营后台服务端的设计与开发；2. 参与活动配置、数据导出、对账等核心模块；3. 参与服务性能优化与稳定性建设。任职要求：1. 熟悉 Java 基础与 JVM 内存模型；2. 熟悉 Spring Boot、MyBatis 等主流框架；3. 熟悉 MySQL 索引与事务，了解 Redis 缓存设计；4. 有高并发或大数据量处理经验者优先。',
   'Java,Spring Boot,MyBatis,MySQL,Redis,JVM',
   '["要求 JVM 内存模型","要求 MySQL 索引与事务","偏好高并发经验"]',
   '2026-12-15','2026-10-15 10:00:00','2026-10-15 10:00:00'),
  (2,2,'字节跳动','后端开发工程师',
   '岗位职责：1. 负责电商交易链路服务端研发；2. 负责订单、履约等核心域的系统设计与稳定性保障；3. 参与服务治理与容量规划。任职要求：1. 熟练掌握 Go 或 Java 至少一门语言；2. 熟悉 MySQL、Redis、Kafka 等中间件；3. 具备分布式系统设计能力，理解幂等、最终一致性；4. 有大型交易系统经验者优先。',
   'Go,Java,MySQL,Redis,Kafka,分布式系统,幂等',
   '["强调幂等与最终一致性","要求 Kafka 使用经验","交易链路经验优先"]',
   '2026-11-30','2026-10-28 09:20:00','2026-10-28 09:20:00'),
  (3,2,'阿里巴巴','Java 研发工程师',
   '岗位职责：1. 负责电商中台服务的研发与维护；2. 参与核心接口性能优化，保障大促稳定性；3. 参与技术方案评审与代码评审。任职要求：1. Java 基础扎实，熟悉集合与并发编程；2. 熟悉 Spring Cloud 微服务体系；3. 熟悉 MySQL 分库分表与索引优化；4. 有大促保障经验者优先。',
   'Java,Spring Cloud,MySQL,分库分表,JUC',
   '["要求并发编程能力","要求分库分表经验","大促保障经验优先"]',
   '2026-12-20','2026-11-01 15:40:00','2026-11-01 15:40:00');

-- ---------------------------------------------------------------------
-- 6) delivery_record —— id=1 必须属于 chenwei（AC-10.6 越权靶子）；id=2、3 属于 demo
--    exam_time/interview_time 用相对当前时间构造，保证"待办 24 小时窗口"随时可见
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `delivery_record`
  (`id`,`user_id`,`job_target_id`,`status`,`delivery_date`,`exam_time`,`interview_time`,`deadline`,`skill_req`,`remark`,`status_history`,`deleted`,`created_at`,`updated_at`)
VALUES
  (1,3,1,'interview','2026-11-05','2026-11-12 14:00:00',DATE_ADD(NOW(), INTERVAL 5 HOUR),'2026-12-15',
   'Java,Spring Boot,MySQL,Redis','一面已过，二面准备中',
   '[{"from":"pending","to":"applied","changedAt":"2026-11-05 10:12:03"},{"from":"applied","to":"exam","changedAt":"2026-11-09 09:30:41"},{"from":"exam","to":"interview","changedAt":"2026-11-12 18:02:17"}]',
   0,'2026-11-05 10:12:03','2026-11-12 18:02:17'),
  (2,2,2,'applied','2026-11-08',NULL,NULL,'2026-11-30',
   'Go,Java,MySQL,Redis,Kafka','内推码已用，等笔试通知',
   '[{"from":"pending","to":"applied","changedAt":"2026-11-08 21:04:52"}]',
   0,'2026-11-08 21:04:52','2026-11-08 21:04:52'),
  (3,2,3,'exam','2026-11-10',DATE_ADD(NOW(), INTERVAL 20 HOUR),NULL,'2026-12-20',
   'Java,Spring Cloud,MySQL,分库分表','笔试链接已收到',
   '[{"from":"pending","to":"applied","changedAt":"2026-11-10 09:15:20"},{"from":"applied","to":"exam","changedAt":"2026-11-11 20:33:08"}]',
   0,'2026-11-10 09:15:20','2026-11-11 20:33:08');

-- ---------------------------------------------------------------------
-- 7) resume_diagnose_record —— id=1 chenwei（success，68 分）+ id=2 demo（degraded，74 分）
--    score = 0.35*skill + 0.30*project + 0.20*education + 0.15*expression，四舍五入取整
--    report_json 内含完整 sourceSnapshot（快照而非引用，DD-1）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `resume_diagnose_record`
  (`id`,`user_id`,`username_snapshot`,`position_snapshot`,`resume_id`,`job_target_id`,`delivery_id`,`task_id`,`score`,`report_json`,`status`,`fail_reason`,`cost_seconds`,`tokens`,`created_at`)
VALUES
  (1,3,'chenwei','Java 后端开发',3,1,1,3,68,
   '{"score":68,"dims":[{"key":"skill","name":"技能匹配","weight":0.35,"score":72},{"key":"project","name":"项目经历","weight":0.30,"score":70},{"key":"education","name":"教育背景","weight":0.20,"score":66},{"key":"expression","name":"表达质量","weight":0.15,"score":57}],"items":[{"entryId":1,"original":"参与游戏运营后台的接口开发，负责活动配置与数据导出模块。","rewrite":"主导游戏运营后台活动配置与数据导出模块的接口设计与开发，覆盖日均 200 万次调用。","problem":"职责动词偏弱，缺少规模数据支撑。","suggestion":"把参与改为主导或负责，并补充调用量、模块数量等可验证指标。","verdict":"rewritten","adopted":false}],"skipped":[],"sourceSnapshot":[{"entryId":1,"order":1,"title":"腾讯科技 · 后端开发实习生","text":"参与游戏运营后台接口开发，负责活动配置与数据导出模块，日均调用量 200 万，导出耗时由 42s 优化至 6s。"}]}',
   'success',NULL,38,4820,'2026-11-12 09:44:12'),
  (2,2,'demo','后端开发工程师',1,2,2,4,74,
   '{"score":74,"dims":[{"key":"skill","name":"技能匹配","weight":0.35,"score":80},{"key":"project","name":"项目经历","weight":0.30,"score":76},{"key":"education","name":"教育背景","weight":0.20,"score":70},{"key":"expression","name":"表达质量","weight":0.15,"score":61}],"items":[{"entryId":5,"original":"设计商品发布、检索与订单流程，实现基于 Redis 的热点商品缓存与分布式锁。","rewrite":"主导校园二手交易平台商品与订单域设计，以 Redis 热点缓存 + 分布式锁支撑日均 300 单、5000 用户。","problem":"成果数字与职责混在一起，缺少结果前置。","suggestion":"把结果前置到条目首句，技术方案用括号补充。","verdict":"rewritten","adopted":false},{"entryId":3,"original":"参与电商交易链路的订单模块开发，承担状态机改造与幂等设计。","rewrite":"负责电商交易订单模块的状态机改造与幂等设计，重复下单率由 0.8% 降至 0.1%。","problem":"缺少量化结果。","suggestion":"补充重复下单率下降幅度。","verdict":"rewritten","adopted":false}],"skipped":[{"entryId":99,"reason":"entry_id_not_found"}],"sourceSnapshot":[{"entryId":2,"order":1,"title":"华中科技大学 · 计算机科学与技术 · 本科","text":"2019.09-2023.06 就读于华中科技大学计算机科学与技术专业，本科，GPA 3.6/4.0。"},{"entryId":5,"order":2,"title":"校园二手交易平台","text":"担任后端负责人，使用 Spring Boot + MySQL + Redis 实现商品发布、检索与订单流程，日均订单 300+，接口平均响应 80ms。"},{"entryId":3,"order":3,"title":"字节跳动 · 后端开发实习生","text":"参与电商交易链路开发，负责订单状态机与幂等改造，将重复下单率由 0.8% 降至 0.1%。"}]}',
   'degraded',NULL,44,5310,'2026-11-09 21:02:35');

-- ---------------------------------------------------------------------
-- 8) mock_interview_record —— demo 1 场（作答中，progress_index=2，演示断点续答）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `mock_interview_record`
  (`id`,`user_id`,`delivery_id`,`job_target_id`,`questions_json`,`answers_json`,`feedback_json`,`request_log_json`,`progress_index`,`review_text`,`archived`,`archive_time`,`status`,`created_at`,`updated_at`)
VALUES
  (1,2,3,3,
   '[{"no":1,"category":"深挖","question":"你的秒杀系统是如何防止超卖的？请说明具体机制。"},{"no":2,"category":"八股","question":"请解释 MySQL 索引失效的常见场景。"},{"no":3,"category":"行为","question":"讲一次你和同事在技术方案上产生分歧的经历。"},{"no":4,"category":"深挖","question":"分布式锁在 Redis 主从切换时可能失效，你如何应对？"},{"no":5,"category":"八股","question":"JVM 内存模型中，对象在什么情况下会晋升到老年代？"},{"no":6,"category":"行为","question":"如果给你两周时间优化一个慢查询接口，你会怎么做？"}]',
   '[{"no":1,"answer":"先用 Redis 预扣库存，Lua 脚本保证原子性，下单成功后再异步落库扣减，最后用消息补偿对齐。","answeredAt":"2026-11-11 21:05:12"},{"no":2,"answer":"对索引列做函数运算、隐式类型转换、前导模糊匹配、or 条件部分无索引都会失效。","answeredAt":"2026-11-11 21:08:40"}]',
   '[{"no":1,"good":"提到了 Lua 保证原子性，思路正确。","bad":"没有说明预扣库存与落库失败时的补偿细节。","sample":"典型回答：Redis 预扣 + Lua 原子扣减 + 异步落库 + 定时对账补偿，三者缺一不可。"},{"no":2,"good":"四类场景基本覆盖。","bad":"没有提到联合索引的最左前缀原则导致的部分失效。","sample":"典型回答：函数运算、隐式转换、前导模糊、or 条件、最左前缀不匹配都会失效。"}]',
   '[{"requestId":"123e4567-e89b-42d3-a456-426614174000","payloadDigest":"1a18c854e335507db3e15a962689857e3a0395af56fbcf316536fe99b9226ad1","resultIndex":0,"resultRef":{"index":0,"answer":"先用 Redis 预扣库存，Lua 脚本保证原子性，下单成功后再异步落库扣减，最后用消息补偿对齐。","feedback":{"good":"提到了 Lua 保证原子性，思路正确。","bad":"没有说明预扣库存与落库失败时的补偿细节。","sample":"典型回答：Redis 预扣 + Lua 原子扣减 + 异步落库 + 定时对账补偿，三者缺一不可。"},"progressIndex":1},"quotaOperationId":"interview:1:123e4567-e89b-42d3-a456-426614174000","startedAt":"2026-11-11 21:05:00","completedAt":"2026-11-11 21:05:12"},{"requestId":"123e4567-e89b-42d3-a456-426614174001","payloadDigest":"bd73034e376df9304f4879896ad141849d01f0e79c65dfc288f578b2e015fda0","resultIndex":1,"resultRef":{"index":1,"answer":"对索引列做函数运算、隐式类型转换、前导模糊匹配、or 条件部分无索引都会失效。","feedback":{"good":"四类场景基本覆盖。","bad":"没有提到联合索引的最左前缀原则导致的部分失效。","sample":"典型回答：函数运算、隐式转换、前导模糊、or 条件、最左前缀不匹配都会失效。"},"progressIndex":2},"quotaOperationId":"interview:1:123e4567-e89b-42d3-a456-426614174001","startedAt":"2026-11-11 21:08:25","completedAt":"2026-11-11 21:08:40"}]',
   2,NULL,0,NULL,'answering','2026-11-11 21:03:55','2026-11-11 21:08:40');

-- ---------------------------------------------------------------------
-- 9) ai_task —— 6 条覆盖 4 类任务 + 1 条失败退款 + 1 条未读（任务通知演示）
--    timeout_at = created_at + 5 分钟（ai.task.timeout-min）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `ai_task`
  (`id`,`user_id`,`task_type`,`biz_id`,`status`,`progress`,`fail_reason`,`result_ref`,`result_json`,`input_snapshot`,`quota_date`,`quota_generation`,`quota_state`,`refunded`,`read_flag`,`created_at`,`started_at`,`finished_at`,`timeout_at`)
VALUES
  (1,2,'resume_parse',1,'SUCCESS',100,NULL,NULL,NULL,
   '{"fileName":"9f2b1c34-6a1e-4c77-9b0d-2f5e6d1a8c33.pdf","entryCount":4}',
   '2026-10-20',0,'settled',0,1,'2026-10-20 11:28:04','2026-10-20 11:28:05','2026-10-20 11:28:39','2026-10-20 11:33:04'),
  (2,2,'resume_generate',NULL,'SUCCESS',100,NULL,NULL,
   '{"draftTaskId":2,"items":[{"entryId":3,"order":1,"title":"字节跳动 · 后端开发实习生","text":"参与电商交易链路开发，负责订单状态机与幂等改造，重复下单率由 0.8% 降至 0.1%。"},{"entryId":7,"order":2,"title":"高并发秒杀系统","text":"完成库存预热、令牌桶限流、异步下单与订单状态补偿，压测峰值 1.2 万 QPS，超卖 0 单。"}],"contentMd":"[虚构草稿正文，不在示例中展示]"}',
   '{"entryIds":[3,6,7,10],"entries":[{"entryId":3,"title":"字节跳动 · 后端开发实习生","text":"参与电商交易链路开发，负责订单状态机与幂等改造，重复下单率由 0.8% 降至 0.1%。","entryType":"internship"},{"entryId":7,"title":"高并发秒杀系统","text":"完成库存预热、令牌桶限流、异步下单与订单状态补偿，压测峰值 1.2 万 QPS，超卖 0 单。","entryType":"project"},{"entryId":6,"title":"分布式任务调度系统","text":"实现 Netty RPC 通信与 ZooKeeper 选主和任务分片，单集群 200 个执行器，调度延迟 P99 小于 200ms。","entryType":"project"},{"entryId":10,"title":"MySQL","text":"熟悉索引优化、事务隔离级别、explain 执行计划分析，使用年限 2 年。","entryType":"skill"}],"jdText":"字节跳动 后端开发工程师","jobTargetId":2,"focus":"comprehensive"}',
   '2026-11-08',0,'settled',0,0,'2026-11-08 20:38:12','2026-11-08 20:38:13','2026-11-08 20:39:02','2026-11-08 20:43:12'),
  (3,3,'diagnose',NULL,'SUCCESS',100,NULL,1,NULL,
   '{"resumeId":3,"jd":"腾讯科技 Java 后端开发","deliveryId":1}',
   '2026-11-12',0,'settled',0,1,'2026-11-12 09:43:40','2026-11-12 09:43:41','2026-11-12 09:44:12','2026-11-12 09:48:40'),
  (4,2,'diagnose',NULL,'SUCCESS',100,NULL,2,NULL,
   '{"resumeId":1,"jd":"字节跳动 后端开发工程师","deliveryId":2}',
   '2026-11-09',0,'settled',0,1,'2026-11-09 21:02:01','2026-11-09 21:02:02','2026-11-09 21:02:35','2026-11-09 21:07:01'),
  (5,2,'interview_gen',NULL,'SUCCESS',100,NULL,1,NULL,
   '{"deliveryId":3,"questionCount":6,"skills":["Java","Spring Cloud","MySQL"],"jdText":"字节跳动 后端开发工程师"}',
   '2026-11-11',0,'settled',0,1,'2026-11-11 21:03:40','2026-11-11 21:03:41','2026-11-11 21:03:55','2026-11-11 21:08:40'),
  (6,2,'resume_parse',1,'FAIL',0,'文档解析超时（超过 5 分钟），已退还配额',NULL,NULL,
   '{"fileName":"broken-scan.pdf","reason":"timeout"}',
   '2026-10-21',0,'refunded',1,1,'2026-10-21 10:12:00','2026-10-21 10:12:01','2026-10-21 10:17:02','2026-10-21 10:17:00');

-- ---------------------------------------------------------------------
-- 10) ai_call_log —— 8 条覆盖 6 个场景、success/fail/degraded，跨 7 天便于趋势图演示
--     只记元数据，严禁写入提示词与回答正文
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `ai_call_log`
  (`id`,`user_id`,`scene`,`model`,`prompt_tokens`,`completion_tokens`,`duration_ms`,`status`,`degraded`,`cost_estimate`,`operation_id`,`quota_date`,`quota_generation`,`quota_state`,`created_at`)
VALUES
  (1,2,'resume_parse','deepseek-chat',4200,760,38250,'success',0,0.0057,'1','2026-10-20',0,'settled',DATE_SUB(NOW(), INTERVAL 20 DAY)),
  (2,2,'jd_parse','deepseek-chat',1800,320,6200,'success',0,0.0024,NULL,'2026-11-08',0,'settled',DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (3,2,'resume_generate','deepseek-chat',3600,1250,41080,'success',0,0.0061,'2','2026-11-08',0,'settled',DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (4,2,'diagnose','deepseek-chat',4480,830,44120,'success',1,0.0061,'4','2026-11-09',0,'settled',DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (5,3,'diagnose','deepseek-chat',4100,720,38200,'success',0,0.0055,'3','2026-11-12',0,'settled',DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (6,2,'interview_gen','deepseek-chat',3900,980,52030,'success',0,0.0059,'5','2026-11-11',0,'settled',DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (7,2,'interview_feedback','deepseek-chat',2100,540,12800,'fail',0,0.0032,'fb-demo-3-1723','2026-11-11',0,'refunded',DATE_SUB(NOW(), INTERVAL 3 DAY)),
  (8,NULL,'resume_parse','deepseek-chat',560,0,60000,'fail',0,0.0006,'7','2026-11-12',0,'refunded',DATE_SUB(NOW(), INTERVAL 1 DAY));

-- ---------------------------------------------------------------------
-- 11) system_log —— 9 条（login 出现 2 次）覆盖 action 9 值中的 8 个
--     （delete_all 故意不预置：预置会与演示账号仍持有数据的事实矛盾，详见说明书 §12 自检清单）
-- ---------------------------------------------------------------------
INSERT IGNORE INTO `system_log`
  (`id`,`user_id`,`username`,`action`,`success`,`ip`,`remark`,`created_at`)
VALUES
  (1,1,'admin','login',1,'127.0.0.1','管理员登录成功',DATE_SUB(NOW(), INTERVAL 7 DAY)),
  (2,2,'demo','login',1,'127.0.0.1','用户登录成功',DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (3,NULL,'demo','login_fail',0,'127.0.0.1','密码错误（第 1 次）',DATE_SUB(NOW(), INTERVAL 6 DAY)),
  (4,1,'admin','quota_adjust',1,'127.0.0.1','被操作对象 ID=2，调整类型=重置当日用量，quota_generation 0→1',DATE_SUB(NOW(), INTERVAL 5 DAY)),
  (5,1,'admin','user_disable',1,'127.0.0.1','被操作对象 ID=3，禁用原因=账号异常登录',DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (6,1,'admin','user_enable',1,'127.0.0.1','被操作对象 ID=3，恢复原因=用户申诉通过',DATE_SUB(NOW(), INTERVAL 4 DAY)),
  (7,NULL,NULL,'file_clean',1,'127.0.0.1','清理过期上传文件 3 个，file_path 已置空',DATE_SUB(NOW(), INTERVAL 2 DAY)),
  (8,NULL,NULL,'log_clean',1,'127.0.0.1','删除 ai_call_log 12 条、置空 report_json 1 条、删除 system_log 5 条',DATE_SUB(NOW(), INTERVAL 1 DAY)),
  (9,NULL,NULL,'circuit_break',0,'job-ai-circuit','熔断触发：60 分钟内失败率 62%（31/50），窗口内调用 50 ≥ 20',DATE_SUB(NOW(), INTERVAL 2 DAY));

-- =====================================================================
-- 断言段：直接看结果，任一行不符合 expected 即视为初始化失败
-- =====================================================================
SELECT 'A1 表数量(期望 11)' AS assertion, COUNT(*) AS actual, 11 AS expected
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ai_jobs' AND TABLE_TYPE = 'BASE TABLE';

SELECT 'A2 三个账号(期望 3)' AS assertion, COUNT(*) AS actual, 3 AS expected
  FROM `user` WHERE `username` IN ('admin','demo','chenwei');

SELECT 'A3 system_config(期望 13)' AS assertion, COUNT(*) AS actual, 13 AS expected
  FROM `system_config`;

SELECT 'A4 demo 已确认能力条目(期望 >=10)' AS assertion,
       COUNT(*) AS actual, 10 AS expected
  FROM `ability_entry` WHERE `user_id` = 2 AND `status` = 'confirmed' AND `deleted` = 0;

SELECT 'A5 chenwei 投递 id=1(期望 1)' AS assertion, COUNT(*) AS actual, 1 AS expected
  FROM `delivery_record` WHERE `id` = 1 AND `user_id` = 3;

SELECT '账号与配额' AS check_item, `username`, `role`, `status`, `quota_override`, `quota_generation`
  FROM `user` ORDER BY `id`;

SELECT '演示数据行数' AS check_item,
       (SELECT COUNT(*) FROM `ability_entry`)            AS ability_entry,
       (SELECT COUNT(*) FROM `user_resume`)              AS user_resume,
       (SELECT COUNT(*) FROM `job_target`)               AS job_target,
       (SELECT COUNT(*) FROM `delivery_record`)          AS delivery_record,
       (SELECT COUNT(*) FROM `resume_diagnose_record`)   AS diagnose_record,
       (SELECT COUNT(*) FROM `mock_interview_record`)    AS mock_interview,
       (SELECT COUNT(*) FROM `ai_task`)                  AS ai_task,
       (SELECT COUNT(*) FROM `ai_call_log`)              AS ai_call_log,
       (SELECT COUNT(*) FROM `system_log`)               AS system_log,
       (SELECT COUNT(*) FROM `system_config`)            AS system_config;
