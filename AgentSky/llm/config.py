"""LLM 配置 -- DeepSeek API 统一初始化 + Token 消耗追踪"""

import os
from dotenv import load_dotenv
from langchain_openai import ChatOpenAI

load_dotenv()

# DeepSeek 定价 (¥/1M tokens)
PRICING = {
    "deepseek-chat": {"input": 1.0, "output": 2.0},
    "deepseek-reasoner": {"input": 4.0, "output": 16.0},
}


class TokenTracker:
    """全局 Token 消耗追踪器（线程不安全，单请求够用）"""

    def __init__(self):
        self.input_tokens = 0
        self.output_tokens = 0
        self.call_count = 0
        self.model = "unknown"

    def add(self, input_tokens: int, output_tokens: int, model: str = "deepseek-chat"):
        self.input_tokens += input_tokens
        self.output_tokens += output_tokens
        self.call_count += 1
        self.model = model

    @property
    def total_tokens(self) -> int:
        return self.input_tokens + self.output_tokens

    def cost_yuan(self, model: str = None) -> float:
        m = model or self.model
        p = PRICING.get(m, {"input": 1.0, "output": 2.0})
        cost = (self.input_tokens / 1_000_000) * p["input"] + (self.output_tokens / 1_000_000) * p["output"]
        return round(cost, 4)

    def summary(self) -> str:
        return (
            f"Token消耗: {self.call_count}次调用 | "
            f"输入{self.input_tokens} + 输出{self.output_tokens} = {self.total_tokens} tokens | "
            f"费用约 ¥{self.cost_yuan()}"
        )

    def to_dict(self) -> dict:
        return {
            "call_count": self.call_count,
            "input_tokens": self.input_tokens,
            "output_tokens": self.output_tokens,
            "total_tokens": self.total_tokens,
            "cost_yuan": self.cost_yuan(),
            "model": self.model,
        }


# 模块级单例（每个请求创建新的 tracker）
_tracker: TokenTracker = None


def get_tracker() -> TokenTracker:
    global _tracker
    if _tracker is None:
        _tracker = TokenTracker()
    return _tracker


def reset_tracker():
    global _tracker
    _tracker = TokenTracker()


def get_model():
    api_key = os.getenv("DEEPSEEK_API_KEY", "")
    if not api_key:
        raise RuntimeError("DEEPSEEK_API_KEY 未设置，请在 .env 文件中配置")

    print(f"  [LLM] deepseek-chat @ api.deepseek.com (key={api_key[:8]}...)")

    return ChatOpenAI(
        model="deepseek-chat",
        base_url="https://api.deepseek.com",
        api_key=api_key,
        temperature=0.7,
        max_tokens=4096,
        request_timeout=120,
    )


def test_connection():
    """快速测试 DeepSeek API 连通性"""
    model = get_model()
    from langchain_core.messages import HumanMessage
    try:
        resp = model.invoke([HumanMessage(content="hi")], config={"timeout": 15})
        print(f"  [LLM] connection OK, response: {resp.content[:50]}...")
        return True
    except Exception as e:
        print(f"  [LLM] connection FAILED: {e}")
        return False
