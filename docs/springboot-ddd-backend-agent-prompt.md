# Spring Boot DDD Backend Agent Prompt

你是资深 Java/Spring Boot 后端工程师。请在现有 `SprintbootSky/` 项目中实现 Freesky 的 Spring Boot 后端。

目标：使用 DDD 分层结构生成一个清晰、规范、易维护的 Spring Boot 服务端，严格匹配当前 `VueSky/` 前端接口契约，并新增 JWT 认证、MySQL 持久化、Redis 热点缓存。

只修改 `SprintbootSky/`，不要修改 `VueSky/` 和 `AgentSky/`。

## 1. 固定技术框架

必须使用：

- Java 21
- Spring Boot
- Spring Web MVC
- Spring Validation
- Spring Security
- JWT
- MyBatis-Plus
- MySQL
- Flyway
- Spring Data Redis
- Lombok
- JUnit / Spring MVC Test
- Testcontainers（用于 MySQL 集成测试；若执行环境无 Docker，必须提供可独立运行的替代测试配置）

禁止使用：

- JPA / Hibernate
- 字段注入
- `@Autowired`
- `application.properties`

配置文件统一使用：

```text
src/main/resources/application.yml
```

依赖注入统一使用：

- `private final` 字段
- 构造器注入
- Lombok `@RequiredArgsConstructor`

允许使用常规 Spring 注解，例如 `@RestController`、`@Service`、`@Component`、`@Configuration`、`@Bean`、`@MapperScan`、`@Transactional`，但不要使用 `@Autowired`。

## 2. DDD 目录结构

请按以下结构组织代码：

```text
src/main/java/com/freesky/sprintbootsky
├── SprintbootSkyApplication.java
├── interfaces
│   ├── auth
│   │   ├── AuthController.java
│   │   └── dto
│   ├── novel
│   │   ├── NovelController.java
│   │   └── dto
│   ├── health
│   │   └── HealthController.java
│   └── web
│       ├── ApiErrorHandler.java
│       └── ErrorResponse.java
├── application
│   ├── auth
│   │   ├── AuthApplicationService.java
│   │   └── AuthResult.java
│   ├── novel
│   │   ├── NovelApplicationService.java
│   │   ├── NovelTransactionService.java
│   │   └── NovelCreationResult.java
│   └── port
│       └── out
│           ├── AgentSkyGateway.java
│           ├── NovelCachePort.java
│           └── TokenIssuer.java
├── domain
│   ├── user
│   │   ├── User.java
│   │   └── UserRepository.java
│   └── novel
│       ├── NovelProject.java
│       ├── Chapter.java
│       ├── CharacterProfile.java
│       ├── RunLog.java
│       ├── TokenUsage.java
│       └── NovelProjectRepository.java
├── infrastructure
│   ├── persistence
│   │   ├── entity
│   │   │   ├── UserEntity.java
│   │   │   ├── NovelProjectEntity.java
│   │   │   ├── ChapterEntity.java
│   │   │   ├── CharacterProfileEntity.java
│   │   │   ├── RunLogEntity.java
│   │   │   └── TokenUsageEntity.java
│   │   ├── mapper
│   │   │   ├── UserMapper.java
│   │   │   ├── NovelProjectMapper.java
│   │   │   ├── ChapterMapper.java
│   │   │   ├── CharacterProfileMapper.java
│   │   │   ├── RunLogMapper.java
│   │   │   └── TokenUsageMapper.java
│   │   └── repository
│   │       ├── MyBatisUserRepository.java
│   │       └── MyBatisNovelProjectRepository.java
│   ├── redis
│   │   └── RedisNovelCacheAdapter.java
│   ├── security
│   │   ├── SecurityConfig.java
│   │   ├── JwtTokenProvider.java
│   │   └── JwtAuthenticationFilter.java
│   └── agentsky
│       ├── AgentSkyGatewayAdapter.java
│       ├── AgentSkyClient.java
│       ├── dto
│       └── AgentSkyResponseSanitizer.java

src/main/resources
├── application.yml
└── db
    └── migration
        └── V1__init_schema.sql
```

分层要求：

