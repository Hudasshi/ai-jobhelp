# AI 求职助手系统

一个基于 Spring Boot 3.5.9 + Vue 3 的 AI 求职辅助项目，覆盖个人能力库、
简历生成与诊断、投递看板、AI 模拟面试和管理端治理。

本目录是完整可运行工程，后端、前端、数据库初始化脚本和联调脚本都在这里，
不依赖工程目录之外的源码。

---

## 1. 技术栈

| 层次 | 技术 |
|---|---|
| 后端 | Java 21、Spring Boot 3.5.9、MyBatis-Plus 3.5.17 |
| AI | LangChain4j 1.23.0、OpenAI 兼容接口、本地 Mock 降级 |
| 数据库 | MySQL 5.7.16+ |
| 缓存/配额 | Redis |
| 认证 | JWT、BCrypt |
| 前端 | Vue 3、Vite 6、Element Plus、Vue Router、Axios |
| 构建 | Maven 3.9+、Node.js 20+（建议 22） |

说明：`pom.xml` 声明 Java 21 运行环境，编译目标固定为 Java 17，
因此代码兼容 JDK 17 及以上；课堂环境使用 JDK 21 即可。

---

## 2. 目录结构

```text
AI求职助手系统/
├─ backend/                     Spring Boot 后端
│  ├─ src/main/java/            业务代码
│  ├─ src/main/resources/       application.yml
│  ├─ scripts/http-integration.mjs  HTTP 全链路联调脚本
│  └─ pom.xml
├─ frontend/                    Vue 3 前端
│  ├─ src/                      页面、路由、接口封装
│  ├─ scripts/ui-smoke.mjs      真实 Chrome 全路由冒烟脚本
│  └─ package.json
├─ sql/                         MySQL 初始化脚本
│  ├─ 01-create-database.sql
│  ├─ 02-create-tables.sql
│  ├─ 03-init-data.sql
│  └─ 04-cleanup.sql
└─ README.md
```

---

## 3. 环境准备

### 3.1 必需软件

- JDK 21
- Maven 3.9+
- MySQL 5.7+
- Redis
- Node.js 20+，npm 10+
- IntelliJ IDEA（后端）
- VS Code（前端）

### 3.2 初始化 MySQL

使用 Navicat 或 MySQL 客户端，按顺序执行：

1. `sql/01-create-database.sql`
2. `sql/02-create-tables.sql`
3. `sql/03-init-data.sql`

`03-init-data.sql` 会写入：

- 13 项系统配置；
- 每日默认 AI 配额 `50`；
- 管理员、演示用户、面试演示用户的丰富演示数据。

默认数据库名为 `ai_jobs`，字符集为 `utf8mb4`。

`04-cleanup.sql` 是数据清理和过期内容维护脚本，不是首次初始化必需步骤。

### 3.3 启动 Redis

确保 Redis 监听 `127.0.0.1:6379`。Windows 服务方式可执行：

```text
Start-Service Redis
```

项目在 Redis 临时不可用时会使用单进程内存计数完成课堂演示，
但正式运行和多实例部署必须保持 Redis 可用。

---

## 4. 后端启动（IntelliJ IDEA）

1. 打开 IDEA，选择 `Open`。
2. 选择 `AI求职助手系统/backend/pom.xml`，以 Maven 项目打开。
3. Project SDK 选择 JDK 21，Maven Runner 使用 JDK 21。
4. 等待 Maven 依赖加载完成。
5. 打开 `src/main/java/com/qznu/jobhelper/JobHelperApplication.java`。
6. 点击 `Run 'JobHelperApplication'`。
7. 控制台出现 `Tomcat started on port 8080` 即启动成功。

后端地址：`http://127.0.0.1:8080`

编译或测试通过 IDEA 右侧 Maven 面板完成：

- `Lifecycle -> compile`
- `Lifecycle -> package`
- `Lifecycle -> test`

默认开发连接：

```text
jdbc:mysql://127.0.0.1:3306/ai_jobs
username: root
password: 123456
```

若本机账号不同，在 IDEA Run Configuration 的 Environment variables 中覆盖。

---

## 5. 前端启动（VS Code）

1. 用 VS Code 打开 `AI求职助手系统/frontend`。
2. 打开集成终端。
3. 安装依赖并启动：

```text
npm.cmd install
npm.cmd run dev
```

浏览器访问：`http://127.0.0.1:5173`

Vite 已将 `/api` 代理到 `http://127.0.0.1:8080`，浏览器不需要额外处理 CORS。

生产构建：

```text
npm.cmd run build
```

构建产物位于 `frontend/dist`。当前主包超过 500 KB 只会产生 Vite 提示，
不影响构建和运行。

---

## 6. 环境变量

后端通过环境变量覆盖默认配置。IDEA 中可在 Run Configuration 的
`Environment variables` 填写。

### 6.1 数据库与 Redis

| 变量 | 默认值 | 说明 |
|---|---|---|
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/ai_jobs?...` | MySQL 连接串 |
| `DB_USERNAME` | `root` | 数据库账号 |
| `DB_PASSWORD` | `123456` | 数据库密码 |
| `REDIS_HOST` | `127.0.0.1` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_DATABASE` | `0` | Redis 数据库编号 |
| `JWT_SECRET` | 开发默认值 | 部署时必须覆盖，至少 32 字符 |

### 6.2 AI 配置

