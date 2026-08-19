# CLAUDE.md — 学习计划与进度

多Agent写作助手项目的 Python 学习路线与开发进度跟踪。

---

## 起点分析

| 已知 | 待学 |
|------|------|
| Java 精通 (Spring Boot 微服务) | Python 零基础 |
| MyBatis-Plus / MySQL | AI Agent 概念 |
| HTTP / REST / JWT | LLM API 调用 |
| Maven 多模块管理 | FastAPI 框架 |
| Git 版本控制 | SSE 流式输出 |

**学习原则**：以项目驱动，用到什么学什么，不学用不到的。

---

## 四阶段学习计划

### P1 — Python 基础 ✅ (目标: 3-5天, 实际: 3天)

```
状态: ✅ 已完成 (7/7, 100%)
```

| 主题 | Java对照 | 掌握标准 | 状态 |
|------|---------|---------|------|
| 变量与类型 | int→int, String→str, 动态类型 | 能写一个函数处理字符串 | ✅ |
| 控制流 | if/for/while 语法一致 | 写一个循环处理列表 | ✅ |
| 函数 | void→def, 没有public/private | 写带参数和返回值的函数 | ✅ |
| 列表与字典 | List<T>→list, Map→dict | 增删改查操作 | ✅ |
| 文件读写 | FileReader→open() | 读文件，写文件 | ✅ |
| 虚拟环境 | — | 创建 venv，pip install 包 | ✅ |
| pip 包管理 | Maven dependency | 理解 requirements.txt | ✅ |

**产出**：`p1/formatter.py` — 读取 ideas.txt，格式化灵感文本，写入 formatted_ideas.txt

---

### P2 — FastAPI 入门 ✅ (目标: 2-3天, 实际: 1天)

```
状态: ✅ 已完成 (5/5)
```

| 主题 | Java对照 | 掌握标准 | 状态 |
|------|---------|---------|------|
| 路由装饰器 | @GetMapping → @app.get() | 写 GET/POST/PUT/DELETE 全套 CRUD | ✅ |
| 请求体 Pydantic | @RequestBody DTO | 定义请求/响应模型 (3个模型) | ✅ |
| 路径参数 | @PathVariable | /sky/{sky_id} | ✅ |
| uvicorn 启动 | Tomcat 启动 | 本地跑起来 | ✅ |
| HTTP 测试 | Postman | Swagger /docs 在线调通 | ✅ |

**额外收获**：SQLAlchemy ORM + Depends 依赖注入 + 项目分层结构

**产出**：`p2/` — 完整 CRUD 服务 (databasep2 / modelp2 / schemasp2 / skyfree / main)

---

### P3 — openai SDK + 第一个 Agent ✅ (目标: 2-3天, 实际: 1天)

```
状态: ✅ 已完成 (5/5)
```

| 主题 | 掌握标准 | 状态 |
|------|---------|------|
| Chat Completions API | 理解 messages 结构 (system/user/assistant) | ✅ |
| System Prompt 设计 | 能写出让 LLM 扮演"大纲设计师"的 prompt | ✅ |
| Temperature / max_tokens | 理解参数含义并调参 | ✅ |
| 流式输出 (stream=True) | 逐 token 输出 | ✅ |
| Ollama 本地适配 | base_url 替换为 localhost:11434/v1 | ✅ |

**额外收获**：qwen3.5:2b thinking 模式的坑 — reasoning 字段 vs content 字段，openai SDK 不兼容 thinking 模型，改用 qwen2.5:3b

**产出**：`p3/outline_agent.py` — 输入灵感一句话，输出 30 章大纲，支持非流式/流式两种模式

---

### P4 — LangChain 入门 (目标: 2-3天)

```
状态: 🔄 进行中 (0/8)
```

**阶段 4a — 理论 (7.21, 3h)**
- 在 GitHub 读 LangChain 源码 + 官方教程，理解核心概念
- 重点：LCEL 原理、Agent 执行流程、Memory 机制

**阶段 4b — 实践 (7.21-7.22)**