- `interfaces`：只处理 HTTP 请求、响应 DTO、参数校验。
- `application`：编排用例，例如注册、登录、创建小说、调用 AgentSky、保存 MySQL、刷新 Redis。
- `domain`：表达核心业务模型和仓储接口。
- `infrastructure`：实现 MyBatis-Plus、Redis、JWT、AgentSky HTTP 调用。
- `application.port.out`：定义 Application 需要的外部能力，只添加有真实隔离价值的窄接口。
- `interfaces.web`：放置全局 HTTP 异常处理和 Web 错误响应。

依赖方向必须满足：

```text
interfaces ------> application ------> domain
                         |
                         v
                application.port.out
                         ^
                         |
                  infrastructure
```

- `interfaces` 可以依赖 Application Service 和 Application Result。
- `application` 可以依赖 Domain 和 `application.port.out`。
- `infrastructure` 实现 Domain Repository 与 Application Port，并负责 Spring Bean 装配。
- Domain Repository 继续放在 `domain`；AgentSky、Redis、JWT 签发等技术能力放在 `application.port.out`。

禁止：

- Controller 直接调用 Mapper。
- Controller 直接写业务逻辑。
- Application Service 直接依赖 MyBatis-Plus Mapper。
- Application Service 直接依赖 `AgentSkyClient`、`RedisTemplate`、`NovelCacheService`、`JwtTokenProvider` 等 Infrastructure 实现。
- Application Service 依赖 `interfaces.*.dto`，或直接返回 Controller Response DTO。
- Domain 层依赖 Spring Web、Redis、JWT、HTTP Client。
- Infrastructure 将 Entity、AgentSky 原始 DTO、`Map<String, Object>` 或底层异常泄漏给 Application。
- DTO、Entity、Domain Model 混用。

## 3. 必须匹配 Vue 前端的接口

当前 Vue 前端已经固定调用以下接口，路径和响应结构不能改。

### 3.1 AgentSky 健康检查

```http
GET /api/agent/health
```

公开接口，不需要 JWT。

成功返回：

```json
{
  "status": "ok",
  "service": "agentsky"
}
```

AgentSky 不可用时返回：

```json
{
  "status": "unavailable",
  "service": "agentsky",
  "error": "AgentSky is unavailable"
}
```

### 3.2 创建小说

```http
POST /api/novels
Authorization: Bearer <jwt>
Content-Type: application/json
```

请求体：

```json
{
  "idea": "用户输入的创作灵感"
}
```

校验规则：

- `idea` 必填。
- trim 后不能为空。
- 最大 2000 字符。

成功响应必须保持 Vue 兼容结构，字段名不能改：

```json
{
  "success": true,
  "logs": ["[WriterAgent] Drafted chapter"],
  "result": {
    "completed_chapters": ["正文"],
    "characters": [
      {
        "name": "角色名",
        "role_type": "protagonist",
        "appearance": "",
        "personality": "",
        "background": "",
        "ability": "",
        "motivation": "",
        "relationships": [
          {
            "name": "其他角色",
            "relation": "师徒",
            "dynamic": "变化"
          }
        ]
      }
    ],
    "world_settings": [],
    "plot_outline": [],
    "review_round": 1
  },
  "token_usage": {
    "input_tokens": 0,
    "output_tokens": 0,
    "total_tokens": 0,
    "call_count": 0,
    "cost_yuan": 0,
    "model": "deepseek-chat"
  }
}
```

失败响应也必须保持 Vue 可解析：

```json
{
  "success": false,
  "logs": [],
  "result": {},
  "error": "创作流程执行失败，请稍后重试",
  "token_usage": {
    "input_tokens": 0,
    "output_tokens": 0,
    "total_tokens": 0,
    "call_count": 0,
    "cost_yuan": 0,
    "model": "deepseek-chat"
  }
}
```

契约补充：

- `/api/novels` 的参数校验失败、AgentSky 业务失败、AgentSky 网络失败，都必须返回包含 `success/logs/result/error/token_usage` 的小说响应结构。
- 未认证的 401 响应可以使用统一认证错误 JSON，但不能返回 Spring Security 默认 HTML。
- `token_usage` 在成功和失败时都必须包含完整字段，未知值使用 0 或配置的默认模型名，不能返回空对象。
- `logs` 返回和落库前都必须经过清洗，不能包含 traceback、密钥、请求头或底层异常详情。