| 变量 | 默认值 | 说明 |
|---|---|---|
| `AI_PROVIDER` | `deepseek` | 供应商标识 |
| `AI_BASE_URL` | `https://api.deepseek.com` | OpenAI 兼容接口地址 |
| `AI_API_KEY` | 空 | API Key |
| `AI_MODEL` | `deepseek-chat` | 模型名 |
| `AI_TIMEOUT_MS` | `60000` | 单次调用超时 |
| `AI_MOCK_MODE` | `auto` | `auto`：有 Key 调真实模型，无 Key 用本地 Mock |

课堂演示建议保持 `AI_MOCK_MODE=auto` 且不配置 Key，所有 AI 流程仍可完整走通。

接入真实模型时，在 IDEA 中设置：

```text
AI_BASE_URL=https://api.deepseek.com
AI_API_KEY=你的Key
AI_MODEL=deepseek-chat
AI_MOCK_MODE=auto
```

AI 调用失败会自动降级到本地演示结果，避免外部网络中断整个业务流程。

---

## 7. 演示账号

| 账号 | 密码 | 角色 | 用途 |
|---|---|---|---|
| `demo` | `123456` | 普通用户 | 完整演示数据 |
| `chenwei` | `123456` | 普通用户 | 第二条面试/投递演示线 |
| `admin` | `admin123` | 管理员 | 管理端统计、用户、日志和配置 |

---

## 8. 主要页面

### 普通用户

| 路由 | 功能 |
|---|---|
| `/dashboard` | 工作台、配额、任务通知、待办 |
| `/abilities` | 六类能力条目 CRUD、AI 解析确认、启停 |
| `/resumes` | 上传解析、能力库生成、版本快照、条目合成 |
| `/diagnosis` | 四维简历诊断、逐条改写、采纳与合成 |
| `/deliveries` | 投递看板、六态状态机、JD 解析、待办 |
| `/interviews` | 模拟面试列表、出题、逐题点评、复盘、归档 |
| `/profile` | 个人资料、改密、注销清空数据 |

### 管理端

| 路由 | 功能 |
|---|---|
| `/admin` | 用户、诊断、AI 调用、成本趋势统计 |
| `/admin/users` | 用户检索、配额覆盖、禁用/启用 |
| `/admin/diagnoses` | 跨用户诊断元数据 |
| `/admin/ai-logs` | AI 调用元数据 |
| `/admin/logs` | 九类系统审计日志 |
| `/admin/configs` | 13 项系统配置 |

管理端只返回治理所需元数据，不返回简历正文、JD、报告正文、Prompt、
模型原始回答、密码或 JWT。

---

## 9. 关键业务规则

- 所有用户数据归属都从 JWT 上下文推导，客户端不能通过 `userId` 越权。
- 业务失败统一返回 HTTP 200 + `code`；401/403/500 使用真实 HTTP 状态。
- 每日默认 AI 配额为 `50`，管理员可对单个用户覆盖。
- 上传解析、简历生成、诊断、面试出题走异步任务，前端轮询任务状态。
- 投递六态固定为：

```text
pending -> applied | rejected
applied -> exam | rejected
exam -> interview | rejected
interview -> exam | offer | rejected
offer/rejected: 仅同值 no-op
```

- 面试逐题点评使用 `requestId` 业务幂等：
  相同 UUID + 相同载荷回放结果，不重复扣配额；
  相同 UUID + 不同载荷返回 `code=409`。
- 逻辑删除重复请求按成功 no-op 处理。
- 简历版本是快照，后续能力库变化不会回写历史版本。

---

## 10. 验证命令

### 10.1 前端生产构建

在 VS Code 的 `frontend` 终端：

```text
npm.cmd run build
```

### 10.2 浏览器全路由冒烟

要求后端、前端开发服务器都已启动：

```text
npm.cmd run smoke:ui
```

脚本使用本机 Chrome DevTools Protocol：

- 以 `demo` 和 `admin` 走真实登录表单；
- 检查全部普通用户和管理端路由；
- 监听浏览器异常和失败的 `/api` 请求；
- 输出每个页面的通过状态。

### 10.3 后端 HTTP 全链路联调

要求后端已启动、MySQL 和 Redis 可用：

```text
node backend\scripts\http-integration.mjs
```

脚本会创建唯一临时账号并验证：

- 注册、登录、资料与配额；
- 能力库 CRUD、确认、启停和重复删除；
- 简历生成、上传解析、版本保存；
- 四维诊断、建议采纳、诊断合成；
- 投递六态矩阵、JD 解析、待办、越权读取；
- 面试出题、逐题点评、请求幂等、复盘、归档；
- 管理端统计、用户、配额、禁用/启用、日志和配置；
- 改密、注销、令牌失效。

联调账号使用唯一用户名，不会修改 `demo`、`chenwei` 或 `admin` 的业务数据。

---

## 11. 常见问题

### 11.1 后端启动时报数据库连接失败

检查 MySQL 服务和 `ai_jobs` 数据库是否已创建，确认 `DB_USERNAME`、
`DB_PASSWORD` 与本机一致。

### 11.2 Redis 未启动

业务仍可借助内存配额降级运行，但建议启动 Redis。检查：

```text
D:\Redis\redis-cli.exe ping
```

应返回 `PONG`。

### 11.3 页面显示登录失效

JWT 默认有效 7 天。账号被禁用、注销或数据库用户行不存在时，
下一次请求会立即返回 403，前端会回到登录页。

### 11.4 AI 没有调用真实模型

检查 `AI_API_KEY` 是否为空、`AI_MOCK_MODE` 是否为 `always`。
没有 Key 时项目会使用本地 Mock，这是预期行为。

### 11.5 上传简历后没有立即生成条目

上传解析是异步任务，前端会自动轮询。解析出的 AI 条目先进入
`pending`，需要在能力库逐条确认后，才能参与简历合成和模拟面试。
