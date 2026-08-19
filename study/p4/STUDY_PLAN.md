# P4 — LangChain 入门

## 前置准备

```bash
cd p4
python -m venv venv
source venv/Scripts/activate
pip install langchain langchain-openai langchain-core
```

Ollama 已就绪（模型：`qwen2.5:3b`）。

## 学习路线（约 60 分钟）

### 1. LLM 封装 — ChatOpenAI 接 Ollama (5min)

LangChain 用 `ChatOpenAI` 对接任何 OpenAI 兼容端点：

```python
from langchain_openai import ChatOpenAI

model = ChatOpenAI(
    base_url="http://localhost:11434/v1",
    api_key="ollama",
    model="qwen2.5:3b",
    temperature=0.7,
    max_tokens=500,
)

response = model.invoke("你好")
print(response.content)
```

`invoke()` 返回 `AIMessage` 对象，`.content` 取文本。

### 2. Prompt Template (10min)

`ChatPromptTemplate` 用占位符 `{variable}` 动态拼 prompt：

```python
from langchain_core.prompts import ChatPromptTemplate

prompt = ChatPromptTemplate.from_messages([
    ("system", "你是一位{role}，擅长{skill}。"),
    ("human", "{user_input}"),
])

# 填坑 → 生成真正的 messages
messages = prompt.invoke({
    "role": "大纲设计师",
    "skill": "设计吸引人的故事大纲",
    "user_input": "主角穿越到修真世界",
})
```

### 3. Chain — LCEL 管道 (10min)

LCEL (LangChain Expression Language) 用 `|` 串联组件：

```python
from langchain_core.output_parsers import StrOutputParser

chain = prompt | model | StrOutputParser()

# 一条管道：prompt → model → 字符串输出
result = chain.invoke({...})  # result 是纯字符串
```

对比 P3 的 raw SDK：不用手动 `response.choices[0].message.content` 了。

### 4. Memory — 多轮对话记忆 (10min)

```python
from langchain_core.chat_history import InMemoryChatMessageHistory
from langchain_core.runnables.history import RunnableWithMessageHistory

store = {}

def get_history(session_id: str):
    if session_id not in store:
        store[session_id] = InMemoryChatMessageHistory()
    return store[session_id]

conversation = RunnableWithMessageHistory(chain, get_history)
# 传入 config={"configurable": {"session_id": "1"}} 自动记住上下文
```

### 5. Agent + Tools (15min)

Agent 能自己决定是否调用工具：

```python
from langchain.agents import AgentExecutor, create_tool_calling_agent
from langchain.tools import tool

@tool
def get_current_chapter(chapter_num: int) -> str:
    """查询已写章节的摘要"""
    # 实际项目中从数据库读
    return f"第{chapter_num}章摘要：..."

tools = [get_current_chapter]

agent = create_tool_calling_agent(model, tools, prompt)
executor = AgentExecutor(agent=agent, tools=tools)
result = executor.invoke({"user_input": "写第5章"})
```

### 6. 流式输出 (5min)

LangChain 的流式：

```python
for chunk in chain.stream({...}):
    print(chunk, end="", flush=True)
```

## 测试方法

```bash
python p4/langchain_outline.py
```

## 产出目标

`p4/langchain_outline.py` — 用 LangChain 重写大纲 Agent，包含：
- Chain 管道替代 raw API
- Memory 记住用户偏好
- 一个自定义 Tool（如查询章节数）

---

> 创建时间: 2026-07-21