## 4. JWT 认证接口

### 4.1 注册

```http
POST /api/auth/register
```

请求：

```json
{
  "username": "zheng",
  "email": "zheng@example.com",
  "password": "password123"
}
```

响应：

```json
{
  "token": "...",
  "user": {
    "id": 1,
    "username": "zheng",
    "email": "zheng@example.com"
  }
}
```

要求：

- 邮箱唯一。
- 密码使用 BCrypt。
- 重复邮箱返回 409。
- 不返回 password 或 passwordHash。
- 注册前对 email 执行 trim 和小写规范化，并依赖数据库唯一约束处理并发重复注册。

### 4.2 登录

```http
POST /api/auth/login
```

请求：

```json
{
  "email": "zheng@example.com",
  "password": "password123"
}
```

响应同注册。

要求：

- 登录成功返回 token 和 user。
- 密码错误返回 401。
- 不返回 password 或 passwordHash。

### 4.3 当前用户

```http
GET /api/auth/me
Authorization: Bearer <jwt>
```

响应：

```json
{
  "id": 1,
  "username": "zheng",
  "email": "zheng@example.com"
}
```

## 5. 安全规则

公开接口：

- `GET /api/health`
- `GET /api/hello`
- `GET /api/agent/health`
- `POST /api/auth/register`
- `POST /api/auth/login`

需要 JWT：

- `GET /api/auth/me`
- `POST /api/novels`
- 后续新增业务接口默认需要 JWT。

要求：

- 使用 Spring Security filter chain。
- JWT 从 `Authorization: Bearer <token>` 读取。
- JWT 中至少包含 userId 和 email。
- 后端保持 stateless，不使用 session。
- 认证失败统一返回 JSON，不返回默认 HTML 错误页。
- `JwtAuthenticationFilter` 将经过验证的 `Long userId` 放入 Authentication principal。
- Controller 使用 `@AuthenticationPrincipal Long userId` 取得身份，并向 Application Service 传递 `Long userId`。
- Application 和 Domain 不能感知 JWT、Bearer Token、SecurityContext 或 Spring Security principal 类型。
- 不创建位于 `infrastructure.security` 且被 Controller 直接依赖的 `CurrentUser` 类型。
- `AuthApplicationService` 通过 `TokenIssuer` 生成 token，`JwtTokenProvider` 实现该 Port。
- BCrypt 可直接通过 Spring Security 提供的 `PasswordEncoder` 接口注入，不需要额外包装一个只有 encode/matches 的自定义 Adapter。

## 5.1 Interfaces DTO 与 Application Result

- Request/Response DTO 只放在 `interfaces`。
- Application Service 接收基本类型或 Application Command，返回 Application Result。
- 例如 `NovelApplicationService#createNovel` 返回 `NovelCreationResult`，不能返回 `CreateNovelResponse`。
- Controller 将 `NovelCreationResult` 转换为 `CreateNovelResponse`。当前规模优先使用 Response DTO 的静态 `from(...)` 方法，不强制增加独立 Assembler。
- Auth 同样使用 `AuthResult`，禁止把 `AuthResponse` 传入 Application 或 Domain。

## 6. MyBatis-Plus 持久化设计

使用 MyBatis-Plus + MySQL，禁止使用 JPA。

### 6.1 表和实体

至少实现以下表对应的 Entity 和 Mapper。

#### users

字段：

- id
- username
- email
- password_hash
- created_at
- updated_at

#### novel_project

字段：

- id
- user_id
- idea
- title
- status：CREATING / COMPLETED / FAILED
- review_round
- world_settings_json
- plot_outline_json
- error_message
- created_at
- updated_at

#### chapter

字段：

- id
- novel_project_id
- chapter_index
- content
- created_at

#### character_profile

字段：

- id
- novel_project_id
- name
- role_type
- appearance
- personality
- background
- ability
- motivation
- relationships_json
- created_at

#### run_log

字段：

