# P5 — 多 Agent 编排

## 前置准备

```bash
cd p5
python -m venv venv
source venv/Scripts/activate
pip install -r requirements.txt
```

Ollama 已就绪（模型：`qwen2.5:3b`）。

## 核心概念

### 1. Agent 状态传递 — NovelState TypedDict

LangGraph 用 TypedDict 定义共享状态，所有 Agent 节点读写同一个 state 对象。每个节点返回 dict（只更新自己负责的字段），LangGraph 自动合并。

```
state = {inspiration, outline, characters, world_settings, chapters, ...}
  ↓ orchestrator 返回 {messages: [...]}
  ↓ outline 返回 {outline: "...", messages: [...]}
  ↓ character 返回 {characters: "...", messages: [...]}
  ↓ worldbuilding 返回 {world_settings: "...", messages: [...]}
  ↓ writer 返回 {chapters: [...], current_chapter: 2, messages: [...]}
```

### 2. 错误重试

`@retry_on_failure(max_retries=3)` 装饰器包裹 LLM 调用，指数退避（2s → 4s → 8s）。

### 3. 逐步交互控制

LangGraph 按 `add_edge` 顺序串行执行节点，每个 Agent 完成后打印输出，流式展示进度。

### 4. 上下文拼接

写第N章时，`build_writer_context()` 拼接：灵感 + 大纲 + 人物档案 + 世界观设定 + 前N-1章摘要。

### 5. LangGraph 编排

```python
builder = StateGraph(NovelState)
builder.add_node("orchestrator", orch_node)
builder.add_node("outline", outline_node)
builder.add_node("character", character_node)
builder.add_node("worldbuilding", worldbuilding_node)
builder.add_node("writer", writer_node)

builder.add_edge(START, "orchestrator")
builder.add_edge("orchestrator", "outline")
...
builder.add_edge("writer", END)

workflow = builder.compile()
result = workflow.invoke(initial_state)
```

## 项目结构

```
p5/
├── config.py          # LLM 初始化
├── state.py           # NovelState TypedDict
├── main.py            # 入口
├── agents/
│   ├── base.py        # BaseAgent + @retry_on_failure
│   ├── orchestrator.py
│   ├── outline.py
│   ├── character.py
│   ├── worldbuilding.py
│   └── writer.py
├── graph/
│   └── workflow.py    # LangGraph StateGraph
└── utils/
    ├── retry.py       # 重试装饰器
    └── context.py     # 上下文拼接
```

## 测试方法

```bash
python main.py
```

输入灵感，观察5个Agent依次执行。

---

> 创建时间: 2026-07-23 | 学习时长: 约90分钟
