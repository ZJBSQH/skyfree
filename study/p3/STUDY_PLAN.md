# P3 — openai SDK + 第一个 Agent

## 前置准备

1. 注册 [DeepSeek 开放平台](https://platform.deepseek.com)
2. 创建 API Key，充值 10 元（够测试用很久）
3. 将 API Key 设为环境变量：`export DEEPSEEK_API_KEY="sk-xxx"`

## 学习路线（约 40 分钟）

### 1. 装 SDK + 配置 (5min)

```bash
pip install openai
export DEEPSEEK_API_KEY="sk-xxx"
export DEEPSEEK_BASE_URL="https://api.deepseek.com"
```

### 2. 理解 messages 结构 (10min) ← 核心概念

Chat Completions API 用 messages 数组传对话上下文，不是单条 prompt：

```python
messages = [
    {"role": "system",    "content": "你是一个大纲设计师..."},   # 人设
    {"role": "user",      "content": "帮我写一个关于XXX的大纲"},  # 用户输入
    {"role": "assistant", "content": "好的，以下是大纲..."},      # LLM 上一次的回复
    {"role": "user",      "content": "第3章不太满意，改一下"},    # 用户又说了
]
```

role 只有三种：system / user / assistant。整个数组就是"聊天记录"。

### 3. 写 System Prompt (5min)

```python
SYSTEM_PROMPT = """你是一位资深网络小说主编，擅长设计吸引人的故事大纲。

用户会给你一个故事灵感，你需要：
1. 理解灵感的核心设定和金手指
2. 设计 30 章的故事大纲
3. 每章包含：章节标题 + 一句话剧情概述
4. 确保每章有冲突或悬念，吸引读者继续读

输出格式：
第1章 标题 | 一句话概述
第2章 标题 | 一句话概述
...
"""
```

### 4. 非流式调用 (10min)

```python
from openai import OpenAI

client = OpenAI(
    api_key="sk-xxx",
    base_url="https://api.deepseek.com"
)

response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[
        {"role": "system", "content": SYSTEM_PROMPT},
        {"role": "user", "content": "主角获得仇恨值面板"}
    ],
    temperature=0.7,   # 创造性 (0=保守, 1=放飞)
    max_tokens=2000    # 最大输出长度
)

# 答案在 choices[0].message.content
print(response.choices[0].message.content)
```

### 5. 改为流式输出 (10min)

```python
response = client.chat.completions.create(
    model="deepseek-chat",
    messages=[...],
    stream=True   # ← 改这个
)

# 流式读取 — 逐 token 打印
for chunk in response:
    if chunk.choices[0].delta.content:
        print(chunk.choices[0].delta.content, end="")
```

## 测试方法

### 第一步：curl 验证 API Key

```bash
curl https://api.deepseek.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{"model":"deepseek-chat","messages":[{"role":"user","content":"你好"}]}'
```

返回 JSON 就说明 Key 没问题。

### 第二步：Python 代码跑

```bash
python p3/outline_agent.py
```

## 产出目标

`p3/outline_agent.py` — 输入灵感一句话，输出 30 章大纲，流式返回。

## API 参考

- [DeepSeek API 文档](https://platform.deepseek.com/api-docs/)
- [OpenAI Python SDK](https://github.com/openai/openai-python)

---

> 创建时间: 2026-07-19 | 次日继续