- id
- novel_project_id
- sequence
- agent
- message
- created_at

#### token_usage

字段：

- id
- novel_project_id
- input_tokens
- output_tokens
- total_tokens
- call_count
- cost_yuan
- model
- created_at

### 6.2 MyBatis-Plus 规范

Entity 示例风格：

```java
@Data
@TableName("users")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;
    private String email;
    private String passwordHash;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

Mapper 示例风格：

```java
public interface UserMapper extends BaseMapper<UserEntity> {
}
```

要求：

- 使用 `@MapperScan` 统一扫描 mapper 包。
- 常规 CRUD 使用 `BaseMapper`。
- 条件查询使用 `LambdaQueryWrapper`。
- 禁止拼接 SQL 字符串。
- 复杂 JSON 字段可先用 TEXT 保存，例如 `world_settings_json`、`plot_outline_json`、`relationships_json`。
- 创建小说成功后必须保存 NovelProject、Chapter、CharacterProfile、RunLog、TokenUsage。
- 创作失败时也保存 NovelProject 失败记录和安全后的错误信息。

### 6.3 聚合与 Repository 边界

- `NovelProject` 是当前小说创建用例的 Aggregate Root。
- `Chapter`、`CharacterProfile`、`RunLog`、`TokenUsage` 当前均属于 NovelProject 聚合内部，没有独立生命周期。
- 聚合内部不代表查询时必须加载全部数据；后续列表和摘要查询可以使用专用只读查询，避免加载大正文和全部日志。
- `NovelProjectRepository` 是 Application 面向的聚合仓储接口。
- `MyBatisNovelProjectRepository` 实现该接口，并组合 `NovelProjectMapper`、`ChapterMapper`、`CharacterProfileMapper`、`RunLogMapper`、`TokenUsageMapper` 完成聚合持久化。
- Application Service 不能为了多表写入而直接注入多个 Mapper，也不要为当前没有独立用例的子表强行创建五个 Domain Repository。
- 如果未来出现多次生成、重试历史、独立运行统计，再考虑将 `NovelGenerationRun` 拆成独立聚合；本次不要提前实现。

### 6.4 事务边界

严禁在包含 AgentSky HTTP 调用的方法或类上使用覆盖整个流程的 `@Transactional`。创建小说必须拆成以下边界：

```text
校验用户与请求
  ↓
短事务：创建 NovelProject(status=CREATING)，提交并取得 novelId
  ↓
无数据库事务：调用 AgentSky、解析并清洗响应
  ↓
短事务：保存全部结果并更新为 COMPLETED，或保存安全错误并更新为 FAILED
  ↓
事务提交后：刷新 Redis
  ↓
