"""
P3 — 大纲 Agent (Ollama 版)

覆盖 P3 全部 5 个主题:
  1. Chat Completions API — messages 结构 (system/user/assistant)
  2. System Prompt 设计 — 让 LLM 扮演"大纲设计师"
  3. temperature / max_tokens — 控制创造性和输出长度
  4. 流式输出 — stream=True 逐 token 输出
  5. Ollama 适配 — base_url 替换为本地地址
"""

from langchain.chatmodel import init_chat_model

from langchain.agents import create_openai_functions_agent
# ============================================================
# 配置 — Ollama 本地 (与 DeepSeek API 的唯一区别)
# ============================================================
client = init_chat_model(
    model="qwen2.5:3b",
    base_url="http://localhost:11434/v1",
    api_key="ollama",
)


agent = create_agent(
    llm=client,
    tools=[genreate_outline, generate_outline_stream],
    system_prompt=SYSTEM_PROMPT,
    context_schema=context
)

# ============================================================
# Topic 2: System Prompt 设计
# ============================================================
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
第30章 标题 | 一句话概述"""


def generate_outline(inspiration: str) -> str:
    """非流式调用 — 等全部生成完再打印"""
    print(f"\n{'='*60}")
    print(f"灵感: {inspiration}")
    print(f"模式: 非流式 (stream=False)")
    print(f"{'='*60}\n")

    response = client.chat.completions.create(
        model=MODEL,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},   # 人设
            {"role": "user", "content": inspiration},        # 用户输入
        ],
        temperature=0.7,   # 创造性 (0=保守, 1=放飞)
        max_tokens=2000,   # 最大输出长度
        stream=False,      # 非流式
    )

    # 答案在 choices[0].message.content
    content = response.choices[0].message.content
    print(content)
    return content


def generate_outline_stream(inspiration: str) -> str:
    """流式调用 - 逐 token 打印，体验边想边输出"""
    print(f"\n{'='*60}")
    print(f"灵感: {inspiration}")
    print(f"模式: 流式 (stream=True)")
    print(f"{'='*60}\n")

    response = client.chat.completions.create(
        model=MODEL,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": inspiration},
        ],
        temperature=0.7,
        max_tokens=2000,   # 最大输出长度
        stream=True,  # ← 改这个就变流式
    )

    full_content = ""
    # 流式读取 — 逐 token 打印
    for chunk in response:
        delta = chunk.choices[0].delta
        if delta.content:
            print(delta.content, end="", flush=True)
            full_content += delta.content

    print("\n")
    return full_content


if __name__ == "__main__":
    # 测试灵感
    inspiration = "一个普通少年意外获得了穿越异世界的能力，他必须在陌生的世界中生存并寻找回家的路。"

    agent.invoke(inspiration)