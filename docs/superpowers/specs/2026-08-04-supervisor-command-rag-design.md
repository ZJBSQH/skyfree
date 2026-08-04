# 设计：Supervisor + Command 路由重构 & RAG 写作资料向量库

日期：2026-08-04
作者：郑
状态：已确认

## 背景

AgentSky 的 LangGraph 工作流（[graph/workflow.py](../../../AgentSky/graph/workflow.py)）当前使用两个条件边（`route_supervisor`、`route_reviewer`）完成路由。希望改为 LangGraph 官方 supervisor 模式：节点直接返回 `Command(goto=...)` 决定下一跳，删除条件边。同时将已有但未接入的 RAG 模块（[agents/rag.py](../../../AgentSky/agents/rag.py)）接入图，作为 writer / setting / character / plot 四个 Agent 的写作资料向量库。

## Part 1 — Supervisor 模式 + Command 路由

### 目标

- 删除 `route_supervisor`、`route_reviewer` 两个条件路由函数
- 删除两处 `add_conditional_edges`
- 路由改由节点返回 `Command(goto=..., update=...)` 决定
- 删除 `next_action`、`task_queue` 两个状态字段
- SupervisorAgent 直接输出节点名（`setting`/`character`/`plot`/`writer`/`finish`），`finish` 映射 `END`

### 新图结构

```
START → supervisor ──(Command)──→ setting / character / plot / writer / END
setting / character / plot → supervisor  (固定边，汇报)
writer → reviewer                    (固定边，写完必审)
reviewer ──(Command)──→ supervisor (未通过且未超轮次) / END (通过或超轮次)
```

### 具体改动

**graph/workflow.py**
- 导入 `from langgraph.types import Command`
- 删除 `route_supervisor`、`route_reviewer` 函数
- `supervisor_node`：调用 `_AGENTS["supervisor"].invoke(state)`，`result.pop("next_action")` 得目标，`finish`→`END`，否则为目标节点名；返回 `Command(goto=..., update=result)`
- `reviewer_node`：调用 `_AGENTS["reviewer"].invoke(state)`，按 `review_passed` 和 `review_round >= max_review_rounds` 决定 goto 为 `END` 或 `supervisor`；返回 `Command(goto=..., update=result)`
- 删除两处 `add_conditional_edges`，保留固定边

**state.py**
- `AgentSkyState` 删除 `next_action: str`、`task_queue: list[str]`
- `make_initial_state` 同步删除两处初始化

**agents/supervisor.py**
- SYSTEM_PROMPT 路由规则取值：`setting_agent→setting`、`character_agent→character`、`plot_agent→plot`、`writer_agent→writer`
- `_route_blueprint_ready`：`"writer_agent"`→`"writer"`，删 `task_queue`
- `_route_by_llm`：决策值改节点名，删 `task_queue`
- `_handle_review`：`action_map` 改为 `{"setting": "setting", "character": "character", "plot": "plot", "writer": "writer"}`，删 `task_queue`
- `next_action` 键名保留，作为 agent 与节点包装层的内部交接字段（被节点层 pop，不进 state）

## Part 2 — RAG 写作资料向量库

### 目标

本地文件夹素材预载入 FAISS 向量库；writer / setting / character / plot 四个 Agent 在 `invoke` 内部自检索相关素材并注入各自 prompt。不加新图节点，检索结果不写入 state。

### 具体改动

**agents/rag.py（重构）**
- 保留 `RagStore`，新增 `ingest_from_folder(folder)`：扫描 `*.txt` 文件，每个文件作为一条文档，构建 FAISS 索引
- 新增模块级单例 `get_store()`（仿 [llm/config.py](../../../AgentSky/llm/config.py) 的 `get_tracker()`），首次调用时从 `data/reference/` 载入
- 删除 `RagAgent`（"Agent 内部自检索"模式下不再需要）

**agents/base.py**
- `BaseAgent.__init__` 增加可选参数 `store=None`

**agents/writer.py、setting.py、character.py、plot.py**
- 构造函数透传 `store`（默认 None）
- `invoke` 内：用 `state["user_request"] + state["task_context"]` 作检索 query，`store.search(top_k=3)`
- 有结果则向 prompt 追加 `## 参考资料（向量检索）` 段（含来源与内容），无结果或无 store 则跳过
- supervisor、reviewer 不接入

**graph/workflow.py**
- `_ensure_agents` 中调用 `get_store()` 并注入四个 Agent 构造函数

**数据目录**
- 新建 `AgentSky/data/reference/`，放示例素材 `.txt`（如世界观、写作风格示例）

## 文件改动清单

| 文件 | 改动 |
|------|------|
| `AgentSky/graph/workflow.py` | 删条件边，改 Command，agent 构造注入 store |
| `AgentSky/state.py` | 删 next_action / task_queue |
| `AgentSky/agents/supervisor.py` | 决策值改节点名，删 task_queue |
| `AgentSky/agents/base.py` | 构造函数加 `store` 可选参数 |
| `AgentSky/agents/rag.py` | 重构：ingest_from_folder + get_store 单例，删 RagAgent |
| `AgentSky/agents/writer.py` | invoke 内检索并注入参考资料段 |
| `AgentSky/agents/setting.py` | 同上 |
| `AgentSky/agents/character.py` | 同上 |
| `AgentSky/agents/plot.py` | 同上 |
| `AgentSky/data/reference/` | 新建目录 + 示例素材 |

`server.py`、`main.py` 无需改动。

## 错误处理与边界

- 素材目录为空或 store 为空索引：`search` 返回空列表，各 Agent 静默跳过，不影响原有流程
- `next_action` 缺失时默认 `finish`（进 END），保证图不中断
- reviewer 的防死循环逻辑（`max_review_rounds`）从 `route_reviewer` 迁移进 `reviewer_node`

## 验证

1. `workflow.get_graph()` 打印节点与边，确认无条件边、节点数正确
2. 单测 `RagStore`：`ingest_from_folder` + `search` 在本地小语料上返回相关结果
3. 有 API Key 则 `python main.py` 跑通全流程；无 Key 则验证图编译 + RAG 检索
