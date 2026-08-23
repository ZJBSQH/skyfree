# Freesky 多 Agent 小说创作工作台设计

## 目标与范围

将 `VueSky` 从现有创作界面重构为可真实连接后端的中文多 Agent 小说创作工作台。首屏直接提供账号、创作参数、服务状态与结果区域，不增加营销页面。

所有改动限制在 `VueSky`。继续使用 Vue 3、TypeScript、Vite、Vitest 与 `lucide-vue-next`，不新增大型 UI 或状态管理框架。

## 信息架构

桌面端采用三栏工作台：

- 左栏：品牌与工作台标题、创作灵感、类型/篇幅/风格、开始创作、服务状态、账号面板。
- 中栏：运行状态、正文与生成结果、剧情大纲；正文支持复制，本次结果支持清空。
- 右栏：Agent 协作日志、角色、世界观、Token 用量。

移动端按“创作输入 → 正文与剧情结果 → Agent 日志与详情”的顺序改为单列，所有主要功能保留在页面流中，不依赖抽屉才能访问。

视觉使用中性浅灰、暖白与克制的墨绿色强调色，卡片圆角最大 8px。按钮和状态只使用 `lucide-vue-next` 图标，不使用手写 SVG、装饰光球或大面积紫色渐变。

## 前端架构

### API 层

- `src/api/client.ts`：统一请求、Bearer Token 注入、JSON 解析、HTTP 状态映射与安全错误归一化。
- `src/api/auth.ts`：注册、登录、查询当前用户。
- `src/api/novels.ts`：Spring Boot 创作请求，以及仅开发环境可用的 AgentSky fallback。
- `src/types/api.ts`：认证、健康、创作结果、角色、关系、Token 用量与错误类型。

正式请求始终优先使用相对路径 `/api/*`，由 Vite 开发代理或生产反向代理转发到 Spring Boot。浏览器不保存 AgentSky 服务令牌。

开发 fallback 使用独立前缀 `/agentsky-api/*`。Vite 仅在本地开发服务器内把它代理到 `http://127.0.0.1:8765`，并从开发服务器进程的 `AGENTSKY_API_TOKEN` 注入 `X-AgentSky-Token`。该变量不使用 `VITE_` 前缀，因此不会进入浏览器包。fallback 只在 Spring Boot 不可达且运行环境为开发模式时启用；生产构建不会绕过 Spring Boot 认证结构。

### Composables

- `useAuth`：共享登录状态，负责注册、登录、`/me` 恢复会话、localStorage Token 持久化、退出以及 401 自动清理。
- `useNovelRun`：接收灵感及创作参数，合成为后端唯一支持的 `idea` 字段；维护 loading、error、result、logs 与 tokenUsage；阻止重复提交并支持清空。
- `useHealthCheck`：分别维护 Spring Boot 与 AgentSky 状态。Java 状态来自 `/api/health`；Agent 状态优先来自 Spring Boot `/api/agent/health`，仅在开发 fallback 探测时直连 Vite 代理。

共享状态保持轻量，使用 Vue 模块级 `ref`，不引入 Pinia。测试通过显式重置函数或依赖注入隔离状态，不将测试专用生命周期方法加入面向组件的公开 API。

## 组件边界

- `AppShell.vue`：三栏/单列布局与页面级组合。
- `AuthPanel.vue`：登录和注册切换、字段校验、提交状态与退出。
- `CreativeStudio.vue`：灵感与创作参数表单；未登录、空输入、运行中分别禁用或提示。
- `AgentTimeline.vue`：按顺序展示 Agent 日志并将已知 Agent 名称中文化。
- `NovelResult.vue`：运行状态、正文、多章节切换、复制与清空。
- `CharacterPanel.vue`：角色卡与人物关系。
- `WorldAndPlotPanel.vue`：兼容字符串或对象形态的世界观、剧情大纲展示。
- `TokenUsage.vue`：输入/输出/总 Token、调用次数、模型与人民币成本。
- `HealthBadge.vue`：单项服务的检查中、正常、异常状态。

`App.vue` 只负责初始化认证与健康检查，并把 composables 状态传给 `AppShell`。组件通过 props 和 emits 协作，不直接调用 fetch。

## 数据流

1. 页面加载时读取本地 Token；有 Token 时调用 `/api/auth/me`，401 则清理会话。
2. 同时检查 Java 与 Agent 服务状态，状态展示不阻断账号表单。
3. 未登录用户填写内容后点击创作，会看到中文登录引导，不发送请求。
4. 已登录用户提交时，前端校验灵感非空，将类型、篇幅和风格附加为简洁的创作要求，并调用 `/api/novels`，请求头包含 Bearer Token。
5. 成功响应被标准化后同时驱动正文、剧情、角色、世界观、日志和 Token 组件。
6. 失败响应保留后端安全的 `error` 文案；Token 用量即使在失败响应中存在也予以保留。
7. 401 会清理 Token 并回到登录态；清空操作只清除当前运行结果，不退出账号或清空创作输入。

## 错误与安全

统一错误映射：

- 网络失败：`服务暂时不可用，请确认后端已启动`
- 401：`登录已过期，请重新登录`
- 429：`请求过于频繁，请稍后再试`
- 其他状态：优先展示响应中的短 `error` 或 `message`；不可用时展示 `操作失败，请稍后重试`

前端不会展示 `traceback`、堆栈、多行内部异常、密钥、Authorization 值或 AgentSky 服务令牌。对后端错误文本做长度限制与敏感模式过滤。

## 测试与验收

Vitest 至少覆盖：

- `useAuth`：登录成功持久化、带 Token 恢复用户、401 清理、注册成功、退出。
- `useNovelRun`：创作参数合并、Bearer 请求、成功状态、重复提交保护、失败 Token 用量保留、401 会话失效、清空。
- 关键组件：未登录引导、空输入校验、loading 禁用、结果渲染、复制、移动端信息顺序。

实现采用测试先行：每个行为先写失败测试并确认失败原因，再写最小实现并运行相关测试。最终执行 `pnpm test` 和 `pnpm build`，同时检查 `git diff -- VueSky`，确认没有 `.env`、`node_modules` 或 `dist` 被纳入改动。

## 明确不做

- 不改动 `AgentSky` 或 `SprintbootSky`。
- 不新增 Pinia、Element Plus、Ant Design 或其他大型依赖。
- 不实现后端未提供的项目保存、历史记录、流式日志、刷新令牌或权限系统。
- 不在浏览器端保存或发送 AgentSky 服务密钥。
