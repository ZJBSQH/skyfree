# SprintbootSky

Freesky 的 Spring Boot 后端（Java 21 + Spring Boot 4.0.7）。负责用户认证、小说创作数据的 MySQL 持久化、Redis 热点缓存，并代理 AgentSky（Python 多 Agent 写作服务）的创作请求。Java 不直接调用 LLM。

## 技术栈

| 能力 | 技术 |
|------|------|
| Web | Spring Web MVC（Boot 4：`spring-boot-starter-webmvc`） |
| 安全 | Spring Security + JWT（jjwt 0.12.6），BCrypt 密码哈希，无状态 |
| 持久化 | MyBatis-Plus 3.5.17（`mybatis-plus-spring-boot4-starter`）+ MySQL 8 |
| 迁移 | Flyway 11（`V1__init_schema.sql`） |
| 缓存 | Spring Data Redis（热点缓存，MySQL 为最终事实来源） |
| 校验 | Spring Validation |
| 测试 | JUnit 5 + Mockito + Spring MVC Test；Testcontainers（MySQL 集成测试） |

## 目录结构（DDD 分层）

```text
src/main/java/com/freesky/sprintbootsky
├── interfaces        HTTP 接口：Controller、DTO、参数校验、全局异常处理
├── application       用例编排：Auth/Novel Application Service、事务边界
│   └── port/out      外部能力端口：AgentSkyGateway、NovelCachePort、TokenIssuer
├── domain            领域模型：User、NovelProject 聚合与仓储接口
└── infrastructure    技术实现：MyBatis-Plus、Redis、JWT、AgentSky HTTP 防腐层
```

依赖方向：`interfaces → application → domain`，`infrastructure` 实现 `domain` 仓储与 `application.port.out` 端口。Controller 不直接调用 Mapper；Application Service 不依赖 RedisTemplate/AgentSkyClient/JwtTokenProvider 等基础设施实现。

## 环境变量配置

所有敏感配置都支持环境变量覆盖，默认值面向本地开发：

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `MYSQL_URL` | `jdbc:mysql://127.0.0.1:3306/freesky?...` | MySQL JDBC 地址 |
| `MYSQL_USER` | `root` | MySQL 用户名 |
| `MYSQL_PASSWORD` | 空 | MySQL 密码 |
| `REDIS_HOST` / `REDIS_PORT` | `127.0.0.1` / `6379` | Redis 地址 |
| `JWT_SECRET` | 无默认值，**必填** | JWT HS256 签名密钥（≥32 字节）；缺失时启动失败 |
| `JWT_EXPIRES_IN_SECONDS` | `86400` | Token 有效期（秒） |
| `AGENTSKY_BASE_URL` | `http://127.0.0.1:8765` | AgentSky 服务地址 |
| `AGENTSKY_API_TOKEN` | 空 | 调用 AgentSky 的服务 Token |
| `AGENTSKY_CONNECT_TIMEOUT` / `AGENTSKY_READ_TIMEOUT` | `5s` / `10m` | 连接 / 读取超时（读取允许长任务但不能无限等待） |
| `AGENTSKY_DEFAULT_MODEL` | `deepseek-chat` | token_usage 缺失时的默认模型名 |

示例（PowerShell）：

```powershell
$env:JWT_SECRET = "请替换为至少32字节的随机密钥"
$env:MYSQL_PASSWORD = "你的密码"
$env:AGENTSKY_API_TOKEN = "与 AgentSky 端 AGENTSKY_API_TOKEN 一致"
.\mvnw.cmd spring-boot:run
```

启动时 Flyway 会自动在 MySQL 中创建全部表结构（`users`、`novel_project`、`chapter`、`character_profile`、`run_log`、`token_usage`），无需手工建表。

## 接口一览

公开接口（无需 JWT）：

- `GET /api/health` — 本服务健康检查
- `GET /api/hello` — 前端连通性检查
- `GET /api/agent/health` — AgentSky 健康检查（`{"status":"ok","service":"agentsky"}`；不可用时返回 503 与 `error` 字段）
- `POST /api/auth/register` — 注册，请求 `{username, email, password}`，响应 `{token, user:{id, username, email}}`；邮箱唯一（规范化 + 数据库唯一约束，重复返回 409）
- `POST /api/auth/login` — 登录，请求 `{email, password}`，响应同注册；密码错误返回 401

需要 JWT（`Authorization: Bearer <token>`）：

- `GET /api/auth/me` — 当前用户
- `POST /api/novels` — 创建小说，请求 `{"idea": "创作灵感"}`（必填、trim 后非空、≤2000 字符）

`/api/novels` 响应与 Vue 前端契约严格一致（成功/失败/校验失败都返回完整结构）：

```json
{
  "success": true,
  "logs": ["[WriterAgent] Drafted chapter"],
  "result": {
    "completed_chapters": ["正文"],
    "characters": [{"name": "...", "role_type": "...", "relationships": [{"name": "...", "relation": "...", "dynamic": "..."}]}],
    "world_settings": [],
    "plot_outline": [],
    "review_round": 1
  },
  "error": "",
  "token_usage": {"input_tokens": 0, "output_tokens": 0, "total_tokens": 0, "call_count": 0, "cost_yuan": 0, "model": "deepseek-chat"}
}
```

创作流程采用“三个短事务”边界：先落 `CREATING` 记录并提交 → 无事务调用 AgentSky → 成功则多表整体落库并置 `COMPLETED`（任一表失败整体回滚），失败则先提交 `FAILED` 再返回 → 事务提交后刷新 Redis。AgentSky 的 429/连接失败/超时/5xx/业务失败全部转换为稳定安全的中文失败文案，traceback、密钥、内部异常绝不返回前端或写入数据库。

## 运行测试

```powershell
# 默认测试：单元测试 + MockMvc 切片测试，不依赖 MySQL/Redis/Docker/AgentSky
.\mvnw.cmd test

# MySQL 集成测试（需要 Docker，Testcontainers 启动 MySQL 8 容器，
# 验证 Flyway 迁移、真实 MySQL 方言、事务回滚、无事务调用 AgentSky 等）
.\mvnw.cmd test -Pit-mysql

# 无 Docker 环境可用本地 MySQL 运行集成测试（会真实执行 Flyway 迁移，请使用专用数据库）
.\mvnw.cmd test -Pit-mysql "-Dfreesky.it.mysql.url=jdbc:mysql://127.0.0.1:3306/freesky_it?createDatabaseIfNotExist=true" "-Dfreesky.it.mysql.username=root" "-Dfreesky.it.mysql.password="
```

> 说明：本机若未安装 Docker，默认 `mvn test` 完全不受影响；Testcontainers 集成测试通过 `@Tag("it")` 隔离，仅在 `-Pit-mysql` 下运行。不要用 H2 冒充 MySQL 方言兼容性验证。

## Vue 前端接入说明

当前 Vue 前端（`VueSky/`）尚未接入 JWT，因此**未修改的 Vue 无法直接完成受保护的创作流程**。后续需要：

1. 调用 `POST /api/auth/login` 或 `POST /api/auth/register` 获取 `token`；
2. 前端保存 token；
3. 调用 `POST /api/novels` 时携带 `Authorization: Bearer <token>` 请求头。

本次交付保证：路径、请求字段和响应 DTO 与 Vue 契约一致；公开接口无需 JWT；`/api/novels` 无 JWT 时返回 JSON 401（非 HTML）；有 JWT 时响应结构与 Vue 现有解析逻辑（`useNovelRun.ts`）完全兼容。
