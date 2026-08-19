"""BaseAgent — 所有Agent的抽象基类"""

from abc import ABC, abstractmethod
from langchain_openai import ChatOpenAI
from state import NovelState
from utils.retry import retry_on_failure


class BaseAgent(ABC):
    """Agent 基类，提供 LLM 调用 + 错误重试能力"""

    def __init__(self, model: ChatOpenAI, system_prompt: str):
        self.model = model
        self.system_prompt = system_prompt
        self.name = self.__class__.__name__

    @abstractmethod
    def invoke(self, state: NovelState) -> dict:
        """子类实现：接收状态，返回部分更新的字段。"""
        ...

    @retry_on_failure(max_retries=3)
    def _call_llm(self, user_message: str) -> str:
        """封装 LLM 调用，自动重试3次"""
        from langchain_core.messages import SystemMessage, HumanMessage

        response = self.model.invoke([
            SystemMessage(content=self.system_prompt),
            HumanMessage(content=user_message),
        ])
        return response.content

    def _log(self, message: str):
        print(f"\n{'='*60}")
        print(f"  [{self.name}] {message}")
        print(f"{'='*60}\n")
