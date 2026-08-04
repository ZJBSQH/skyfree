# Freesky — 多Agent创意写作助手

> 用多 Agent 协作的方式，把一句灵感创作成完整的小说正文。

## 项目定位

| 阶段 | 说明 |
|------|------|
| **Phase A（当前）** | 学习项目 — 跑通多Agent写作核心链路 |
| **Phase B（后续）** | 简历项目 — 功能完整、代码规范 |
| **Phase C（远期）** | 可用产品 — 真实用户使用 |

## 架构

一个写作流程由 6 个专业 Agent 协作完成，`supervisor`（主编）负责调度：

```
supervisor ──→ setting / character / plot ──→ writer ──→ reviewer
  主编调度        设定师 / 人物 / 剧情        写手       审核专家
                                              ↑              │
                                              └──────◄───────┘
                                              (未通过则返工)
```

- **supervisor** 主编：解析需求，路由任务，协调审核闭环
- **setting** 设定师：构建世界观 / 势力 / 能力规则
- **character** 人物设计师：设计人物卡与人物关系
- **plot** 剧情策划：设计大纲与伏笔
- **writer** 写手：将蓝图转化为正文
- **reviewer** 审核专家：五维检查（设定冲突 / 逻辑 / OOC / 文笔 / 需求偏离）

图编排基于 **LangGraph**，采用 Supervisor 模式：各节点通过 `Command(goto=...)` 决定下一跳路由。

### RAG 写作资料库

writer / setting / character / plot 四个 Agent 在写前会检索本地 `data/reference/` 素材向量库（FAISS），把相关参考资料注入各自提示词，保证创作贴合已有设定与风格。

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 前端 | uni-app (Vue 3) | 复用已有经验 |
| 后端 | Java Spring Boot | 用户/项目/灵感/Skill CRUD |
| Agent | Python FastAPI | 多Agent编排 + LLM调用 |
| LLM | DeepSeek | 国产模型起步，架构预留多模型 |
| RAG | FAISS + SentenceTransformer | 写作资料向量检索 |
| 数据库 | MySQL | 10张表，见 `design/er-diagram.drawio` |

技术边界：前端 → Java（业务 CRUD + API Key 管理）→ Python（Agent 编排 + LLM 调用）。Java 不直接调 LLM，Python 不存业务数据，通信走 HTTP REST + SSE 流式。

## 快速开始

```bash
cd AgentSky
python -m venv venv
venv\Scripts\activate           # Windows
pip install -r requirements.txt

# 配置 DeepSeek API Key
cp .env.example .env
# 编辑 .env 填入 DEEPSEEK_API_KEY

# 交互式/命令行运行
python main.py "一个被宗门视为废物的少年，意外觉醒了'仇恨值系统'"

# 启动 Web 服务（端口 8765）
python server.py
```

### 准备 RAG 素材（可选）

向 `AgentSky/data/reference/` 放入 `.txt` 参考文档（如世界观设定集、写作风格示例），启动时自动建索引，供 writer 等 Agent 检索。

## 目录结构

```
├── AgentSky/               # Python 多Agent编排核心
│   ├── agents/             # 6 个 Agent + BaseAgent + RAG
│   ├── graph/              # LangGraph 工作流（supervisor 模式 + Command）
│   ├── llm/                # LLM 配置 + Token 追踪
│   ├── state.py            # 全局状态 TypedDict
│   ├── server.py           # FastAPI 服务
│   └── main.py             # 命令行入口
├── Design/                 # 需求分析 + ER 图
└── docs/                   # 设计文档
```

## 设计文档

- `Design/requirements.md` — 需求分析
- `Design/er-diagram.drawio` — 数据库 ER 图
- `docs/superpowers/specs/` — 功能设计 spec

## 作者

郑
