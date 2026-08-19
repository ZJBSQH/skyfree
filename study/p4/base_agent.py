from langchain.chat_models import init_chat_model
from langchain.agents import create_agent
from langchain.tools import tool

# ============================================================
# 1. 创建 LLM 配置 — 接 Ollama 本地模型
# ============================================================
model = init_chat_model(
    model="qwen2.5:3b",
    model_provider="openai",            # ← 必须！告诉 LangChain 用 OpenAI 兼容接口
    base_url="http://localhost:11434/v1",
    api_key="ollama",
    temperature=0.7,
)

# ============================================================
# 2. 定义 System Prompt
# ============================================================
SYSTEM_PROMPT = """你是一位资深网络小说主编，擅长设计吸引人的故事大纲。

当你收到一个故事灵感时，你需要调用 generate_outline 工具来生成大纲。
该工具会返回完整的 30 章大纲内容。
"""


# ============================================================
# 3. 定义工具 — Agent 可以调用的函数
# ============================================================
@tool
def generate_outline(inspiration: str) -> str:
    """根据故事灵感生成 30 章大纲，每章包含标题 + 一句话概述。

    Args:
        inspiration: 故事灵感描述，例如'主角穿越到修真世界，获得仇恨值面板'
    """
    # 直接用 LLM 生成大纲
    outline_prompt = f"""请根据以下灵感设计 30 章故事大纲：

灵感：{inspiration}

输出格式：
第1章 标题 | 一句话概述
第2章 标题 | 一句话概述
...
第30章 标题 | 一句话概述"""

    response = model.invoke(outline_prompt)
    return response.content


# ============================================================
# 4. 创建 Agent
# ============================================================
agent = create_agent(
    model=model,
    tools=[generate_outline],
    system_prompt=SYSTEM_PROMPT,
)

# ============================================================
# 5. 运行
# ============================================================
if __name__ == "__main__":
    inspiration = "一个普通少年意外获得了穿越异世界的能力，他必须在陌生的世界中生存并寻找回家的路。"

    print("=" * 60)
    print(f"灵感: {inspiration}")
    print("=" * 60)

    result = agent.invoke(
        {"messages": [{"role": "user", "content": inspiration}]}
    )

    # 打印最终回复
    for msg in result["messages"]:
        if hasattr(msg, "content") and msg.content:
            print(msg.content)
