"""人物Agent — 基于大纲设计人物档案"""

from state import NovelState
from agents.base import BaseAgent

SYSTEM_PROMPT = """你是一位资深人物设计师，擅长塑造立体、有魅力的角色。

根据故事灵感和大纲，设计完整的人物档案。要求：

1. 主角（1个）：名称、身份、性格特点、背景故事、核心动机、金手指
2. 重要配角（2-3个）：名称、身份、与主角的关系、性格特点、在故事中的作用
3. 反派（1个）：名称、身份、动机、与主角的冲突点

输出格式用简洁的分类描述，每个人物约150-200字。"""


class CharacterAgent(BaseAgent):
    def invoke(self, state: NovelState) -> dict:
        inspiration = state["inspiration"]
        outline = state.get("outline", "")

        user_msg = f"故事灵感：{inspiration}\n\n故事大纲：\n{outline}"
        self._log("基于大纲设计人物档案...")

        characters = self._call_llm(user_msg)
        print(characters)

        return {"characters": characters, "messages": [characters]}