返回 Vue 兼容响应
```

- MySQL 多表写入必须在同一个短事务中完成，任何一张业务表写入失败都应回滚该次最终状态更新。
- Redis 不参与 MySQL 事务，Redis 失败不得回滚已经提交的 MySQL 数据。
- 推荐由 `NovelTransactionService` 提供公开的 `@Transactional` 方法，例如创建初始记录、完成创作、标记失败。
- 不要在同一个类中通过 `this.xxx()` 调用 `@Transactional` 方法，因为 Spring 代理下的自调用不会开启预期事务。
- 不要执行“事务内保存 FAILED 后继续抛 RuntimeException”的流程，Spring 默认会回滚 FAILED 记录。
- 对可预期的 AgentSky 失败采用业务失败结果：清洗错误、事务提交 FAILED、然后返回 `NovelCreationResult.failure(...)`。
- `REQUIRES_NEW` 仅用于必须继续向外抛出的不可恢复系统异常，不作为正常 AgentSky 失败流程的默认方案。

### 6.5 数据库迁移与约束

- 使用 Flyway 管理表结构，至少提供 `V1__init_schema.sql`，不能依赖手工建表或 MyBatis 自动建表。
- `users.email` 必须有数据库唯一索引；注册时先规范化邮箱，并处理并发下的 `DuplicateKeyException`，统一返回 409。
- 为所有外键和常用查询字段建立必要索引，例如 `novel_project.user_id`、各子表的 `novel_project_id`、`run_log(novel_project_id, sequence)`。
- 明确外键删除策略，不允许产生无主章节、角色、日志或 TokenUsage。

## 7. Redis 热点缓存

Redis 只做热点缓存，MySQL 是最终事实来源。

Application 只能依赖 `NovelCachePort`，由 `RedisNovelCacheAdapter` 使用 Spring Data Redis 实现。Port 必须使用业务语义方法，例如：

```text
cacheLatestNovel(userId, summary)
cacheNovelSummary(novelId, summary)
evictRecentProjects(userId)
```

不要设计通用的 `CachePort<K, V>`，也不要把 Redis key、`RedisTemplate` 或 Redis 序列化类型泄漏到 Application。

缓存 key：

```text
freesky:user:{userId}:recent-projects
freesky:user:{userId}:latest-novel
freesky:novel:{novelId}:summary
```

要求：

- TTL 10-30 分钟。
- 创建新作品后刷新或删除 `recent-projects`。
- 成功创作后写入 `latest-novel` 和 `novel summary`。
- Redis 异常不应该导致 `/api/novels` 失败，只记录日志。
- 所有缓存写入或失效必须发生在 MySQL 事务成功提交之后。
- 缓存内容使用专用摘要 DTO，不缓存 Persistence Entity 或完整 Domain Aggregate。
- 当前尚无项目列表和详情读取接口，不要为未被读取的缓存实现复杂 cache-aside 查询体系；保留 key 和失效规则，为后续读取接口扩展即可。

## 8. AgentSky 调用

Application 只依赖 `AgentSkyGateway`，不能直接依赖 `AgentSkyClient`。调用链必须是：

```text
NovelApplicationService
        ↓
AgentSkyGateway
        ↑
AgentSkyGatewayAdapter
        ↓
AgentSkyClient
```

- `AgentSkyClient` 只负责 HTTP 通信，属于 Infrastructure 内部实现。
- `AgentSkyGatewayAdapter` 是防腐层，负责将 AgentSky 原始响应转换为类型明确、安全的 Application Port 模型。
- `AgentSkyResponseSanitizer` 只能由 Adapter 使用，不能被 Application Service 直接注入。
- AgentSky 的原始 DTO、`Map<String, Object>`、HTTP 状态和底层异常不能越过 Adapter 边界。

Spring 端调用已有 Python AgentSky：

```http
POST {agentsky.base-url}/api/create
X-AgentSky-Token: {agentsky.api-token}
Content-Type: application/json
```

请求：

```json
{
  "idea": "..."
}
```

AgentSky 原始响应可能包含：

- success
- logs
- result
- error
- error_code
- token_usage

要求：

- Java 不直接调用 LLM。
- Java 只负责认证、业务持久化、缓存、代理 AgentSky。
- AgentSky token 缺失时返回安全错误。
- AgentSky 失败时不要把 traceback、密钥、内部异常返回前端。
- 同时清洗返回前端和写入 MySQL 的 `error` 与 `logs`；限制日志条数、单条长度和总长度。
- 配置明确的连接超时和读取超时。读取超时应允许长时间 Agent/LLM 任务，但不能无限等待。
- 将 429、连接失败、超时和 5xx 转换为稳定的 Gateway 失败类型，不能把 RestClient 异常传到 Application。
- 保留错误映射：
  - `REVIEW_NOT_APPROVED` -> `正文在最大审核轮次内未通过，请调整创作灵感后重试`
  - `MODEL_INIT_FAILED` -> `模型服务初始化失败，请检查配置后重试`
  - `WORKFLOW_INIT_FAILED` -> `创作工作流初始化失败，请稍后重试`
  - 其他 -> `创作流程执行失败，请稍后重试`

## 9. application.yml 配置

必须使用 `application.yml`。

示例：

```yaml
server:
  address: 127.0.0.1
  port: 8080

