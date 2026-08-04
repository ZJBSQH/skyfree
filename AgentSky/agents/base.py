"""BaseAgent — 所有 Agent 的抽象基类，含 JSON 解析 + 重试"""

import json
import re
import time
from abc import ABC, abstractmethod
from typing import Optional
from langchain_openai import ChatOpenAI
from state import AgentSkyState


class BaseAgent(ABC):
    """Agent 基类

    提供:
      - _call_llm: LLM 调用
      - _call_llm_json: 调用并提取 JSON（自动处理 markdown 代码块）
      - _retry_on_failure: 指数退避重试
    """

    def __init__(self, model: ChatOpenAI, system_prompt: str, name: Optional[str] = None):
        self.model = model
        self.system_prompt = system_prompt
        self.name = name or self.__class__.__name__

    @abstractmethod
    def invoke(self, state: AgentSkyState) -> dict:
        ...

    def _call_llm(self, user_message: str) -> str:
        from langchain_core.messages import SystemMessage, HumanMessage
        from llm.config import get_tracker

        response = self.model.invoke([
            SystemMessage(content=self.system_prompt),
            HumanMessage(content=user_message),
        ])

        # 提取 token 用量
        usage = {}
        if hasattr(response, "response_metadata"):
            usage = response.response_metadata.get("token_usage", {})
        if not usage and hasattr(response, "usage_metadata"):
            usage = response.usage_metadata

        input_tokens = usage.get("prompt_tokens", 0) or usage.get("input_tokens", 0)
        output_tokens = usage.get("completion_tokens", 0) or usage.get("output_tokens", 0)

        if input_tokens or output_tokens:
            get_tracker().add(input_tokens, output_tokens, model="deepseek-chat")

        return response.content

    def _call_llm_json(self, user_message: str, max_retries: int = 2) -> dict:
        """调用 LLM 并解析 JSON 返回，自动重试解析失败"""
        last_error = None
        for attempt in range(max_retries + 1):
            try:
                raw = self._call_llm(user_message)
                return self._extract_json(raw)
            except (json.JSONDecodeError, ValueError) as e:
                last_error = e
                if attempt < max_retries:
                    time.sleep(1)
                    user_message = f"{user_message}\n\n上次输出格式错误({e})，请严格输出JSON格式。"
        raise RuntimeError(f"JSON解析失败(重试{max_retries}次): {last_error}")

    @staticmethod
    def _extract_json(text: str) -> dict:
        """从 LLM 响应中提取 JSON，处理 markdown 代码块"""
        text = text.strip()

        # 尝试匹配 ```json ... ``` 代码块
        m = re.search(r"```(?:json)?\s*\n?(.*?)\n?```", text, re.DOTALL)
        if m:
            text = m.group(1).strip()

        # 尝试直接解析
        try:
            return json.loads(text)
        except json.JSONDecodeError:
            pass

        # 平衡括号匹配：从第一个 { 找到配对的 }
        start = text.find("{")
        if start == -1:
            raise ValueError(f"No JSON object found in response: {text[:200]}")

        depth = 0
        for i in range(start, len(text)):
            ch = text[i]
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth == 0:
                    return json.loads(text[start : i + 1])

        raise ValueError(f"Unmatched braces in response: {text[:200]}")

    def _log(self, message: str):
        print(f"  [{self.name}] {message}")
