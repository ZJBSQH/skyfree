# CLAUDE.md — Freesky

多Agent创意写作助手 (Multi-Agent Creative Writing Assistant)。

## 项目定位

- **Phase A (当前)**：学习项目 — 跑通多Agent写作核心链路
- **Phase B (后续)**：简历项目 — 功能完整、代码规范
- **Phase C (远期)**：可用产品 — 真实用户使用

## 技术栈

| 层 | 技术 | 说明 |
|----|------|------|
| 前端 | uni-app (Vue 3) | 复用已有经验 |
| 后端 | Java Spring Boot | 用户/项目/灵感/Skill CRUD |
| Agent | Python FastAPI | 多Agent编排 + LLM调用 |
| LLM | DeepSeek / 通义千问 | 国产模型起步，架构预留多模型 |
| 数据库 | MySQL | 10张表，见 design/er-diagram.drawio |

## 技术边界

```
前端 → Java (业务CRUD + API Key管理) → Python (Agent编排 + LLM调用)
```

- Java 不直接调 LLM
- Python 不存业务数据
- 通信方式：HTTP REST + SSE 流式

## Agent 架构

- **方案**：自建轻量框架（openai SDK 直调），理解原理后升级 LangGraph
- **模型**：Orchestrator + Workers（主编分配任务给专业 Agent）
- **交互**：逐步交互（每步生成 → 用户确认 → 继续）

## 设计文档

- `design/requirements.md` — 需求分析
- `design/er-diagram.drawio` — 数据库 ER 图

## 作者

郑