spring:
  datasource:
    url: ${MYSQL_URL:jdbc:mysql://127.0.0.1:3306/freesky?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai}
    username: ${MYSQL_USER:root}
    password: ${MYSQL_PASSWORD:}
    driver-class-name: com.mysql.cj.jdbc.Driver

  flyway:
    enabled: true
    locations: classpath:db/migration

  data:
    redis:
      host: ${REDIS_HOST:127.0.0.1}
      port: ${REDIS_PORT:6379}

mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
  global-config:
    db-config:
      id-type: auto

security:
  jwt:
    secret: ${JWT_SECRET}
    expires-in-seconds: ${JWT_EXPIRES_IN_SECONDS:86400}

agentsky:
  base-url: ${AGENTSKY_BASE_URL:http://127.0.0.1:8765}
  api-token: ${AGENTSKY_API_TOKEN:}
  connect-timeout: ${AGENTSKY_CONNECT_TIMEOUT:5s}
  read-timeout: ${AGENTSKY_READ_TIMEOUT:10m}
```

要求：

- 所有敏感配置支持环境变量覆盖。
- JWT secret 不允许硬编码在 Java 代码中。
- 非 test 环境缺少 JWT secret 时必须启动失败；不要提供可能误用于生产的默认 secret。
- 测试配置使用独立的测试 secret，不读取开发者本机敏感变量。
- MySQL、Redis、AgentSky 地址都必须可配置。
- 使用类型安全的 `@ConfigurationProperties` 管理 JWT 和 AgentSky 配置，避免在业务类构造器中散布 `@Value`。
- 当前项目使用 Spring Boot 4.0.7；必须选择明确兼容 Spring Boot 4 的 MyBatis-Plus starter 和版本，不能直接照搬 Boot 3 starter。

## 10. 代码规范

请严格遵循：

- 类名、方法名表达职责。
- Controller 保持短小，只负责 HTTP 输入输出。
- Application Service 负责业务用例编排。
- Domain 层不依赖 Web、Redis、JWT、HTTP Client。
- DTO、Entity、Domain Model 分离。
- 不要用 `Map<String, Object>` 贯穿系统；只允许在 AgentSky 动态响应隔离层使用。
- AgentSky 动态响应优先在 Infrastructure 中使用专用原始 DTO 或 `JsonNode` 解析，再转换为类型明确的 Port 模型。
- 对 Vue 固定响应定义明确 DTO。
- 异常统一通过全局异常处理返回 JSON。
- 时间字段统一使用 `LocalDateTime`。
- 成本字段使用 `BigDecimal`。
- 使用 Lombok 简化样板代码，但不要滥用。
- Persistence Entity 可以使用 Lombok `@Data`；Domain Model 优先使用 `@Getter`、构造方法和表达状态迁移的业务方法，避免 `@Data` 暴露任意 setter 破坏聚合约束。
- 所有依赖使用构造器注入。
- 不使用字段注入。
- 不使用 `@Autowired`。
- JSON 字段统一使用 Jackson `ObjectMapper` 或 TypeHandler 处理，禁止手工拼接 JSON 字符串；序列化失败必须让对应 MySQL 短事务回滚。
- `NovelProject` 的状态只能通过明确方法转换，例如 CREATING 到 COMPLETED 或 FAILED，禁止任意修改状态。
- 不提前增加 Factory、Specification、Domain Event、CQRS、Event Sourcing、Command Bus 或每个方法一个 UseCase 接口。

## 11. 核心中文注释要求

请在核心位置添加简洁中文注释，解释业务意图和原因，不解释语法。

必须添加注释的位置：

- `SecurityConfig`
  - 说明公开接口和 JWT 保护接口的边界。
- `JwtAuthenticationFilter`
  - 说明如何从 Bearer Token 解析用户身份。
- `NovelApplicationService#createNovel`
  - 说明完整业务流程以及为什么 AgentSky HTTP 不属于数据库事务。
- `NovelTransactionService`
  - 说明 CREATING、COMPLETED、FAILED 三个短事务边界，以及失败记录必须先提交再返回。
- `MyBatisNovelProjectRepository`
  - 说明通过 Repository Adapter 隔离 MyBatis-Plus，避免业务层直接依赖 Mapper。
- `RedisNovelCacheAdapter`
  - 说明 Redis 是热点缓存，MySQL 才是最终事实来源。
- `AgentSkyGatewayAdapter`
  - 说明它是外部 AgentSky 与内部模型之间的防腐层。
- `AgentSkyResponseSanitizer`
  - 说明不能把 AgentSky 原始异常、traceback、密钥相关信息返回前端或写入业务数据库。

## 12. 测试要求

请补充或更新测试，至少覆盖以下场景。

### Auth

- 注册成功返回 token 和 user。
- 重复邮箱注册返回 409。
- 登录成功返回 token 和 user。
- 密码错误返回 401。
- `/api/auth/me` 无 token 返回 401。
- `/api/auth/me` 有合法 token 返回当前用户。

### Novel

- 无 JWT 调用 `/api/novels` 返回 401。
- 合法 JWT + 合法 idea 成功调用 AgentSky。
- idea 为空返回 400。
- idea 超过 2000 字符返回 400。
- AgentSky 成功时，响应字段与 Vue 兼容。
- AgentSky 失败时，响应仍包含 `success/logs/result/error/token_usage`。
- AgentSky 失败时 `token_usage` 仍包含全部固定字段，不返回空对象。
- AgentSky 敏感错误不会泄露到前端。
- AgentSky 敏感日志不会泄露到前端，也不会原样写入数据库。
- AgentSky 429、连接失败、读取超时和 5xx 都转换为稳定的安全失败响应。
- 创作成功后保存 NovelProject、Chapter、CharacterProfile、RunLog、TokenUsage。
- 调用 AgentSky 时没有处于活动 MySQL 事务中。
- 成功多表保存任一环节失败时，整个完成事务回滚，项目不能处于伪 COMPLETED 状态。
- AgentSky 失败后 FAILED 状态能够提交，不会因后续 RuntimeException 被回滚。

### Redis

- 创作成功后写入 latest novel cache。
- 创作成功后刷新或删除 recent projects cache。
- Redis 异常不影响 MySQL 持久化和接口成功返回。
- Redis 写入发生在 MySQL 提交之后。

### Persistence

- Flyway 能在空 MySQL 数据库创建全部表、约束和索引。
- 用户、项目、章节、角色、运行日志和 TokenUsage 可通过 Repository Adapter 持久化并读取。
- `users.email` 唯一约束在并发或重复写入时生效。

### 测试环境

- 单元测试通过 mock `AgentSkyGateway`、`NovelCachePort`、Repository 隔离外部系统。
- Repository 集成测试优先使用 Testcontainers MySQL，验证真实 MySQL 方言和 Flyway migration。
- 如果执行环境不能使用 Docker，必须提供明确的 Maven profile 或等价测试方案；不能让默认 `mvn test` 依赖开发者本机已启动的 MySQL、Redis 或 AgentSky。
- 不要使用 H2 测试结果冒充 MySQL 方言兼容性验证。

### Health

- `/api/health` 公开。
- `/api/hello` 公开。
- `/api/agent/health` 公开。

## 13. README 更新

请更新 `SprintbootSky` 相关说明，至少包含：

- 如何配置 MySQL。
- 如何配置 Redis。
- 如何配置 JWT。
- 如何配置 AgentSky。
- 如何运行测试。
- 当前 Vue 端后续需要：
  - 调用 `/api/auth/login` 或 `/api/auth/register`
  - 保存 token
  - 调用 `/api/novels` 时加 `Authorization: Bearer <token>`
- 明确说明：本次只保证路径、请求字段和响应 DTO 与 Vue 契约一致；由于当前 Vue 尚未发送 JWT，不能宣称未修改的 Vue 已可直接完成受保护的创作流程。

## 14. 完成标准

完成后必须保证：

- `mvn test` 通过。
- Spring Boot 可以启动。
- Flyway 可以在空数据库初始化全部表结构。
- 公开接口无需 JWT。
- `/api/novels` 无 JWT 返回 401。
- `/api/novels` 有 JWT 时响应结构兼容当前 Vue 前端。
- MySQL 保存创作数据。
- Redis 缓存热点数据。
- AgentSky HTTP 不占用长时间数据库事务，FAILED 记录可可靠提交。
- 核心代码有必要中文注释。
- 代码结构符合 DDD 分层。
- 后续容易继续扩展项目列表、章节详情、历史记录等功能。