| 主题 | 掌握标准 | 状态 |
|------|---------|------|
| LLM 封装 | ChatOpenAI 对接 Ollama，替代 raw openai SDK | ⬜ |
| Prompt Template | ChatPromptTemplate + MessagesPlaceholder | ⬜ |
| Chain (LCEL) | `prompt \| model \| parser` 管道式链 | ⬜ |
| Output Parser | StrOutputParser / PydanticOutputParser | ⬜ |
| Memory | InMemoryChatMessageHistory + RunnableWithMessageHistory | ⬜ |
| Agent | create_tool_calling_agent + AgentExecutor | ⬜ |
| Tools | @tool 装饰器，自定义工具函数 | ⬜ |
| 流式输出 | LangChain 的 stream 模式 | ⬜ |

**产出**：`p4/` — 用 LangChain 重写大纲 Agent，加上 Memory 和自定义 Tool

---

### P5 — 多 Agent 编排 (目标: 3-5天)

```
状态: ⬜ 未开始
```

| 主题 | 掌握标准 | 状态 |
|------|---------|------|
| Agent 状态传递 | 一个 TypedDict 贯穿所有 Agent，每个 Agent 读写自己的字段 | ⬜ |
| 错误重试 | LLM 调用失败时自动重试 3 次 | ⬜ |
| 逐步交互控制 | 每步生成完暂停，等用户确认信号再继续 | ⬜ |
| 上下文拼接 | 写第N章时，把前N-1章摘要 + 人物状态拼进 prompt | ⬜ |
| LangGraph 编排 | 用 StateGraph 编排多 Agent 协作流程 | ⬜ |

**产出**：主编 Agent → 大纲 → 人物 → 世界观 → 写稿 全链路。

---

### P6 — Java ↔ Python 联调 (目标: 2-3天)

```
状态: ⬜ 未开始
```

| 主题 | 掌握标准 | 状态 |
|------|---------|------|
| WebClient 调 Python | Java 发 POST 到 Python，接收 JSON | ⬜ |
| SSE 流式消费 | Java 读 SSE 流，逐条转发前端 | ⬜ |
| 超时与容错 | Python 挂了不影响 Java 主流程 | ⬜ |

**产出**：前端 → Java → Python → Agent → SSE 响应 完整链路。

---

## 每日学习节奏

```
上午 (60-90min): Python 学习
  30min 看教程 / 读文档
  30-60min 写练习代码

下午 (60-90min): 项目开发
  按周计划推进 Freesky 项目
```

---

## 周进度记录

### Week 1 (7.16 - 7.19)
- P1 完成度: 100% ✅ — formatter.py (读/写文件 + 格式化)
- P2 完成度: 0%
- 额外: 力扣 Phase 1 10/10 ✅

### Week 2 (7.19 - )
- P2 完成度: 100% ✅ — 完整 CRUD 服务 (database/models/schemas/router/main)
- P3 完成度: 100% ✅ — 大纲 Agent (Ollama qwen2.5:3b + openai SDK)

---

## 学习资源

| 资源 | 用途 | 优先级 |
|------|------|--------|
| [Python 官方教程](https://docs.python.org/zh-cn/3/tutorial/) | Python 语法基础 | ⭐⭐⭐ |
| [FastAPI 官方文档](https://fastapi.tiangolo.com/zh/) | FastAPI 入门 | ⭐⭐⭐ |
| [OpenAI Python SDK](https://github.com/openai/openai-python) | Agent 调用 LLM | ⭐⭐⭐ |
| [DeepSeek API 文档](https://platform.deepseek.com/api-docs/) | 国产模型对接 | ⭐⭐⭐ |
| [LangChain 官方文档](https://python.langchain.com/docs/) | LLM 应用框架 | ⭐⭐⭐ |
| [LangGraph 官方文档](https://langchain-ai.github.io/langgraph/) | 多 Agent 编排 | ⭐⭐⭐ |

---

> 创建时间: 2026-07-15 | 作者: 郑
