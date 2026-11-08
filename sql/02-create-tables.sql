-- =====================================================================
-- AI 求职助手 · 02-create-tables.sql
-- 用途：建立 11 张业务表（不增不减）
-- 目标环境：MySQL 5.7.16 / InnoDB / utf8mb4_general_ci / ROW_FORMAT=DYNAMIC
-- 执行前置：01-create-database.sql 已执行
-- 可重复执行：是（全部 CREATE TABLE IF NOT EXISTS；结构变更请用迁移脚本，勿直接改本文件后重跑）
-- 重要约定：
--   1) 不建物理外键，关系用"逻辑外键 + 索引"表达，由应用层（Service 事务）保证一致性；
--   2) 所有时间列用 datetime，禁止字符串存时间；
--   3) 金额/成本用 decimal，绝不用 float/double；
--   4) 状态枚举一律 varchar，禁用 MySQL ENUM；大小写按契约：ai_task.status 大写（QUEUED/RUNNING/SUCCESS/FAIL），其余小写；
--   5) TEXT / MEDIUMTEXT / JSON 列不写 DEFAULT（MySQL 5.7 不允许这几类列有默认值），可空性由 NULL 表达。
-- =====================================================================

USE `ai_jobs`;

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------
-- 1/11 user —— 用户账号表（账号域，M1 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user` (
  `id`                     bigint(20)   NOT NULL AUTO_INCREMENT           COMMENT '用户主键',
  `username`               varchar(50)  NOT NULL                          COMMENT '登录用户名（唯一；3~50 字符）',
  `password`               varchar(100) NOT NULL                          COMMENT '密码密文：BCrypt（60 字符，含算法标识/代价因子/盐）',
  `nickname`               varchar(50)  NOT NULL DEFAULT ''               COMMENT '昵称（工作台欢迎语展示，不参与鉴权）',
  `email`                  varchar(100) DEFAULT NULL                      COMMENT '邮箱（个人中心只读展示；不建唯一键，本期不做邮箱登录/找回）',
  `role`                   varchar(20)  NOT NULL DEFAULT 'user'           COMMENT '角色：user=普通用户，admin=管理员',
  `status`                 tinyint(1)   NOT NULL DEFAULT 1                COMMENT '账号状态：1=正常，0=禁用（禁用后禁止登录且已签发令牌立即失效）',
  `quota_override`         int(11)      DEFAULT NULL                      COMMENT '个人每日 AI 配额覆盖值（0~999）；NULL=取 system_config.quota.daily.default',
  `quota_generation`       bigint(20)   NOT NULL DEFAULT 0                COMMENT '配额重置代次（管理员重置配额时 +1，用于退款幂等判定的代次比对）',
  `ai_call_limit_override` int(11)      DEFAULT NULL                      COMMENT '个人日 AI 调用次数上限覆盖值（仅隔离的评测账号可设）；NULL=取 system_config.ai.circuit.max-calls',
  `last_login_at`          datetime     DEFAULT NULL                      COMMENT '最近一次登录成功时间（管理端用户详情展示）',
  `deleted`                tinyint(1)   NOT NULL DEFAULT 0                COMMENT '注销标记：0=未注销，1=已注销（行保留，用于审计与"用户总数不变"统计口径）',
  `deleted_at`             datetime     DEFAULT NULL                      COMMENT '注销时间（deleted=1 时写入）',
  `created_at`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '注册时间',
  `updated_at`             datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_username` (`username`),
  KEY `idx_user_nickname` (`nickname`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='用户账号表（账号域，M1 唯一写入）：注册登录、状态、配额覆盖、注销标记';

-- ---------------------------------------------------------------------
-- 2/11 ability_entry —— 能力库条目表（事实域，M2 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ability_entry` (
  `id`               bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '能力条目主键',
  `user_id`          bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）；所有查询强制附加此条件',
  `entry_type`       varchar(20)  NOT NULL                COMMENT '条目类型：education/internship/project/skill/certificate/other（6 类，见 FR-10 字段契约）',
  `title`            varchar(500) NOT NULL                COMMENT '条目标题（≤500 字，超长按句截断）',
  `detail_json`      text         NULL                    COMMENT '类型明细 JSON（键名按 entry_type 对应 FR-10 字段契约；描述类字段 ≤2000 字）',
  `source`           varchar(10)  NOT NULL DEFAULT 'manual' COMMENT '来源：manual=手工录入，ai=AI 解析（创建后不可变）',
  `status`           varchar(20)  NOT NULL DEFAULT 'pending' COMMENT '状态：pending=待确认，confirmed=已确认（单向，拒绝即逻辑删除；手工新增直接 confirmed）',
  `enabled`          tinyint(1)   NOT NULL DEFAULT 1      COMMENT '启用标记：1=启用，0=停用（与 status 正交；仅 confirmed 可切换；停用不回溯已生成版本）',
  `sort_no`          int(11)      NOT NULL DEFAULT 0      COMMENT '排序号（升序，决定简历合成与 AI 上下文组装顺序）',
  `source_resume_id` bigint(20)   DEFAULT NULL            COMMENT '解析来源简历版本 ID（逻辑外键 → user_resume.id；源版本被删后置 NULL，前端显示"原稿已删除"）',
  `deleted`          tinyint(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除（拒绝待确认条目=逻辑删除）',
  `created_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ability_entry_user_type_status` (`user_id`,`entry_type`,`status`),
  KEY `idx_ability_entry_user_status` (`user_id`,`status`),
  KEY `idx_ability_entry_source_resume` (`source_resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='能力库条目表（事实域，M2 唯一写入）：简历合成的唯一事实源，含待确认闸门';

-- ---------------------------------------------------------------------
-- 3/11 user_resume —— 简历版本表（事实域，M2 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `user_resume` (
  `id`              bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '简历版本主键',
  `user_id`         bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）',
  `name`            varchar(100) NOT NULL                COMMENT '版本名称（同用户内允许重名）',
  `source_type`     varchar(20)  NOT NULL                COMMENT '来源类型：upload=上传，generate=能力库生成，diagnose=诊断采纳合成',
  `status`          varchar(20)  NOT NULL DEFAULT 'ready' COMMENT '状态：parsing=解析中，ready=就绪，failed=解析失败（仅 upload 会经过 parsing）',
  `target_position` varchar(100) DEFAULT NULL            COMMENT '目标岗位（生成/诊断合成时记录；上传版本可为空）',
  `content_json`    mediumtext   NULL                    COMMENT '条目快照 JSON：[{entryId,order,title,text}]；诊断采纳合成的唯一定位依据',
  `content_md`      mediumtext   NULL                    COMMENT '渲染后的 Markdown 正文；仅用于展示与导出，不参与精确替换',
  `file_path`       varchar(255) DEFAULT NULL            COMMENT '上传文件相对路径（仅 upload；UUID 命名；到期清理置 NULL，raw_text 保留）',
  `raw_text`        mediumtext   NULL                    COMMENT '文件抽取原文（供"查看原文"溯源；清理任务不删除）',
  `sync_status`     tinyint(1)   NOT NULL DEFAULT 0      COMMENT '可同步标记：0=最新，1=能力库有更新可重新生成（创建时判定；新建版本不回溯旧版本）',
  `deleted`         tinyint(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除（界面不提供恢复）',
  `created_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_resume_user_deleted_created` (`user_id`,`deleted`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='简历版本表（事实域，M2 唯一写入）：条目快照双字段（content_json+content_md）';

-- ---------------------------------------------------------------------
-- 4/11 job_target —— 岗位目标表（事实域，M4 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `job_target` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '岗位目标主键',
  `user_id`    bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）',
  `company`    varchar(100) NOT NULL                COMMENT '公司名称（≤100 字；写入前 trim）',
  `position`   varchar(100) NOT NULL                COMMENT '岗位名称（≤100 字；写入前 trim）',
  `jd_raw`     mediumtext   NULL                    COMMENT 'JD 原文（≤8000 字；AI 解析前的原始快照）',
  `skills`     varchar(500) DEFAULT NULL            COMMENT 'AI 解析出的技能要求（原始值，不与 delivery_record.skill_req 同步）',
  `highlights` text         NULL                    COMMENT 'AI 解析出的要点摘要 JSON（数组）',
  `deadline`   date         DEFAULT NULL            COMMENT 'AI 解析出的截止日期（原始值，仅溯源；待办/展示一律以 delivery_record.deadline 为准）',
  `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间（JD 变更时更新）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_job_target_user_company_position` (`user_id`,`company`,`position`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='岗位目标表（事实域，M4 唯一写入）：同一"用户+公司+岗位"只保留一份 JD，被投递/诊断/面试三方复用';

-- ---------------------------------------------------------------------
-- 5/11 delivery_record —— 投递记录表（业务域，M4 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `delivery_record` (
  `id`             bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '投递记录主键',
  `user_id`        bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）',
  `job_target_id`  bigint(20)   NOT NULL                COMMENT '关联岗位目标 ID（逻辑外键 → job_target.id）；公司/岗位/JD 一律回读该表，本表不冗余',
  `status`         varchar(20)  NOT NULL DEFAULT 'pending' COMMENT '当前状态：pending/applied/exam/interview/offer/rejected（六值；offer、rejected 为终态）',
  `delivery_date`  date         NOT NULL                COMMENT '投递日期（默认当天；用户可填未来计划日期；不参与待办窗口计算）',
  `exam_time`      datetime     DEFAULT NULL            COMMENT '笔试时间（待办：24 小时内区间）',
  `interview_time` datetime     DEFAULT NULL            COMMENT '面试时间（待办：24 小时内区间）',
  `deadline`       date         DEFAULT NULL            COMMENT '投递截止日期（待办：72 小时内区间；用户确认后的最终值）',
  `skill_req`      varchar(500) DEFAULT NULL            COMMENT '技能要求（用户在表单确认/修改后的最终值，与 job_target.skills 不同步）',
  `remark`         varchar(500) DEFAULT NULL            COMMENT '备注（≤500 字）',
  `status_history` json         NULL                    COMMENT '状态变更历史 JSON：[{from,to,changedAt}]；与状态更新同事务追加，不建第 12 张表',
  `deleted`        tinyint(1)   NOT NULL DEFAULT 0      COMMENT '逻辑删除：0=正常，1=已删除（界面不提供恢复入口）',
  `created_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`     datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_delivery_record_user_status` (`user_id`,`status`),
  KEY `idx_delivery_record_user_exam` (`user_id`,`exam_time`),
  KEY `idx_delivery_record_user_interview` (`user_id`,`interview_time`),
  KEY `idx_delivery_record_user_deadline` (`user_id`,`deadline`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='投递记录表（业务域，M4 唯一写入）：看板六列、待办提醒、状态迁移审计';

-- ---------------------------------------------------------------------
-- 6/11 resume_diagnose_record —— 诊断记录表（业务域，M3 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `resume_diagnose_record` (
  `id`                bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '诊断记录主键',
  `user_id`           bigint(20)   DEFAULT NULL           COMMENT '归属用户 ID（逻辑外键 → user.id）；注销时置 NULL（脱敏保留元数据）',
  `username_snapshot` varchar(50)  DEFAULT NULL           COMMENT '用户名快照；注销时改写为"已注销"',
  `position_snapshot` varchar(100) DEFAULT NULL           COMMENT '目标岗位名称快照（注销后仍保留，是唯一可展示的岗位信息）',
  `resume_id`         bigint(20)   DEFAULT NULL           COMMENT '被诊断简历版本 ID（逻辑外键 → user_resume.id）；注销/版本物理删除时置 NULL',
  `job_target_id`     bigint(20)   DEFAULT NULL           COMMENT '岗位目标 ID（逻辑外键 → job_target.id）；注销/物理删除时置 NULL',
  `delivery_id`       bigint(20)   DEFAULT NULL           COMMENT '来源投递记录 ID（可选；从投递详情发起诊断时写入）；注销/物理删除时置 NULL',
  `task_id`           bigint(20)   DEFAULT NULL           COMMENT '产出本报告的异步任务 ID（逻辑外键 → ai_task.id）；注销时置 NULL',
  `score`             int(11)      DEFAULT NULL           COMMENT '四维加权总分 0~100（后端按固定权重计算，不接受模型总分）；失败记录为 NULL',
  `report_json`       mediumtext   NULL                   COMMENT '四维报告 JSON：{score,dims[4],items[{...,adopted}],skipped[{entryId,reason}],sourceSnapshot}；过期或注销时置 NULL',
  `status`            varchar(20)  NOT NULL               COMMENT '容错状态：success=成功，degraded=降级（部分建议被剔除），failed=失败（无默认值，由应用显式写入）',
  `fail_reason`       varchar(500) DEFAULT NULL           COMMENT '失败原因（≤500 字，仅元数据，不含正文）',
  `cost_seconds`      int(11)      DEFAULT NULL           COMMENT 'AI 调用总耗时（秒）',
  `tokens`            int(11)      DEFAULT NULL           COMMENT '本次诊断 token 消耗合计（输入+输出）',
  `created_at`        datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '诊断时间（精确到秒）',
  PRIMARY KEY (`id`),
  KEY `idx_resume_diagnose_record_user_created` (`user_id`,`created_at`),
  KEY `idx_resume_diagnose_record_created` (`created_at`),
  KEY `idx_resume_diagnose_record_resume` (`resume_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='简历诊断记录表（业务域，M3 唯一写入）：四维报告、降级标记、注销后脱敏保留';

-- ---------------------------------------------------------------------
-- 7/11 mock_interview_record —— 模拟面试记录表（业务域，M5 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `mock_interview_record` (
  `id`               bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '模拟面试记录主键',
  `user_id`          bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）',
  `delivery_id`      bigint(20)   NOT NULL                COMMENT '来源投递记录 ID（逻辑外键 → delivery_record.id；发起面试必填，与投递同事务物理删除）',
  `job_target_id`    bigint(20)   NOT NULL                COMMENT '岗位目标 ID（逻辑外键 → job_target.id；出题上下文来源）',
  `questions_json`   text         NULL                    COMMENT '题单 JSON：[{no,category,question}]；category ∈ 深挖/八股/行为；有 JD 6 题、无 JD 5 题',
  `answers_json`     mediumtext   NULL                    COMMENT '作答 JSON：[{no,answer,answeredAt}]；每题 ≤2000 字，6 题合计逼近 TEXT 64KB 上限故用 MEDIUMTEXT',
  `feedback_json`    mediumtext   NULL                    COMMENT '三段式点评 JSON：[{no,good,bad,sample}]（优点/问题/示范答案）',
  `request_log_json` mediumtext   NULL                    COMMENT '同步点评幂等台账：requestId、载荷摘要、不可变 resultRef 原响应快照、quotaOperationId 和起止时间；同请求回放原结果',
  `progress_index`   int(11)      NOT NULL DEFAULT 0      COMMENT '已完成题数（断点续答据此定位下一题）',
  `review_text`      text         NULL                    COMMENT '复盘文本（服务端模板拼装，不调用 AI、不消耗配额）',
  `archived`         tinyint(1)   NOT NULL DEFAULT 0      COMMENT '归档标记：1=已归档到投递记录（一场面试只能归档一次，归档后只读）',
  `archive_time`     datetime     DEFAULT NULL            COMMENT '归档时间（archived=1 时必填）',
  `status`           varchar(20)  NOT NULL DEFAULT 'created' COMMENT '状态：created=已创建，answering=作答中，reviewed=已复盘，archived=已归档',
  `created_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_mock_interview_record_user_created` (`user_id`,`created_at`),
  KEY `idx_mock_interview_record_delivery` (`delivery_id`),
  KEY `idx_mock_interview_record_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='模拟面试记录表（业务域，M5 唯一写入）：题单、作答、点评、复盘、归档与断点续答';

-- ---------------------------------------------------------------------
-- 8/11 ai_task —— 异步任务表（治理域，M7 唯一写入方；兼任务通知）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_task` (
  `id`               bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '任务主键（同时作为配额 operationId）',
  `user_id`          bigint(20)   NOT NULL                COMMENT '归属用户 ID（逻辑外键 → user.id）',
  `task_type`        varchar(20)  NOT NULL                COMMENT '任务类型 4 值：resume_parse/resume_generate/diagnose/interview_gen（JD 解析与逐题点评为同步调用，不落任务）',
  `biz_id`           bigint(20)   DEFAULT NULL            COMMENT '业务对象 ID：仅 resume_parse→user_resume.id；resume_generate/diagnose/interview_gen 一律 NULL（生成草稿在 result_json，诊断/面试结果在 result_ref）',
  `status`           varchar(20)  NOT NULL DEFAULT 'QUEUED' COMMENT '状态机：QUEUED→RUNNING→SUCCESS|FAIL，另允许 QUEUED→FAIL；非法流转抛 IllegalStateException',
  `progress`         int(11)      NOT NULL DEFAULT 0      COMMENT '进度百分比 0~100（仅用于前端进度展示，非状态判据）',
  `fail_reason`      varchar(500) DEFAULT NULL            COMMENT '失败原因（≤500 字，仅元数据）',
  `result_ref`       bigint(20)   DEFAULT NULL            COMMENT '结果对象 ID（diagnose→resume_diagnose_record.id；interview_gen→mock_interview_record.id）',
  `result_json`      mediumtext   NULL                    COMMENT '任务结果 JSON；resume_generate 固定为 {draftTaskId,items[{entryId,order,title,text}],contentMd}；成功满180天置NULL并由API返回resultExpired=true',
  `input_snapshot`   mediumtext   NULL                    COMMENT '冻结的业务输入快照：完整 JSON（entryIds、entries[{entryId,title,text,entryType}]、jdText、jobTargetId、focus），Worker 只读本字段、不反查 ability_entry',
  `quota_date`       date         DEFAULT NULL            COMMENT '扣费日期（Asia/Shanghai 自然日），退款幂等三元组之一',
  `quota_generation` bigint(20)   DEFAULT NULL            COMMENT '扣费时的配额代次（user.quota_generation 快照），退款幂等三元组之一',
  `quota_state`      varchar(20)  DEFAULT NULL            COMMENT '配额结算状态：charged=已扣未结算，settled=成功已结算，refunded=已退还（契约 §3）',
  `refunded`         tinyint(1)   NOT NULL DEFAULT 0      COMMENT '已退款标记（结果镜像，仅用于展示与补偿扫描，不作原子判据）',
  `read_flag`        tinyint(1)   NOT NULL DEFAULT 0      COMMENT '通知已读标记：0=未读，1=已读；M7 TaskController 经 TaskReadService 校验本人后写入，M1 仅经 TaskNoticeProvider.recent 查询',
  `created_at`       datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（通知区按此倒序取最近 5 条）',
  `started_at`       datetime     DEFAULT NULL            COMMENT '进入 RUNNING 的时间（执行时写入，非提交时）',
  `finished_at`      datetime     DEFAULT NULL            COMMENT '进入终态的时间（终态事务只写一次）',
  `timeout_at`       datetime     NOT NULL                COMMENT '超时时刻（创建时 = 当前时间 + ai.task.timeout-min 分钟），超时扫描判据',
  PRIMARY KEY (`id`),
  KEY `idx_ai_task_user_type_status_created` (`user_id`,`task_type`,`status`,`created_at`),
  KEY `idx_ai_task_user_created` (`user_id`,`created_at`),
  KEY `idx_ai_task_status_timeout` (`status`,`timeout_at`),
  KEY `idx_ai_task_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='异步任务表（治理域，M7 唯一写入）：四类任务状态机、配额凭证、任务通知复用';

-- ---------------------------------------------------------------------
-- 9/11 ai_call_log —— AI 调用日志表（治理域，M7 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `ai_call_log` (
  `id`                bigint(20)    NOT NULL AUTO_INCREMENT COMMENT '调用日志主键',
  `user_id`           bigint(20)    DEFAULT NULL            COMMENT '归属用户 ID；注销时置 NULL（日志行保留，用于平台统计）',
  `scene`             varchar(20)   NOT NULL                COMMENT '调用场景 6 值：resume_parse/resume_generate/diagnose/jd_parse/interview_gen/interview_feedback',
  `model`             varchar(50)   NOT NULL                COMMENT '模型标识（取生效配置 ai.model）',
  `prompt_tokens`     int(11)       NOT NULL DEFAULT 0      COMMENT '输入 token 数',
  `completion_tokens` int(11)       NOT NULL DEFAULT 0      COMMENT '输出 token 数',
  `duration_ms`       int(11)       NOT NULL DEFAULT 0      COMMENT '调用耗时（毫秒）',
  `status`            varchar(20)   NOT NULL                COMMENT '调用结果：success=成功，fail=失败（小写；降级由 degraded 单独表达，不计入失败率）',
  `degraded`          tinyint(1)    NOT NULL DEFAULT 0      COMMENT '是否降级：1=降级返回（AiResult.DEGRADED），不计入失败率、不退配额',
  `cost_estimate`     decimal(10,4) NOT NULL DEFAULT 0.0000 COMMENT '成本估算（元，= 输入token×ai.price.input + 输出token×ai.price.output；decimal 不用 float）',
  `operation_id`      varchar(100)  DEFAULT NULL            COMMENT '配额操作凭证（异步任务=taskId，同步点评=requestId）；与 quota_date/quota_generation 组成退款幂等三元组',
  `quota_date`        date          DEFAULT NULL            COMMENT '扣费日期（Asia/Shanghai 自然日）',
  `quota_generation`  bigint(20)    DEFAULT NULL            COMMENT '扣费时的配额代次快照',
  `quota_state`       varchar(20)   DEFAULT NULL            COMMENT '配额结算状态：charged/settled/refunded（与 ai_task 同义，契约 §3）',
  `created_at`        datetime      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '调用时间（统计大屏按此分组）',
  PRIMARY KEY (`id`),
  KEY `idx_ai_call_log_created` (`created_at`),
  KEY `idx_ai_call_log_user_created` (`user_id`,`created_at`),
  KEY `idx_ai_call_log_operation_id` (`operation_id`,`quota_date`,`quota_generation`),
  KEY `idx_ai_call_log_scene_status_created` (`scene`,`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='AI 调用日志表（治理域，M7 唯一写入）：只存元数据，严禁提示词与回答正文（隐私墙）';

-- ---------------------------------------------------------------------
-- 10/11 system_log —— 系统日志表（治理域，M1 唯一写入方）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_log` (
  `id`         bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '系统日志主键',
  `user_id`    bigint(20)   DEFAULT NULL            COMMENT '关联用户 ID；登录失败（无有效账号）与系统定时任务为 NULL；注销后保留原值便于审计',
  `username`   varchar(50)  DEFAULT NULL            COMMENT '用户名快照（登录失败时记输入值；用户注销后仍保留）',
  `action`     varchar(50)  NOT NULL                COMMENT '动作类型 9 值：login/login_fail/quota_adjust/delete_all/user_disable/user_enable/file_clean/log_clean/circuit_break',
  `success`    tinyint(1)   NOT NULL DEFAULT 1      COMMENT '结果：1=成功，0=失败',
  `ip`         varchar(50)  DEFAULT NULL            COMMENT '来源 IP（IPv4/IPv6 字符串，直取 X-Forwarded-For 首段或 remoteAddr）',
  `remark`     varchar(500) DEFAULT NULL            COMMENT '备注（清理条数/熔断原因/被操作对象 ID 等元数据；严禁写入正文或密钥）',
  `created_at` datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间（保留 180 天）',
  PRIMARY KEY (`id`),
  KEY `idx_system_log_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='系统日志表（治理域，M1 唯一写入）：九类审计动作，不含任何正文';

-- ---------------------------------------------------------------------
-- 11/11 system_config —— 系统配置表（治理域，M1 唯一写入，仅 ConfigLoader 可写）
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `system_config` (
  `id`           bigint(20)   NOT NULL AUTO_INCREMENT COMMENT '配置主键',
  `config_key`   varchar(100) NOT NULL                COMMENT '配置键（唯一；点分命名，如 ai.model）',
  `config_value` varchar(500) DEFAULT NULL            COMMENT '配置值（统一按字符串存储，由 ConfigLoader 按类型解析；值为空串视为未配置）',
  `remark`       varchar(200) DEFAULT NULL            COMMENT '配置说明（管理端配置页展示）',
  `updated_at`   datetime     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '最后更新时间（配置页"更新时间"列）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_system_config_key` (`config_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC
  COMMENT='系统配置表（治理域，M1 唯一写入）：13 个可热更新键；优先级高于 yml';

-- ---------------------------------------------------------------------
-- 结构自检：应返回 11 行
-- ---------------------------------------------------------------------
SELECT COUNT(*) AS table_count_expected_11
  FROM information_schema.TABLES
 WHERE TABLE_SCHEMA = 'ai_jobs'
   AND TABLE_TYPE = 'BASE TABLE';
