"""BaseAgent — 所有 Agent 的抽象基类，含 JSON 解析 + 重试"""

import json
import re
import time
from abc import ABC, abstractmethod
from typing import Optional
from langchain_openai import ChatOpenAI
from state import AgentSkyState
from langchain_core.messages import SystemMessage, HumanMessage
from llm.config import get_tracker
from typing import List, Dict, Any


class AgentOutputError(ValueError):
    """Raised when an agent response cannot be parsed or validated."""


class BaseAgent(ABC):
    """Agent 基类

    提供:
      - _call_llm: LLM 调用
      - _call_llm_json: 调用并提取 JSON（自动处理 markdown 代码块）
      - _retry_on_failure: 指数退避重试
    """

    def __init__(
        self,
        model: ChatOpenAI,
        system_prompt: str,
        name: Optional[str] = None,
        store=None,
        enable_rag: bool = True,
        enable_log: bool = True
    ):
        self.model = model
        self.system_prompt = system_prompt
        self.name = name or self.__class__.__name__
        self.store = store
        self.enable_rag = enable_rag
        self.enable_log = enable_log
        #自动读取模型名称,解决硬编码问题
        self.model_name = getattr(self.model, "model_name", "unknown-model")

    @abstractmethod
    def invoke(self, state: AgentSkyState) -> dict:
        ...

    def _call_llm(self, user_message: str) -> str:
        """调用 LLM 并返回文本响应，同时记录 token 用量"""
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
            get_tracker().add(input_tokens, output_tokens, model=self.model_name)

        return response.content

    def _call_llm_json(self, user_message: str, max_retries: int = 2) -> dict:
        """调用 LLM 并解析 JSON；重试耗尽后抛出异常。"""
        last_error = None
        for attempt in range(max_retries + 1):
            try:
                raw = self._call_llm(user_message)
                result = self._extract_json(raw)
                if not isinstance(result, dict):
                    raise ValueError("JSON response must be an object")
                return result
            except (json.JSONDecodeError, ValueError) as e:
                last_error = e
                if attempt < max_retries:
                    sleep_time = 1 * (attempt+1)  # 指数退避

                    time.sleep(sleep_time)
                    user_message = f"{user_message}\n\n上次输出格式错误({e})，请严格输出JSON格式。"
        self._log(f"JSON解析最终失败(重试{max_retries}次): {last_error}")
        raise AgentOutputError(
            f"{self.name} failed to return valid JSON after {max_retries + 1} attempts"
        ) from last_error

    def _validate_result(self, result: dict, schema: dict[str, type], context: str) -> None:
        for field, expected_type in schema.items():
            if field not in result:
                raise AgentOutputError(f"{context} output missing required field: {field}")
            if not isinstance(result[field], expected_type):
                raise AgentOutputError(
                    f"{context} field {field} must be {expected_type.__name__}"
                )

    @staticmethod
    def _extract_json(text: str) -> Dict[str, Any]:
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

    def _retrieve_rag(self, state, top_k: int = 3, query_max_len: int = 800) -> List[Dict[str, Any]]:
        """用 user_request + task_context 检索写作资料；无 store 或无结果返回 []"""
        if self.store is None or not self.enable_rag:
            return []
        raw_query = f"{state.get('user_request', '')} {state.get('task_context', '')}".strip()
        query = raw_query[:query_max_len]  # 限制长度，避免过长
        if not query:
            return []
        return self.store.search(query, top_k=top_k)

    def _log(self, message: str):
        if self.enable_log:
            print(f"  [{self.name}] {message}")
