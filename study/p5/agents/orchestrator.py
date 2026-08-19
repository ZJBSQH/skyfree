"""主编Agent — 分析灵感，规划创作任务"""

from state import NovelState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深网络小说主编，负责分析故事灵感和规划创作任务。

当收到用户的故事灵感时，你需要：
1. 分析灵感的亮点和卖点
2. 指出核心冲突和金手指设定
3. 给出创作方向的建议
4. 确认接下来的创作步骤：大纲设计 → 人物设计 → 世界观构建 → 正式写作

用简洁的要点形式输出，不要展开具体内容（后续有专业Agent负责）。"""


class OrchestratorAgent(BaseAgent):
    def invoke(self, state: NovelState) -> dict:
        inspiration = state["inspiration"]
        self._log(f"收到灵感，开始分析...")

        analysis = self._call_llm(inspiration)
        print(analysis)

        return {"messages": [analysis]}
